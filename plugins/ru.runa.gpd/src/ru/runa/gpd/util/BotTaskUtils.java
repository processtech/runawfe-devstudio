package ru.runa.gpd.util;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.regex.Pattern;
import org.dom4j.Document;
import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import org.dom4j.io.OutputFormat;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.jface.resource.ImageDescriptor;
import ru.runa.gpd.BotCache;
import ru.runa.gpd.PluginLogger;
import ru.runa.gpd.SharedImages;
import ru.runa.gpd.extension.DelegableProvider;
import ru.runa.gpd.extension.HandlerArtifact;
import ru.runa.gpd.extension.HandlerRegistry;
import ru.runa.gpd.extension.bot.IBotFileSupportProvider;
import ru.runa.gpd.extension.handler.ConfigBasedProvider;
import ru.runa.gpd.extension.handler.ParamBasedProvider;
import ru.runa.gpd.extension.handler.ParamDefConfig;
import ru.runa.gpd.extension.handler.ParamDefGroup;
import ru.runa.gpd.lang.model.BotTask;
import ru.runa.gpd.lang.model.BotTaskLink;
import ru.runa.gpd.lang.model.BotTaskType;
import ru.runa.gpd.lang.model.Swimlane;
import ru.runa.gpd.lang.model.TaskState;
import ru.runa.gpd.swimlane.BotSwimlaneInitializer;
import ru.runa.gpd.swimlane.SwimlaneInitializer;
import ru.runa.gpd.swimlane.SwimlaneInitializerParser;

/**
 * The class provide methods for perform operation with bot task config.
 * 
 * @author rivenforce
 * @since 3.6
 */
public class BotTaskUtils {
    private static final String EXTENDED_ELEMENT = "extended";
    private static final String BOTCONFIG_ELEMENT = "botconfig";
    private static final String PARAMETERS_ELEMENT = "parameters";
    public static final String EMBEDDED_SUFFIX = ".embedded";

    public static ParamDefConfig createEmptyParamDefConfig() {
        ParamDefConfig paramDefConfig = new ParamDefConfig();
        paramDefConfig.getGroups().add(new ParamDefGroup(ParamDefGroup.NAME_INPUT));
        paramDefConfig.getGroups().add(new ParamDefGroup(ParamDefGroup.NAME_OUTPUT));
        return paramDefConfig;
    }

    public static String createBotTaskConfiguration(BotTask botTask) {
        if (botTask.getType() == BotTaskType.EXTENDED || botTask.getType() == BotTaskType.GLOBALSECTION) {
            Document document = DocumentHelper.createDocument();
            Element root = document.addElement(EXTENDED_ELEMENT);
            Element parametersElement = root.addElement(PARAMETERS_ELEMENT);
            botTask.getParamDefConfig().writeXml(parametersElement);
            Element botConfigElement = root.addElement(BOTCONFIG_ELEMENT);
            if (XmlUtil.isXml(botTask.getDelegationConfiguration())) {
                Document conf = XmlUtil.parseWithoutValidation(botTask.getDelegationConfiguration());
                botConfigElement.add(conf.getRootElement().detach());
            } else {
                botConfigElement.addCDATA(botTask.getDelegationConfiguration());
            }
            return XmlUtil.toString(document, OutputFormat.createPrettyPrint());
        } else if (botTask.getType() == BotTaskType.PARAMETERIZED) {
            if (!Strings.isNullOrEmpty(botTask.getDelegationConfiguration())) {
                // http://sourceforge.net/p/runawfe/bugs/317/
                return botTask.getDelegationConfiguration();
            }
            Document document = DocumentHelper.createDocument();
            botTask.getParamDefConfig().writeXml(document);
            return XmlUtil.toString(document);
        } else {
            return botTask.getDelegationConfiguration();
        }
    }

    private static boolean isBotTaskExtendedConfiguration(String config) {
        try {
            Document document = XmlUtil.parseWithoutValidation(config);
            Element el = document.getRootElement();
            return el.getName().equals(EXTENDED_ELEMENT) && el.element(PARAMETERS_ELEMENT) != null && el.element(BOTCONFIG_ELEMENT) != null;
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean isTaskHandlerParameterized(String className) {
        HandlerArtifact artifact = HandlerRegistry.getInstance().getArtifact(className);
        if (artifact != null) {
            DelegableProvider provider = HandlerRegistry.getProvider(className);
            return provider instanceof ConfigBasedProvider;
        }
        return false;
    }

    public static InputStream createBotStationInfo(String botStationName) {
        StringBuilder buffer = new StringBuilder();
        buffer.append(botStationName);
        buffer.append("\n");
        return new ByteArrayInputStream(buffer.toString().getBytes());
    }

    public static BotTask createBotTask(String botStationName, String botName, String botTaskName, String handlerClassName, String configuration) {
        BotTask botTask = new BotTask(botStationName, botName, botTaskName);
        botTask.setDelegationClassName(handlerClassName);
        if (isTaskHandlerParameterized(botTask.getDelegationClassName())) {
            botTask.setType(BotTaskType.PARAMETERIZED);
            Document document = XmlUtil.parseWithoutValidation(configuration);
            botTask.setParamDefConfig(ParamDefConfig.parse(document));
            botTask.setDelegationConfiguration(configuration);
        } else if (isBotTaskExtendedConfiguration(configuration)) {
            botTask.setType(BotTaskType.EXTENDED);
            Document document = XmlUtil.parseWithoutValidation(configuration);
            Element botElement = document.getRootElement();
            Element element = botElement.element(PARAMETERS_ELEMENT).element(ParamDefConfig.NAME_CONFIG);
            Preconditions.checkNotNull(element);
            botTask.setParamDefConfig(ParamDefConfig.parse(element));
            Element botConfigElement = botElement.element(BOTCONFIG_ELEMENT);
            if (botConfigElement.elements().size() > 0) {
                Element configElement = (Element) botConfigElement.elements().get(0);
                botTask.setDelegationConfiguration(XmlUtil.toString(configElement, OutputFormat.createPrettyPrint()));
            } else {
                String config = botConfigElement.getText();
                config = config.replaceAll(Pattern.quote("param:"), "");
                botTask.setDelegationConfiguration(config);
            }
        } else {
            botTask.setType(BotTaskType.SIMPLE);
            botTask.setDelegationConfiguration(configuration);
        }
        return botTask;
    }

    /**
     * Gets associated with this swimlane bot name.
     * 
     * @param swimlane
     *            any swimlane, can be <code>null</code>
     * @return bot name or <code>null</code>.
     */
    public static String getBotName(Swimlane swimlane) {
        if (swimlane != null && swimlane.getDelegationConfiguration() != null
                && Swimlane.DEFAULT_DELEGATION_CLASS_NAME.equals(swimlane.getDelegationClassName())) {
            try {
                SwimlaneInitializer swimlaneInitializer = SwimlaneInitializerParser.parse(swimlane.getDelegationConfiguration());
                if (swimlaneInitializer instanceof BotSwimlaneInitializer) {
                    return ((BotSwimlaneInitializer) swimlaneInitializer).getBotName();
                }
            } catch (Exception e) {
                PluginLogger.logErrorWithoutDialog("Unable to get bot name for " + swimlane, e);
            }
        }
        return null;
    }

    /**
     * Opens dialog with formal parameters mapping for bounded to task state bot task.
     * 
     * @param taskState
     *            task state with valid bot task link and swimlane
     */
    public static void editBotTaskLinkConfiguration(TaskState taskState) {
        BotTaskLink botTaskLink = taskState.getBotTaskLink();
        BotTask botTask = BotCache.getBotTaskNotNull(taskState.getSwimlaneBotName(), botTaskLink.getBotTaskName());
        botTaskLink.setDelegationClassName(botTask.getDelegationClassName());
        ParamDefConfig config = botTask.getParamDefConfig();
        if (config == null) {
            throw new RuntimeException("No config found in bot task " + botTask);
        }
        String newConfiguration = null;
        if (BotTaskUtils.isTaskHandlerParameterized(botTaskLink.getDelegationClassName())) {
            // this is the case of
            // ru.runa.gpd.lang.model.BotTaskType.PARAMETERIZED
            ParamBasedProvider provider = (ParamBasedProvider) HandlerRegistry.getProvider(botTaskLink.getDelegationClassName());
            newConfiguration = provider.showConfigurationDialog(botTaskLink, null);
        } else {
            // this is the case of ru.runa.gpd.lang.model.BotTaskType.EXTENDED
            ImageDescriptor logo = SharedImages.getImageDescriptor("/icons/bottasklink.png");
            newConfiguration = ParamBasedProvider.showConfigurationDialog(botTaskLink, config, logo);
        }
        if (newConfiguration != null) {
            // #871
            String newConfigurationHash = newConfiguration.replaceAll(" ", "").replaceAll("\n", "");
            String oldConfigurationHash = botTaskLink.getDelegationConfiguration().replaceAll(" ", "").replaceAll("\n", "");
            if (!newConfigurationHash.equals(oldConfigurationHash)) {
                botTaskLink.setDelegationConfiguration(newConfiguration);
                taskState.setDirty();
            }
        }
    }

    /**
     * Copy or move a bot task configuration file and an existing embedded file.
     * 
     * @param botTaskFile
     * @param botTask
     * @param newName
     * @param targetBotFolder
     * @throws CoreException
     */
    public static void copyBotTaskConfig(IFile botTaskFile, BotTask botTask, String newName, IFolder targetBotFolder) throws CoreException {
        String oldName = botTask.getName();
        if (!Strings.isNullOrEmpty(botTask.getDelegationClassName())) {
            DelegableProvider provider = HandlerRegistry.getProvider(botTask.getDelegationClassName());
            if (provider instanceof IBotFileSupportProvider) {
                IBotFileSupportProvider botFileProvider = (IBotFileSupportProvider) provider;
                // Copy templates if them exist
                String oldEmbeddedFileName = botFileProvider.getEmbeddedFileName(botTask);
                botFileProvider.taskRenamed(botTask, oldName, newName);
                botTask.setName(newName);
                if (!Strings.isNullOrEmpty(oldEmbeddedFileName) && EmbeddedFileUtils.isBotTaskFileName(oldEmbeddedFileName, oldName)) {
                    IFile embeddedFile = ((IFolder) botTaskFile.getParent()).getFile(oldEmbeddedFileName);
                    if (embeddedFile.exists()) {
                        String newEmbeddedFileName = botFileProvider.getEmbeddedFileName(botTask);
                        IPath newEmbeddedFilePath = targetBotFolder.getFullPath().append(newEmbeddedFileName);
                        if (botTaskFile.exists()) {
                            embeddedFile.copy(newEmbeddedFilePath, true, null);
                        } else {
                            embeddedFile.move(newEmbeddedFilePath, true, null);
                        }
                    }
                }
            }
        }
        botTask.setName(newName);
        WorkspaceOperations.saveBotTask(targetBotFolder.getFile(newName), botTask);
    }
}
