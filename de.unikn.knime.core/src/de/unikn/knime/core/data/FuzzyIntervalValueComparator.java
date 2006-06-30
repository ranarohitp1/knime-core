/* -------------------------------------------------------------------
 * This source code, its documentation and all appendant files
 * are protected by copyright law. All rights reserved.
 * 
 * Copyright, 2003 - 2006
 * Universitaet Konstanz, Germany.
 * Lehrstuhl fuer Angewandte Informatik
 * Prof. Dr. Michael R. Berthold
 * 
 * You may not modify, publish, transmit, transfer or sell, reproduce,
 * create derivative works from, distribute, perform, display, or in
 * any way exploit any of the content, in whole or in part, except as
 * otherwise expressly permitted in writing by the copyright owner.
 * -------------------------------------------------------------------
 * 
 * History
 *   07.07.2005 (mb): created
 *   21.06.06 (bw & po): reviewed
 */
package de.unikn.knime.core.data;

/**
 * Comparator returned by the <code>FuzzyIntervalValue</code> datacell type.
 *  
 * @see de.unikn.knime.core.data.FuzzyIntervalValue.FuzzyIntervalUtilityFactory
 * @author Michael Berthold, Konstanz University
 */
public class FuzzyIntervalValueComparator extends DataValueComparator {

    /**
     * The compare function called by the abstract DataValueComparator class.
     * The comparison is based on the border values returned by
     * <code>FuzzyIntervalValue.get{Min,Max}{Core,Support}()</code> methods.
     * Note that comparing fuzzy intervals is far from trivial - we base the
     * comparison used here on the center of gravities of the fuzzy sets. Do not
     * call this method directly. Use <code>DataCell.compareTo</code> instead.
     * 
     * @see de.unikn.knime.core.data.DataValueComparator
     *      #compareDataValues(DataValue, DataValue)
     */
    @Override
    protected int compareDataValues(final DataValue v1, final DataValue v2) {

        FuzzyIntervalValue f1 = (FuzzyIntervalValue)v1;
        FuzzyIntervalValue f2 = (FuzzyIntervalValue)v2;

        // compute center of gravities of both trapezoid
        double f1CoG = f1.getCenterOfGravity();
        double f2CoG = f2.getCenterOfGravity();
        // perform actual comparison
        if (f1CoG > f2CoG) {
            return +1;
        }
        if (f1CoG == f2CoG) {
            return 0;
        }
        return -1;
    }

}
