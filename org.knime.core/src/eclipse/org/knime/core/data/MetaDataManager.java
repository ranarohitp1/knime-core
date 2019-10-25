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

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.knime.core.data.meta.MetaData;
import org.knime.core.data.meta.MetaDataRegistry;
import org.knime.core.data.meta.MetaDataSerializer;
import org.knime.core.node.InvalidSettingsException;
import org.knime.core.node.config.ConfigRO;
import org.knime.core.node.config.ConfigWO;

/**
 * Manages {@link MetaData} for a {@link DataColumnSpec}. Currently, only {@link MetaData} is supported.
 *
 * @author Adrian Nembach, KNIME GmbH, Konstanz, Germany
 */
final class MetaDataManager {

    private final Map<Class<? extends MetaData>, MetaData> m_valueMetaDataMap;

    static final MetaDataManager EMPTY = new MetaDataManager(Collections.emptyMap());

    private MetaDataManager(final Map<Class<? extends MetaData>, MetaData> valueMetaDataMap) {
        m_valueMetaDataMap = valueMetaDataMap;
    }

    <M extends MetaData> Optional<M>
        getForType(final Class<M> metaDataClass) {
        final MetaData wildCardMetaData = m_valueMetaDataMap.get(metaDataClass);
        if (wildCardMetaData == null) {
            return Optional.empty();
        }
        @SuppressWarnings("unchecked") // the check above ensures that wildCardMetaData
        // is indeed of type MetaData<T>
        final M typedMetaData = (M)wildCardMetaData;
        return Optional.of(typedMetaData);
    }

    void save(final ConfigWO config) {
        m_valueMetaDataMap.forEach((k, v) -> MetaDataRegistry.INSTANCE.getSerializer(k).save(v, config));
    }

    static MetaDataManager load(final DataType type, final ConfigRO config) throws InvalidSettingsException {
        final Map<Class<? extends MetaData>, MetaData> metaDataMap = new HashMap<>();

        for (String key : config) {
            final MetaDataSerializer serializer = MetaDataRegistry.INSTANCE.getSerializer(key);
            final MetaData metaData = serializer.load(config.getConfig(key));
            metaDataMap.put(metaData.getClass(), metaData);
        }
        return new MetaDataManager(metaDataMap);
    }

    MetaDataManager merge(final MetaDataManager other) {
        final Creator creator = new Creator(this);
        creator.merge(other);
        return creator.create();
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

    static final class Creator {
        private final Map<Class<? extends MetaData>, MetaData> m_valueMetaDataMap;

        Creator() {
            m_valueMetaDataMap = new HashMap<>();
        }

        Creator(final MetaDataManager metaData) {
            m_valueMetaDataMap = metaData.m_valueMetaDataMap.values().stream()
                .collect(Collectors.toMap(MetaData::getClass, Function.identity()));
        }

        void addMetaData(final MetaData metaData, final boolean overwrite) {
            if (overwrite) {
                m_valueMetaDataMap.put(metaData.getClass(), metaData);
            } else {
                m_valueMetaDataMap.merge(metaData.getClass(), metaData, (m1, m2) -> m1.merge(m2));
            }
        }

        void merge(final MetaDataManager metaData) {
            metaData.m_valueMetaDataMap.values()
                .forEach(m -> m_valueMetaDataMap.merge(m.getClass(), m, (m1, m2) -> m1.merge(m2)));
        }

        MetaDataManager create() {
            return new MetaDataManager(new HashMap<>(m_valueMetaDataMap));
        }
    }

}
