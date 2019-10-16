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
package org.knime.core.data;

import java.util.Collection;
import java.util.Optional;

/**
 * A {@link MetaData} object stores {@link DataValue} type specific meta information and allows the client to retrieve
 * this information for a particular {@link DataValue} type via the {@link MetaData#getForType(Class)} method. Since a
 * cell might implement multiple {@link DataValue} interfaces, it is possible that there are multiple
 * {@link DataValueMetaData} objects stored in a single {@link MetaData} object, one for each implemented
 * {@link DataValue} with meta data.
 *
 * TODO
 *
 * @author Adrian Nembach, KNIME GmbH, Konstanz, Germany
 * @since 4.1
 */
public interface MetaData {

    /**
     * Creates a merged {@link MetaData} object that contains both the information of {@link MetaData this} as well as
     * the information of {@link MetaData other}.
     *
     * @param other the MetaData to merge with (typically of the same class)
     * @return the merged MetaData
     * @throws IllegalArgumentException if this MetaData is incompatible with <b>other</b>
     */
    MetaData merge(MetaData other);

    /**
     * Lists all {@link DataValueMetaData} stored in this object.
     * Note that the returned collection is NOT mutable i.e. it is not possible to add or remove any elements.
     *
     * @return an immutable collection containing all {@link DataValueMetaData meta data} stored in this object
     */
    Collection<DataValueMetaData<?>> getAllMetaData();

    /**
     * Retrieves the {@link DataValueMetaData} for the {@link DataValue} with class <b>dataValueClass</b>.
     * An empty {@link Optional} is returned if no {@link DataValueMetaData} is available for <b>dataValueClass</b>.
     *
     * @param dataValueClass the type of {@link DataValue} for which the {@link DataValueMetaData} is required
     * @return the {@link DataValueMetaData} for type <b>dataValueClass</b>
     */
    <T extends DataValue> Optional<DataValueMetaData<T>> getForType(final Class<T> dataValueClass);

    /**
     * Convenience wrapper around {@link MetaData#getForType(Class)} that also casts the {@link DataValueMetaData} to the
     * expected type <b>expectedMetaDataClass</b>.
     * An empty {@link Optional} is returned if there is no {@link DataValueMetaData} available for <b>dataValueClass</b>
     * OR it cannot be casted to <b>expectedMetaDataClass</b>. (TODO discuss if we should fail in this case)
     *
     * @param dataValueClass the type of {@link DataValue} for which the {@link DataValueMetaData} is required
     * @param expectedMetaDataClass the type of {@link DataValueMetaData} that is expected by the client
     * @return the {@link DataValueMetaData} for <b>dataValueClass</b> casted to the specific type <b>expectedMetaDataClass</b>
     */
    <T extends DataValue, M extends DataValueMetaData<T>> Optional<M> getForType(final Class<T> dataValueClass,
        final Class<M> expectedMetaDataClass);
}
