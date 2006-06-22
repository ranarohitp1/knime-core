/* 
 * -------------------------------------------------------------------
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
 *   17.11.2005 (cebron): created
 */
package de.unikn.knime.core.node.meta;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import de.unikn.knime.core.data.DataTable;
import de.unikn.knime.core.data.DataTableSpec;
import de.unikn.knime.core.node.CanceledExecutionException;
import de.unikn.knime.core.node.DataOutPort;
import de.unikn.knime.core.node.ExecutionMonitor;
import de.unikn.knime.core.node.InvalidSettingsException;
import de.unikn.knime.core.node.NodeFactory;
import de.unikn.knime.core.node.NodeLogger;
import de.unikn.knime.core.node.NodeModel;
import de.unikn.knime.core.node.NodeSettings;
import de.unikn.knime.core.node.NodeStateListener;
import de.unikn.knime.core.node.NodeStatus;
import de.unikn.knime.core.node.SpecialNodeModel;
import de.unikn.knime.core.node.tableinput.DataTableInputFactory;
import de.unikn.knime.core.node.tableinput.DataTableOutputFactory;
import de.unikn.knime.core.node.tableinput.ModelInputFactory;
import de.unikn.knime.core.node.tableinput.ModelOutputFactory;
import de.unikn.knime.core.node.workflow.ConnectionContainer;
import de.unikn.knime.core.node.workflow.NodeContainer;
import de.unikn.knime.core.node.workflow.WorkflowEvent;
import de.unikn.knime.core.node.workflow.WorkflowListener;
import de.unikn.knime.core.node.workflow.WorkflowManager;

/**
 * 
 * @author Thorsten Meinl, University of Konstanz
 * @author Nicolas Cebron, University of Konstanz
 */
public class MetaNodeModel extends SpecialNodeModel
    implements WorkflowListener {
    private static final String WORKFLOW_KEY = "workflow";
    private static final String INOUT_CONNECTIONS_KEY = "inOutConnections";
    
    private WorkflowManager m_internalWFM;

    private final NodeContainer[] m_dataInContainer, m_dataOutContainer;
    private final NodeContainer[] m_modelInContainer, m_modelOutContainer;
    private final MetaInputNodeModel[] m_dataInModels;
    private final MetaOutputNodeModel[] m_dataOutModels;    
    private final MetaInputModelNodeModel[] m_modelInModels;
    private final MetaOutputModelNodeModel[] m_modelOutModels;
    
    
    /*
     * The listeners that are interested in node state changes.
     */
    private final ArrayList<NodeStateListener> m_stateListeners;

    private final NodeFactory m_myFactory;
    
    private static final NodeLogger LOGGER =
        NodeLogger.getLogger(MetaNodeModel.class);

    
    /**
     * Creates a new new meta node model.
     * 
     * @param outerDataIns the number of data input ports
     * @param outerDataOuts the number of data output ports
     * @param outerPredParamsIns the number of predictor param input ports
     * @param outerPredParamsOuts the number of predictor param output ports
     * @param innerDataIns the number of meta data input nodes
     * @param innerDataOuts the number of meta data output nodes
     * @param innerPredParamsIns the number of meta model input nodes
     * @param innerPredParamsOuts the number of meta model output nodes
     * @param factory the factory that created this model
     */
    protected MetaNodeModel(final int outerDataIns, final int outerDataOuts,
            final int outerPredParamsIns, final int outerPredParamsOuts,
            final int innerDataIns, final int innerDataOuts,
            final int innerPredParamsIns, final int innerPredParamsOuts,
            final MetaNodeFactory factory) {
        super(outerDataIns, outerDataOuts, outerPredParamsIns,
                outerPredParamsOuts);

        m_dataInContainer = new NodeContainer[innerDataIns];
        m_dataOutContainer = new NodeContainer[innerDataOuts];
        m_modelInContainer = new NodeContainer[innerPredParamsIns];
        m_modelOutContainer = new NodeContainer[innerPredParamsOuts];

        m_dataInModels = new MetaInputNodeModel[innerDataIns];
        m_dataOutModels = new MetaOutputNodeModel[innerDataOuts];
        m_modelInModels = new MetaInputModelNodeModel[innerPredParamsIns];
        m_modelOutModels = new MetaOutputModelNodeModel[innerPredParamsOuts];

        m_myFactory = factory;        
        m_stateListeners = new ArrayList<NodeStateListener>();
    }

    
    /**
     * Creates a new new meta node model.
     * 
     * @param nrDataIns the number of data input ports
     * @param nrDataOuts the number of data output ports
     * @param nrPredParamsIns the number of predictor param input ports
     * @param nrPredParamsOuts the number of predictor param output ports
     * @param factory the factory that created this model
     */
    protected MetaNodeModel(final int nrDataIns, final int nrDataOuts,
            final int nrPredParamsIns, final int nrPredParamsOuts,
            final MetaNodeFactory factory) {
        this(nrDataIns, nrDataOuts, nrPredParamsIns, nrPredParamsOuts,
             nrDataIns, nrDataOuts, nrPredParamsIns, nrPredParamsOuts, factory);
    }
    

    /**
     * The number of inputs and outputs must be provided, the corresponding
     * <code>MetaInputNode</code>s and <code>MetaOutputNode</code>s are created 
     * in the inner workflow.
     * 
     * @param nrIns number of input nodes.
     * @param nrOuts number of output nodes.
     * @param f the factory that created this model
     */
    protected MetaNodeModel(final int nrIns, final int nrOuts,
            final MetaNodeFactory f) {
        this(nrIns, nrOuts, 0, 0, f);
    }

    
    
    /**
     * The inSpecs are manually set in the <code>MetaInputNode</code>s. The
     * resulting outSpecs from the <code>MetaOutputNode</code>s are returned.
     * @see de.unikn.knime.core.node.NodeModel#configure(DataTableSpec[])
     */
    @Override
    protected DataTableSpec[] configure(final DataTableSpec[] inSpecs)
            throws InvalidSettingsException {
        createInternalWFM();
        
        for (int i = 0; i < m_dataInModels.length; i++) {
            m_dataInModels[i].setDataTableSpec(inSpecs[i]);
            m_internalWFM.resetAndConfigureNode(m_dataInContainer[i].getID());
        }
        
        // collect all output specs
        DataTableSpec[] outspecs = new DataTableSpec[m_dataOutContainer.length];
        final int min = Math.min(outspecs.length, m_dataOutContainer.length);
        for (int i = 0; i < min; i++) {
            if ((m_dataOutContainer[i] != null)
                    && (m_dataOutContainer[0].getOutPorts().size() > 0)) {
                outspecs[i] = ((DataOutPort) m_dataOutContainer[i]
                    .getOutPorts().get(0)).getDataTableSpec();
            }
        }
        return outspecs;
    }

    /**
     * During execute, the inData <code>DataTables</code> are passed on to the 
     * <code>MetaInputNode</code>s.
     * The inner workflow gets executed and the output <code>DataTable</code>s 
     * from the <code>MetaOutputNode</code>s are returned.
     * @see de.unikn.knime.core.node.NodeModel
     *  #execute(DataTable[], ExecutionMonitor)
     */
    @Override
    protected DataTable[] execute(final DataTable[] inData,
            final ExecutionMonitor exec) throws Exception {
        exec.setMessage("Executing inner workflow");
        
        m_internalWFM.executeAll(true);
        exec.checkCanceled();
        
        // translate output
        exec.setMessage("Collecting output");
        DataTable[] out = new DataTable[m_dataOutContainer.length];
        for (int i = 0; i < out.length; i++) {
            out[i] = ((DataOutPort) m_dataOutContainer[i]
                 .getOutPorts().get(0)).getDataTable();
            if (out[i] == null) {
                System.out.println();
            }
        }
        return out;
    }

    /**
     * Loads the Meta Workflow from the settings. Internal references to the
     * MetaInput and MetaOuput - Nodes are updated.
     * 
     * @see de.unikn.knime.core.node.NodeModel
     *  #loadValidatedSettingsFrom(NodeSettings)
     */
    @Override
    protected void loadValidatedSettingsFrom(final NodeSettings settings)
            throws InvalidSettingsException {
        createInternalWFM();
        // load the "internal" workflow
        if (settings.containsKey(WORKFLOW_KEY)) {
            m_internalWFM.clear();
            addInOutNodes();

            m_internalWFM.load(settings.getConfig(WORKFLOW_KEY));
            addInOutConnections(settings.getConfig(INOUT_CONNECTIONS_KEY));
        }
    }

    /**
     * Returns a factory for the meta data input node with the given index. The
     * default implementation returns a {@link DataTableInputFactory}.
     * Subclasses may override this method and return other special factory
     * which must create subclasses of {@link MetaInputNodeModel}s.
     * 
     * @param inputNodeIndex the index of the data input node
     * @return a factory
     */
    protected NodeFactory getDataInputNodeFactory(final int inputNodeIndex) {
        return new DataTableInputFactory() {
            @Override
            public String getOutportDescription(final int index) {
                return m_myFactory.getInportDescription(inputNodeIndex);
            }

            @Override
            public NodeModel createNodeModel() {
                return new MetaInputNodeModel();
            }
        };
    }
    
    /**
     * Returns a factory for the meta model input node with the given index. The
     * default implementation returns a {@link ModelInputFactory}. Subclasses
     * may override this method and return other special factory which must
     * create subclasses of {@link MetaInputNodeModel}s.
     * 
     * @param inputNodeIndex the index of the model input node
     * @return a factory
     */
    protected NodeFactory getModelInputNodeFactory(final int inputNodeIndex) {
        return new ModelInputFactory() {
            @Override
            public String getPredParamOutDescription(final int index) {
                return m_myFactory.getPredParamInDescription(inputNodeIndex);
            }                        
            
            @Override
            public NodeModel createNodeModel() {
                return new MetaInputModelNodeModel();
            }            
        };
    }
    

    /**
     * Returns a factory for the meta data output node with the given index. The
     * default implementation returns a {@link ModelOutputFactory}.
     * Subclasses may override this method and return other special factory
     * which must create subclasses of {@link MetaOutputNodeModel}s.
     * 
     * @param outputNodeIndex the index of the data input node
     * @return a factory
     */
    protected NodeFactory getModelOutputNodeFactory(final int outputNodeIndex) {
        return new ModelOutputFactory() {
            @Override
            public String getPredParamInDescription(final int index) {
                return m_myFactory.getPredParamOutDescription(outputNodeIndex);
            }
            
            @Override
            public NodeModel createNodeModel() {
                return new MetaOutputNodeModel();
            }
        };
    }
    
    
    /**
     * Returns a factory for the meta model output node with the given index.
     * The default implementation returns a {@link DataTableOutputFactory}.
     * Subclasses may override this method and return other special factory
     * which must create subclasses of {@link MetaOutputNodeModel}s.
     * 
     * @param outputNodeIndex the index of the data input node
     * @return a factory
     */
    protected NodeFactory getDataOutputNodeFactory(final int outputNodeIndex) {
        return new DataTableOutputFactory() {
            @Override
            public String getInportDescription(final int index) {
                return m_myFactory.getOutportDescription(outputNodeIndex);
            }
            
            @Override
            public NodeModel createNodeModel() {
                return new MetaOutputNodeModel();
            }            
        };
    }
    
    
    /**
     * Adds the input and output nodes to the workflow.
     */
    private void addInOutNodes() {
        LOGGER.debug("Adding in- and output nodes");
        for (int i = 0; i < m_dataInContainer.length; i++) {
            NodeFactory f = getDataInputNodeFactory(i);
            m_dataInModels[i] = (MetaInputNodeModel)f.createNodeModel();
            m_dataInContainer[i] = m_internalWFM.addNewNode(f);
        }

        for (int i = 0; i < m_dataOutContainer.length; i++) {
            NodeFactory f = getDataOutputNodeFactory(i);
            m_dataOutModels[i] = (MetaOutputNodeModel)f.createNodeModel();
            m_dataOutContainer[i] = m_internalWFM.addNewNode(f);
        }

        for (int i = 0; i < m_modelInContainer.length; i++) {
            NodeFactory f = getModelInputNodeFactory(i);
            m_modelInModels[i] = (MetaInputModelNodeModel)f.createNodeModel();
            m_modelInContainer[i] = m_internalWFM.addNewNode(f);
        }

        for (int i = 0; i < m_modelOutContainer.length; i++) {
            NodeFactory f = getModelOutputNodeFactory(i);
            m_modelOutModels[i] = (MetaOutputModelNodeModel)f.createNodeModel();
            m_modelOutContainer[i] = m_internalWFM.addNewNode(f);
        }        
    }
    
    
    /**
     * Adds the connection from and to the in-/output nodes.
     * 
     * @param connections the settings in which the connections are stored
     * @throws InvalidSettingsException if one of the settings is invalid
     */
    private void addInOutConnections(final NodeSettings connections)
    throws InvalidSettingsException {
        LOGGER.debug("Adding in- and output connections");
        for (String key : connections) {
            if (key.startsWith("connection_")) {
                int[] conn = connections.getIntArray(key);
                m_internalWFM.addConnection(conn[0], conn[1], conn[2],
                        conn[3]);
            }
        }        
    }
    
    /**
     * A reset at the MetaInputNodes of the inner workflow is triggered in order
     * to reset all nodes in the inner workflow.
     *  
     * @see de.unikn.knime.core.node.NodeModel#reset()
     */
    @Override
    protected void reset() {
        // TODO reset the internal workflow, but only if is currently not
        // already resetting i.e. the reset was triggered from an internal node
    }

    /** 
     * @see de.unikn.knime.core.node.SpecialNodeModel
     *  #saveSettingsTo(java.io.File, de.unikn.knime.core.node.ExecutionMonitor)
     */
    @Override
    protected void saveSettingsTo(final File nodeFile,
            final ExecutionMonitor exec) {
        if (m_internalWFM == null) { return; }
        Set<NodeContainer> omitNodes = new HashSet<NodeContainer>();
        for (NodeContainer nc : m_dataInContainer) {
            omitNodes.add(nc);
        }
        for (NodeContainer nc : m_dataOutContainer) {
            omitNodes.add(nc);
        }
        for (NodeContainer nc : m_modelInContainer) {
            omitNodes.add(nc);
        }
        for (NodeContainer nc : m_modelOutContainer) {
            omitNodes.add(nc);
        }
        
        
        try {
            File f = new File(nodeFile.getParentFile(), "workflow.knime");
            f.createNewFile();
            m_internalWFM.save(f, omitNodes);
        } catch (IOException ex) {
            LOGGER.error(ex);
        } catch (CanceledExecutionException ex) {
            LOGGER.error(ex);
        }
    }

    /**
     * @see de.unikn.knime.core.node.NodeModel#validateSettings(NodeSettings)
     */
    @Override
    protected void validateSettings(final NodeSettings settings)
            throws InvalidSettingsException {
        // maybe do some sanity checks?
    }

    /**
     * Returns the workflow manager representing the meta-workflow.
     * 
     * @return the meta-workflow manager for this meta-node
     */
    public WorkflowManager getMetaWorkflowManager() {
        createInternalWFM();
        
        return m_internalWFM;
    }

    /**
     * Reacts on a workflow event of the underlying workflow manager of this
     * meta workflow model.
     * 
     * @see de.unikn.knime.core.node.workflow.WorkflowListener#workflowChanged
     *      (de.unikn.knime.core.node.workflow.WorkflowEvent)
     */
    public void workflowChanged(final WorkflowEvent event) {

        if (event instanceof WorkflowEvent.NodeExtrainfoChanged) {
            notifyStateListeners(new NodeStatus.ExtrainfoChanged());
        } else if (event instanceof WorkflowEvent.ConnectionAdded) {
            notifyStateListeners(new NodeStatus.ExtrainfoChanged());
        } else if (event instanceof WorkflowEvent.ConnectionRemoved) {
            notifyStateListeners(new NodeStatus.ExtrainfoChanged());
        } else if (event instanceof WorkflowEvent.ConnectionExtrainfoChanged) {
            notifyStateListeners(new NodeStatus.ExtrainfoChanged());
        }
    }

    /**
     * Notifies all state listeners that the state of this meta node model has
     * changed.
     * 
     * @param state <code>NodeStateListener</code>
     */
    private void notifyStateListeners(final NodeStatus state) {
        for (NodeStateListener l : m_stateListeners) {
            l.stateChanged(state, -1);
        }
    }


    /** 
     * @see de.unikn.knime.core.node.SpecialNodeModel
     *  #inportHasNewDataTable(DataTable, int)
     */
    @Override
    protected void inportHasNewDataTable(final DataTable table,
            final int inPortID) {
        createInternalWFM();
        m_dataInModels[inPortID].setDataTable(table);
        if (table != null) {
            m_internalWFM.executeUpToNode(m_dataInContainer[inPortID].getID(),
                    true);
        }
    }


    /** 
     * @see de.unikn.knime.core.node.SpecialNodeModel#inportWasDisconnected(int)
     */
    @Override
    protected void inportWasDisconnected(final int inPortID) {
        createInternalWFM();
        LOGGER.debug("Resetting input node #" + inPortID);
        super.inportWasDisconnected(inPortID);
        // m_dataInContainer[inPortID].reset();
        if (inPortID < getNrDataIns()) {
            m_dataInModels[inPortID].setDataTable(null);
            m_internalWFM.resetAndConfigureNode(m_dataInContainer[inPortID].getID());
        } else {
            m_modelInModels[inPortID - getNrDataIns()].setPredictorParams(null);
            m_internalWFM.resetAndConfigureNode(
                    m_modelInContainer[inPortID - getNrDataIns()].getID());
        }
    }


    /**
     * Returns the node container for a data input node.
     * 
     * @param index the index of the data input node
     * @return a node container
     */
    protected final NodeContainer dataInContainer(final int index) {
        return m_dataInContainer[index];
    }


    /**
     * Returns the node container for a data output node.
     * 
     * @param index the index of the data output node
     * @return a node container
     */
    protected final NodeContainer dataOutContainer(final int index) {
        return m_dataOutContainer[index];
    }

    
    /**
     * Returns the node container for a model input node.
     * 
     * @param index the index of the model input node
     * @return a node container
     */
    protected final NodeContainer modelInContainer(final int index) {
        return m_modelInContainer[index];
    }

    /**
     * Returns the node container for a model output node.
     * 
     * @param index the index of the model output node
     * @return a node container
     */
    protected final NodeContainer modelOutContainer(final int index) {
        return m_modelOutContainer[index];
    }
    
   
    private void createInternalWFM() {
        if (m_internalWFM == null) {
            m_internalWFM = createSubManager();
            m_internalWFM.addListener(this);
            addInOutNodes();
        }        
    }


    /**
     * @see de.unikn.knime.core.node.NodeModel
     *  #saveSettingsTo(de.unikn.knime.core.node.NodeSettings)
     */
    @Override
    protected void saveSettingsTo(final NodeSettings settings) {
        if (m_internalWFM == null) { return; }
        
        NodeSettings connections = settings.addConfig(INOUT_CONNECTIONS_KEY);
        
        int count = 0;
        int[] conn = new int[4];
        
        for (NodeContainer nc : m_dataInContainer) {
            List<ConnectionContainer> conncon =
                m_internalWFM.getOutgoingConnectionsAt(nc, 0);
            for (ConnectionContainer cc : conncon) {
                conn[0] = cc.getSource().getID();
                conn[1] = cc.getSourcePortID();
                conn[2] = cc.getTarget().getID();
                conn[3] = cc.getTargetPortID();
                
                connections.addIntArray("connection_dataIn_" + count++, conn);
            }
        }
        
        for (NodeContainer nc : m_dataOutContainer) {
            ConnectionContainer cc =
                m_internalWFM.getIncomingConnectionAt(nc, 0);
            if (cc != null) {
                conn[0] = cc.getSource().getID();
                conn[1] = cc.getSourcePortID();
                conn[2] = cc.getTarget().getID();
                conn[3] = cc.getTargetPortID();
                
                connections.addIntArray("connection_dataOut_" + count++, conn);
            }
        }

        for (NodeContainer nc : m_modelInContainer) {
            List<ConnectionContainer> conncon =
                m_internalWFM.getOutgoingConnectionsAt(nc, 0);
            for (ConnectionContainer cc : conncon) {
                conn[0] = cc.getSource().getID();
                conn[1] = cc.getSourcePortID();
                conn[2] = cc.getTarget().getID();
                conn[3] = cc.getTargetPortID();
                
                connections.addIntArray("connection_modelIn_" + count++, conn);
            }
        }
        
        for (NodeContainer nc : m_modelOutContainer) {
            ConnectionContainer cc =
                m_internalWFM.getIncomingConnectionAt(nc, 0);
            if (cc != null) {
                conn[0] = cc.getSource().getID();
                conn[1] = cc.getSourcePortID();
                conn[2] = cc.getTarget().getID();
                conn[3] = cc.getTargetPortID();
                
                connections.addIntArray("connection_modelOut_" + count++, conn);
            }
        }
    }
    
    protected WorkflowManager internalWFM() {
        createInternalWFM();
        return m_internalWFM;
    }
}
