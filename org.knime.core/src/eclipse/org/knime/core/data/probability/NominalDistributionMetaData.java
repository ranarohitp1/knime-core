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
 *   Oct 7, 2019 (Adrian Nembach, KNIME GmbH, Konstanz, Germany): created
 */
package org.knime.core.data.probability;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

import org.knime.core.data.DataCell;

/**
 * Holds information shared by multiple {@link ProbabilityDistributionCell ProbabilityDistributionCells} created by the
 * same process e.g. the values the distribution is defined over.
 *
 * @author Adrian Nembach, KNIME GmbH, Konstanz, Germany
 */
final class NominalDistributionMetaData {

    private static final MemoryAlertAwareGuavaCache CACHE = new MemoryAlertAwareGuavaCache();

    private final LinkedHashMap<DataCell, Integer> m_valueMap = new LinkedHashMap<>();

    private final UUID m_id;

    NominalDistributionMetaData(final DataCell[] values) {
        this(UUID.randomUUID(), values);
    }

    private NominalDistributionMetaData(final UUID id, final DataCell[] values) {
        m_id = id;
        Arrays.stream(values).forEach(v -> m_valueMap.put(v, m_valueMap.size()));
    }

    UUID getID() {
        return m_id;
    }

    /**
     * @param cell the cell for which to retrieve the index
     * @return the index of <b>cell</b> or -1 if <b>cell</b> is unknown
     */
    int getIndex(final DataCell cell) {
        final Integer index = m_valueMap.get(cell);
        return index == null ? -1 : index.intValue();
    }

    int size() {
        return m_valueMap.size();
    }

    Set<DataCell> getValues() {
        return Collections.unmodifiableSet(m_valueMap.keySet());
    }

    void write(final ObjectOutputStream out) throws IOException {
        out.writeObject(m_id);
        // TODO how do we use custom serializers for cells
        out.writeObject(m_valueMap.keySet().toArray(new DataCell[0]));
    }

    static NominalDistributionMetaData read(final ObjectInputStream in)
        throws ClassNotFoundException, IOException, ExecutionException {
        final UUID id = (UUID)in.readObject();
        return CACHE.get(id, () -> new NominalDistributionMetaData(id, (DataCell[])in.readObject()));
    }

}
