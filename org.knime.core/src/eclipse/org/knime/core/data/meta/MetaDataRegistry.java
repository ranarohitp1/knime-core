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
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
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

    private final Map<Class<? extends MetaData>, MetaDataSerializer<?>> m_serializers;

    private final Map<Class<? extends MetaData>, MetaDataCreatorFactory<?>> m_creatorFactories;

    private Map<String, Class<? extends MetaData>> m_metaDataClasses;

    private MetaDataRegistry() {
        final IExtensionPoint point = getExtensionPoint();
        m_creatorFactories = Arrays.stream(point.getExtensions()).map(IExtension::getConfigurationElements)
            .flatMap(Arrays::stream).filter(e -> e.getAttribute("creatorFactory") != null)
            .map(e -> createInstance(e, "creatorFactory", MetaDataCreatorFactory.class,
                "Could not create meta data creator factory from plug-in '%s'", e.getNamespaceIdentifier()))
            .filter(Objects::nonNull).collect(Collectors.toMap(MetaDataCreatorFactory::getMetaDataClass,
                Function.identity(), MetaDataRegistry::mergeCreatorFactories, LinkedHashMap::new));

        m_serializers = new HashMap<>();
        m_metaDataClasses = new HashMap<>();
        for (IExtension ext : point.getExtensions()) {
            for (IConfigurationElement config : ext.getConfigurationElements()) {
                final String metaDataClassName = config.getAttribute("metaData");
                final Class<? extends MetaData> metaDataClass = getMetaDataClass(metaDataClassName);
                if (metaDataClass == null) {
                    continue;
                }
                m_metaDataClasses.put(metaDataClassName, metaDataClass);
                final MetaDataSerializer<?> serializer = createInstance(config, "serializer", MetaDataSerializer.class,
                    "Could not create serializer for meta data '%s' from plug-in '%s'.", metaDataClassName,
                    config.getNamespaceIdentifier());
                m_serializers.put(metaDataClass, serializer);
            }
        }

    }

    private static Class<? extends MetaData> getMetaDataClass(final String name) {
        final Class<?> clazz;
        try {
            clazz = Class.forName(name);
        } catch (ClassNotFoundException ex) {
            LOGGER.error(String.format("The class declaration for the meta data class '%s' could not be found.", name),
                ex);
            return null;
        }
        return clazz.asSubclass(MetaData.class);
    }

    public <M extends MetaData> Class<M> getClass(final M metaData) {
        final String name = metaData.getClass().getName();
        final Class<? extends MetaData> wildcardClass = m_metaDataClasses.get(name);
        CheckUtils.checkState(wildcardClass != null,
            "The meta data class '%s' is not registered at the MetaDataType extension point.", name);
        // m_metaDataClasses maps from class names to there class instance
        @SuppressWarnings("unchecked")
        final Class<M> typedClass = (Class<M>)wildcardClass;
        return typedClass;
    }

    private static MetaDataCreatorFactory<?> mergeCreatorFactories(final MetaDataCreatorFactory<?> first,
        final MetaDataCreatorFactory<?> second) {
        assert first.getMetaDataClass().equals(second.getMetaDataClass());
        LOGGER.errorWithFormat(
            "Two meta data factories for the meta data '%s' registered."
                + "This indicates an implementation error and only the first one is kept.",
            first.getMetaDataClass().getName());
        return first;
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

    public <T extends MetaData> MetaDataCreator<T> getCreator(final Class<T> metaDataClass) {
        final MetaDataCreatorFactory<T> factory = retrieveTyped(m_creatorFactories, metaDataClass);
        return factory.create();
    }

    private interface RefObj<T> {
        Class<T> getRefClass();
    }

    private static <M extends MetaData, F extends MetaDataFramework<M>> F retrieveTyped(
        final Map<Class<? extends MetaData>, ? extends MetaDataFramework<?>> map, final Class<M> metaDataClass) {
        MetaDataFramework<?> wildcardFrameworkObject = map.get(metaDataClass);
        CheckUtils.checkState(wildcardFrameworkObject != null, "Unregistered meta data '%s' encountered.",
            metaDataClass.getName());
        return checkAndCast(metaDataClass, wildcardFrameworkObject);
    }

    private static <M extends MetaData, F extends MetaDataFramework<M>> F checkAndCast(final Class<M> metaDataClass,
        final MetaDataFramework<?> frameworkObject) {
        CheckUtils.checkState(metaDataClass.equals(frameworkObject.getMetaDataClass()), "Illegal mapping detected.");
        // the check ensures that frameworkObject indeed applies to M
        @SuppressWarnings("unchecked")
        final F typedFrameworkObject = (F)frameworkObject;
        return typedFrameworkObject;
    }

    public <T extends MetaData> MetaDataCreator<T> getCreator(final T metaData) {
        Class<T> metaDataClass = getClass(metaData);
        return getCreator(metaDataClass).merge(metaData);
    }

    /**
     * Fetches a collection of all {@link MetaDataCreator creators} that can be used to create meta data for columns of
     * {@link DataType} type. </br>
     * An empty collection is returned if there are no {@link MetaDataCreator creators} for this type.
     *
     * @param type the {@link DataType type} for which the {@link MetaDataCreator MetaDataCreators} are required
     * @return the {@link MetaDataCreator creators} for meta data referring to {@link DataType type}
     */
    public Collection<MetaDataCreator<?>> getCreators(final DataType type) {
        CheckUtils.checkNotNull(type);
        return type.getValueClasses().stream()
            .flatMap(d -> m_creatorFactories.values().stream().filter(m -> m.getDataValueClass().isAssignableFrom(d)))
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
        return type.getValueClasses().stream().anyMatch(
            d -> m_creatorFactories.values().stream().anyMatch(m -> m.getDataValueClass().isAssignableFrom(d)));
    }

    /**
     * Gets the {@link MetaDataSerializer} for {@link MetaData} of class <b>metaDataClass</b>.
     *
     * @param metaDataClass the class of {@link MetaData} the serializer is required for
     * @return the serializer for {@link MetaData} of class <b>metaDataClass</b>
     */
    public <T extends MetaData> MetaDataSerializer<T> getSerializer(final Class<T> metaDataClass) {
        return retrieveTyped(m_serializers, metaDataClass);
    }

    /**
     * Gets the {@link MetaDataSerializer} for {@link MetaData} with the class name <b>metaDataClass</b>.
     *
     * @param metaDataClassName the name of the class of {@link MetaData} the serializer is required for
     * @return the serializer for {@link MetaData} with class name <b>metaDataClassName</b>
     */
    public MetaDataSerializer<?> getSerializer(final String metaDataClassName) {
        return m_serializers.get(getMetaDataClass(metaDataClassName));
    }

    private static IExtensionPoint getExtensionPoint() {
        final IExtensionRegistry registry = Platform.getExtensionRegistry();
        final IExtensionPoint point = registry.getExtensionPoint(EXT_POINT_ID);
        assert point != null : "Invalid extension point id: " + EXT_POINT_ID;
        return point;
    }

}
