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

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Set;
import java.util.concurrent.ExecutionException;

import org.knime.core.data.DataCell;
import org.knime.core.data.DataCellDataInput;
import org.knime.core.data.DataCellDataOutput;
import org.knime.core.data.DataCellSerializer;
import org.knime.core.data.DataType;
import org.knime.core.data.filestore.FileStore;
import org.knime.core.data.filestore.FileStoreCell;
import org.knime.core.node.util.CheckUtils;

/**
 * Implementation of {@link NominalDistributionValue} that shares the value set between multiple instances defined over
 * the same set of values.
 *
 * @author Adrian Nembach, KNIME GmbH, Konstanz, Germany
 * @since 4.1
 */
public class NominalDistributionCell extends FileStoreCell implements NominalDistributionValue {

    static final DataType TYPE = DataType.getType(NominalDistributionCell.class);

    private static final long serialVersionUID = 1L;

    private transient NominalDistributionMetaData m_metaData;

    private double[] m_probabilities;

    NominalDistributionCell(final NominalDistributionMetaData metaData, final FileStore fileStore,
        final double[] probabilities) {
        super(fileStore);
        CheckUtils.checkArgument(metaData.size() == probabilities.length,
            "The number of elements in probabilities must match the number of values in metaData.");
        m_metaData = metaData;
        m_probabilities = probabilities;
    }

    private NominalDistributionCell(final double[] probabilities) {
        m_probabilities = probabilities;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void flushToFileStore() throws IOException {
        final File file = getFile();
        synchronized (file) {
            if (isNotWrittenYet(file)) {
                try (final ObjectOutputStream out = new ObjectOutputStream(new FileOutputStream(file))) {
                    m_metaData.write(out);
                }
            }
        }
    }

    private static boolean isNotWrittenYet(final File file) {
        return !file.exists();
    }

    private File getFile() {
        final FileStore fileStore = getFileStores()[0];
        return fileStore.getFile();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void postConstruct() throws IOException {
        final File file = getFile();
        try (final ObjectInputStream in = new ObjectInputStream(new FileInputStream(file))) {
            m_metaData = NominalDistributionMetaData.read(in);
        } catch (ClassNotFoundException | ExecutionException ex) {
            throw new IOException("The meta data cannot be read.", ex);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public double getProbability(final DataCell value) {
        final int idx = m_metaData.getIndex(value);
        return idx == -1 ? 0.0 : m_probabilities[idx];
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isKnown(final DataCell value) {
        return m_metaData.getIndex(value) != -1;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Set<DataCell> getKnownValues() {
        return m_metaData.getValues();
    }

    /**
     * Serializer for {@link NominalDistributionCell NominalDistributionCells}.
     *
     * @author Adrian Nembach, KNIME GmbH, Konstanz, Germany
     * @noreference This class is not intended to be referenced by clients.
     */
    public static final class NominalDistributionSerializer implements DataCellSerializer<NominalDistributionCell> {

        /**
         * {@inheritDoc}
         */
        @Override
        public void serialize(final NominalDistributionCell cell, final DataCellDataOutput output) throws IOException {
            final int length = cell.m_probabilities.length;
            output.writeInt(length);
            for (int i = 0; i < length; i++) {
                output.writeDouble(cell.m_probabilities[i]);
            }
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public NominalDistributionCell deserialize(final DataCellDataInput input) throws IOException {
            int length = input.readInt();
            final double[] probabilities = new double[length];
            for (int i = 0; i < length; i++) {
                probabilities[i] = input.readDouble();
            }
            return new NominalDistributionCell(probabilities);
        }

    }

}
