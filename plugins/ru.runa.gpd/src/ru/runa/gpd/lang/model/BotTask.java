package ru.runa.gpd.lang.model;

import com.google.common.collect.Lists;
import java.util.List;
import java.util.ListIterator;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import ru.runa.gpd.BotCache;
import ru.runa.gpd.Localization;
import ru.runa.gpd.PluginConstants;
import ru.runa.gpd.PluginLogger;
import ru.runa.gpd.extension.HandlerArtifact;
import ru.runa.gpd.extension.HandlerRegistry;
import ru.runa.gpd.extension.VariableFormatRegistry;
import ru.runa.gpd.extension.handler.ParamDef;
import ru.runa.gpd.extension.handler.ParamDefConfig;
import ru.runa.gpd.extension.handler.ParamDefGroup;
import ru.runa.gpd.extension.handler.XmlBasedConstructorProvider;
import ru.runa.gpd.ui.view.ValidationErrorsView;
import ru.runa.gpd.util.XmlUtil;

public class BotTask implements Delegable, Comparable<BotTask> {
    private BotTaskType type = BotTaskType.SIMPLE;
    private String id;
    private String name;
    private String delegationClassName = "";
    private String delegationConfiguration = "";
    private ParamDefConfig paramDefConfig;
    private final List<String> filesToSave;
    private IFile globalSectionFile = null;

    public BotTask(String station, String bot, String name) {
        this.id = String.format("%s/%s/%s", station, bot, name);
        this.name = name;
        filesToSave = Lists.newArrayList();
    }

    public BotTaskType getType() {
        return type;
    }

    public void setType(BotTaskType type) {
        this.type = type;
    }

    public List<String> getFilesToSave() {
        return filesToSave;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
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
    public void setDelegationClassName(String delegateClassName) {
        this.delegationClassName = delegateClassName;
    }

    @Override
    public String getDelegationConfiguration() {
        return delegationConfiguration;
    }

    @Override
    public void setDelegationConfiguration(String configuration) {
        delegationConfiguration = configuration;
    }

    public boolean isDelegationConfigurationInXml() {
        if (HandlerRegistry.getProvider(delegationClassName) instanceof XmlBasedConstructorProvider) {
            return true;
        }
        return XmlUtil.isXml(delegationConfiguration);
    }
    
    public IFile getGlobalSectionFile() {
        return globalSectionFile;
    }

    public void setGlobalSectionFile(IFile GSDFile) {
        this.globalSectionFile = GSDFile;
    }

    /**
     * param-based config
     * 
     * @return null for simple bot task type
     */
    public ParamDefConfig getParamDefConfig() {
        return paramDefConfig;
    }

    /**
     * param-based config
     */
    public void setParamDefConfig(ParamDefConfig paramDefConfig) {
        this.paramDefConfig = paramDefConfig;
    }

    @Override
    public List<String> getVariableNames(boolean includeSwimlanes, String... typeClassNameFilters) {
        List<String> result = Lists.newArrayList();
        if (paramDefConfig != null) {
            for (ParamDefGroup group : paramDefConfig.getGroups()) {
                for (ParamDef paramDef : group.getParameters()) {
                    boolean applicable = typeClassNameFilters == null || typeClassNameFilters.length == 0;
                    if (!applicable && paramDef.getFormatFilters().size() > 0) {
                        for (String typeClassNameFilter : typeClassNameFilters) {
                            if (VariableFormatRegistry.isAssignableFrom(typeClassNameFilter, paramDef.getFormatFilters().get(0))) {
                                applicable = true;
                                break;
                            }
                        }
                    }
                    if (applicable) {
                        result.add(paramDef.getName());
                    }
                }
            }
        }
        return result;
    }

    @Override
    public int compareTo(BotTask o) {
        if (id == null || o == null || o.id == null) {
            return -1;
        }
        return id.compareTo(o.id);
    }

    @Override
    public boolean equals(Object obj) {
        BotTask botTask = (BotTask) obj;
        return id.equals(botTask.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return Localization.getString("property.botTaskName") + ": " + name;
    }

    public void logErrors(List<String> errors) {
        try {
            IFile definitionFile = BotCache.getBotTaskFile(this);
            definitionFile.deleteMarkers(ValidationErrorsView.ID, true, IResource.DEPTH_INFINITE);
            ListIterator<String> iterator = errors.listIterator();
            while (iterator.hasNext()) {
                addError(definitionFile, definitionFile, iterator.next());
            }
        } catch (Throwable e) {
            PluginLogger.logError(e);
        }
    }

    private static void addError(IFile definitionFile, IResource resource, String errorMessage) {
        try {
            IMarker marker = resource.createMarker(ValidationErrorsView.ID);
            if (marker.exists()) {
                marker.setAttribute(IMarker.MESSAGE, errorMessage);
                marker.setAttribute(IMarker.SEVERITY, IMarker.SEVERITY_ERROR);
                marker.setAttribute(IMarker.LOCATION, resource.getName());
                marker.setAttribute(PluginConstants.PROCESS_NAME_KEY,
                        /* Localization.getString("property.botTaskName") + ": " + */ resource.getName());
            }
        } catch (CoreException e) {
            PluginLogger.logError(e);
        }
    }
 	
}
