package ru.runa.gpd.editor.graphiti;

import com.google.common.base.Strings;
import com.google.common.collect.Sets;
import java.util.List;
import java.util.Set;
import org.eclipse.graphiti.dt.IDiagramTypeProvider;
import org.eclipse.graphiti.features.ICreateConnectionFeature;
import org.eclipse.graphiti.features.ICreateFeature;
import org.eclipse.graphiti.features.context.IDoubleClickContext;
import org.eclipse.graphiti.features.context.IPictogramElementContext;
import org.eclipse.graphiti.features.context.impl.CreateConnectionContext;
import org.eclipse.graphiti.features.context.impl.CreateContext;
import org.eclipse.graphiti.features.custom.ICustomFeature;
import org.eclipse.graphiti.mm.algorithms.GraphicsAlgorithm;
import org.eclipse.graphiti.mm.algorithms.Image;
import org.eclipse.graphiti.mm.algorithms.Polyline;
import org.eclipse.graphiti.mm.pictograms.ConnectionDecorator;
import org.eclipse.graphiti.mm.pictograms.ContainerShape;
import org.eclipse.graphiti.mm.pictograms.PictogramElement;
import org.eclipse.graphiti.mm.pictograms.Shape;
import org.eclipse.graphiti.tb.ContextButtonEntry;
import org.eclipse.graphiti.tb.DefaultToolBehaviorProvider;
import org.eclipse.graphiti.tb.IContextButtonPadData;
import org.eclipse.ui.views.properties.IPropertyDescriptor;
import org.eclipse.ui.views.properties.IPropertySource;
import ru.runa.gpd.Activator;
import ru.runa.gpd.Localization;
import ru.runa.gpd.PropertyNames;
import ru.runa.gpd.editor.graphiti.create.CreateDataStoreFeature;
import ru.runa.gpd.editor.graphiti.create.CreateDottedTransitionFeature;
import ru.runa.gpd.editor.graphiti.create.CreateDragAndDropElementFeature;
import ru.runa.gpd.editor.graphiti.create.CreateElementFeature;
import ru.runa.gpd.editor.graphiti.create.CreateStartNodeFeature;
import ru.runa.gpd.editor.graphiti.create.CreateSwimlaneFeature;
import ru.runa.gpd.editor.graphiti.create.CreateTransitionFeature;
import ru.runa.gpd.editor.graphiti.update.OpenSubProcessFeature;
import ru.runa.gpd.extension.HandlerArtifact;
import ru.runa.gpd.extension.HandlerRegistry;
import ru.runa.gpd.lang.NodeRegistry;
import ru.runa.gpd.lang.NodeTypeDefinition;
import ru.runa.gpd.lang.model.Action;
import ru.runa.gpd.lang.model.Delegable;
import ru.runa.gpd.lang.model.GraphElement;
import ru.runa.gpd.lang.model.Node;
import ru.runa.gpd.lang.model.Subprocess;
import ru.runa.gpd.lang.model.Swimlane;
import ru.runa.gpd.lang.model.TaskState;
import ru.runa.gpd.lang.model.Transition;
import ru.runa.gpd.lang.model.bpmn.DottedTransition;
import ru.runa.gpd.lang.model.bpmn.TextDecorationNode;
import ru.runa.gpd.settings.PrefConstants;

public class DiagramToolBehaviorProvider extends DefaultToolBehaviorProvider {
    public DiagramToolBehaviorProvider(IDiagramTypeProvider provider) {
        super(provider);
    }

    @Override
    protected DiagramFeatureProvider getFeatureProvider() {
        return (DiagramFeatureProvider) super.getFeatureProvider();
    }

    @Override
    public ICustomFeature getDoubleClickFeature(IDoubleClickContext context) {
        if (context.getPictogramElements().length == 1) {
            PictogramElement pe = context.getPictogramElements()[0];
            GraphElement element = (GraphElement) getFeatureProvider().getBusinessObjectForPictogramElement(pe);
            if (element instanceof Subprocess) {
                return new OpenSubProcessFeature(getFeatureProvider());
            }
            NodeTypeDefinition definition = element.getTypeDefinition();
            if (definition != null && definition.getGraphitiEntry() != null) {
                return definition.getGraphitiEntry().createDoubleClickFeature(getFeatureProvider());
            }
        }
        return super.getDoubleClickFeature(context);
    }

    @Override
    public IContextButtonPadData getContextButtonPad(IPictogramElementContext context) {
        IContextButtonPadData data = super.getContextButtonPad(context);
        PictogramElement pe = context.getPictogramElement();
        GraphElement element = (GraphElement) getFeatureProvider().getBusinessObjectForPictogramElement(pe);
        if (element == null || element instanceof Swimlane || element instanceof TextDecorationNode) {
            return null;
        }
        setGenericContextButtons(data, pe, CONTEXT_BUTTON_DELETE);
        //
        CreateConnectionContext createConnectionContext = new CreateConnectionContext();
        createConnectionContext.setSourcePictogramElement(pe);
        boolean allowTargetNodeCreation = (element instanceof Node) && ((Node) element).canAddLeavingTransition();
        //
        ContainerShape targetContainer;
        if (element.getParentContainer() instanceof Swimlane) {
            targetContainer = (ContainerShape) getFeatureProvider().getPictogramElementForBusinessObject(element.getParentContainer());
        } else {
            targetContainer = getFeatureProvider().getDiagramTypeProvider().getDiagram();
        }
        CreateContext createContext = new CreateContext();
        createContext.setTargetContainer(targetContainer);
        createContext.putProperty(CreateElementFeature.CONNECTION_PROPERTY, createConnectionContext);

        boolean expandContextButtonPad = Activator.getPrefBoolean(PrefConstants.P_BPMN_EXPAND_CONTEXT_BUTTON_PAD);
        if (allowTargetNodeCreation && !expandContextButtonPad) {
            NodeTypeDefinition taskStateDefinition = NodeRegistry.getNodeTypeDefinition(TaskState.class);
            CreateDragAndDropElementFeature createTaskStateFeature = new CreateDragAndDropElementFeature(createContext);
            createTaskStateFeature.setNodeDefinition(taskStateDefinition);
            createTaskStateFeature.setFeatureProvider(getFeatureProvider());
            ContextButtonEntry createTaskStateButton = new ContextButtonEntry(createTaskStateFeature, createConnectionContext);
            createTaskStateButton.setText(taskStateDefinition.getLabel());
            createTaskStateButton.setIconId(taskStateDefinition.getPaletteIcon());
            createTaskStateButton.addDragAndDropFeature(createTaskStateFeature);
            data.getDomainSpecificContextButtons().add(createTaskStateButton);
        }

        //
        ContextButtonEntry createTransitionButton = new ContextButtonEntry(null, context);
        NodeTypeDefinition transitionDefinition = NodeRegistry.getNodeTypeDefinition(Transition.class);
        createTransitionButton.setText(transitionDefinition.getLabel());
        createTransitionButton.setIconId(transitionDefinition.getPaletteIcon());
        ContextButtonEntry createDottedTransitionButton = new ContextButtonEntry(null, context);
        NodeTypeDefinition dottedTransitionDefinition = NodeRegistry.getNodeTypeDefinition(DottedTransition.class);
        createDottedTransitionButton.setText(dottedTransitionDefinition.getLabel());
        createDottedTransitionButton.setIconId(dottedTransitionDefinition.getPaletteIcon());
        ICreateConnectionFeature[] features = getFeatureProvider().getCreateConnectionFeatures();
        for (ICreateConnectionFeature feature : features) {
            if (feature.isAvailable(createConnectionContext)
                    && feature.canStartConnection(createConnectionContext)) {
                if(feature instanceof CreateTransitionFeature)
                    createTransitionButton.addDragAndDropFeature(feature);
                if(feature instanceof CreateDottedTransitionFeature)
                    createDottedTransitionButton.addDragAndDropFeature(feature);
            }
        }
        //
        
        if (allowTargetNodeCreation) {
            ContextButtonEntry createElementButton = null;
            if (!expandContextButtonPad) {
                createElementButton = new ContextButtonEntry(null, null);
                createElementButton.setText(Localization.getString("new.element.label"));
                createElementButton.setDescription(Localization.getString("new.element.description"));
                createElementButton.setIconId("elements.png");
                data.getDomainSpecificContextButtons().add(createElementButton);
            }

            for (ICreateFeature feature : getFeatureProvider().getCreateFeatures()) {
                if (feature instanceof CreateSwimlaneFeature || feature instanceof CreateStartNodeFeature
                        || feature instanceof CreateDataStoreFeature) {
                    continue;
                }
                if (feature instanceof CreateElementFeature && feature.canCreate(createContext)) {
                    CreateElementFeature createElementFeature = (CreateElementFeature) feature;
                    NodeTypeDefinition typeDefinition = createElementFeature.getNodeDefinition();
                    CreateDragAndDropElementFeature createDragAndDropElementFeature = new CreateDragAndDropElementFeature(createContext);
                    createDragAndDropElementFeature.setNodeDefinition(typeDefinition);
                    createDragAndDropElementFeature.setFeatureProvider(getFeatureProvider());
                    ContextButtonEntry createButton = new ContextButtonEntry(createDragAndDropElementFeature, createConnectionContext);
                    createButton.setText(typeDefinition.getLabel());
                    createButton.setIconId(typeDefinition.getPaletteIcon());
                    if (expandContextButtonPad) {
                        createButton.addDragAndDropFeature(createDragAndDropElementFeature);
                        data.getDomainSpecificContextButtons().add(createButton);
                    } else {
                        createElementButton.addDragAndDropFeature(createDragAndDropElementFeature);
                        createElementButton.getContextButtonMenuEntries().add(createButton);
                    }
                }
            }
        }
        if (createTransitionButton.getDragAndDropFeatures().size() > 0) {
            if (expandContextButtonPad) {
                data.getDomainSpecificContextButtons().add(createTransitionButton);
            } else {
                data.getDomainSpecificContextButtons().add(createTransitionButton.getDragAndDropFeatures().size(), createTransitionButton);
            }
        }
        if (createDottedTransitionButton.getDragAndDropFeatures().size() > 0) {
            if (expandContextButtonPad) {
                data.getDomainSpecificContextButtons().add(createDottedTransitionButton);
            } else {
                data.getDomainSpecificContextButtons().add(createDottedTransitionButton.getDragAndDropFeatures().size(), createDottedTransitionButton);
            }
        }
        return data;
    }

    static final private Set<String> TOOL_TIP_PROPERTY_NAMES = Sets.newHashSet(PropertyNames.PROPERTY_ID, PropertyNames.PROPERTY_NAME,
            PropertyNames.PROPERTY_DESCRIPTION, PropertyNames.PROPERTY_CLASS);

    @Override
    public String getToolTip(GraphicsAlgorithm ga) {
        PictogramElement pe = ga.getPictogramElement();
        if (ga instanceof Polyline) {
            Object element = getFeatureProvider().getBusinessObjectForPictogramElement(pe);
            if (element instanceof Transition) {
                Transition transition = (Transition) element;
                Object orderNum = transition.getPropertyValue(Transition.PROPERTY_ORDERNUM);
                if (orderNum != null) {
                    return Localization.getString("Transition.property.orderNum") + ": " + orderNum;
                }
            }
        }
        Object bo = getFeatureProvider().getBusinessObjectForPictogramElement(pe);
        if (bo instanceof IPropertySource) {
            IPropertyDescriptor[] propertyDescriptors = ((IPropertySource) bo).getPropertyDescriptors();
            String toolTip = "";
            for (IPropertyDescriptor propertyDescriptor : propertyDescriptors) {
                if (TOOL_TIP_PROPERTY_NAMES.contains(propertyDescriptor.getId())) {
                    Object propertyValue = ((IPropertySource) bo).getPropertyValue(propertyDescriptor.getId());
                    if (propertyValue != null && !Strings.isNullOrEmpty(propertyValue.toString())) {
                        if (!Strings.isNullOrEmpty(toolTip)) {
                            toolTip += "\n";
                        }
                        toolTip += " " + propertyDescriptor.getDisplayName() + ": " + propertyValue + " ";
                    }
                }
            }
            if (bo instanceof Delegable) {
                String delegationClassName = ((Delegable) bo).getDelegationClassName();
                HandlerArtifact delegationClassNameArtifact = HandlerRegistry.getInstance().getArtifact(delegationClassName);
                if (delegationClassNameArtifact != null) {
                    toolTip = toolTip.replace(delegationClassName, delegationClassNameArtifact.getLabel());
                }
            }
            if (!Strings.isNullOrEmpty(toolTip)) {
                return toolTip;
            }
        }
        if (ga instanceof Image && PropertyUtil.hasProperty(pe, GaProperty.CLASS, GaProperty.ACTIONS_ICON)) {
            GraphElement ge = (GraphElement) getFeatureProvider().getBusinessObjectForPictogramElement(((Shape) pe).getContainer());
            List<Action> actions = ge.getActions();
            String toolTip = " " + Localization.getString("pref.extensions.handler") + ": ";
            for (Action act : actions) {
                toolTip += " \n " + act.getLabel();
            }
            return toolTip;
        }
        return (String) super.getToolTip(ga);
    }

    @Override
    public PictogramElement getSelection(PictogramElement originalPe, PictogramElement[] oldSelection) {
        if (originalPe instanceof ConnectionDecorator) {
            if (PropertyUtil.hasProperty(originalPe, GaProperty.ID, GaProperty.TRANSITION_NUMBER)
                    || PropertyUtil.hasProperty(originalPe, GaProperty.ID, GaProperty.TRANSITION_COLOR_MARKER)) {
                return getDiagramTypeProvider().getDiagram();
            }
        }
        return super.getSelection(originalPe, oldSelection);
    }

}
