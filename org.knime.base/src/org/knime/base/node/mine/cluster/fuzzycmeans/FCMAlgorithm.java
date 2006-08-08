/* @(#)$RCSfile$ 
 * $Revision$ $Date$ $Author$
 * 
 * -------------------------------------------------------------------
 * This source code, its documentation and all appendant files
 * are protected by copyright law. All rights reserved.
 *
 * Copyright, 2003 - 2006
 * University of Konstanz, Germany.
 * Chair for Bioinformatics and Information Mining
 * Prof. Dr. Michael R. Berthold
 *
 * You may not modify, publish, transmit, transfer or sell, reproduce,
 * create derivative works from, distribute, perform, display, or in
 * any way exploit any of the content, in whole or in part, except as
 * otherwise expressly permitted in writing by the copyright owner or
 * as specified in the license file distributed with this product.
 *
 * If you have any quesions please contact the copyright holder:
 * website: www.knime.org
 * email: contact@knime.org
 * -------------------------------------------------------------------
 * 
 * History
 *   17.07.2006 (cebron): created
 */
package org.knime.base.node.mine.cluster.fuzzycmeans;

import java.util.Iterator;
import java.util.Random;

import org.knime.base.node.mine.bfn.Distance;
import org.knime.core.data.DataCell;
import org.knime.core.data.DataRow;
import org.knime.core.data.DataTable;
import org.knime.core.data.DoubleValue;
import org.knime.core.data.RowIterator;
import org.knime.core.node.CanceledExecutionException;
import org.knime.core.node.ExecutionContext;

/**
 * The Fuzzy c-means algorithm.
 * 
 * @author Nicolas Cebron, University of Konstanz
 */
public class FCMAlgorithm {
    // dimension of input space
    private int m_dimension;

    // number of rows
    private int m_nrRows;

    // number of clusters to be used
    private int m_nrClusters;

    // clusters generated by the algorithm
    private double[][] m_clusters;

    // the weight matrix
    private double[][] m_weightMatrix;

    /*
     * 'fuzzifier' controls how much the clusters can overlap
     */
    private double m_fuzzifier;

    /*
     * Flag indicating whether a noise cluster is induced.
     */
    private boolean m_noise;

    /*
     * Flag indicating whether delta is given or should be updated
     * automatically.
     */
    private boolean m_calculateDelta;

    /*
     * The delta value of the noise cluster.
     */
    private double m_delta;

    /*
     * Lambda value for automatic update process of noise delta.
     */
    private double m_lambda;

    /*
     * Sum of all within-cluster variations
     */
    private double m_allsum = 0;

    /*
     * The finished state in the execute method
     */
    private boolean m_finished = false;

    /*
     * DataTable to be clustered
     */
    private DataTable m_table;

    /*
     * Distance object to calculate distance
     */
    private Distance m_distance;

    /**
     * Constructor for a Fuzzy c-means algorithm (with no noise detection).
     * 
     * @param nrClusters the number of cluster prototypes to use
     * @param fuzzifier allows the clusters to overlap
     */
    public FCMAlgorithm(final int nrClusters, final double fuzzifier) {
        m_nrClusters = nrClusters;
        m_fuzzifier = fuzzifier;
        m_noise = false;
        m_calculateDelta = false;
        m_distance = Distance.getInstance();
    }

    /**
     * Constructor for a Fuzzy c-means algorithm with noise detection. It can be
     * indicated, whether the delta value of the noise cluster should be updated
     * automatically or if it should be calculated automatically. The last
     * parameter specifies either the delta value or the lambda value, depending
     * on the boolean flag in the parameter before.
     * 
     * @param nrClusters the number of clusters to use
     * @param fuzzifier the fuzzifier, controls how much the clusters can
     *            overlap
     * @param calculateDelta indicate whether delta should be calculated
     *            automatically
     * @param deltalambda the delta value, if the previous parameter is
     *            <code>false</code>, the lambda value otherwise
     */
    public FCMAlgorithm(final int nrClusters, final double fuzzifier,
            final boolean calculateDelta, final double deltalambda) {
        this(nrClusters + 1, fuzzifier);
        m_calculateDelta = calculateDelta;
        if (m_calculateDelta) {
            m_lambda = deltalambda;
            m_delta = .5; // initial delta
        } else {
            m_delta = deltalambda;
        }
        m_noise = true;
    }

    /**
     * Inits the cluster centers and the weight matrix. Must be called before
     * the iterations are carried out.
     * 
     * @param nrRows number of rows in the DataTable
     * @param dimension the dimension of the table
     * @param table the table to use.
     */
    public void init(final int nrRows, final int dimension,
            final DataTable table) {
        m_nrRows = nrRows;
        m_dimension = dimension;

        // initialize membership matrix W
        m_weightMatrix = new double[m_nrRows][m_nrClusters];

        m_clusters = new double[m_nrClusters][];
        Random rand = new Random();
        for (int c = 0; c < m_nrClusters; c++) {
            m_clusters[c] = new double[m_dimension];
            for (int i = 0; i < m_clusters[c].length; i++) {
                m_clusters[c][i] = rand.nextDouble();
            }
        }
        m_table = table;
        // TODO: checks on table: only double columns, nrRows, dimension
    }

    /**
     * An easier initialization, the rowcount and dimension are determined by
     * iterating over the table.
     * 
     * @param table the table to use.
     */
    public void init(final DataTable table) {
        int nrdimensions = table.getDataTableSpec().getNumColumns();
        int nrRows = 0;
        Iterator it = table.iterator();
        while (it.hasNext()) {
            it.next();
            nrRows++;
        }
        init(nrRows, nrdimensions, table);
    }

    /**
     * Does one iteration in the Fuzzy c-means algorithm. First, the weight
     * matrix is updated and then the cluster prototypes are recalculated.
     * 
     * @param exec execution context to cancel the execution
     * @return boolean indicating whether the cluster prototypes have stopped
     *         moving
     * @throws CanceledExecutionException if the operation is canceled
     */
    public boolean doOneIteration(final ExecutionContext exec)
            throws CanceledExecutionException {
        assert (m_table != null);
        exec.checkCanceled();
        m_finished = true;
        updateWeightMatrix(m_table, exec);
        updateClusterCenters(m_table, exec);
        return m_finished;
    }

    /*
     * The update method for the weight matrix
     */
    private void updateWeightMatrix(final DataTable inData,
            final ExecutionContext exec) throws CanceledExecutionException {
        RowIterator ri = inData.iterator();

        for (int currentRow = 0; currentRow < m_nrRows; currentRow++) {
            DataRow dRow = ri.next();
            exec.checkCanceled();
            int i = 0;

            // first check if the actual row is equal to a cluster center
            int sameCluster = -1;

            int nrClusters = (m_noise) ? m_clusters.length - 1
                    : m_clusters.length;
            while ((sameCluster < 0) && (i < nrClusters)) {
                for (int j = 0; j < dRow.getNumCells(); j++) {
                    if (!(dRow.getCell(j).isMissing())) {
                        DataCell currentCell = dRow.getCell(j);
                        if (currentCell instanceof DoubleValue) {
                            if (((DoubleValue)currentCell).
                                    getDoubleValue() == m_clusters[i][j]) {
                                sameCluster = i;
                            } else {
                                sameCluster = -1;
                                break;
                            }
                        }
                    }
                }

                i++;
            }

            /*
             * The weight of a data point is 1 if it is exactly on the position
             * of the cluster, in this case 0 for the others
             */
            if (sameCluster >= 0) {
                for (i = 0; i < m_weightMatrix[0].length; i++) {
                    if (i != sameCluster) {
                        m_weightMatrix[currentRow][i] = 0;
                    } else {
                        m_weightMatrix[currentRow][i] = 1;
                    }
                }
            } else {
                // calculate the fuzzy membership to each cluster
                for (int j = 0; j < m_clusters.length; j++) {
                    // for each cluster
                    double distNumerator = 0;
                    if (m_noise && j == m_clusters.length - 1) {
                        distNumerator = Math.pow(m_delta, 2.0);
                    } else {
                        distNumerator = getDistance(m_clusters[j], dRow);
                    }
                    double sum = 0;
                    for (int k = 0; k < m_clusters.length; k++) {
                        double distance = 0;
                        if (m_noise && k == m_clusters.length - 1) {
                            distance = Math.pow(m_delta, 2.0);
                        } else {
                            distance = getDistance(m_clusters[k], dRow);
                        }
                        sum += Math.pow((distNumerator / distance),
                                (1.0 / (m_fuzzifier - 1.0)));
                    }
                    m_weightMatrix[currentRow][j] = 1 / sum;
                }
            }
        }
    }

    /*
     * Helper method for the quadratic distance between a double-array and a
     * DataRow
     * 
     */
    private double getDistance(final double[] vector1, final DataRow vector2) {
        double distance = 0.0;
        assert (vector1.length == vector2.getNumCells());
        for (int i = 0; i < vector1.length; i++) {
            double diff = 0;
            if (!vector2.getCell(i).isMissing()) {
                diff = vector1[i]
                        - ((DoubleValue)vector2.getCell(i)).getDoubleValue();
            }
            distance += diff * diff;
        }
        return distance;
    }

    /*
     * The update method for the cluster centers
     */
    private void updateClusterCenters(final DataTable inData,
            final ExecutionContext exec) throws CanceledExecutionException {
        double[] sumNumerator = new double[m_dimension];
        double sumDenominator = 0;
        double sumupdate = 0;
        // for each cluster center
        for (int c = 0; c < m_nrClusters; c++) {
            if (m_noise) {
                // stop updating at noise cluster position.
                if (c == m_nrClusters - 1) {
                    break;
                }
            }
            for (int j = 0; j < m_dimension; j++) {
                sumNumerator[j] = 0;
            }
            sumDenominator = 0;

            RowIterator ri = inData.iterator();
            int i = 0;
            while (ri.hasNext()) {
                exec.checkCanceled();
                DataRow dRow = ri.next();
                // for all attributes in X
                for (int j = 0; j < m_dimension; j++) {
                    if (!(dRow.getCell(j).isMissing())) {
                        DataCell dc = dRow.getCell(j);
                        if (dc instanceof DoubleValue) {
                            sumNumerator[j] += Math.pow(m_weightMatrix[i][c],
                                    m_fuzzifier)
                                    * ((DoubleValue)dc).getDoubleValue();
                        }
                    }
                }
                sumDenominator += Math.pow(m_weightMatrix[i][c], m_fuzzifier);
                i++;
                if (m_noise && m_calculateDelta) {
                    sumupdate += m_distance.compute(m_clusters[c], dRow);
                }
            } // end while for all datarows sum up
            for (int j = 0; j < m_dimension; j++) {
                double newValue = sumNumerator[j] / sumDenominator;
                if (Math.abs(m_clusters[c][j] - newValue) > 1e-10) {
                    m_finished = false;
                }
                m_clusters[c][j] = newValue;
            }
        }

        /*
         * Update the delta-value automatically if choosen.
         */
        if (m_noise && m_calculateDelta) {
            m_delta = Math.sqrt(m_lambda
                    * (sumupdate / (m_nrRows * (m_clusters.length - 1))));
        }

    } // end update cluster centers

    /**
     * @return the cluster centres in a 2-dimensional double matrix
     */
    public double[][] getClusterCentres() {
        return m_clusters;
    }

    /**
     * @return the 2-dimensional weight matrix
     */
    public double[][] getweightMatrix() {
        return m_weightMatrix;
    }

    /**
     * @return flag indicating whether a noise clustering was performed
     */
    public boolean noiseClustering() {
        return m_noise;
    }

    /*
     * Helper method to determine the winner cluster center (The cluster center
     * to which the DataRow has the highest membership value).
     */
    private final int getWinner(final double[] weights) {
        int max = -1;
        double maxvalue = -1;
        for (int i = 0; i < weights.length; i++) {
            if (weights[i] > maxvalue) {
                maxvalue = weights[i];
                max = i;
            }
        }
        return max;
    }

    /**
     * Calculates the Within-Cluster Variation for each cluster. We take 'crisp'
     * cluster centers to determine the membership from a datarow to a cluster
     * center.
     * 
     * @return withinClusterVariations
     */
    public double[] getWithinClusterVariations() {

        // calculate within-cluster cariation for each cluster center
        double[] sum = new double[m_clusters.length];
        int[] nrRows = new int[m_clusters.length];

        DataRow dRow;
        int winner = -1;

        RowIterator rowit = m_table.iterator();
        int i = 0;
        Distance dist = Distance.getInstance();
        while (rowit.hasNext()) {
            dRow = rowit.next();
            winner = getWinner(m_weightMatrix[i]);
            sum[winner] += dist.compute(m_clusters[winner], dRow);
            nrRows[winner]++;
            i++;
        }

        m_allsum = 0;
        double[] withinclustervariations = new double[m_clusters.length];
        for (int c = 0; c < m_clusters.length; c++) {
            double clustervariation = sum[c] / nrRows[c];
            withinclustervariations[c] = clustervariation;
            m_allsum += clustervariation;
        }
        m_allsum = (m_allsum / m_clusters.length);
        return withinclustervariations;
    }

    /**
     * Calculates the Between-Cluster Variation.
     * 
     * @return the between cluster variation.
     */
    public double getBetweenClusterVariation() {
        double sum = 0;
        for (int j = 0; j < m_clusters.length; j++) {
            for (int i = 0; i < m_clusters.length; i++) {
                sum += clusterdistance(m_clusters[i], m_clusters[j]);
            }
        }
        sum = sum / (m_clusters.length * m_clusters.length);
        return (sum / m_allsum);
    }

    /*
     * Helper method to calculate the distance between two cluster centers
     */
    private double clusterdistance(final double[] cluster1,
            final double[] cluster2) {
        double distance = 0;
        for (int i = 0; i < cluster1.length; i++) {
            double d = cluster1[i] - cluster2[i];
            distance += d * d;
        }
        return distance;
    }
}
