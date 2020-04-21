package ru.runa.gpd.lang.par;

import com.google.common.collect.Sets;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.dom4j.Document;
import org.dom4j.io.OutputFormat;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.ltk.core.refactoring.Change;
import org.eclipse.ltk.core.refactoring.CompositeChange;
import ru.runa.gpd.PluginLogger;
import ru.runa.gpd.lang.model.FormNode;
import ru.runa.gpd.lang.model.ProcessDefinition;
import ru.runa.gpd.lang.model.SubprocessDefinition;
import ru.runa.gpd.lang.model.Variable;
import ru.runa.gpd.lang.model.VariableUserType;
import ru.runa.gpd.ltk.RenameUserTypeAttributeRefactoring;
import ru.runa.gpd.util.IOUtils;
import ru.runa.gpd.util.XmlUtil;
import ru.runa.gpd.validation.FormNodeValidation;
import ru.runa.gpd.validation.ValidatorParser;

public class ParContentProvider {
    public static final String PROCESS_DEFINITION_FILE_NAME = "processdefinition.xml";
    public static final String GLOBAL_SECTION_DEFINITION_FILE_NAME = "globalsectiondefinition.xml";
    public static final String SUBPROCESS_DEFINITION_PREFIX = "sub";
    public static final String PROCESS_DEFINITION_DESCRIPTION_FILE_NAME = "index.html";
    public static final String FORM_CSS_FILE_NAME = "form.css";
    public static final String FORM_JS_FILE_NAME = "form.js";
    public static final String PROCESS_IMAGE_FILE_NAME = "processimage.png";
    public static final String PROCESS_IMAGE_OLD_FILE_NAME = "processimage.jpg";
    public static final String PROCESS_INSTANCE_START_IMAGE_FILE_NAME = "start.png";
    public static final String REGULATIONS_XML_FILE_NAME = "regulations.xml";
    public static final String REGULATIONS_HTML_FILE_NAME = "regulations.html";
    private static final FormsXmlContentProvider FORMS_XML_CONTENT_PROVIDER = new FormsXmlContentProvider();
    private static final List<AuxContentProvider> CONTENT_PROVIDERS = new ArrayList<AuxContentProvider>();
    static {
        CONTENT_PROVIDERS.add(new VariablesXmlContentProvider());
        CONTENT_PROVIDERS.add(FORMS_XML_CONTENT_PROVIDER);
        CONTENT_PROVIDERS.add(new GpdXmlContentProvider());
        CONTENT_PROVIDERS.add(new SwimlaneGUIContentProvider());
        CONTENT_PROVIDERS.add(new ActionDescriptionContentProvider());
        CONTENT_PROVIDERS.add(new SubstitutionExceptionsXmlContentProvider());
        CONTENT_PROVIDERS.add(new BotsXmlContentProvider());
        CONTENT_PROVIDERS.add(new VersionCommentXmlContentProvider());
        CONTENT_PROVIDERS.add(new RegulationsXmlContentProvider());
    }

    public static void readAuxInfo(IFile definitionFile, ProcessDefinition definition) throws Exception {
        IFolder folder = (IFolder) definitionFile.getParent();
        for (AuxContentProvider contentProvider : CONTENT_PROVIDERS) {
            String fileName = contentProvider.getFileName();
            if (definition instanceof SubprocessDefinition) {
                if (!contentProvider.isSupportedForEmbeddedSubprocess()) {
                    continue;
                }
                fileName = definition.getId() + "." + fileName;
            }
            IFile file = folder.getFile(fileName);
            if (!file.exists()) {
                continue;
            }
            Document document = XmlUtil.parseWithoutValidation(file.getContents(true));
            contentProvider.read(document, definition);
        }
    }

    public static void saveAuxInfo(IFile definitionFile, ProcessDefinition definition) {
        try {
            IFolder folder = (IFolder) definitionFile.getParent();
            for (AuxContentProvider contentProvider : CONTENT_PROVIDERS) {
                saveAuxInfo(contentProvider, folder, definition);
            }
        } catch (Exception e) {
            PluginLogger.logError(e);
        }
    }

    public static void saveFormsXml(ProcessDefinition definition) {
        try {
            IFolder folder = (IFolder) definition.getFile().getParent();
            saveAuxInfo(FORMS_XML_CONTENT_PROVIDER, folder, definition);
        } catch (Exception e) {
            PluginLogger.logError(e);
        }
    }

    public static List<FormNode> getFormsWhereVariableUsed(IFile definitionFile, ProcessDefinition definition, String variableName) {
        List<FormNode> result = new ArrayList<FormNode>();
        List<FormNode> allNodes = definition.getChildren(FormNode.class);
        for (FormNode formNode : allNodes) {
            if (formNode.hasFormValidation()) {
                FormNodeValidation validation = formNode.getValidation(definitionFile);
                if (validation.getVariableNames().contains(variableName)) {
                    result.add(formNode);
                }
            }
        }
        return result;
    }

    public static List<FormNode> getFormsWhereUserTypeAttributeUsed(IFile definitionFile, ProcessDefinition definition, VariableUserType type,
            Variable attribute) {
        Set<String> affectedFormNames = Sets.newHashSet();
        try {
            String newVariableName = UUID.randomUUID().toString();
            RenameUserTypeAttributeRefactoring refactoring = new RenameUserTypeAttributeRefactoring(definitionFile, definition, type, attribute,
                    newVariableName, newVariableName);
            refactoring.checkInitialConditions(null);
            for (Change change : refactoring.createChange(null).getChildren()) {
                if (change instanceof CompositeChange) {
                    affectedFormNames.add(change.getName());
                }
            }
        } catch (CoreException e) {
            PluginLogger.logError(e);
        }
        return definition.getChildren(FormNode.class).stream().filter(formNode -> affectedFormNames.contains(formNode.getName()))
                .collect(Collectors.toList());
    }

    public static void rewriteFormValidationsRemoveVariable(IFile definitionFile, List<FormNode> formNodes, String variableName) {
        for (FormNode formNode : formNodes) {
            if (formNode.hasFormValidation()) {
                IFile validationFile = IOUtils.getAdjacentFile(definitionFile, formNode.getValidationFileName());
                FormNodeValidation validation = formNode.getValidation(validationFile);
                validation.getFieldConfigs().remove(variableName);
                ValidatorParser.writeValidation(validationFile, formNode, validation);
            }
        }
    }

    private static void saveAuxInfo(AuxContentProvider contentProvider, IFolder definitionFolder, ProcessDefinition definition) throws Exception {
        String fileName = contentProvider.getFileName();
        if (definition instanceof SubprocessDefinition) {
            if (!contentProvider.isSupportedForEmbeddedSubprocess()) {
                return;
            }
            fileName = definition.getId() + "." + fileName;
        }
        IFile file = definitionFolder.getFile(fileName);
        Document document = contentProvider.save(definition);
        if (document != null) {
            byte[] contentBytes;
            if (contentProvider instanceof BotsXmlContentProvider) {
                // TODO why this is really need?
                contentBytes = XmlUtil.writeXml(document, OutputFormat.createPrettyPrint());
            } else {
                contentBytes = XmlUtil.writeXml(document);
            }
            InputStream content = new ByteArrayInputStream(contentBytes);
            IOUtils.createOrUpdateFile(file, content);
        } else {
            if (file.exists()) {
                file.delete(true, null);
            }
        }
    }

}
