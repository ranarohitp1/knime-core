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
 *   Oct 15, 2019 (Adrian Nembach, KNIME GmbH, Konstanz, Germany): created
 */
package org.knime.core.data;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IConfigurationElement;
import org.eclipse.core.runtime.IExtensionPoint;
import org.eclipse.core.runtime.IExtensionRegistry;
import org.eclipse.core.runtime.Platform;
import org.knime.core.data.DataValueMetaData.Serializer;
import org.knime.core.node.NodeLogger;

/**
 * The registry for the {@link DataValueMetaData} extension point.
 *
 * @author Adrian Nembach, KNIME GmbH, Konstanz, Germany
 * @since 4.1
 */
enum DataValueMetaDataRegistry {

        INSTANCE;

    private static final String DATA_VALUE_META_DATA_SERIALIZER_CLASS = "dataValueMetaDataSerializerClass";

    private static final String DATA_VALUE_META_DATA_CLASS = "dataValueMetaDataClass";

    private static final NodeLogger LOGGER = NodeLogger.getLogger(DataValueMetaDataRegistry.class);

    private static final String EXT_POINT_ID = "org.knime.core.DataValueMetaData";

    private final Map<Class<? extends DataValueMetaData<?>>, Serializer> m_serializers = new ConcurrentHashMap<>();

    private final Map<String, Class<? extends DataValueMetaData<?>>> m_metaDataClasses = new ConcurrentHashMap<>();

    /**
     * Static registry for {@link DataValueMetaData} extension point.
     */
    private DataValueMetaDataRegistry() {
        // just to ensure the extension point is present
        getExtensionPoint();
    }

    static DataValueMetaDataRegistry getInstance() {
        return INSTANCE;
    }

    public <M extends DataValueMetaData<?>> Optional<Serializer<M>>
        getSerializer(final Class<? extends DataValueMetaData<?>> metaDataClass) {
        final Serializer<M> serializer =
            m_serializers.computeIfAbsent(metaDataClass, k -> scanExtensionPointForSerializer(k.getName()));
        return Optional.ofNullable(serializer);
    }

    private static <M extends DataValueMetaData<?>> Serializer<M>
        scanExtensionPointForSerializer(final String metaDataClassName) {
        final IExtensionPoint point = getExtensionPoint();
        Optional<IConfigurationElement> o =
            Stream.of(point.getExtensions()).flatMap(ext -> Stream.of(ext.getConfigurationElements()))
                .filter(cfe -> cfe.getAttribute(DATA_VALUE_META_DATA_CLASS).equals(metaDataClassName)).findFirst();
        if (o.isPresent()) {
            IConfigurationElement configElement = o.get();
            return createSerializer(configElement);
        } else {
            return null;
        }
    }

    private static <M extends DataValueMetaData<?>> Serializer<M>
        createSerializer(final IConfigurationElement configElement) {
        final String metaDataClassName = configElement.getAttribute(DATA_VALUE_META_DATA_CLASS);
        try {
            @SuppressWarnings("unchecked")
            final Serializer<M> safe =
                (Serializer<M>)configElement.createExecutableExtension(DATA_VALUE_META_DATA_SERIALIZER_CLASS);
            return safe;
        } catch (CoreException ex) {
            LOGGER.errorWithFormat(String.format("Could not create meta data serializer for '%s' from plug-in '%s': %",
                metaDataClassName, ex.getMessage()), ex);
            return null;
        }
    }

    private static IExtensionPoint getExtensionPoint() {
        final IExtensionRegistry registry = Platform.getExtensionRegistry();
        final IExtensionPoint point = registry.getExtensionPoint(EXT_POINT_ID);
        assert point != null : "Invalid extension point id: " + EXT_POINT_ID;
        return point;
    }
}
