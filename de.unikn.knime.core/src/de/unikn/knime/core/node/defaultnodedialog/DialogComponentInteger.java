/* @(#)$RCSfile$ 
 * $Revision$ $Date$ $Author$
 * 
 * -------------------------------------------------------------------
 * This source code, its documentation and all appendant files
 * are protected by copyright law. All rights reserved.
 * 
 * Copyright, 2003 - 2004
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
 *   16.11.2005 (gdf): created
 */

package de.unikn.knime.core.node.defaultnodedialog;

import java.awt.Dimension;
import java.text.NumberFormat;
import java.text.ParseException;

import javax.swing.JFormattedTextField;
import javax.swing.JLabel;

import de.unikn.knime.core.data.DataTableSpec;
import de.unikn.knime.core.node.InvalidSettingsException;
import de.unikn.knime.core.node.NodeSettings;

/**
 * Provide a standard component for a dialog that allows to edit a int value.
 * Provides label and JFormattedTextField that checks ranges as well as
 * functionality to load/store into config object.
 * 
 * @author Thomas Gabriel, Konstanz University
 */
public class DialogComponentInteger extends DialogComponent {

    private final int m_dvalue;

    private final JFormattedTextField m_dvalueField;

    private final NumberFormat m_dvalueFormat;

    private final String m_configName;

    /**
     * Constructor put label and JFormattedTextField into panel.
     * 
     * @param configName name used in configuration file
     * @param label label for dialog in front of JFormattedTextField
     * @param defaultValue initial value if no value is stored in the config
     */
    public DialogComponentInteger(final String configName, final String label,
            final int defaultValue) {
        this.add(new JLabel(label));
        m_dvalue = defaultValue;
        m_dvalueFormat = NumberFormat.getIntegerInstance();
        m_dvalueField = new JFormattedTextField(m_dvalueFormat);
        m_dvalueField.setValue(new Integer(m_dvalue));
        m_dvalueField.setColumns(6);
        this.add(m_dvalueField);
        m_configName = configName;
    }

    /**
     * Read value for this dialog component from configuration object.
     * 
     * @param settings The <code>NodeSettings</code> to read from.
     * @param specs The input specs.
     */
    public void loadSettingsFrom(final NodeSettings settings,
            final DataTableSpec[] specs) {
        int value = settings.getInt(m_configName, m_dvalue);
        m_dvalueField.setValue(new Integer(value));
    }

    /**
     * write settings of this dialog component into the configuration object.
     * 
     * @param settings The <code>NodeSettings</code> to write into.
     * @throws InvalidSettingsException if the user has entered wrong values.
     */
    public void saveSettingsTo(final NodeSettings settings)
            throws InvalidSettingsException {
        try {
            m_dvalueField.commitEdit();
        } catch (ParseException e) {
            throw new InvalidSettingsException("Only ints please");
        }
        int amount = ((Number)m_dvalueField.getValue()).intValue();
        settings.addInt(m_configName, amount);
    }

    /**
     * @see de.unikn.knime.core.node.defaultnodedialog.DialogComponent
     *      #setEnabledComponents(boolean)
     */
    @Override
    public void setEnabledComponents(final boolean enabled) {
        m_dvalueField.setEnabled(enabled);
    }
    
    /**
     * Sets the preferred size of the internal component.
     * @param width The width.
     * @param height The height.
     */
    public void setSizeComponents(final int width, final int height) {
        m_dvalueField.setPreferredSize(new Dimension(width, height));
    }
}

