package ru.runa.gpd.ui.wizard;

import java.io.ByteArrayInputStream;
import java.lang.reflect.InvocationTargetException;
import java.util.Map;

import org.dom4j.Document;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.gef.EditPart;
import org.eclipse.jface.operation.IRunnableWithProgress;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.wizard.Wizard;
import org.eclipse.ui.INewWizard;
import org.eclipse.ui.IWorkbench;

import ru.runa.gpd.Localization;
import ru.runa.gpd.PluginLogger;
import ru.runa.gpd.ProcessCache;
import ru.runa.gpd.form.FormCSSTemplate;
import ru.runa.gpd.form.FormCSSTemplateRegistry;
import ru.runa.gpd.lang.BpmnSerializer;
import ru.runa.gpd.lang.Language;
import ru.runa.gpd.lang.ProcessSerializer;
import ru.runa.gpd.lang.model.ProcessDefinition;
import ru.runa.gpd.lang.par.ParContentProvider;
import ru.runa.gpd.ui.custom.Dialogs;
import ru.runa.gpd.util.IOUtils;
import ru.runa.gpd.util.SwimlaneDisplayMode;
import ru.runa.gpd.util.WorkspaceOperations;
import ru.runa.gpd.util.XmlUtil;
import ru.runa.wfe.definition.ProcessDefinitionAccessType;

import com.google.common.collect.Maps;

public class NewGlobalSectionDefinitionWizard extends Wizard implements INewWizard {
    private IStructuredSelection selection;
    private NewGlobalSectionDefinitionWizardPage page;
    private final ProcessDefinitionAccessType accessType;
    private IFolder parentProcessDefinitionFolder;
    private ProcessDefinition parentProcessDefinition;
    private IFile definitionFile;

    public NewGlobalSectionDefinitionWizard(ProcessDefinitionAccessType accessType) {
        setWindowTitle(Localization.getString("NewGlobalSectionDefinitionWizard.wizard.title"));
        this.accessType = accessType;
    }

    @Override
    public void init(IWorkbench workbench, IStructuredSelection currentSelection) {
        this.selection = currentSelection;
        if (accessType == ProcessDefinitionAccessType.EmbeddedSubprocess) {
            Object selectedElement = selection.getFirstElement();
            if (selectedElement instanceof EditPart) {
                IFile file = IOUtils.getCurrentFile();
                parentProcessDefinitionFolder = (IFolder) (file == null ? null : file.getParent());
            }
            if (selectedElement instanceof IFolder) {
                parentProcessDefinitionFolder = (IFolder) selectedElement;
            }
            if (parentProcessDefinitionFolder == null) {
                throw new IllegalArgumentException("Unable to get process definition selection");
            }
            IFile parentProcessDefinitionFile = IOUtils.getProcessDefinitionFile(parentProcessDefinitionFolder);
            parentProcessDefinition = ProcessCache.getProcessDefinition(parentProcessDefinitionFile);
            if (parentProcessDefinition == null) {
                throw new IllegalArgumentException("Unable to get parent process definition from selection");
            }
        }
    }

    @Override
    public void addPages() {
        page = new NewGlobalSectionDefinitionWizardPage(selection, parentProcessDefinition);
        addPage(page);
    }

    @Override
    public boolean performFinish() {
        try {
            if (parentProcessDefinition != null) {
                getContainer().run(false, false, new CreateEmbeddedSubprocessOperation());
            } else {
                getContainer().run(false, false, new CreateProcessOperation());
            }
        } catch (InvocationTargetException e) {
            PluginLogger.logError(Localization.getString("NewProcessDefinitionWizard.error.creation"), e.getTargetException());
            return false;
        } catch (InterruptedException e) {
        }
        return true;
    }

    private class CreateProcessOperation implements IRunnableWithProgress {

        @Override
        public void run(IProgressMonitor monitor) throws InvocationTargetException {
            try {
                monitor.beginTask(Localization.getString("NewProcessDefinitionWizard.monitor.title"), 4);
                IFolder folder = page.getProcessFolderByCreate();
                folder.create(true, true, null);
                monitor.worked(1);
                definitionFile = IOUtils.getProcessDefinitionFile(folder);
                String processName = folder.getName();
                Language language = page.getLanguage();
                Map<String, String> properties = Maps.newHashMap();
                if (language == Language.BPMN) {
                    properties.put(BpmnSerializer.SHOW_SWIMLANE, page.getSwimlaneDisplayMode().name());
                }
                properties.put(ProcessSerializer.ACCESS_TYPE, accessType.name());
                Document document = language.getSerializer().getInitialProcessDefinitionDocument(processName, properties);
                byte[] bytes = XmlUtil.writeXml(document);
                definitionFile.create(new ByteArrayInputStream(bytes), true, null);
                String cssTemplateName = page.getFormCSSTemplateName();
                if (cssTemplateName != null) {
                    FormCSSTemplate template = FormCSSTemplateRegistry.getTemplateNotNull(cssTemplateName);
                    IFile cssFile = IOUtils.getAdjacentFile(definitionFile, ParContentProvider.FORM_CSS_FILE_NAME);
                    IOUtils.createFile(cssFile, template.getContentAsStream());
                }
                monitor.worked(1);
                ProcessCache.newProcessDefinitionWasCreated(definitionFile);
                WorkspaceOperations.openGlobalSectionDefinition(definitionFile);
                monitor.done();
            } catch (Exception e) {
                throw new InvocationTargetException(e);
            }
        }
    }

    private class CreateEmbeddedSubprocessOperation implements IRunnableWithProgress {

        @Override
        public void run(IProgressMonitor monitor) throws InvocationTargetException {
            try {
                monitor.beginTask(Localization.getString("NewProcessDefinitionWizard.monitor.title"), 4);
                int subprocessIndex = 1;
                definitionFile = parentProcessDefinitionFolder.getFile(ParContentProvider.SUBPROCESS_DEFINITION_PREFIX + "1."
                        + ParContentProvider.PROCESS_DEFINITION_FILE_NAME);
                while (definitionFile.exists()) {
                    subprocessIndex++;
                    definitionFile = parentProcessDefinitionFolder.getFile(ParContentProvider.SUBPROCESS_DEFINITION_PREFIX + subprocessIndex + "."
                            + ParContentProvider.PROCESS_DEFINITION_FILE_NAME);
                }
                monitor.worked(1);
                String processName = page.getProcessName();
                Map<String, String> properties = Maps.newHashMap();
                properties.put(BpmnSerializer.SHOW_SWIMLANE, SwimlaneDisplayMode.none.name());
                properties.put(ProcessSerializer.ID, ParContentProvider.SUBPROCESS_DEFINITION_PREFIX + subprocessIndex);
                properties.put(ProcessSerializer.ACCESS_TYPE, accessType.name());
                Document document = parentProcessDefinition.getLanguage().getSerializer()
                        .getInitialProcessDefinitionDocument(processName, properties);
                byte[] bytes = XmlUtil.writeXml(document);
                definitionFile.create(new ByteArrayInputStream(bytes), true, null);
                monitor.worked(1);
                ProcessCache.newProcessDefinitionWasCreated(definitionFile);
                WorkspaceOperations.openGlobalSectionDefinition(definitionFile);
                monitor.done();
            } catch (Exception e) {
                throw new InvocationTargetException(e);
            }
        }
    }

    public IFile getDefinitionFile() {
        return definitionFile;
    }
}
