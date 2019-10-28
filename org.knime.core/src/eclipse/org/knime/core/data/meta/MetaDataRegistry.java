/*
 * ------------------------------------------------------------------------
 *
 *  Copyright by KNIME AG, Zurich, Switzerland
 *  Website: http://www.knime.com; Email: contact@knime.com
 *
 *  This program is free software; you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License, Version 3, as
 *  published by the Free Software Foundation.
 *
 *  This program is distributed in the hope that it will be useful, but
 *  WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program; if not, see <http://www.gnu.org/licenses>.
 *
 *  Additional permission under GNU GPL version 3 section 7:
 *
 *  KNIME interoperates with ECLIPSE solely via ECLIPSE's plug-in APIs.
 *  Hence, KNIME and ECLIPSE are both independent programs and are not
 *  derived from each other. Should, however, the interpretation of the
 *  GNU GPL Version 3 ("License") under any applicable laws result in
 *  KNIME and ECLIPSE being a combined program, KNIME AG herewith grants
 *  you the additional permission to use and propagate KNIME together with
 *  ECLIPSE with only the license terms in place for ECLIPSE applying to
 *  ECLIPSE and the GNU GPL Version 3 applying for KNIME, provided the
 *  license terms of ECLIPSE themselves allow for the respective use and
 *  propagation of ECLIPSE together with KNIME.
 *
 *  Additional permission relating to nodes for KNIME that extend the Node
 *  Extension (and in particular that are based on subclasses of NodeModel,
 *  NodeDialog, and NodeView) and that only interoperate with KNIME through
 *  standard APIs ("Nodes"):
 *  Nodes are deemed to be separate and independent programs and to not be
 *  covered works.  Notwithstanding anything to the contrary in the
 *  License, the License does not apply to Nodes, you are not required to
 *  license Nodes under the License, and you are granted a license to
 *  prepare and propagate Nodes, in each case even if such Nodes are
 *  propagated with or for interoperation with KNIME.  The owner of a Node
 *  may freely choose the license terms applicable to such Node, including
 *  when such Node is propagated with or for interoperation with KNIME.
 * ---------------------------------------------------------------------
 *
 * History
 *   Oct 25, 2019 (Adrian Nembach, KNIME GmbH, Konstanz, Germany): created
 */
package org.knime.core.data.meta;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IConfigurationElement;
import org.eclipse.core.runtime.IExtension;
import org.eclipse.core.runtime.IExtensionPoint;
import org.eclipse.core.runtime.IExtensionRegistry;
import org.eclipse.core.runtime.Platform;
import org.knime.core.data.DataType;
import org.knime.core.node.NodeLogger;
import org.knime.core.node.util.CheckUtils;

/**
 * The registry for {@link MetaData} registered via the MetaData extension point.
 *
 * It allows to retrieve creators and serializers for MetaData.
 *
 * @author Adrian Nembach, KNIME GmbH, Konstanz, Germany
 * @since 4.1
 */
public enum MetaDataRegistry {

        /**
         * The MetaDataRegistry instance.
         */
        INSTANCE;

    private static final String EXT_POINT_ID = "org.knime.core.MetaDataType";

    private static final NodeLogger LOGGER = NodeLogger.getLogger(MetaDataRegistry.class);

    private final Map<String, MetaDataSerializer> m_serializers;

    private final Collection<MetaDataCreatorFactory> m_creatorFactories;

    private MetaDataRegistry() {
        final IExtensionPoint point = getExtensionPoint();
        m_creatorFactories = Arrays.stream(point.getExtensions()).map(IExtension::getConfigurationElements)
            .flatMap(Arrays::stream).filter(e -> e.getAttribute("creatorFactory") != null)
            .map(e -> createInstance(e, "creatorFactory", MetaDataCreatorFactory.class,
                "Could not create meta data creator factory from plug-in '%s'", e.getNamespaceIdentifier()))
            .filter(Objects::nonNull).collect(Collectors.toList());

        m_serializers = new HashMap<>();
        for (IExtension ext : point.getExtensions()) {
            for (IConfigurationElement config : ext.getConfigurationElements()) {
                final String metaDataClass = config.getAttribute("metaData");
                final MetaDataSerializer serializer = createInstance(config, "serializer", MetaDataSerializer.class,
                    "Could not create serializer for meta data '%s' from plug-in '%s'.", metaDataClass,
                    config.getNamespaceIdentifier());
                m_serializers.put(metaDataClass, serializer);
            }
        }
    }

    private static <T> T createInstance(final IConfigurationElement configElement, final String key,
        final Class<T> expectedClass, final String format, final Object... args) {
        try {
            return expectedClass.cast(configElement.createExecutableExtension(key));
        } catch (CoreException ex) {
            LOGGER.error(String.format(format + ": " + ex.getMessage(), args), ex);
            return null;
        }
    }

    /**
     * Fetches a collection of all {@link MetaDataCreator creators} that can be used to create meta data for columns of
     * {@link DataType} type. </br>
     * An empty collection is returned if there are no {@link MetaDataCreator creators} for this type.
     *
     * @param type the {@link DataType type} for which the {@link MetaDataCreator MetaDataCreators} are required
     * @return the {@link MetaDataCreator creators} for meta data referring to {@link DataType type}
     */
    public Collection<MetaDataCreator> getCreators(final DataType type) {
        CheckUtils.checkNotNull(type);
        return type.getValueClasses().stream()
            .flatMap(d -> m_creatorFactories.stream().filter(m -> m.getDataValueClass().isAssignableFrom(d)))
            .map(MetaDataCreatorFactory::create).collect(Collectors.toList());
    }

    /**
     * Checks if there are any {@link MetaDataCreator MetaDataCreators} associated with {@link DataType} type.
     *
     * @param type the {@link DataType} for which to check if there is any {@link MetaDataCreator} associated with it
     * @return true if there is at least one {@link MetaDataCreator} that can generate {@link MetaData} for
     *         {@link DataType type}
     */
    public boolean hasMetaData(final DataType type) {
        return type.getValueClasses().stream()
            .anyMatch(d -> m_creatorFactories.stream().anyMatch(m -> m.getDataValueClass().isAssignableFrom(d)));
    }

    /**
     * Gets the {@link MetaDataSerializer} for {@link MetaData} of class <b>metaDataClass</b>.
     *
     * @param metaDataClass the class of {@link MetaData} the serializer is required for
     * @return the serializer for {@link MetaData} of class <b>metaDataClass</b>
     */
    public MetaDataSerializer getSerializer(final Class<? extends MetaData> metaDataClass) {
        return m_serializers.get(metaDataClass.getName());
    }

    /**
     * Gets the {@link MetaDataSerializer} for {@link MetaData} with the class name <b>metaDataClass</b>.
     *
     * @param metaDataClassName the name of the class of {@link MetaData} the serializer is required for
     * @return the serializer for {@link MetaData} with class name <b>metaDataClassName</b>
     */
    public MetaDataSerializer getSerializer(final String metaDataClassName) {
        return m_serializers.get(metaDataClassName);
    }

    private static IExtensionPoint getExtensionPoint() {
        final IExtensionRegistry registry = Platform.getExtensionRegistry();
        final IExtensionPoint point = registry.getExtensionPoint(EXT_POINT_ID);
        assert point != null : "Invalid extension point id: " + EXT_POINT_ID;
        return point;
    }

}
