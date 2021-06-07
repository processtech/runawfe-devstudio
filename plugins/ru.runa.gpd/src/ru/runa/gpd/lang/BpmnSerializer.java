package ru.runa.gpd.lang;

import com.google.common.base.Objects;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.dom4j.Document;
import org.dom4j.Element;
import org.dom4j.QName;
import org.eclipse.core.resources.IFile;
import ru.runa.gpd.Application;
import ru.runa.gpd.Localization;
import ru.runa.gpd.PluginLogger;
import ru.runa.gpd.PropertyNames;
import ru.runa.gpd.editor.graphiti.TransitionUtil;
import ru.runa.gpd.lang.model.ActionImpl;
import ru.runa.gpd.lang.model.Delegable;
import ru.runa.gpd.lang.model.Describable;
import ru.runa.gpd.lang.model.EndState;
import ru.runa.gpd.lang.model.EndTokenState;
import ru.runa.gpd.lang.model.EndTokenSubprocessDefinitionBehavior;
import ru.runa.gpd.lang.model.GraphElement;
import ru.runa.gpd.lang.model.ISendMessageNode;
import ru.runa.gpd.lang.model.ITimed;
import ru.runa.gpd.lang.model.MultiSubprocess;
import ru.runa.gpd.lang.model.MultiTaskState;
import ru.runa.gpd.lang.model.NamedGraphElement;
import ru.runa.gpd.lang.model.Node;
import ru.runa.gpd.lang.model.NodeAsyncExecution;
import ru.runa.gpd.lang.model.ProcessDefinition;
import ru.runa.gpd.lang.model.StartState;
import ru.runa.gpd.lang.model.Subprocess;
import ru.runa.gpd.lang.model.SubprocessDefinition;
import ru.runa.gpd.lang.model.Swimlane;
import ru.runa.gpd.lang.model.SwimlanedNode;
import ru.runa.gpd.lang.model.TaskState;
import ru.runa.gpd.lang.model.Timer;
import ru.runa.gpd.lang.model.TimerAction;
import ru.runa.gpd.lang.model.Transition;
import ru.runa.gpd.lang.model.TransitionColor;
import ru.runa.gpd.lang.model.Variable;
import ru.runa.gpd.lang.model.bpmn.AbstractEventNode;
import ru.runa.gpd.lang.model.bpmn.BusinessRule;
import ru.runa.gpd.lang.model.bpmn.CatchEventNode;
import ru.runa.gpd.lang.model.bpmn.ConnectableViaDottedTransition;
import ru.runa.gpd.lang.model.bpmn.DataStore;
import ru.runa.gpd.lang.model.bpmn.DottedTransition;
import ru.runa.gpd.lang.model.bpmn.EventNodeType;
import ru.runa.gpd.lang.model.bpmn.ExclusiveGateway;
import ru.runa.gpd.lang.model.bpmn.IBoundaryEvent;
import ru.runa.gpd.lang.model.bpmn.IBoundaryEventContainer;
import ru.runa.gpd.lang.model.bpmn.ParallelGateway;
import ru.runa.gpd.lang.model.bpmn.ScriptTask;
import ru.runa.gpd.lang.model.bpmn.TextAnnotation;
import ru.runa.gpd.lang.model.bpmn.ThrowEventNode;
import ru.runa.gpd.ui.custom.Dialogs;
import ru.runa.gpd.util.Duration;
import ru.runa.gpd.util.MultiinstanceParameters;
import ru.runa.gpd.util.SwimlaneDisplayMode;
import ru.runa.gpd.util.VariableMapping;
import ru.runa.gpd.util.XmlUtil;
import ru.runa.wfe.definition.ProcessDefinitionAccessType;
import ru.runa.wfe.lang.AsyncCompletionMode;
import ru.runa.wfe.lang.MultiTaskCreationMode;
import ru.runa.wfe.lang.MultiTaskSynchronizationMode;

@SuppressWarnings("unchecked")
public class BpmnSerializer extends ProcessSerializer {

    private static final String BPMN_PREFIX = "";
    private static final String BPMN_NAMESPACE = "http://www.omg.org/spec/BPMN/20100524/MODEL";
    private static final String RUNA_PREFIX = "runa";
    private static final String RUNA_NAMESPACE = "http://runa.ru/wfe/xml";
    private static final String DEFINITIONS = "definitions";
    private static final String PROCESS = "process";
    private static final String EXTENSION_ELEMENTS = "extensionElements";
    private static final String EXECUTABLE = "isExecutable";
    private static final String PROPERTY = "property";
    private static final String END_EVENT = "endEvent";
    private static final String TOKEN = "token";
    private static final String TEXT_ANNOTATION = "textAnnotation";
    private static final String TEXT = "text";
    private static final String SERVICE_TASK = "serviceTask";
    private static final String SCRIPT_TASK = "scriptTask";
    public static final String DATA_STORE = "dataStore";
    private static final String VARIABLES = "variables";
    private static final String SOURCE_REF = "sourceRef";
    private static final String TARGET_REF = "targetRef";
    private static final String SUBPROCESS = "subProcess";
    private static final String MULTI_INSTANCE = "multiInstance";
    private static final String EXCLUSIVE_GATEWAY = "exclusiveGateway";
    private static final String BUSINESS_RULE = "businessRuleTask";
    private static final String PARALLEL_GATEWAY = "parallelGateway";
    private static final String DEFAULT_TASK_DEADLINE = "defaultTaskDeadline";
    private static final String TASK_DEADLINE = "taskDeadline";
    private static final String USER_TASK = "userTask";
    private static final String MULTI_TASK = "multiTask";
    private static final String START_EVENT = "startEvent";
    private static final String LANE_SET = "laneSet";
    private static final String LANE = "lane";
    private static final String FLOW_NODE_REF = "flowNodeRef";
    public static final String SHOW_SWIMLANE = "showSwimlane";
    private static final String SEQUENCE_FLOW = "sequenceFlow";
    public static final String DOTTED_TRANSITION = "dataStoreReference";
    private static final String ITEM_SUBJECT_REF = "itemSubjectRef";
    private static final String DATA_STORE_REF = "dataStoreRef";
    private static final String DOCUMENTATION = "documentation";
    private static final String CONFIG = "config";
    private static final String MAPPED_NAME = "mappedName";
    private static final String USAGE = "usage";
    private static final String SEND_TASK = "sendTask";
    private static final String RECEIVE_TASK = "receiveTask";
    private static final String BOUNDARY_EVENT = "boundaryEvent";
    private static final String INTERMEDIATE_THROW_EVENT = "intermediateThrowEvent";
    private static final String INTERMEDIATE_CATCH_EVENT = "intermediateCatchEvent";
    private static final String TIMER_EVENT_DEFINITION = "timerEventDefinition";
    private static final String CANCEL_ACTIVITY = "cancelActivity";
    private static final String ATTACHED_TO_REF = "attachedToRef";
    private static final String TIME_DURATION = "timeDuration";
    private static final String REPEAT = "repeat";
    public static final String START_TEXT_DECORATION = "startTextDecoration";
    public static final String END_TEXT_DECORATION = "endTextDecoration";
    private static final String ACTION_HANDLER = "actionHandler";
    private static final String EVENT_TYPE = "eventType";
    private static final String PROPERTY_USE_EXTERNAL_STORAGE_OUT = "useExternalStorageOut";
    private static final String PROPERTY_USE_EXTERNAL_STORAGE_IN = "useExternalStorageIn";

    @Override
    public boolean isSupported(Document document) {
        return DEFINITIONS.equals(document.getRootElement().getName());
    }

    @Override
    public Document getInitialProcessDefinitionDocument(String processName, Map<String, String> properties) {
        Document document = XmlUtil.createDocument(DEFINITIONS);
        Element definitionsElement = document.getRootElement();
        definitionsElement.addNamespace(BPMN_PREFIX, BPMN_NAMESPACE);
        definitionsElement.addNamespace("bpmndi", "http://www.omg.org/spec/BPMN/20100524/DI");
        definitionsElement.addNamespace("omgdc", "http://www.omg.org/spec/DD/20100524/DC");
        definitionsElement.addNamespace("omgdi", "http://www.omg.org/spec/DD/20100524/DI");
        definitionsElement.addNamespace(RUNA_PREFIX, RUNA_NAMESPACE);
        definitionsElement.addAttribute("targetNamespace", RUNA_NAMESPACE);
        Element process = definitionsElement.addElement(PROCESS, BPMN_NAMESPACE);
        process.addAttribute(NAME, processName);
        if (properties != null) {
            writeExtensionElements(process, properties);
        }
        return document;
    }

    @Override
    public void saveToXML(ProcessDefinition definition, Document document) {
        Element definitionsElement = document.getRootElement();
        writeDataStores(definitionsElement, definition.getChildren(DataStore.class));
        Element processElement = definitionsElement.element(QName.get(PROCESS, BPMN_NAMESPACE));
        processElement.addAttribute(NAME, definition.getName());
        Map<String, String> processProperties = Maps.newLinkedHashMap();
        if (definition.getDefaultTaskTimeoutDelay().hasDuration()) {
            processProperties.put(DEFAULT_TASK_DEADLINE, definition.getDefaultTaskTimeoutDelay().getDuration());
        }
        if (definition.getSwimlaneDisplayMode() != null) {
            processProperties.put(SHOW_SWIMLANE, definition.getSwimlaneDisplayMode().name());
        }
        if (definition.getId() != null) {
            processProperties.put(ID, definition.getId());
        }
        processProperties.put(ACCESS_TYPE, definition.getAccessType().name());
        processProperties.put(VERSION, Application.getVersion().toString());
        if (definition.isInvalid()) {
            processElement.addAttribute(EXECUTABLE, "false");
        }
        if (!Strings.isNullOrEmpty(definition.getDescription())) {
            processProperties.put(DOCUMENTATION, definition.getDescription());
        }
        if (definition.getDefaultNodeAsyncExecution() != NodeAsyncExecution.DEFAULT) {
            processProperties.put(NODE_ASYNC_EXECUTION, definition.getDefaultNodeAsyncExecution().getValue());
        }
        if (definition.isUseGlobals()) {
            processProperties.put(USE_GLOBALS, "true");
        }
        writeExtensionElements(processElement, processProperties);
        if (definition.getClass() != SubprocessDefinition.class) {
            Element laneSetElement = processElement.addElement(LANE_SET).addAttribute(ID, "laneSet1");
            List<Swimlane> swimlanes = definition.getSwimlanes();
            for (Swimlane swimlane : swimlanes) {
                Element swimlaneElement = writeElement(laneSetElement, swimlane);
                writeDelegation(swimlaneElement, swimlane);
                List<GraphElement> swimlaneElements = definition.getContainerElements(swimlane);
                for (GraphElement child : swimlaneElements) {
                    swimlaneElement.addElement(FLOW_NODE_REF).addText(child.getId());
                }
            }
        }
        StartState startState = definition.getFirstChild(StartState.class);
        if (startState != null) {
            writeTaskState(processElement, startState);
            writeTransitions(processElement, startState);
        }
        List<ExclusiveGateway> exclusiveGateways = definition.getChildren(ExclusiveGateway.class);
        for (ExclusiveGateway gateway : exclusiveGateways) {
            writeNode(processElement, gateway);
        }
        List<BusinessRule> businessRules = definition.getChildren(BusinessRule.class);
        for (BusinessRule businessRule : businessRules) {
            writeNode(processElement, businessRule);
        }
        List<TaskState> taskStates = definition.getChildren(TaskState.class);
        for (TaskState taskState : taskStates) {
            writeTaskState(processElement, taskState);
            writeBoundaryTimer(processElement, taskState);
            writeBoundaryEvents(processElement, taskState);
            writeTransitions(processElement, taskState);
        }
        List<Timer> timers = definition.getChildren(Timer.class);
        for (Timer timer : timers) {
            Element intermediateEventElement = processElement.addElement(INTERMEDIATE_CATCH_EVENT);
            writeTimer(intermediateEventElement, timer);
            writeBoundaryEvents(processElement, timer);
            writeTransitions(processElement, timer);
        }
        List<ScriptTask> scriptTasks = definition.getChildren(ScriptTask.class);
        for (ScriptTask scriptTask : scriptTasks) {
            writeNode(processElement, scriptTask);
            writeBoundaryEvents(processElement, scriptTask);
            writeDottedTransitions(processElement, scriptTask);
        }
        List<DataStore> dataStores = definition.getChildren(DataStore.class);
        for (DataStore dataStore : dataStores) {
            writeDottedTransitions(processElement, dataStore);
        }
        List<ParallelGateway> parallelGateways = definition.getChildren(ParallelGateway.class);
        for (ParallelGateway gateway : parallelGateways) {
            writeNode(processElement, gateway);
        }
        List<Subprocess> subprocesses = definition.getChildren(Subprocess.class);
        for (Subprocess subprocess : subprocesses) {
            Element element = writeNode(processElement, subprocess);
            element.addAttribute(RUNA_PREFIX + ":" + PROCESS, subprocess.getSubProcessName());
            Map<String, Object> properties = Maps.newLinkedHashMap();
            if (subprocess instanceof MultiSubprocess) {
                properties.put(MULTI_INSTANCE, true);
                properties.put(PropertyNames.PROPERTY_DISCRIMINATOR_CONDITION, ((MultiSubprocess) subprocess).getDiscriminatorCondition());
            }
            if (subprocess.isEmbedded()) {
                properties.put(EMBEDDED, true);
            }
            if (subprocess.isTransactional()) {
                properties.put(TRANSACTIONAL, true);
            }
            if (subprocess.isValidateAtStart()) {
                properties.put(VALIDATE_AT_START, true);
            }
            if (subprocess.isAsync()) {
                properties.put(ASYNC, Boolean.TRUE.toString());
                properties.put(ASYNC_COMPLETION_MODE, subprocess.getAsyncCompletionMode().name());
            }
            writeExtensionElements(element, properties);
            if (!subprocess.isEmbedded()) {
                writeVariables(element, subprocess.getVariableMappings());
            }
            writeBoundaryEvents(processElement, subprocess);
        }
        List<ThrowEventNode> throwEventNodes = definition.getChildren(ThrowEventNode.class);
        for (ThrowEventNode throwEventNode : throwEventNodes) {
            writeEventNode(processElement, throwEventNode);
        }
        List<CatchEventNode> catchEventNodes = definition.getChildren(CatchEventNode.class);
        for (CatchEventNode catchEventNode : catchEventNodes) {
            writeEventNode(processElement, catchEventNode);
            writeBoundaryEvents(processElement, catchEventNode);
            writeBoundaryTimer(processElement, catchEventNode);
        }
        List<EndTokenState> endTokenStates = definition.getChildren(EndTokenState.class);
        for (EndTokenState endTokenState : endTokenStates) {
            Element element = writeNode(processElement, endTokenState);
            Map<String, String> properties = Maps.newLinkedHashMap();
            properties.put(TOKEN, "true");
            if (definition instanceof SubprocessDefinition) {
                properties.put(BEHAVIOR, endTokenState.getSubprocessDefinitionBehavior().name());
            }
            writeExtensionElements(element, properties);
        }

        List<EndState> endStates = definition.getChildren(EndState.class);
        for (EndState endState : endStates) {
            writeNode(processElement, endState);
        }
        List<TextAnnotation> textAnnotations = definition.getChildren(TextAnnotation.class);
        for (TextAnnotation textAnnotation : textAnnotations) {
            Element element = processElement.addElement(textAnnotation.getTypeDefinition().getBpmnElementName());
            setAttribute(element, ID, textAnnotation.getId());
            String description = textAnnotation.getDescription();
            if (!Strings.isNullOrEmpty(description)) {
                element.addElement(TEXT).addCDATA(description);
            }
        }
    }

    private void writeVariables(Element element, List<VariableMapping> variableMappings) {
        Map<String, Object> properties = Maps.newLinkedHashMap();
        properties.put(VARIABLES, variableMappings);
        writeExtensionElements(element, properties);
    }

    private Element writeNode(Element processElement, Node node) {
        Element nodeElement = writeElement(processElement, node);
        if (node.isDelegable()) {
            writeDelegation(nodeElement, (Delegable) node);
        }
        writeNodeAsyncExecution(nodeElement, node);
        writeTransitions(processElement, node);
        return nodeElement;
    }

    private void writeNodeAsyncExecution(Element nodeElement, Node node) {
        if (node.getAsyncExecution() != NodeAsyncExecution.DEFAULT) {
            Map<String, Object> properties = Maps.newLinkedHashMap();
            properties.put(NODE_ASYNC_EXECUTION, node.getAsyncExecution().getValue());
            writeExtensionElements(nodeElement, properties);
        }
    }

    private Element writeTaskState(Element parent, SwimlanedNode swimlanedNode) {
        Element nodeElement = writeElement(parent, swimlanedNode);
        Map<String, String> properties = Maps.newLinkedHashMap();
        String swimlaneName = swimlanedNode.getSwimlaneName();
        if (((ProcessDefinition) swimlanedNode.getParent()).getSwimlaneByName(swimlaneName) != null) {
            properties.put(LANE, swimlaneName);
        }
        if (swimlanedNode instanceof StartState) {
            StartState startState = (StartState) swimlanedNode;
            if (startState.isReassignSwimlaneToTaskPerformer() != null) {
                properties.put(REASSIGN_SWIMLANE_TO_TASK_PERFORMER, String.valueOf(startState.isReassignSwimlaneToTaskPerformer()));
            }
        }
        if (swimlanedNode instanceof TaskState) {
            TaskState taskState = (TaskState) swimlanedNode;
            if (taskState.isAsync()) {
                properties.put(ASYNC, Boolean.TRUE.toString());
                properties.put(ASYNC_COMPLETION_MODE, taskState.getAsyncCompletionMode().name());
            }
            String taskDeadline = taskState.getTimeOutDueDate();
            if (taskDeadline != null) {
                properties.put(TASK_DEADLINE, taskDeadline);
            }
            if (taskState.isReassignSwimlaneToInitializer() != null) {
                properties.put(REASSIGN, String.valueOf(taskState.isReassignSwimlaneToInitializer()));
            }
            if (taskState.isReassignSwimlaneToTaskPerformer() != null) {
                properties.put(REASSIGN_SWIMLANE_TO_TASK_PERFORMER, String.valueOf(taskState.isReassignSwimlaneToTaskPerformer()));
            }
            if (taskState.isIgnoreSubstitutionRules()) {
                properties.put(IGNORE_SUBSTITUTION_RULES, "true");
            }
        }
        if (swimlanedNode instanceof MultiTaskState) {
            MultiTaskState multiTaskNode = (MultiTaskState) swimlanedNode;
            properties.put(PropertyNames.PROPERTY_MULTI_TASK_CREATION_MODE, multiTaskNode.getCreationMode().name());
            properties.put(PropertyNames.PROPERTY_MULTI_TASK_SYNCHRONIZATION_MODE, multiTaskNode.getSynchronizationMode().name());
            properties.put(PropertyNames.PROPERTY_DISCRIMINATOR_USAGE, multiTaskNode.getDiscriminatorUsage());
            properties.put(PropertyNames.PROPERTY_DISCRIMINATOR_VALUE, multiTaskNode.getDiscriminatorValue());
            properties.put(PropertyNames.PROPERTY_DISCRIMINATOR_CONDITION, multiTaskNode.getDiscriminatorCondition());
            writeVariables(nodeElement, multiTaskNode.getVariableMappings());
        }
        writeExtensionElements(nodeElement, properties);
        writeNodeAsyncExecution(nodeElement, swimlanedNode);
        return nodeElement;
    }

    private void writeBoundaryTimer(Element processElement, ITimed timed) {
        Timer timer = timed.getTimer();
        if (timer == null) {
            return;
        }
        Element boundaryEventElement = processElement.addElement(BOUNDARY_EVENT);
        writeTimer(boundaryEventElement, timer);
        boundaryEventElement.addAttribute(CANCEL_ACTIVITY, String.valueOf(timer.isInterruptingBoundaryEvent()));
        boundaryEventElement.addAttribute(ATTACHED_TO_REF, ((GraphElement) timed).getId());
        writeTransitions(processElement, timer);
    }

    private void writeTimer(Element parentElement, Timer timer) {
        if (timer == null) {
            return;
        }
        writeBaseProperties(parentElement, timer);
        Element eventDefinitionElement = parentElement.addElement(timer.getTypeDefinition().getBpmnElementName());
        // TODO bc
        // if (!Strings.isNullOrEmpty(timer.getDescription())) {
        // eventDefinitionElement.addElement(DOCUMENTATION).addCDATA(timer.getDescription());
        // }
        writeNodeAsyncExecution(eventDefinitionElement, timer);
        TimerAction timerAction = timer.getAction();
        if (timerAction != null) {
            writeDelegation(eventDefinitionElement, timerAction);
            if (timerAction.getRepeatDelay().hasDuration()) {
                Element extensionsElement = eventDefinitionElement.element(EXTENSION_ELEMENTS);
                Element propertyElement = extensionsElement.addElement(RUNA_PREFIX + ":" + PROPERTY);
                propertyElement.addAttribute(NAME, REPEAT);
                propertyElement.addAttribute(VALUE, timerAction.getRepeatDelay().getDuration());
            }
        }
        Element durationElement = eventDefinitionElement.addElement(TIME_DURATION);
        durationElement.addText(timer.getDelay().getDuration());
    }

    private void writeEventNode(Element processElement, AbstractEventNode eventNode) {
        Element intermediateEventElement = writeElement(processElement, eventNode);
        writeEventNodeContent(processElement, intermediateEventElement, eventNode);
    }

    private void writeBoundaryEvents(Element processElement, IBoundaryEventContainer boundaryEventContainer) {
        List<CatchEventNode> catchEventNodes = ((GraphElement) boundaryEventContainer).getChildren(CatchEventNode.class);
        for (CatchEventNode eventNode : catchEventNodes) {
            Element boundaryEventElement = processElement.addElement(BOUNDARY_EVENT);
            writeBaseProperties(boundaryEventElement, eventNode);
            writeEventNodeContent(processElement, boundaryEventElement, eventNode);
            boundaryEventElement.addAttribute(CANCEL_ACTIVITY, String.valueOf(eventNode.isInterruptingBoundaryEvent()));
            boundaryEventElement.addAttribute(ATTACHED_TO_REF, eventNode.getParent().getId());
        }
    }

    private void writeEventNodeContent(Element processElement, Element eventElement, AbstractEventNode eventNode) {
        eventElement.addAttribute(RUNA_PREFIX + ":" + TYPE, eventNode.getEventNodeType().name());
        // setAttribute(eventDefinitionElement, ID, eventNode.getId());
        if (eventNode instanceof ISendMessageNode) {
            eventElement.addAttribute(RUNA_PREFIX + ":" + TIME_DURATION, eventNode.getTtlDuration().getDuration());
        }
        writeVariables(eventElement, eventNode.getVariableMappings());
        writeNodeAsyncExecution(eventElement, eventNode);
        writeTransitions(processElement, eventNode);
        // Element eventDefinitionElement =
        // eventElement.addElement(eventNode.getEventNodeType().getXmlElementName());
    }

    private Element writeElement(Element parent, GraphElement graphElement) {
        String bpmnElementName;
        if (graphElement instanceof EndTokenState) {
            bpmnElementName = END_EVENT;
        } else if (graphElement instanceof MultiSubprocess) {
            bpmnElementName = SUBPROCESS;
        } else {
            bpmnElementName = graphElement.getTypeDefinition().getBpmnElementName();
        }
        Element element = parent.addElement(bpmnElementName);
        writeBaseProperties(element, graphElement);
        writeActionHandlers(element, graphElement);
        return element;
    }

    private void writeBaseProperties(Element element, GraphElement graphElement) {
        setAttribute(element, ID, graphElement.getId());
        if (graphElement instanceof NamedGraphElement) {
            setAttribute(element, NAME, ((NamedGraphElement) graphElement).getName());
        }
        if (graphElement instanceof Describable) {
            String description = ((Describable) graphElement).getDescription();
            if (!Strings.isNullOrEmpty(description)) {
                element.addElement(DOCUMENTATION).addCDATA(description);
            }
        }
    }

    private void writeTransitions(Element processElement, Node node) {
        List<Transition> transitions = node.getLeavingTransitions();
        for (Transition transition : transitions) {
            Element transitionElement = processElement.addElement(SEQUENCE_FLOW);
            transitionElement.addAttribute(ID, transition.getId());
            transitionElement.addAttribute(NAME, transition.getName());
            String sourceNodeId = transition.getSource().getId();
            String targetNodeId = transition.getTarget().getId();
            if (Objects.equal(sourceNodeId, targetNodeId)) {
                throw new IllegalArgumentException("Invalid transition " + transition);
            }
            transitionElement.addAttribute(SOURCE_REF, sourceNodeId);
            transitionElement.addAttribute(TARGET_REF, targetNodeId);
            if (!Strings.isNullOrEmpty(transition.getDescription())) {
                transitionElement.addElement(DOCUMENTATION).addCDATA(transition.getDescription());
            }
            writeActionHandlers(transitionElement, transition);
            if (transition.getColor() != TransitionColor.DEFAULT) {
                Map<String, String> properties = Maps.newLinkedHashMap();
                properties.put(PropertyNames.PROPERTY_COLOR, transition.getColor().name().toLowerCase());
                writeExtensionElements(transitionElement, properties);
            }
        }
    }

    private void writeDottedTransitions(Element processElement, ConnectableViaDottedTransition node) {
        final List<DottedTransition> transitions = node.getLeavingDottedTransitions();
        for (DottedTransition transition : transitions) {
            final Element transitionElement = processElement.addElement(DOTTED_TRANSITION);
            transitionElement.addAttribute(ID, transition.getId());
            transitionElement.addAttribute(NAME, transition.getName());

            final String sourceNodeId = transition.getSource().getId();
            final String targetNodeId = transition.getTarget().getId();
            Preconditions.checkState(!Objects.equal(sourceNodeId, targetNodeId), "Invalid transition " + transition);

            transitionElement.addAttribute(ITEM_SUBJECT_REF, sourceNodeId);
            transitionElement.addAttribute(DATA_STORE_REF, targetNodeId);
        }
    }

    private void writeActionHandlers(Element element, GraphElement graphElement) {
        for (ActionImpl action : graphElement.getChildren(ActionImpl.class)) {
            writeActionHandler(element, action);
        }
    }

    private void writeActionHandler(Element parent, ActionImpl action) {
        Element extElements = parent.element(EXTENSION_ELEMENTS);
        if (extElements == null) {
            extElements = parent.addElement(EXTENSION_ELEMENTS);
        }
        Element element = extElements.addElement(RUNA_PREFIX + ":" + ACTION_HANDLER);
        writeBaseProperties(element, action);
        Map<String, Object> properties = Maps.newLinkedHashMap();
        if (!Strings.isNullOrEmpty(action.getEventType())) {
            properties.put(EVENT_TYPE, action.getEventType());
        }
        properties.put(CLASS, action.getDelegationClassName());
        Element extensionsElement = writeExtensionElements(element, properties);
        if (!Strings.isNullOrEmpty(action.getDelegationConfiguration())) {
            extensionsElement.addElement(RUNA_PREFIX + ":" + PROPERTY).addAttribute(NAME, CONFIG).addCDATA(action.getDelegationConfiguration());
        }
    }

    private void parseBaseProperties(Element element, GraphElement graphElement) {
        graphElement.setId(element.attributeValue(ID));
        if (graphElement instanceof NamedGraphElement) {
            ((NamedGraphElement) graphElement).setName(element.attributeValue(NAME));
        }
        Element description = element.element(DOCUMENTATION);
        if (description != null) {
            graphElement.setDescription(description.getTextTrim());
        }
    }

    private void parseActionHandlers(Element element, GraphElement graphElement) {
        Element extElements = element.element(EXTENSION_ELEMENTS);
        if (extElements != null) {
            List<Element> actionHandlers = extElements.elements(QName.get(ACTION_HANDLER, RUNA_NAMESPACE));
            for (Element actionHandler : actionHandlers) {
                ActionImpl action = create(actionHandler, graphElement);
                parseBaseProperties(actionHandler, action);
                Map<String, String> extProperties = parseExtensionProperties(actionHandler);
                action.setEventType(extProperties.get(EVENT_TYPE));
                action.setDelegationClassName(extProperties.get(CLASS));
                action.setDelegationConfiguration(extProperties.get(CONFIG));
            }
        }
    }

    private Element writeExtensionElements(Element parent, Map<String, ? extends Object> properties) {
        List<VariableMapping> variableMappings = (List<VariableMapping>) properties.remove(VARIABLES);
        if (properties.isEmpty() && (variableMappings == null || variableMappings.isEmpty())) {
            return null;
        }
        Element extensionsElement = parent.element(EXTENSION_ELEMENTS);
        if (extensionsElement == null) {
            extensionsElement = parent.addElement(EXTENSION_ELEMENTS);
        }
        if (variableMappings != null) {
            Element variablesElement = extensionsElement.addElement(RUNA_PREFIX + ":" + VARIABLES);
            for (VariableMapping variableMapping : variableMappings) {
                Element variableElement = variablesElement.addElement(RUNA_PREFIX + ":" + VARIABLE);
                setAttribute(variableElement, NAME, variableMapping.getName());
                setAttribute(variableElement, MAPPED_NAME, variableMapping.getMappedName());
                setAttribute(variableElement, USAGE, variableMapping.getUsage());
            }
        }
        for (Map.Entry<String, ? extends Object> entry : properties.entrySet()) {
            if (entry.getValue() != null) {
                Element propertyElement = extensionsElement.addElement(RUNA_PREFIX + ":" + PROPERTY);
                propertyElement.addAttribute(NAME, entry.getKey());
                propertyElement.addAttribute(VALUE, entry.getValue().toString());
            }
        }
        return extensionsElement;
    }

    private void writeDelegation(Element parent, Delegable delegable) {
        Map<String, Object> properties = Maps.newLinkedHashMap();
        properties.put(CLASS, delegable.getDelegationClassName());
        Element extensionsElement = writeExtensionElements(parent, properties);
        extensionsElement.addElement(RUNA_PREFIX + ":" + PROPERTY).addAttribute(NAME, CONFIG).addCDATA(delegable.getDelegationConfiguration());
        if (delegable instanceof Variable && ((Variable) delegable).isGlobal()) {
            extensionsElement.addElement(RUNA_PREFIX + ":" + PROPERTY).addAttribute(NAME, GLOBAL).addAttribute(VALUE, "true");
        }
        if (delegable instanceof ScriptTask) {
            ScriptTask scriptTask = (ScriptTask) delegable;
            if (scriptTask.isUseExternalStorageIn()) {
                extensionsElement.addElement(RUNA_PREFIX + ":" + PROPERTY).addAttribute(NAME, PROPERTY_USE_EXTERNAL_STORAGE_IN).addAttribute(VALUE,
                        Boolean.TRUE.toString());
            }
            if (scriptTask.isUseExternalStorageOut()) {
                extensionsElement.addElement(RUNA_PREFIX + ":" + PROPERTY).addAttribute(NAME, PROPERTY_USE_EXTERNAL_STORAGE_OUT).addAttribute(VALUE,
                        Boolean.TRUE.toString());
            }
        }
    }

    @Override
    public void validateProcessDefinitionXML(IFile file) {
        try {
            XmlUtil.parseWithXSDValidation(file.getContents(), "BPMN20.xsd");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private <T extends GraphElement> T create(Element node, GraphElement parent) {
        Map<String, String> properties = parseExtensionProperties(node);
        String bpmnElementName;
        if (properties.containsKey(TOKEN)) {
            bpmnElementName = "endTokenEvent";
        } else if (properties.containsKey(MULTI_INSTANCE)) {
            bpmnElementName = "multiProcess";
        } else {
            bpmnElementName = node.getName();
            if (SEND_TASK.equals(bpmnElementName)) {
                bpmnElementName = INTERMEDIATE_THROW_EVENT;
            }
            if (RECEIVE_TASK.equals(bpmnElementName)) {
                bpmnElementName = INTERMEDIATE_CATCH_EVENT;
            }
            if (BOUNDARY_EVENT.equals(bpmnElementName)) {
                bpmnElementName = INTERMEDIATE_CATCH_EVENT;
            }
        }
        GraphElement element = NodeRegistry.getNodeTypeDefinition(Language.BPMN, bpmnElementName).createElement(parent, false);
        init(element, node, properties);
        if (parent != null) {
            parent.addChild(element);
        }
        return (T) element;
    }

    private void init(GraphElement element, Element node, Map<String, String> properties) {
        String nodeId = node.attributeValue(ID);
        String name = node.attributeValue(NAME);
        if (element instanceof Node && nodeId == null) {
            nodeId = name;
        }
        element.setId(nodeId);
        if (element instanceof NamedGraphElement) {
            ((NamedGraphElement) element).setName(name);
        }
        List<Element> nodeList = node.elements();
        for (Element childNode : nodeList) {
            if (DOCUMENTATION.equals(childNode.getName())) {
                ((Describable) element).setDescription(childNode.getTextTrim());
            }
            if (TIME_DURATION.equals(childNode.getName())) {
                ((Timer) element).setDelay(new Duration(childNode.getTextTrim()));
            }
        }
        if (element instanceof Delegable) {
            element.setDelegationClassName(properties.get(CLASS));
            element.setDelegationConfiguration(properties.get(CONFIG));
        }
        if (element instanceof ScriptTask) {
            ((ScriptTask) element).setUseExternalStorageIn(Boolean.valueOf(properties.get(PROPERTY_USE_EXTERNAL_STORAGE_IN)));
            ((ScriptTask) element).setUseExternalStorageOut(Boolean.valueOf(properties.get(PROPERTY_USE_EXTERNAL_STORAGE_OUT)));
        }
        if (element instanceof Node && properties.containsKey(NODE_ASYNC_EXECUTION)) {
            ((Node) element).setAsyncExecution(NodeAsyncExecution.getByValueNotNull(properties.get(NODE_ASYNC_EXECUTION)));
        }
        if (element instanceof TaskState) {
            parseActionHandlers(node, element);
        }
        if (element instanceof Variable && properties.containsKey(GLOBAL)) {
            ((Variable) element).setGlobal("true".equals(properties.get(GLOBAL)));
        }
    }

    private Map<String, String> parseExtensionProperties(Element element) {
        Map<String, String> map = Maps.newLinkedHashMap();
        Element extensionsElement = element.element(EXTENSION_ELEMENTS);
        if (extensionsElement != null) {
            List<Element> propertyElements = extensionsElement.elements(QName.get(PROPERTY, RUNA_NAMESPACE));
            for (Element propertyElement : propertyElements) {
                String name = propertyElement.attributeValue(NAME);
                String value = propertyElement.attributeValue(VALUE);
                if (value == null) {
                    value = propertyElement.getText();
                }
                map.put(name, value);
            }
        }
        return map;
    }

    private List<VariableMapping> parseVariableMappings(Element element) {
        List<VariableMapping> list = Lists.newArrayList();
        Element extensionsElement = element.element(EXTENSION_ELEMENTS);
        if (extensionsElement != null) {
            Element variablesElement = extensionsElement.element(QName.get(VARIABLES, RUNA_NAMESPACE));
            if (variablesElement != null) {
                List<Element> variableElements = variablesElement.elements(QName.get(VARIABLE, RUNA_NAMESPACE));
                for (Element variableElement : variableElements) {
                    VariableMapping variableMapping = new VariableMapping();
                    variableMapping.setName(variableElement.attributeValue(NAME));
                    variableMapping.setMappedName(variableElement.attributeValue(MAPPED_NAME));
                    variableMapping.setUsage(variableElement.attributeValue(USAGE));
                    list.add(variableMapping);
                }
            }
        }
        return list;
    }

    @Override
    public void parseXML(Document document, ProcessDefinition definition) {
        Element definitionsElement = document.getRootElement();
        parseDataStores(definitionsElement.elements(DATA_STORE), definition);
        Element processElement = definitionsElement.element(PROCESS);
        definition.setInvalid(!Boolean.parseBoolean(processElement.attributeValue(EXECUTABLE, "true")));
        Map<String, String> processProperties = parseExtensionProperties(processElement);
        init(definition, processElement, processProperties);
        String defaultTaskTimeout = processProperties.get(DEFAULT_TASK_DEADLINE);
        if (!Strings.isNullOrEmpty(defaultTaskTimeout)) {
            definition.setDefaultTaskTimeoutDelay(new Duration(defaultTaskTimeout));
        }
        String accessTypeString = processProperties.get(ACCESS_TYPE);
        if (!Strings.isNullOrEmpty(accessTypeString)) {
            definition.setAccessType(ProcessDefinitionAccessType.valueOf(accessTypeString));
        }
        if (processProperties.containsKey(ID)) {
            definition.setId(processProperties.get(ID));
        }
        if (processProperties.containsKey(DOCUMENTATION)) {
            definition.setDescription(processProperties.get(DOCUMENTATION));
        }
        if (processProperties.containsKey(NODE_ASYNC_EXECUTION)) {
            definition.setDefaultNodeAsyncExecution(NodeAsyncExecution.getByValueNotNull(processProperties.get(NODE_ASYNC_EXECUTION)));
        }
        if (processProperties.containsKey(USE_GLOBALS)) {
            definition.setUseGlobals("true".equals(processProperties.get(USE_GLOBALS)));
        }
        String swimlaneDisplayModeName = processProperties.get(SHOW_SWIMLANE);
        if (swimlaneDisplayModeName != null) {
            definition.setSwimlaneDisplayMode(SwimlaneDisplayMode.valueOf(swimlaneDisplayModeName));
        }
        Map<Swimlane, List<String>> swimlaneElementIds = Maps.newLinkedHashMap();
        Element swimlaneSetElement = processElement.element(LANE_SET);
        if (swimlaneSetElement != null) {
            List<Element> swimlanes = swimlaneSetElement.elements(LANE);
            for (Element swimlaneElement : swimlanes) {
                if (!"true".equals(parseExtensionProperties(swimlaneElement).get(GLOBAL))) {
                    Swimlane swimlane = create(swimlaneElement, definition);
                    List<Element> flowNodeRefElements = swimlaneElement.elements(FLOW_NODE_REF);
                    List<String> flowNodeIds = Lists.newArrayList();
                    for (Element flowNodeRefElement : flowNodeRefElements) {
                        flowNodeIds.add(flowNodeRefElement.getTextTrim());
                    }
                    swimlaneElementIds.put(swimlane, flowNodeIds);
                }
            }
        }
        List<Element> startStates = processElement.elements(START_EVENT);
        if (startStates.size() > 0) {
            if (startStates.size() > 1) {
                Dialogs.error(Localization.getString("model.validation.multipleStartStatesNotAllowed"));
            }
            Element startStateElement = startStates.get(0);
            StartState startState = create(startStateElement, definition);
            String swimlaneName = parseExtensionProperties(startStateElement).get(LANE);
            Swimlane swimlane = definition.getSwimlaneByName(swimlaneName);
            startState.setSwimlane(swimlane);
        }
        List<Element> taskStateElements = new ArrayList<Element>(processElement.elements(USER_TASK));
        taskStateElements.addAll(processElement.elements(MULTI_TASK));
        for (Element taskStateElement : taskStateElements) {
            TaskState state = create(taskStateElement, definition);
            if (state instanceof TaskState) {
                Map<String, String> properties = parseExtensionProperties(taskStateElement);
                String swimlaneName = properties.get(LANE);
                if (!Strings.isNullOrEmpty(swimlaneName)) {
                    Swimlane swimlane = definition.getSwimlaneByName(swimlaneName);
                    state.setSwimlane(swimlane);
                }
                String reassign = properties.get(REASSIGN);
                if (reassign != null) {
                    state.setReassignSwimlaneToInitializer(Boolean.parseBoolean(reassign));
                }
                String reassignSwimlaneToTaskPerformer = properties.get(REASSIGN_SWIMLANE_TO_TASK_PERFORMER);
                if (reassignSwimlaneToTaskPerformer != null) {
                    state.setReassignSwimlaneToTaskPerformer(Boolean.parseBoolean(reassignSwimlaneToTaskPerformer));
                }
                String ignore = properties.get(IGNORE_SUBSTITUTION_RULES);
                if (ignore != null) {
                    state.setIgnoreSubstitutionRules(Boolean.parseBoolean(ignore));
                }
                String async = properties.get(ASYNC);
                if (async != null) {
                    state.setAsync(Boolean.parseBoolean(async));
                }
                String asyncCompletionMode = properties.get(ASYNC_COMPLETION_MODE);
                if (asyncCompletionMode != null) {
                    state.setAsyncCompletionMode(AsyncCompletionMode.valueOf(asyncCompletionMode));
                }
                String taskDeadline = properties.get(TASK_DEADLINE);
                if (taskDeadline != null) {
                    state.setTimeOutDelay(new Duration(taskDeadline));
                }
                if (state instanceof MultiTaskState) {
                    MultiTaskState multiTaskState = (MultiTaskState) state;
                    multiTaskState.setCreationMode(MultiTaskCreationMode.valueOf(properties.get(PropertyNames.PROPERTY_MULTI_TASK_CREATION_MODE)));
                    multiTaskState.setSynchronizationMode(
                            MultiTaskSynchronizationMode.valueOf(properties.get(PropertyNames.PROPERTY_MULTI_TASK_SYNCHRONIZATION_MODE)));
                    multiTaskState.setDiscriminatorUsage(properties.get(PropertyNames.PROPERTY_DISCRIMINATOR_USAGE));
                    multiTaskState.setDiscriminatorValue(properties.get(PropertyNames.PROPERTY_DISCRIMINATOR_VALUE));
                    multiTaskState.setDiscriminatorCondition(properties.get(PropertyNames.PROPERTY_DISCRIMINATOR_CONDITION));
                    multiTaskState.setVariableMappings(parseVariableMappings(taskStateElement));
                }
            }
        }
        {
            // backward compatibility: versions affected: 4.0.0 .. 4.0.4
            List<Element> scriptTaskElements = processElement.elements(SERVICE_TASK);
            for (Element node : scriptTaskElements) {
                create(node, definition);
            }
        }
        List<Element> scriptTaskElements = processElement.elements(SCRIPT_TASK);
        for (Element node : scriptTaskElements) {
            create(node, definition);
        }
        List<Element> parallelGatewayElements = processElement.elements(PARALLEL_GATEWAY);
        for (Element node : parallelGatewayElements) {
            create(node, definition);
        }
        List<Element> exclusiveGatewayElements = processElement.elements(EXCLUSIVE_GATEWAY);
        for (Element node : exclusiveGatewayElements) {
            create(node, definition);
        }
        List<Element> businessRuleElements = processElement.elements(BUSINESS_RULE);
        for (Element node : businessRuleElements) {
            create(node, definition);
        }
        List<Element> subprocessElements = processElement.elements(SUBPROCESS);
        for (Element subprocessElement : subprocessElements) {
            Subprocess subprocess = create(subprocessElement, definition);
            subprocess.setSubProcessName(subprocessElement.attributeValue(QName.get(PROCESS, RUNA_NAMESPACE)));
            subprocess.setVariableMappings(parseVariableMappings(subprocessElement));
            Map<String, String> properties = parseExtensionProperties(subprocessElement);
            if (subprocess instanceof MultiSubprocess) {
                MultiinstanceParameters.convertBackCompatible((MultiSubprocess) subprocess);
                ((MultiSubprocess) subprocess).setDiscriminatorCondition(properties.get(PropertyNames.PROPERTY_DISCRIMINATOR_CONDITION));
            }
            if (properties.containsKey(EMBEDDED)) {
                subprocess.setEmbedded(Boolean.parseBoolean(properties.get(EMBEDDED)));
            }
            if (properties.containsKey(TRANSACTIONAL)) {
                subprocess.setTransactional(Boolean.parseBoolean(properties.get(TRANSACTIONAL)));
            }
            if (properties.containsKey(VALIDATE_AT_START)) {
                subprocess.setValidateAtStart(Boolean.parseBoolean(properties.get(VALIDATE_AT_START)));
            }
            String async = properties.get(ASYNC);
            if (async != null) {
                subprocess.setAsync(Boolean.parseBoolean(async));
            }
            String asyncCompletionMode = properties.get(ASYNC_COMPLETION_MODE);
            if (asyncCompletionMode != null) {
                subprocess.setAsyncCompletionMode(AsyncCompletionMode.valueOf(asyncCompletionMode));
            }
        }
        {
            // back compatibility before rm#212
            List<Element> sendMessageElements = processElement.elements(SEND_TASK);
            for (Element messageElement : sendMessageElements) {
                ThrowEventNode throwEventNode = create(messageElement, definition);
                String duration = messageElement.attributeValue(TIME_DURATION, "1 days");
                throwEventNode.setTtlDuration(new Duration(duration));
                throwEventNode.setVariableMappings(parseVariableMappings(messageElement));
            }
            List<Element> receiveMessageElements = processElement.elements(RECEIVE_TASK);
            for (Element messageElement : receiveMessageElements) {
                CatchEventNode catchEventNode = create(messageElement, definition);
                catchEventNode.setVariableMappings(parseVariableMappings(messageElement));
            }
        }
        List<Element> intermediateThrowEventElements = processElement.elements(INTERMEDIATE_THROW_EVENT);
        for (Element eventElement : intermediateThrowEventElements) {
            parseEventElement(definition, definition, eventElement);
        }
        List<Element> intermediateCatchEventElements = processElement.elements(INTERMEDIATE_CATCH_EVENT);
        for (Element eventElement : intermediateCatchEventElements) {
            parseEventElement(definition, definition, eventElement);
        }
        List<Element> boundaryEventElements = processElement.elements(BOUNDARY_EVENT);
        for (Element boundaryEventElement : boundaryEventElements) {
            String parentNodeId = boundaryEventElement.attributeValue(ATTACHED_TO_REF);
            GraphElement parent = definition.getGraphElementByIdNotNull(parentNodeId);
            IBoundaryEvent boundaryEvent = (IBoundaryEvent) parseEventElement(definition, parent, boundaryEventElement);
            ((GraphElement) boundaryEvent).setParentContainer(parent);
            String interrupting = boundaryEventElement.attributeValue(CANCEL_ACTIVITY);
            if (!Strings.isNullOrEmpty(interrupting)) {
                boundaryEvent.setInterruptingBoundaryEvent(Boolean.parseBoolean(interrupting));
            }
        }
        List<Element> endStates = processElement.elements(END_EVENT);
        for (Element element : endStates) {
            Node endNode = create(element, definition);
            if (endNode instanceof EndTokenState) {
                Map<String, String> properties = parseExtensionProperties(element);
                if (properties.containsKey(BEHAVIOR)) {
                    ((EndTokenState) endNode).setSubprocessDefinitionBehavior(EndTokenSubprocessDefinitionBehavior.valueOf(properties.get(BEHAVIOR)));
                }
            }
        }
        List<Element> textAnnotationElements = processElement.elements(TEXT_ANNOTATION);
        for (Element textAnnotationElement : textAnnotationElements) {
            TextAnnotation textAnnotation = create(textAnnotationElement, definition);
            textAnnotation.setDescription(textAnnotationElement.elementText(TEXT));
        }
        List<Element> transitions = processElement.elements(SEQUENCE_FLOW);
        for (Element transitionElement : transitions) {
            Node source = definition.getGraphElementByIdNotNull(transitionElement.attributeValue(SOURCE_REF));
            Node target = definition.getGraphElementById(transitionElement.attributeValue(TARGET_REF));
            if (target == null) {
                PluginLogger.logErrorWithoutDialog("Unable to restore transition " + transitionElement.attributeValue(ID)
                        + " due to missed target node " + transitionElement.attributeValue(TARGET_REF));
                continue;
            }
            Transition transition = NodeRegistry.getNodeTypeDefinition(Transition.class).createElement(source, false);
            transition.setId(transitionElement.attributeValue(ID));
            transition.setName(transitionElement.attributeValue(NAME));
            Element description = transitionElement.element(DOCUMENTATION);
            if (description != null) {
                transition.setDescription(description.getTextTrim());
            }
            transition.setTarget(target);
            parseActionHandlers(transitionElement, transition);
            Map<String, String> properties = parseExtensionProperties(transitionElement);
            if (properties.containsKey(PropertyNames.PROPERTY_COLOR)) {
                transition.setColor(TransitionColor.findByValue(properties.get(PropertyNames.PROPERTY_COLOR)));
            }
            source.addLeavingTransition(transition);
        }
        definition.getChildren(ExclusiveGateway.class).stream().forEach(eg -> TransitionUtil.setDefaultFlow(eg, eg.getDelegationConfiguration()));
        for (Map.Entry<Swimlane, List<String>> entry : swimlaneElementIds.entrySet()) {
            for (String nodeId : entry.getValue()) {
                definition.getGraphElementByIdNotNull(nodeId).setParentContainer(entry.getKey());
            }
        }

        final List<Element> dottedTransitions = processElement.elements(DOTTED_TRANSITION);
        for (Element transitionElement : dottedTransitions) {
            final Node source = definition.getGraphElementByIdNotNull(transitionElement.attributeValue(ITEM_SUBJECT_REF));
            final Node target = definition.getGraphElementById(transitionElement.attributeValue(DATA_STORE_REF));
            if (target == null) {
                PluginLogger.logErrorWithoutDialog("Unable to restore transition " + transitionElement.attributeValue(ID)
                        + " due to missed target node " + transitionElement.attributeValue(DATA_STORE_REF));
                continue;
            }

            final DottedTransition transition = NodeRegistry.getNodeTypeDefinition(DottedTransition.class).createElement(source, false);
            transition.setId(transitionElement.attributeValue(ID));
            transition.setName(transitionElement.attributeValue(NAME));
            transition.setTarget(target);
            ((ConnectableViaDottedTransition) source).addLeavingDottedTransition(transition);
        }
        definition.onLoadingCompleted();
    }

    private GraphElement parseEventElement(ProcessDefinition definition, GraphElement parent, Element eventElement) {
        Element timerEventDefinitionElement = eventElement.element(TIMER_EVENT_DEFINITION);
        if (timerEventDefinitionElement != null) {
            Timer timer = create(timerEventDefinitionElement, parent);
            timer.setId(eventElement.attributeValue(ID));
            timer.setName(eventElement.attributeValue(NAME));
            Map<String, String> properties = parseExtensionProperties(timerEventDefinitionElement);
            String delegationClassName = properties.get(CLASS);
            if (delegationClassName != null) {
                String delegationConfiguration = properties.get(CONFIG);
                String repeat = properties.get(REPEAT);
                TimerAction timerAction = new TimerAction(definition);
                timerAction.setRepeatDuration(repeat);
                timerAction.setDelegationClassName(delegationClassName);
                timerAction.setDelegationConfiguration(delegationConfiguration);
                timer.setAction(timerAction);
            }
            return timer;
        } else {
            AbstractEventNode eventNode = create(eventElement, parent);
            eventNode.setEventNodeType(EventNodeType.valueOf(eventElement.attributeValue(TYPE, "message")));
            if (eventNode instanceof ThrowEventNode) {
                String duration = eventElement.attributeValue(TIME_DURATION, "1 days");
                eventNode.setTtlDuration(new Duration(duration));
            }
            eventNode.setVariableMappings(parseVariableMappings(eventElement));
            return eventNode;
        }
    }

    private void writeDataStores(Element definitionsElement, List<DataStore> dataStores) {
        for (DataStore dataStore : dataStores) {
            final Element dataStoreElement = definitionsElement.addElement(DATA_STORE, BPMN_NAMESPACE);
            writeBaseProperties(dataStoreElement, dataStore);
        }
    }

    private void parseDataStores(List<Element> dataStoreElements, ProcessDefinition definition) {
        for (Element dataStoreElement : dataStoreElements) {
            create(dataStoreElement, definition);
        }
    }
}
