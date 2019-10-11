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
 *   Oct 9, 2019 (Adrian Nembach, KNIME GmbH, Konstanz, Germany): created
 */
package org.knime.core.data.probability;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;

import org.knime.core.data.DataCell;
import org.knime.core.data.MetaData;
import org.knime.core.node.InvalidSettingsException;
import org.knime.core.node.config.ConfigRO;
import org.knime.core.node.config.ConfigWO;
import org.knime.core.node.util.CheckUtils;

/**
 * TODO figure out if we could just reuse NominalDistributionMetaData
 *
 * @author Adrian Nembach, KNIME GmbH, Konstanz, Germany
 * @since 4.1
 */
public final class DefaultNominalDistributionValueMetaData implements NominalDistributionValueMetaData {

    private static final String CFG_VALUES = "values";

    private Set<DataCell> m_values;

    /**
     * Framework constructor.
     * @noreference This constructor is not intended to be referenced by clients.
     */
    public DefaultNominalDistributionValueMetaData() {

    }

    public DefaultNominalDistributionValueMetaData(final DataCell[] values) {
        m_values = toLinkedHashSet(values);
    }

    DefaultNominalDistributionValueMetaData(final Collection<DataCell> values) {
        m_values = new LinkedHashSet<>(values);
    }

    private DefaultNominalDistributionValueMetaData(final LinkedHashSet<DataCell> values) {
        m_values = values;
    }

    private static LinkedHashSet<DataCell> toLinkedHashSet(final DataCell[] values) {
        return Arrays.stream(values).collect(Collectors.toCollection(LinkedHashSet::new));
    }

    @Override
    public Set<DataCell> getValues() {
        return Collections.unmodifiableSet(m_values);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void load(final ConfigRO config) throws InvalidSettingsException {
        m_values = toLinkedHashSet(config.getDataCellArray(CFG_VALUES));
    }

    @Override
    public void save(final ConfigWO config) {
        config.addDataCellArray(CFG_VALUES, m_values.toArray(new DataCell[0]));
    }

    @Override
    public NominalDistributionValueMetaData merge(final MetaData other) {
        // TODO informative error message
        CheckUtils.checkArgument(other instanceof NominalDistributionValueMetaData, "Incompatible meta data type %s",
            other.getClass());
        final NominalDistributionValueMetaData otherMeta = (NominalDistributionValueMetaData)other;
        if (other == this) {
            return this;
        } else if (m_values.equals(otherMeta.getValues())) {
            return this;
        } else {
            final LinkedHashSet<DataCell> mergedValues = new LinkedHashSet<>();
            mergedValues.addAll(m_values);
            mergedValues.addAll(otherMeta.getValues());
            return new DefaultNominalDistributionValueMetaData(mergedValues);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Class<NominalDistributionValue> getValueType() {
        return NominalDistributionValue.class;
    }

}
