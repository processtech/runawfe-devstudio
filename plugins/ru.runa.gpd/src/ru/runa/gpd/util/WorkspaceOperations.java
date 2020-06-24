package ru.runa.gpd.util;

import com.google.common.base.Charsets;
import com.google.common.base.Objects;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import org.dom4j.Document;
import org.dom4j.DocumentException;
import org.dom4j.io.SAXReader;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.resources.WorkspaceJob;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.QualifiedName;
import org.eclipse.core.runtime.Status;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.jface.window.Window;
import org.eclipse.jface.wizard.WizardDialog;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IEditorReference;
import org.eclipse.ui.IFileEditorInput;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.ide.IDE;
import org.eclipse.ui.part.FileEditorInput;
import ru.runa.gpd.BotCache;
import ru.runa.gpd.Localization;
import ru.runa.gpd.PluginLogger;
import ru.runa.gpd.ProcessCache;
import ru.runa.gpd.SubprocessMap;
import ru.runa.gpd.editor.BotTaskEditor;
import ru.runa.gpd.editor.ProcessEditorBase;
import ru.runa.gpd.editor.GlobalSectionEditorBase;
import ru.runa.gpd.editor.ProcessSaveHistory;
import ru.runa.gpd.editor.gef.GEFProcessEditor;
import ru.runa.gpd.editor.graphiti.GraphitiProcessEditor;
import ru.runa.gpd.editor.graphiti.GraphitiGlobalSectionEditor;
import ru.runa.gpd.extension.DelegableProvider;
import ru.runa.gpd.extension.HandlerRegistry;
import ru.runa.gpd.extension.bot.IBotFileSupportProvider;
import ru.runa.gpd.lang.Language;
import ru.runa.gpd.lang.ProcessSerializer;
import ru.runa.gpd.lang.model.BotTask;
import ru.runa.gpd.lang.model.BotTaskType;
import ru.runa.gpd.lang.model.ProcessDefinition;
import ru.runa.gpd.lang.model.Subprocess;
import ru.runa.gpd.lang.model.SubprocessDefinition;
import ru.runa.gpd.lang.model.TaskState;
import ru.runa.gpd.lang.par.ParContentProvider;
import ru.runa.gpd.ui.custom.Dialogs;
import ru.runa.gpd.ui.dialog.DataSourceDialog;
import ru.runa.gpd.ui.dialog.ProcessSaveHistoryDialog;
import ru.runa.gpd.ui.dialog.RenameBotDialog;
import ru.runa.gpd.ui.dialog.RenameBotStationDialog;
import ru.runa.gpd.ui.dialog.RenameBotTaskDialog;
import ru.runa.gpd.ui.dialog.RenameProcessDefinitionDialog;
import ru.runa.gpd.ui.wizard.CompactWizardDialog;
import ru.runa.gpd.ui.wizard.CopyBotTaskWizard;
import ru.runa.gpd.ui.wizard.CopyProcessDefinitionWizard;
import ru.runa.gpd.ui.wizard.ExportBotElementWizardPage;
import ru.runa.gpd.ui.wizard.ExportBotWizard;
import ru.runa.gpd.ui.wizard.ExportDataSourceWizard;
import ru.runa.gpd.ui.wizard.ExportParWizard;
import ru.runa.gpd.ui.wizard.ExportGlbWizard;
import ru.runa.gpd.ui.wizard.ImportBotElementWizardPage;
import ru.runa.gpd.ui.wizard.ImportBotWizard;
import ru.runa.gpd.ui.wizard.ImportDataSourceWizard;
import ru.runa.gpd.ui.wizard.ImportParWizard;
import ru.runa.gpd.ui.wizard.ImportGlbWizard;
import ru.runa.gpd.ui.wizard.NewBotStationWizard;
import ru.runa.gpd.ui.wizard.NewBotTaskWizard;
import ru.runa.gpd.ui.wizard.NewBotWizard;
import ru.runa.gpd.ui.wizard.NewFolderWizard;
import ru.runa.gpd.ui.wizard.NewProcessDefinitionWizard;
import ru.runa.gpd.ui.wizard.NewGlobalSectionDefinitionWizard;
import ru.runa.gpd.ui.wizard.NewProcessProjectWizard;
import ru.runa.wfe.InternalApplicationException;
import ru.runa.wfe.datasource.DataSourceStuff;
import ru.runa.wfe.definition.ProcessDefinitionAccessType;

public class WorkspaceOperations {

    public static final QualifiedName PROPERTY_FILE_WILL_BE_DELETED_SHORTLY = new QualifiedName(null, "file.will.be.deleted.shortly");

    public static void deleteResources(List<IResource> resources) {
        List<IFile> deletedDefinitions = new ArrayList<IFile>();
        for (IResource resource : resources) {
            try {
                resource.refreshLocal(IResource.DEPTH_INFINITE, null);
                boolean projectResource = (resource instanceof IProject);
                boolean folderResource = (resource instanceof IFolder);
                boolean fileResource = (resource instanceof IFile);
                String messageKey;
                if (projectResource) {
                    messageKey = "Delete.project.message";
                } else if (folderResource) {
                    if (IOUtils.isProcessDefinitionFolder((IFolder) resource)) {
                        messageKey = "Delete.process.message";
                    } else {
                        messageKey = "Delete.folder.message";
                    }
                } else if (fileResource) {
                    messageKey = "Delete.process.message";
                } else {
                    throw new IllegalArgumentException("Unexpected " + resource);
                }
                if (Dialogs.confirm(Localization.getString(messageKey, resource.getName()))) {
                    List<IFile> tmpFiles = new ArrayList<IFile>();
                    if (projectResource) {
                        tmpFiles.addAll(IOUtils.getProcessDefinitionFiles((IProject) resource));
                    } else if (folderResource) {
                        if (IOUtils.isProcessDefinitionFolder((IFolder) resource)) {
                            tmpFiles.add(IOUtils.getProcessDefinitionFile((IFolder) resource));
                        } else {
                            tmpFiles.addAll(IOUtils.getProcessDefinitionFiles((IFolder) resource));
                        }
                    } else {
                        IFile definitionFile = (IFile) resource;
                        try {
                            SubprocessDefinition subprocessDefinition = (SubprocessDefinition) ProcessCache.getProcessDefinition(definitionFile);
                            subprocessDefinition.getParent().getEmbeddedSubprocesses().remove(subprocessDefinition.getId());
                            for (Subprocess sp : subprocessDefinition.getParent().getChildren(Subprocess.class))
                            {
                                if (!sp.getSubProcessName().equals(subprocessDefinition.getName())) {
                                    continue;
                                }
                                sp.setSubProcessName("");
                                break;
                            }
                            subprocessDefinition.setName("");
                        } catch (Exception e) {
                            PluginLogger.logErrorWithoutDialog("Unable to deregister embedded subprocess", e);
                        }
                        int index = definitionFile.getName().indexOf(ParContentProvider.PROCESS_DEFINITION_FILE_NAME);
                        Preconditions.checkArgument(index != -1, "not a subprocess definition file");
                        String subprocessFileStart = definitionFile.getName().substring(0, index);
                        tmpFiles.add(definitionFile);
                        for (IResource testResource : definitionFile.getParent().members()) {
                            if (testResource.getName().startsWith(subprocessFileStart)) {
                                testResource.delete(true, null);
                            }
                        }
                    }
                    if (folderResource && IOUtils.isProcessDefinitionFolder((IFolder) resource)) {
                        ProcessSaveHistory.clear((IFolder) resource);
                    }
                    resource.delete(true, null);
                    deletedDefinitions.addAll(tmpFiles);
                }
            } catch (CoreException e) {
                PluginLogger.logError("Error deleting", e);
            }
        }
        for (IFile definitionFile : deletedDefinitions) {
            ProcessCache.processDefinitionWasDeleted(definitionFile);
        }
    }

    private static String getConfirmMessage(IResource resource) {
        if (resource instanceof IProject) {
            return "Delete.botStation.message";
        } else if (resource instanceof IFolder) {
            return "Delete.bot.message";
        } else {
            return "Delete.botTask.message";
        }
    }

    public static void refreshResources(List<IResource> resources) {
        for (IResource resource : resources) {
            refreshResource(resource);
        }
        BotCache.reload();
    }

    public static void refreshResource(IResource resource) {
        try {
            resource.refreshLocal(IResource.DEPTH_INFINITE, null);
            IOUtils.setUtfCharsetRecursively(resource);
        } catch (CoreException e) {
            PluginLogger.logError("Unable to refresh resource " + resource, e);
        }
    }

    public static void createNewProject() {
        NewProcessProjectWizard wizard = new NewProcessProjectWizard();
        wizard.init(PlatformUI.getWorkbench(), null);
        WizardDialog dialog = new WizardDialog(PlatformUI.getWorkbench().getActiveWorkbenchWindow().getShell(), wizard);
        dialog.open();
    }

    public static void createNewFolder(IStructuredSelection selection) {
        NewFolderWizard wizard = new NewFolderWizard();
        wizard.init(PlatformUI.getWorkbench(), selection);
        WizardDialog dialog = new WizardDialog(PlatformUI.getWorkbench().getActiveWorkbenchWindow().getShell(), wizard);
        dialog.open();
    }

    public static ProcessDefinition createNewProcessDefinition(IStructuredSelection selection, ProcessDefinitionAccessType accessType) {
        NewProcessDefinitionWizard wizard = new NewProcessDefinitionWizard(accessType);
        wizard.init(PlatformUI.getWorkbench(), selection);
        WizardDialog dialog = new WizardDialog(PlatformUI.getWorkbench().getActiveWorkbenchWindow().getShell(), wizard);
        if (dialog.open() == Window.OK) {
            return ProcessCache.getProcessDefinition(wizard.getDefinitionFile());
        }
        return null;
    }

    public static ProcessDefinition createNewGlobalSectionDefinition(IStructuredSelection selection, ProcessDefinitionAccessType accessType) {
        NewGlobalSectionDefinitionWizard wizard = new NewGlobalSectionDefinitionWizard(accessType);
        wizard.init(PlatformUI.getWorkbench(), selection);
        WizardDialog dialog = new WizardDialog(PlatformUI.getWorkbench().getActiveWorkbenchWindow().getShell(), wizard);
        if (dialog.open() == Window.OK) {
            return ProcessCache.getProcessDefinition(wizard.getDefinitionFile());
        }
        return null;
    }
    
    public static void copyProcessDefinition(IStructuredSelection selection) {
        IFolder processDefinitionFolder = (IFolder) selection.getFirstElement();
        IDE.saveAllEditors(new IResource[] { processDefinitionFolder }, true);
        CopyProcessDefinitionWizard wizard = new CopyProcessDefinitionWizard();
        wizard.init(PlatformUI.getWorkbench(), selection);
        CompactWizardDialog dialog = new CompactWizardDialog(wizard);
        dialog.open();
    }

    public static void renameProcessDefinition(IStructuredSelection selection) {
        IFolder oldDefinitionFolder = (IFolder) selection.getFirstElement();
        IFile oldDefinitionFile = IOUtils.getProcessDefinitionFile(oldDefinitionFolder);
        RenameProcessDefinitionDialog dialog = new RenameProcessDefinitionDialog(oldDefinitionFolder);
        ProcessDefinition definition = ProcessCache.getProcessDefinition(oldDefinitionFile);
        String oldName = definition.getName();
        dialog.setName(oldName);
        if (dialog.open() != IDialogConstants.OK_ID) {
            return;
        }
        String newName = dialog.getName();
        try {
            // Close ALL editors related to the process BEFORE renaiming it.
            IProject project = oldDefinitionFolder.getProject();
            IWorkbenchPage page = PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage();
            IEditorReference[] editorRefs = page.getEditorReferences();
            ArrayList<IEditorReference> editorRefsToClose = new ArrayList<IEditorReference>(editorRefs.length);
            for (IEditorReference e : editorRefs) {
                // Work only on IEditorReference-s since IEditorPart-s may be uninitialized and we don't want to "restore" them just to close.
                if (e.getEditorInput() instanceof IFileEditorInput) {
                    // We get here at least if e is GraphitiProcessEditor or FormEditor.
                    IFile f = ((IFileEditorInput) e.getEditorInput()).getFile();
                    if (f.getProject() == project && Objects.equal(f.getFullPath().segment(1), oldName)) {
                        editorRefsToClose.add(e);
                    }
                }
            }
            if (!editorRefsToClose.isEmpty()) {
                // Close all at once! Otherwise, for example, when ProcessEditor is closed FormEditor may take focus and initialize.
                // 2nd parameter save=false prevents "Save changed files?" dialog: they are saved anyway.
                page.closeEditors(editorRefsToClose.toArray(new IEditorReference[editorRefsToClose.size()]), false);
            }
            ProcessCache.processDefinitionWasDeleted(oldDefinitionFile);
            IPath newPath = oldDefinitionFolder.getParent().getFolder(new Path(newName)).getFullPath();
            IFolder newDefinitionFolder = ResourcesPlugin.getWorkspace().getRoot().getFolder(newPath);
            oldDefinitionFolder.copy(newDefinitionFolder.getFullPath(), true, new NullProgressMonitor());
            IFile newDefinitionFile = IOUtils.getProcessDefinitionFile(newDefinitionFolder);
            definition = ProcessCache.getProcessDefinition(newDefinitionFile);
            definition.setName(newName);
            saveProcessDefinition(definition);
            ProcessCache.newProcessDefinitionWasCreated(newDefinitionFile);
            ProcessSaveHistory.clear(oldDefinitionFolder);
            oldDefinitionFolder.delete(true, new NullProgressMonitor());
            refreshResource(newDefinitionFolder);
        } catch (Exception e) {
            PluginLogger.logError(e);
        }
    }

    public static void renameGlobalDefinition(IStructuredSelection selection) {
        IFolder oldDefinitionFolder = (IFolder) selection.getFirstElement();
        IFile oldDefinitionFile = IOUtils.getProcessDefinitionFile(oldDefinitionFolder);
        RenameProcessDefinitionDialog dialog = new RenameProcessDefinitionDialog(oldDefinitionFolder);
        ProcessDefinition definition = ProcessCache.getProcessDefinition(oldDefinitionFile);
        String oldName = definition.getName();
        if (oldName.length() > 1) {
        	oldName = oldName.substring(1);
        }
        dialog.setName(oldName);
        if (dialog.open() != IDialogConstants.OK_ID) {
            return;
        }
        String newName = "." + dialog.getName();
        try {
            // Close ALL editors related to the process BEFORE renaiming it.
            IProject project = oldDefinitionFolder.getProject();
            IWorkbenchPage page = PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage();
            IEditorReference[] editorRefs = page.getEditorReferences();
            ArrayList<IEditorReference> editorRefsToClose = new ArrayList<IEditorReference>(editorRefs.length);
            for (IEditorReference e : editorRefs) {
                // Work only on IEditorReference-s since IEditorPart-s may be uninitialized and we don't want to "restore" them just to close.
                if (e.getEditorInput() instanceof IFileEditorInput) {
                    // We get here at least if e is GraphitiProcessEditor or FormEditor.
                    IFile f = ((IFileEditorInput) e.getEditorInput()).getFile();
                    if (f.getProject() == project && Objects.equal(f.getFullPath().segment(1), oldName)) {
                        editorRefsToClose.add(e);
                    }
                }
            }
            if (!editorRefsToClose.isEmpty()) {
                // Close all at once! Otherwise, for example, when ProcessEditor is closed FormEditor may take focus and initialize.
                // 2nd parameter save=false prevents "Save changed files?" dialog: they are saved anyway.
                page.closeEditors(editorRefsToClose.toArray(new IEditorReference[editorRefsToClose.size()]), false);
            }
            ProcessCache.processDefinitionWasDeleted(oldDefinitionFile);
            IPath newPath = oldDefinitionFolder.getParent().getFolder(new Path(newName)).getFullPath();
            IFolder newDefinitionFolder = ResourcesPlugin.getWorkspace().getRoot().getFolder(newPath);
            oldDefinitionFolder.copy(newDefinitionFolder.getFullPath(), true, new NullProgressMonitor());
            IFile newDefinitionFile = IOUtils.getProcessDefinitionFile(newDefinitionFolder);
            definition = ProcessCache.getProcessDefinition(newDefinitionFile);
            definition.setName(newName);
            saveProcessDefinition(definition);
            ProcessCache.newProcessDefinitionWasCreated(newDefinitionFile);
            ProcessSaveHistory.clear(oldDefinitionFolder);
            oldDefinitionFolder.delete(true, new NullProgressMonitor());
            refreshResource(newDefinitionFolder);
        } catch (Exception e) {
            PluginLogger.logError(e);
        }
    }
    
    public static void renameSubProcessDefinition(IStructuredSelection selection) {
        IFile subdefinitionFile = (IFile) selection.getFirstElement();
        IFolder definitionFolder = (IFolder) subdefinitionFile.getParent();
        SubprocessDefinition subprocessDefinition = (SubprocessDefinition) ProcessCache.getProcessDefinition(subdefinitionFile);
        ProcessDefinition definition = subprocessDefinition.getParent();

        RenameProcessDefinitionDialog dialog = new RenameProcessDefinitionDialog(definition);
        dialog.setName(subprocessDefinition.getName());
        if (dialog.open() == IDialogConstants.OK_ID) {
            String newName = dialog.getName();
            try {
                IWorkbenchPage page = PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage();
                IEditorPart editor = page.findEditor(new FileEditorInput(subdefinitionFile));
                if (editor != null) {
                    page.closeEditor(editor, false);
                }

                for (Subprocess sp : definition.getChildren(Subprocess.class)) {
                    if (sp.getSubProcessName().equals(subprocessDefinition.getName())) {
                        sp.setSubProcessName(newName);
                        IFile definitionFile = IOUtils.getProcessDefinitionFile(definitionFolder);
                        editor = page.findEditor(new FileEditorInput(definitionFile));
                        if (editor != null) {
                            page.closeEditor(editor, false);
                        }
                        saveProcessDefinition(definition);
                        ProcessCache.invalidateProcessDefinition(definitionFile);
                        break;
                    }
                }
                for (SubprocessDefinition subdefinition : definition.getEmbeddedSubprocesses().values()) {
                    for (Subprocess sp : subdefinition.getChildren(Subprocess.class)) {
                        if (sp.getSubProcessName().equals(subprocessDefinition.getName())) {
                            sp.setSubProcessName(newName);
                            IFile file = IOUtils.getFile(subdefinition.getId() + "." + ParContentProvider.PROCESS_DEFINITION_FILE_NAME);
                            editor = page.findEditor(new FileEditorInput(file));
                            if (editor != null) {
                                page.closeEditor(editor, false);
                            }
                            saveProcessDefinition(subdefinition);
                            ProcessCache.invalidateProcessDefinition(file);
                            break;
                        }
                    }
                }
                subprocessDefinition.setName(newName);
                
                saveProcessDefinition(subprocessDefinition);
                ProcessCache.invalidateProcessDefinition(subdefinitionFile);
                refreshResource(definitionFolder);
            } catch (Exception e) {
                PluginLogger.logError(e);
            }
        }
    }

    public static void saveProcessDefinition(ProcessDefinition definition) throws Exception {
        ProcessSerializer serializer = definition.getLanguage().getSerializer();
        Document document = serializer.getInitialProcessDefinitionDocument(definition.getName(), null);
        serializer.saveToXML(definition, document);
        byte[] bytes = XmlUtil.writeXml(document);
        ParContentProvider.saveAuxInfo(definition.getFile(), definition);
        definition.getFile().setContents(new ByteArrayInputStream(bytes), true, false, null);
    }

    public static ProcessEditorBase openProcessDefinition(IFile definitionFile) {
        try {
            ProcessDefinition processDefinition = ProcessCache.getProcessDefinition(definitionFile);
            String editorId;
            if (processDefinition.getLanguage() == Language.BPMN) {
                editorId = GraphitiProcessEditor.ID;
            } else {
                editorId = GEFProcessEditor.ID;
            }
            IEditorPart editorPart = IDE.openEditor(PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage(), definitionFile, editorId,
                    true);
            if (editorPart instanceof ProcessEditorBase) {
                return (ProcessEditorBase) editorPart;
            }
        } catch (PartInitException e) {
            PluginLogger.logError("Unable open diagram", e);
        }
        return null;
    }
    
    public static ProcessEditorBase openGlobalSectionDefinition(IFile definitionFile) {
        try {
            ProcessDefinition processDefinition = ProcessCache.getProcessDefinition(definitionFile);
            String editorId;
            if (processDefinition.getLanguage() == Language.BPMN) {
                editorId = GraphitiGlobalSectionEditor.ID;
            } else {
                editorId = GEFProcessEditor.ID;
            }
            IEditorPart editorPart = IDE.openEditor(PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage(), definitionFile, editorId,
                    true);
            if (editorPart instanceof ProcessEditorBase) {
                return (ProcessEditorBase) editorPart;
            }
        } catch (PartInitException e) {
            PluginLogger.logError("Unable open diagram", e);
        }
        return null;
    }

    /**
     * @return process editor or <code>null</code>
     */
    public static void openSubprocessDefinition(Subprocess subprocess) {
        if (Strings.isNullOrEmpty(subprocess.getSubProcessName())) {
            return;
        }
        if (subprocess.isEmbedded()) {
            SubprocessDefinition definition = subprocess.getEmbeddedSubprocess();
            if (definition != null) {
                String id = definition.getId();
                IFile definitionFile = IOUtils.getFile(id + "." + ParContentProvider.PROCESS_DEFINITION_FILE_NAME);
                openProcessDefinition(definitionFile);
            }
        } else {
            IFile definitionFile = null;
            String value = SubprocessMap.get(subprocess.getQualifiedId());
            if (value != null) {
                definitionFile = (IFile) ResourcesPlugin.getWorkspace().getRoot()
                        .findMember(value + "/" + ParContentProvider.PROCESS_DEFINITION_FILE_NAME);
            }
            if (definitionFile == null) {
                definitionFile = ProcessCache.getFirstProcessDefinitionFile(subprocess.getSubProcessName(), null);
            }
            if (definitionFile != null) {
                openProcessDefinition(definitionFile);
            }
        }
    }

    public static void exportProcessDefinition(IStructuredSelection selection) {
        ExportParWizard wizard = new ExportParWizard();
        wizard.init(PlatformUI.getWorkbench(), selection);
        CompactWizardDialog dialog = new CompactWizardDialog(wizard);
        dialog.open();
    }

    public static void importProcessDefinition(IStructuredSelection selection) {
        ImportParWizard wizard = new ImportParWizard();
        wizard.init(PlatformUI.getWorkbench(), selection);
        CompactWizardDialog dialog = new CompactWizardDialog(wizard);
        dialog.open();
    }

    public static void exportGlobalSectionDefinition(IStructuredSelection selection) {
        ExportGlbWizard wizard = new ExportGlbWizard();
        wizard.init(PlatformUI.getWorkbench(), selection);
        CompactWizardDialog dialog = new CompactWizardDialog(wizard);
        dialog.open();
    }

    public static void importGlobalSectionDefinition(IStructuredSelection selection) {
        ImportGlbWizard wizard = new ImportGlbWizard();
        wizard.init(PlatformUI.getWorkbench(), selection);
        CompactWizardDialog dialog = new CompactWizardDialog(wizard);
        dialog.open();
    }
    
    public static void showProcessSaveHistory(IStructuredSelection selection) {
        new ProcessSaveHistoryDialog((IFolder) selection.getFirstElement()).open();
    }

    public static void deleteBotResources(List<IResource> resources) {
        for (IResource resource : resources) {
            try {
                resource.refreshLocal(IResource.DEPTH_INFINITE, null);
                if (Dialogs.confirm(Localization.getString(getConfirmMessage(resource), resource.getName()))) {
                    if (resource instanceof IFile) {
                        IWorkbenchPage page = PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage();
                        IFile botTaskFile = (IFile) resource;
                        IEditorPart editor = page.findEditor(new FileEditorInput(botTaskFile));
                        if (editor != null) {
                            page.closeEditor(editor, false);
                        }
                        BotTask botTask = BotCache.getBotTaskNotNull(botTaskFile.getParent().getName(), botTaskFile.getName());
                        deleteBotTask(botTaskFile, botTask);
                    } else {
                        resource.delete(true, null);
                    }
                }
            } catch (CoreException e) {
                PluginLogger.logError("Error deleting", e);
            }
        }
        BotCache.reload();
    }

    public static void createNewBotStation(IStructuredSelection selection) {
        NewBotStationWizard wizard = new NewBotStationWizard();
        wizard.init(PlatformUI.getWorkbench(), selection);
        WizardDialog dialog = new WizardDialog(PlatformUI.getWorkbench().getActiveWorkbenchWindow().getShell(), wizard);
        if (dialog.open() == IDialogConstants.OK_ID) {
            BotCache.reload();
        }
    }

    public static void createNewBot(IStructuredSelection selection) {
        NewBotWizard wizard = new NewBotWizard();
        wizard.init(PlatformUI.getWorkbench(), selection);
        WizardDialog dialog = new WizardDialog(PlatformUI.getWorkbench().getActiveWorkbenchWindow().getShell(), wizard);
        if (dialog.open() == IDialogConstants.OK_ID) {
            BotCache.reload();
        }
    }

    public static void createNewBotTask(IStructuredSelection selection) {
        NewBotTaskWizard wizard = new NewBotTaskWizard();
        wizard.init(PlatformUI.getWorkbench(), selection);
        WizardDialog dialog = new WizardDialog(PlatformUI.getWorkbench().getActiveWorkbenchWindow().getShell(), wizard);
        dialog.open();
    }

    public static void saveBotTask(IFile botTaskFile, BotTask botTask) {
        try {
            StringBuffer info = new StringBuffer();
            info.append(botTask.getDelegationClassName());
            info.append("\n");
            String configuration = BotTaskUtils.createBotTaskConfiguration(botTask);
            if (!Strings.isNullOrEmpty(configuration)) {
                String configurationFileName = botTask.getName() + "." + BotCache.CONFIGURATION_FILE_EXTENSION;
                IFile configurationFile = ((IFolder) botTaskFile.getParent()).getFile(configurationFileName);
                ByteArrayInputStream stream = new ByteArrayInputStream(configuration.getBytes(Charsets.UTF_8));
                IOUtils.createOrUpdateFile(configurationFile, stream);
                info.append(configurationFileName);
                stream.close();
            }
            info.append("\n");
            InputStream infoStream = new ByteArrayInputStream(info.toString().getBytes(Charsets.UTF_8));
            IOUtils.createOrUpdateFile(botTaskFile, infoStream);
            BotCache.invalidateBotTask(botTaskFile, botTask);
            infoStream.close();
        } catch (CoreException | IOException e) {
            throw new InternalApplicationException(e);
        }
    }

    public static void deleteBotTask(IFile botTaskFile, BotTask botTask) {
        try {
            if (!Strings.isNullOrEmpty(botTask.getDelegationClassName())) {
                DelegableProvider provider = HandlerRegistry.getProvider(botTask.getDelegationClassName());
                if (provider instanceof IBotFileSupportProvider) {
                    IBotFileSupportProvider botFileProvider = (IBotFileSupportProvider) provider;
                    String oldEmbeddedFileName = botFileProvider.getEmbeddedFileName(botTask);
                    if (!Strings.isNullOrEmpty(oldEmbeddedFileName)) {
                        IFile embeddedFile = ((IFolder) botTaskFile.getParent()).getFile(oldEmbeddedFileName);
                        if (embeddedFile.exists()) {
                            embeddedFile.delete(true, null);
                        }
                    }
                }
            }
            deleteBotTaskFile(botTaskFile, botTask);
        } catch (CoreException e) {
            throw new InternalApplicationException(e);
        }
    }

    private static void deleteBotTaskFile(IFile botTaskFile, BotTask botTask) throws CoreException {
        botTaskFile.delete(true, null);
        String configurationFileName = botTask.getName() + "." + BotCache.CONFIGURATION_FILE_EXTENSION;
        IFile configurationFile = ((IFolder) botTaskFile.getParent()).getFile(configurationFileName);
        if (configurationFile.exists()) {
            configurationFile.delete(true, null);
        }
        BotCache.botTaskHasBeenDeleted(botTaskFile, botTask);
    }

    public static void openBotTask(IFile botTaskFile) {
        try {
            IDE.openEditor(PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage(), botTaskFile, BotTaskEditor.ID, true);
        } catch (PartInitException e) {
            PluginLogger.logError("Unable open bot task", e);
        }
    }

    public static void exportBotElement(IStructuredSelection selection, ExportBotElementWizardPage page) {
        ExportBotWizard wizard = new ExportBotWizard(page);
        wizard.init(PlatformUI.getWorkbench(), selection);
        CompactWizardDialog dialog = new CompactWizardDialog(wizard);
        dialog.open();
    }

    public static void importBotElement(IStructuredSelection selection, ImportBotElementWizardPage page) {
        ImportBotWizard wizard = new ImportBotWizard(page);
        wizard.init(PlatformUI.getWorkbench(), selection);
        CompactWizardDialog dialog = new CompactWizardDialog(wizard);
        if (dialog.open() == IDialogConstants.OK_ID) {
            BotCache.reload();
        }
    }

    @SuppressWarnings("unchecked")
    public static void renameBotStationFolder(IStructuredSelection selection) {
        try {
            IProject botStationProject = (IProject) selection.getFirstElement();
            IFile botStationFile = botStationProject.getFolder("/src/botstation/").getFile("botstation");
            BufferedReader botStationReader = new BufferedReader(new InputStreamReader(botStationFile.getContents(), Charsets.UTF_8));
            String oldName = botStationReader.readLine();
            String oldRmi = botStationReader.readLine();
            botStationReader.close();
            RenameBotStationDialog dialog = new RenameBotStationDialog(oldName, oldRmi);
            if (dialog.open() == IDialogConstants.OK_ID) {
                String newName = dialog.getName();
                String newRmi = dialog.getRmi();
                if (!newName.equals(oldName)) {
                    IWorkbenchPage page = PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage();
                    List<IFolder> bots = IOUtils.getBotFolders(botStationProject);
                    for (IFolder botFolder : bots) {
                        List<IFile> botTasks = IOUtils.getBotTaskFiles(botFolder);
                        for (IFile botTask : botTasks) {
                            IEditorPart editor = page.findEditor(new FileEditorInput(botTask));
                            if (editor != null) {
                                page.closeEditor(editor, false);
                            }
                        }
                    }
                    IPath newPath = ResourcesPlugin.getWorkspace().getRoot().getProject(newName).getFullPath(); // botStationProject.getParent().getFolder(new
                                                                                                                // Path(newName)).getFullPath();
                    botStationProject.copy(newPath, true, null);
                    botStationProject = ResourcesPlugin.getWorkspace().getRoot().getProject(newName);
                    // rename in file
                    IFile file = botStationProject.getFolder("/src/botstation/").getFile("botstation");
                    IOUtils.createOrUpdateFile(file, BotTaskUtils.createBotStationInfo(newName, newRmi));
                    ResourcesPlugin.getWorkspace().getRoot().getProject(oldName).delete(true, null);
                } else if (!newRmi.equals(oldRmi)) {
                    IFile file = botStationProject.getFolder("/src/botstation/").getFile("botstation");
                    IOUtils.createOrUpdateFile(file, BotTaskUtils.createBotStationInfo(newName, newRmi));
                    refreshResources(selection.toList());
                }
            }
            BotCache.reload();
        } catch (Exception e) {
            PluginLogger.logError(e);
        }
    }

    public static void renameBotFolder(IStructuredSelection selection) {
        IFolder botFolder = (IFolder) selection.getFirstElement();
        RenameBotDialog renameBotDialog = new RenameBotDialog(botFolder);

        if (renameBotDialog.open() == IDialogConstants.OK_ID) {
            String newName = renameBotDialog.getValue();
            try {
                IWorkbenchPage page = PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage();
                List<IFile> botTasks = IOUtils.getBotTaskFiles(botFolder);
                for (IFile botTask : botTasks) {
                    IEditorPart editor = page.findEditor(new FileEditorInput(botTask));
                    if (editor != null) {
                        page.closeEditor(editor, false);
                    }
                }
                IPath oldPath = botFolder.getFullPath();
                IPath newPath = botFolder.getParent().getFolder(new Path(newName)).getFullPath();
                botFolder.copy(newPath, true, null);
                botFolder = ResourcesPlugin.getWorkspace().getRoot().getFolder(newPath);
                ResourcesPlugin.getWorkspace().getRoot().getFolder(oldPath).delete(true, null);
                BotCache.reload();
            } catch (Exception e) {
                PluginLogger.logError(e);
            }
        }
    }

    public static void renameBotTaskFile(IStructuredSelection selection) {
        IFile botTaskFile = (IFile) selection.getFirstElement();
        RenameBotTaskDialog dialog = new RenameBotTaskDialog(botTaskFile);
        dialog.setName(botTaskFile.getName());
        if (dialog.open() == IDialogConstants.OK_ID) {
            IFolder botFolder = (IFolder) botTaskFile.getParent();
            BotTask botTask = BotCache.getBotTaskNotNull(botTaskFile);
            List<String> dependentDefinitions = findDependentProcessDefinitions(botFolder, botTask);
            if (dependentDefinitions.size() > 0) {
                showDependentTasksDialog(dependentDefinitions);
                return;
            }

            try {
                IWorkbenchPage page = PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage();
                IEditorPart editor = page.findEditor(new FileEditorInput(botTaskFile));
                if (editor != null) {
                    page.closeEditor(editor, true);
                }
                deleteBotTaskFile(botTaskFile, botTask);
                BotTaskUtils.copyBotTaskConfig(botTaskFile, botTask, dialog.getName(), botFolder);
            } catch (Exception e) {
                PluginLogger.logError(e);
            }
        }
    }

    private static List<String> findDependentProcessDefinitions(IFolder botFolder, BotTask botTask) {
        List<String> dependentDefinitions = new ArrayList<String>();
        Set<ProcessDefinition> definitions = ProcessCache.getAllProcessDefinitions();
        for (ProcessDefinition definition : definitions) {
            for (TaskState taskState : definition.getChildren(TaskState.class)) {
                if (botTask.getType() == BotTaskType.SIMPLE) {
                    if (Objects.equal(botTask.getName(), taskState.getName())) {
                        if (Objects.equal(botFolder.getName(), taskState.getSwimlaneBotName())) {
                            dependentDefinitions.add(definition.getName());
                            break;
                        }
                    }
                } else {
                    if (taskState.getBotTaskLink() != null && Objects.equal(botTask.getName(), taskState.getBotTaskLink().getBotTaskName())) {
                        dependentDefinitions.add(definition.getName());
                        break;
                    }
                }
            }
        }
        return dependentDefinitions;
    }

    private static void showDependentTasksDialog(List<String> dependentDefinitions) {
        StringBuffer detailsMessage = new StringBuffer();
        detailsMessage.append(Localization.getString("DependentTasksDialog.detailsMessage"));
        for (String processDefinitionName : dependentDefinitions) {
            detailsMessage.append("\n");
            detailsMessage.append(processDefinitionName);
        }
        Dialogs.error(Localization.getString("DependentTasksDialog.errorMessage"), detailsMessage.toString());
    }

    public static void copyBotTask(IStructuredSelection selection) {
        CopyBotTaskWizard wizard = new CopyBotTaskWizard();
        wizard.init(PlatformUI.getWorkbench(), selection);
        CompactWizardDialog dialog = new CompactWizardDialog(wizard);
        dialog.open();
        BotCache.reload();
    }

    public static void addDataSource() {
        editDataSource(null);
    }

    public static void editDataSource(IStructuredSelection selection) {
        IFile selected = (selection == null ? null : (IFile) selection.getFirstElement());
        DataSourceDialog dialog = new DataSourceDialog(selected);
        if (dialog.open() == IDialogConstants.OK_ID) {
            try (InputStream is = new ByteArrayInputStream(dialog.getXml().getBytes(Charsets.UTF_8));) {
                IFile dsFile = DataSourceUtils.getDataSourcesProject().getFile(dialog.getName() + DataSourceStuff.DATA_SOURCE_FILE_SUFFIX);
                IOUtils.createOrUpdateFile(dsFile, is);
                if (selected != null && !selected.getName().equals(dsFile.getName())) {
                    selected.delete(true, null);
                }
            } catch (CoreException | IOException e) {
                throw new InternalApplicationException(e);
            }
        }
    }

    public static void copyDataSource(IStructuredSelection selection) {
        IFile srcFile = (IFile) selection.getFirstElement();
        String srcDSName = srcFile.getName();
        srcDSName = srcDSName.substring(0, srcDSName.lastIndexOf('.'));
        IProject dsProject = srcFile.getProject();
        String dstDSName = null;
        IFile dstFile = null;
        for (int i = 2; i < 100; i++) {
            dstDSName = srcDSName + i;
            dstFile = dsProject.getFile(dstDSName + DataSourceStuff.DATA_SOURCE_FILE_SUFFIX);
            if (!dstFile.exists()) {
                break;
            }
        }
        try (InputStream is = srcFile.getContents()) {
            SAXReader reader = new SAXReader();
            reader.setMergeAdjacentText(true);
            reader.setStripWhitespaceText(true);
            reader.setErrorHandler(SimpleErrorHandler.getInstance());
            Document document = reader.read(new InputStreamReader(is, Charsets.UTF_8));
            document.getRootElement().attribute(DataSourceStuff.ATTR_NAME).setValue(dstDSName);
            IOUtils.createOrUpdateFile(dstFile, new ByteArrayInputStream(XmlUtil.toString(document).getBytes(Charsets.UTF_8)));
        } catch (IOException | CoreException | DocumentException e) {
            throw new InternalApplicationException(e);
        }
    }

    public static void deleteDataSources(List<IResource> resources) {
        for (IResource resource : resources) {
            try {
                resource.refreshLocal(IResource.DEPTH_ZERO, null);
                if (Dialogs.confirm(Localization.getString("Delete.dataSource.message", resource.getName()))) {
                    if (resource instanceof IFile) {
                        resource.delete(true, null);
                    }
                }
            } catch (CoreException e) {
                PluginLogger.logError("Error deleting", e);
            }
        }
    }

    public static void exportDataSource(IStructuredSelection selection) {
        ExportDataSourceWizard wizard = new ExportDataSourceWizard();
        wizard.init(PlatformUI.getWorkbench(), selection);
        CompactWizardDialog dialog = new CompactWizardDialog(wizard);
        dialog.open();
    }

    public static void importDataSource() {
        ImportDataSourceWizard wizard = new ImportDataSourceWizard();
        wizard.init(PlatformUI.getWorkbench(), new StructuredSelection(DataSourceUtils.getDataSourcesProject()));
        CompactWizardDialog dialog = new CompactWizardDialog(wizard);
        dialog.open();
    }

    public static void job(String jobName, Consumer<?> todo) {
        WorkspaceJob job = new WorkspaceJob(jobName) {
            @Override
            public IStatus runInWorkspace(IProgressMonitor monitor) {
                todo.accept(null);
                return Status.OK_STATUS;
            }
        };
        job.setUser(true);
        job.schedule();
    }

}
