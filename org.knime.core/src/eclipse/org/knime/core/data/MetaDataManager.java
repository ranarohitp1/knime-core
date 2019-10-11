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
 *   Oct 11, 2019 (Adrian Nembach, KNIME GmbH, Konstanz, Germany): created
 */
package org.knime.core.data;

import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.Map;

import org.knime.core.node.InvalidSettingsException;
import org.knime.core.node.config.ConfigRO;
import org.knime.core.node.config.ConfigWO;
import org.knime.core.node.util.CheckUtils;

/**
 * Manages {@link MetaData} for a {@link DataColumnSpec}. Currently, only {@link DataValueMetaData} is supported.
 *
 * @author Adrian Nembach, KNIME GmbH, Konstanz, Germany
 */
final class MetaDataManager {

    private final Map<Class<?>, DataValueMetaData<?>> m_valueMetaDataMap;

    MetaDataManager() {
        m_valueMetaDataMap = new HashMap<>();
    }

    private MetaDataManager(final Map<Class<?>, DataValueMetaData<?>> valueMetaDataMap) {
        m_valueMetaDataMap = valueMetaDataMap;
    }

    <T extends DataValue> DataValueMetaData<T> getMetaData(final Class<T> dataValueClass) {
        final DataValueMetaData<?> wildCardMetaData = m_valueMetaDataMap.get(dataValueClass);
        // TODO what should we do if no meta data is present? Optional?
        CheckUtils.checkState(dataValueClass.equals(wildCardMetaData.getValueType()), "Illegal mapping detected.");
        @SuppressWarnings("unchecked") // the check above ensures that wildCardMetaData
        // is indeed of type DataValueMetaData<T>
        final DataValueMetaData<T> typedMetaData = (DataValueMetaData<T>)wildCardMetaData;
        return typedMetaData;
    }

    void addMetaData(final DataValueMetaData<?> metaData) {
        // TODO thread safety? How to handle collisions?
        m_valueMetaDataMap.put(metaData.getValueType(), metaData);
    }

    void save(final ConfigWO config) {
        m_valueMetaDataMap.values().forEach(m -> m.save(config.addConfig(m.getClass().getCanonicalName())));
    }

    void load(final ConfigRO config) throws InvalidSettingsException {
        for (final String key : config) {
            loadMetaData(key, config.getConfig(key));
        }
    }

    MetaDataManager merge(final MetaDataManager other) {
        // TODO what to do with MetaData that is only contained in one of the managers?
        final HashMap<Class<?>, DataValueMetaData<?>> merged = new HashMap<>(m_valueMetaDataMap);
        other.m_valueMetaDataMap.forEach((k, v) -> merged.merge(k, v, (v1, v2) -> v1.merge(v2)));
        return new MetaDataManager(merged);
    }

    private void loadMetaData(final String metaDataClassName, final ConfigRO config) throws InvalidSettingsException {
        try {
            final Class<?> metaDataClass = Class.forName(metaDataClassName);
            final DataValueMetaData<?> metaData = (DataValueMetaData<?>)metaDataClass.getConstructor().newInstance();
            metaData.load(config);
            m_valueMetaDataMap.put(metaData.getValueType(), metaData);
        } catch (InstantiationException | IllegalAccessException | IllegalArgumentException | InvocationTargetException
                | NoSuchMethodException | SecurityException ex) {
            throw new InvalidSettingsException(
                String.format("Instantiation of a meta data object of class %s failed.", metaDataClassName), ex);
        } catch (ClassNotFoundException ex) {
            throw new InvalidSettingsException(
                String.format("Unknown meta data class %s encountered.", metaDataClassName), ex);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean equals(final Object obj) {
        return m_valueMetaDataMap.equals(obj);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode() {
        return m_valueMetaDataMap.hashCode();
    }

}
