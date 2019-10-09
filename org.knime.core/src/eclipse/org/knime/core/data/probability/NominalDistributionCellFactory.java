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
 *   Oct 8, 2019 (Adrian Nembach, KNIME GmbH, Konstanz, Germany): created
 */
package org.knime.core.data.probability;

import java.io.IOException;
import java.util.UUID;

import org.knime.core.data.DataCell;
import org.knime.core.data.DataType;
import org.knime.core.data.filestore.FileStore;
import org.knime.core.node.ExecutionContext;
import org.knime.core.node.util.CheckUtils;

/**
 * Factory for {@link NominalDistributionCell NominalDistributionCells}. Cells created by such a factory share the same
 * meta data and {@link FileStore}.
 *
 * @author Adrian Nembach, KNIME GmbH, Konstanz, Germany
 * @since 4.1
 */
public class NominalDistributionCellFactory {

    public static final DataType TYPE = NominalDistributionCell.TYPE;

    private final NominalDistributionMetaData m_metaData;

    private final FileStore m_fileStore;

    /**
     * Constructs a {@link NominalDistributionCellFactory} for the creation of {@link NominalDistributionCell
     * NominalDistributionCells}.
     *
     * @param exec used to create the {@link FileStore} shared by the cells created by this factory
     * @param values the values the distribution is defined over
     */
    public NominalDistributionCellFactory(final ExecutionContext exec, final DataCell[] values) {
        try {
            m_fileStore = exec.createFileStore(UUID.randomUUID().toString());
        } catch (IOException ex) {
            // TODO is this correct?
            throw new IllegalStateException(ex);
        }
        m_metaData = new NominalDistributionMetaData(values);
    }

    /**
     * @param probabilities the probabilities for the different values
     * @return a {@link NominalDistributionCell} containing <b>probabilities</b>
     * @throws NullPointerException if <b>probabilities</b> is null
     * @throws IllegalArgumentException if <b>probabilities</b> does not have the same number of elements as there are
     *             values in the meta data
     */
    public NominalDistributionCell createCell(final double[] probabilities, final double epsilon) {
        // TODO implement epsilon functionality
        CheckUtils.checkNotNull(probabilities);
        CheckUtils.checkArgument(probabilities.length == m_metaData.size(),
            "The number of elements in probabilities (%s) must match the number of values in the meta data (%s).",
            probabilities.length, m_metaData.size());
        return new NominalDistributionCell(m_metaData, m_fileStore, probabilities.clone());
    }

    public NominalDistributionCell createCell(final DataCell value) {
        final int idx = m_metaData.getIndex(value);
        CheckUtils.checkArgument(idx != -1, "Unknown value '%s'.", value);
        final double[] probs = new double[m_metaData.size()];
        probs[idx] = 1.0;
        return new NominalDistributionCell(m_metaData, m_fileStore, probs);
    }

}
