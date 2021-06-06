package ru.runa.gpd.lang.model;

import java.util.ArrayList;
import java.util.List;

import javax.print.Doc;

import org.dom4j.Document;
import org.dom4j.Element;
import org.eclipse.core.resources.IFile;

import ru.runa.gpd.BotCache;
import ru.runa.gpd.Localization;
import ru.runa.gpd.PluginLogger;
import ru.runa.gpd.ProcessCache;
import ru.runa.gpd.extension.HandlerArtifact;
import ru.runa.gpd.util.BotTaskUtils;
import ru.runa.gpd.util.XmlUtil;

import com.google.common.collect.Lists;

/**
 * Provides mapping with formal parameters between {@link TaskState} and {@link BotTask}.
 * 
 * Configuration is in param-based xml.
 * 
 * @author Dofs
 * @since 3.6
 */
public class BotTaskLink implements Delegable {
    private String botTaskName;
    private String delegationClassName;
    private String delegationConfiguration = "";
    private String botName;
    private TaskState taskState;
    private String Filename;
    private final String xmlHeader = "<?xml version=\"1.0\" encoding=\"UTF-8\"?> \n \n";
    private final String  InternalStorageClassName = "ru.runa.wfe.office.storage.handler.InternalStorageHandler";
   
    /**
     * linked {@link BotTask} name
     */
    public String getBotTaskName() {
        return botTaskName;
    }

    /**
     * linked {@link BotTask} name
     */
    public void setBotTaskName(String botTaskName) {
        this.botTaskName = botTaskName;
    }

    @Override
    public String getDelegationType() {
        return HandlerArtifact.TASK_HANDLER;
    }

    @Override
    public String getDelegationClassName() {
        return delegationClassName;
    }

    @Override
    public void setDelegationClassName(String delegationClassName) {
        this.delegationClassName = delegationClassName;
    }

    @Override
    public String getDelegationConfiguration() {
        return delegationConfiguration;
    }

    @Override
    public void setDelegationConfiguration(String delegationConfiguration) {
        this.delegationConfiguration = delegationConfiguration;
    }

    public TaskState getTaskState() {
        return taskState;
    }

    public void setTaskState(TaskState taskState) {
        this.taskState = taskState;
    }

    @Override
    public List<String> getVariableNames(boolean includeSwimlanes, String... typeClassNameFilters) {
        if (taskState == null) {
        	return Lists.newArrayList();
        }
//        if (delegationClassName.equals(InternalStorageClassName)&&checkFormat(typeClassNameFilters) ) {
//        	List<String> variablesNames = new ArrayList<String>();
//        	List<Variable>  typeAttributes = getGlobalSection();
//        	if (typeAttributes == null) {
//        		return taskState.getProcessDefinition().getVariableNames(includeSwimlanes, typeClassNameFilters);
//        	}
//        	for(Variable variable : taskState.getProcessDefinition().getVariables(true, includeSwimlanes, typeClassNameFilters)) {
//        			if(variable.getUserType().getAttributes().equals(typeAttributes)) {
//        				variablesNames.add(variable.getName());
//        			}
//        	}
//        	return variablesNames;
//        }
        return taskState.getProcessDefinition().getVariableNames(includeSwimlanes, typeClassNameFilters);
        
    }
//    public List<Variable> getGlobalSection() {
//    	
//    	BotTask botTask = BotCache.getBotTask(taskState.getSwimlaneBotName(), botTaskName);
//    	
//    	String xmmText = botTask.getDelegationConfiguration();
//    	if(!XmlUtil.isXml(xmmText)) {
//    		return null;
//    	}
//    	IFile globalSection =  botTask.getGlobalSectionFile();
//    	Document doc = XmlUtil.parseWithoutValidation(xmmText);
//    	Element root = doc.getRootElement();
//    	String typeName = root.element("binding").attributeValue("variable");
//    	Variable variable = ProcessCache.getProcessDefinition(globalSection).getVariableByName(typeName);
//		if (variable.getUserType().equals(null)) {
//			return variable.getUserType().getAttributes() ;
//		}
//		return null;
//    }
    private boolean checkFormat(String... typeClassNameFilters) {
    	for(String format : typeClassNameFilters) {
    		if("ru.runa.wfe.var.UserTypeMap".equals(format)) {
    			return true;
    		}
    	}    	
    	return false;
    }
    public BotTaskLink getCopy(TaskState taskState) {
        BotTaskLink copy = new BotTaskLink();
        copy.setBotTaskName(botTaskName);
        copy.setDelegationClassName(delegationClassName);
        copy.setDelegationConfiguration(delegationConfiguration);
        copy.setTaskState(taskState);
        return copy;
    }
    
    @Override
    public String toString() {
        return Localization.getString("BotTaskLink.description", taskState, botTaskName);
    }
   
}
