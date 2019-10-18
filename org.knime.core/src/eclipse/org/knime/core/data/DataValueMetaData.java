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

import org.knime.core.node.InvalidSettingsException;
import org.knime.core.node.config.ConfigRO;
import org.knime.core.node.config.ConfigWO;

/**
 * This interface describes meta data that belongs to a certain type of {@link DataValue}.
 * {@link DataValueMetaData} objects are expected to be immutable (except for the load method).
 * Every implementation must provide a default constructor for serialization.
 *
 * @author Adrian Nembach, KNIME GmbH, Konstanz, Germany
 * @param <T> the type of {@link DataValue} this {@link MetaData} belongs to
 * @since 4.1
 */
public interface DataValueMetaData<T extends DataValue> {

    /**
     * Loads the meta data from the {@link ConfigRO config}.
     * @param config to load from
     * @throws InvalidSettingsException if the stored config is invalid
     */
    void load(final ConfigRO config) throws InvalidSettingsException;

    /**
     * Saves the meta data to {@link ConfigWO config}.
     * @param config to save to
     */
    void save(final ConfigWO config);

    /**
     * @return the {@link DataValue} type this meta data belongs to
     */
    Class<T> getValueType();

    /**
     * TODO
     * Merges the contents of <b>this</b> and <b>other</b> to create a new(!) {@link DataValueMetaData} object.
     * This method should not modify <b>this</b> or <b>other</b>.
     *
     * Note: Implementing classes must ensure that <b>other</b> has the correct value type i.e.
     * <code>other.getValueType().equals(this.getValueType())</code>.
     *
     * @param other the {@link DataValueMetaData} to merge with
     * @return a new {@link DataValueMetaData} object that contains the merged information of <b>this</b> and <b>other</b>
     */
    DataValueMetaData<T> merge(DataValueMetaData<?> other);

    /**
     *
     * @author Adrian Nembach, KNIME GmbH, Konstanz, Germany
     */
    public interface Serializer <M extends DataValueMetaData<?>> {

        /**
         * Creates a new {@link DataValueMetaData} object from {@link ConfigRO}.
         *
         * @param config the config defining the meta data
         * @return a new {@link DataValueMetaData} containing the meta data stored in {@link ConfigRO config}
         * @throws InvalidSettingsException if the meta data in {@link ConfigRO config} is invalid
         */
        M load(final ConfigRO config) throws InvalidSettingsException;

        /**
         * Saves {@link DataValueMetaData metaData} in {@link ConfigWO config}.
         *
         * @param metaData to save
         * @param config to save to
         */
        void save(final M metaData, final ConfigWO config);

    }

}
