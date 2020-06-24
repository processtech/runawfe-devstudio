package ru.runa.gpd.editor;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.eclipse.core.internal.resources.ResourceException;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IResourceChangeEvent;
import org.eclipse.core.resources.IResourceChangeListener;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.draw2d.IFigure;
import org.eclipse.gef.EditDomain;
import org.eclipse.gef.EditPart;
import org.eclipse.gef.GraphicalViewer;
import org.eclipse.gef.commands.CommandStack;
import org.eclipse.gef.ui.actions.ActionRegistry;
import org.eclipse.gef.ui.parts.GraphicalEditor;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IEditorSite;
import org.eclipse.ui.ISelectionListener;
import org.eclipse.ui.IViewPart;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchPart;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.editors.text.TextEditor;
import org.eclipse.ui.part.FileEditorInput;
import org.eclipse.ui.part.MultiPageEditorPart;
import org.eclipse.ui.views.contentoutline.ContentOutline;
import org.eclipse.ui.views.contentoutline.IContentOutlinePage;
import ru.runa.gpd.Localization;
import ru.runa.gpd.PluginLogger;
import ru.runa.gpd.ProcessCache;
import ru.runa.gpd.PropertyNames;
import ru.runa.gpd.editor.gef.GEFImageHelper;
import ru.runa.gpd.editor.gef.GEFProcessEditor;
import ru.runa.gpd.editor.gef.part.graph.ElementGraphicalEditPart;
import ru.runa.gpd.editor.graphiti.DiagramEditorPage;
import ru.runa.gpd.extension.regulations.ui.RegulationsNotesView;
import ru.runa.gpd.extension.regulations.ui.RegulationsSequenceView;
import ru.runa.gpd.lang.model.FormNode;
import ru.runa.gpd.lang.model.GraphElement;
import ru.runa.gpd.lang.model.ProcessDefinition;
import ru.runa.gpd.lang.model.SubprocessDefinition;
import ru.runa.gpd.lang.model.Swimlane;
import ru.runa.gpd.lang.model.Variable;
import ru.runa.gpd.lang.par.ParContentProvider;
import ru.runa.gpd.lang.par.ProcessDefinitionValidator;
import ru.runa.gpd.ui.view.ValidationErrorsView;
import ru.runa.gpd.util.EditorUtils;
import ru.runa.gpd.util.IOUtils;
import ru.runa.gpd.util.WorkspaceOperations;

public abstract class GlobalSectionEditorBase extends ProcessEditorBase {

    protected ProcessDefinition definition;
    protected IFile definitionFile;
    protected SwimlaneEditorPage swimlanePage;
    protected VariableEditorPage variablePage;
    protected VariableTypeEditorPage variableTypeEditorPage;

    @Override
    public void init(IEditorSite site, IEditorInput input) throws PartInitException {
        definitionFile = ((FileEditorInput) input).getFile();
        definition = ProcessCache.getProcessDefinition(definitionFile);
        definition.setDirty(false);
        definition.addPropertyChangeListener(this);

        setPartName(definition.getName());
        
        super.init(site, input);
        getSite().getPage().addSelectionListener(this);
        ResourcesPlugin.getWorkspace().addResourceChangeListener(this, IResourceChangeEvent.POST_CHANGE);
    }

    @Override
    public void dispose() {
        try {
            IOUtils.restoreDeletedFiles(definitionFile.getParent());
            if (definition != null) {
                definition.removePropertyChangeListener(this);
                // If process definition is dirty (hasn't been saved) we should
                // reload it from XML
                if (definition.isDirty()) {
                    ProcessCache.invalidateProcessDefinition(definitionFile);
                }
                if (definitionFile.exists()) {
                    definitionFile.deleteMarkers(ValidationErrorsView.ID, true, IResource.DEPTH_INFINITE);
                    definitionFile.deleteMarkers(RegulationsSequenceView.ID, true, IResource.DEPTH_INFINITE);
                    definitionFile.deleteMarkers(RegulationsNotesView.ID, true, IResource.DEPTH_INFINITE);
                }
            }
        } catch (Exception e) {
            PluginLogger.logError(e);
        }
        ResourcesPlugin.getWorkspace().removeResourceChangeListener(this);
        getSite().getPage().removeSelectionListener(this);
        super.dispose();
    }

    @Override
    public void resourceChanged(IResourceChangeEvent event) {
        EditorUtils.closeEditorIfRequired(event, definitionFile, this);
    }

    @Override
    public void selectionChanged(IWorkbenchPart part, ISelection selection) {
        try {
            if (!(selection instanceof IStructuredSelection)) {
                return;
            }
            IStructuredSelection structuredSelection = (IStructuredSelection) selection;
            Object selectedObject = structuredSelection.getFirstElement();
            if (selectedObject == null) {
                return;
            }
            if (part instanceof GEFProcessEditor) {
                // select only variables and swimlanes
                if (selectedObject instanceof Swimlane || selectedObject instanceof Variable) {
                    selectGraphElement((GraphElement) selectedObject);
                }
            }
            if (part instanceof ContentOutline) {
                Object model = ((EditPart) selectedObject).getModel();
                if (!(model instanceof GraphElement)) {
                    return;
                }
                select((GraphElement) model);
            }
            String activateViewId = null;
            if (selectedObject instanceof ElementGraphicalEditPart) {
                activateViewId = ((ElementGraphicalEditPart) selectedObject).getAssociatedViewId();
            }
            if (activateViewId != null) {
                IViewPart viewPart = getSite().getPage().findView(activateViewId);
                if (!getSite().getPage().isPartVisible(viewPart)) {
                    try {
                        getSite().getPage().showView(activateViewId, null, IWorkbenchPage.VIEW_VISIBLE);
                    } catch (Exception e) {
                    }
                }
            }
        } catch (Exception e) {
            PluginLogger.logErrorWithoutDialog("DesignerEditor.selectionChanged", e);
        }
    }

    @Override
    protected void createPages() {
        try {
            if (!(definition instanceof SubprocessDefinition)) {
                swimlanePage = addNewPage(new SwimlaneEditorPage((ProcessEditorBase)this), "DesignerEditor.title.swimlanes");
                variablePage = addNewPage(new VariableEditorPage((ProcessEditorBase)this), "DesignerEditor.title.variables");
                variableTypeEditorPage = addNewPage(new VariableTypeEditorPage((ProcessEditorBase)this), "VariableUserType.collection");
            }
            ProcessDefinitionValidator.validateDefinition(definition);
        } catch (PartInitException e) {
            PluginLogger.logError(Localization.getString("DesignerEditor.error.can_not_create_graphical_viewer"), e);
            throw new RuntimeException(e);
        }
    }

    public void select(GraphElement model) {
        if (model instanceof Swimlane && swimlanePage != null) {
            openPage(1);
            swimlanePage.select((Swimlane) model);
        } else if (model instanceof Variable && variablePage != null) {
            openPage(2);
            variablePage.select((Variable) model);
        } else {
            openPage(0);
            selectGraphElement(model);
        }
    }

    @Override
    public Object getAdapter(Class adapter) {
        return null;
    }

    public void openPage(int number) {
        if (getActivePage() != number) {
            setActivePage(number);
            setFocus();
        }
    }

    public void refresh() {
        IFigure figure = getRootFigure();
        figure.revalidate();
        figure.repaint();
        figure.invalidateTree();
    }

    public ProcessDefinition getDefinition() {
        return definition;
    }

    @Override
    public void propertyChange(PropertyChangeEvent evt) {
        if (PropertyNames.PROPERTY_DIRTY.equals(evt.getPropertyName())) {
            firePropertyChange(IEditorPart.PROP_DIRTY);
        }
        if (PropertyNames.PROPERTY_SHOW_GRID.equals(evt.getPropertyName())) {
            updateGridLayerVisibility(definition.isShowGrid());
        }
    }

    @Override
    public void doSave(IProgressMonitor monitor) {
        try {
            ProcessDefinitionValidator.validateDefinition(definition);
            WorkspaceOperations.saveProcessDefinition(definition);
            definition.setDirty(false);
            ProcessSaveHistory.addSavepoint(definitionFile);
        } catch (Exception e) {
            PluginLogger.logError(e);
        }
        ProcessDefinition mainProcessDefinition = definition.getMainProcessDefinition();
        try {
            Set<String> usedFormFiles = new HashSet<String>();
            usedFormFiles.add(ParContentProvider.PROCESS_DEFINITION_DESCRIPTION_FILE_NAME);
            usedFormFiles.add(ParContentProvider.FORM_JS_FILE_NAME);
            usedFormFiles.add(ParContentProvider.REGULATIONS_HTML_FILE_NAME);
            fetchUsedFormFiles(usedFormFiles, mainProcessDefinition);
            for (SubprocessDefinition subprocessDefinition : mainProcessDefinition.getEmbeddedSubprocesses().values()) {
                fetchUsedFormFiles(usedFormFiles, subprocessDefinition);
            }
            IFolder folder = (IFolder) definitionFile.getParent();
            IResource[] children = folder.members(true);
            for (IResource resource : children) {
                boolean interested = IOUtils.looksLikeFormFile(resource.getName());
                if (interested && !usedFormFiles.contains(resource.getName())) {
                    try {
                        PluginLogger.logInfo("Deleting unused " + resource);
                        resource.delete(true, monitor);
                    } catch (ResourceException e) {
                        PluginLogger.logErrorWithoutDialog(resource + " is being used by another process");
                    }
                }
                if (ParContentProvider.PROCESS_IMAGE_OLD_FILE_NAME.equals(resource.getName())) {
                    PluginLogger.logInfo("Deleting jpg graph image: " + resource);
                    resource.delete(true, null);
                }
            }
            IOUtils.eraseDeletedFiles(folder);
        } catch (CoreException e) {
            PluginLogger.logErrorWithoutDialog("Cleaning unused form files", e);
        }
    }

    public IFile getDefinitionFile() {
        return definitionFile;
    }

    @Override
    public void doSaveAs() {
    }

    @Override
    public boolean isSaveAsAllowed() {
        return false;
    }

    @Override
    public boolean isDirty() {
        return definition.isDirty();
    }

    @Override
    public boolean isSaveOnCloseNeeded() {
        return isDirty();
    }

    protected abstract GraphicalEditor createGraphPage();

    protected abstract void selectGraphElement(GraphElement model);

    protected abstract void updateGridLayerVisibility(boolean enabled);

    @Override
    protected void pageChange(int newPageIndex) {
        super.pageChange(newPageIndex);
    }

    private String getGraphImagePath() {
        IFile file = ((FileEditorInput) getEditorInput()).getFile();
        String fileName = ParContentProvider.PROCESS_IMAGE_FILE_NAME;
        if (definition instanceof SubprocessDefinition) {
            fileName = definition.getId() + "." + fileName;
        }
        IFile imageFile = IOUtils.getAdjacentFile(file, fileName);
        return imageFile.getRawLocation().toOSString();
    }

    private void fetchUsedFormFiles(Set<String> usedFormFiles, ProcessDefinition processDefinition) {
        List<FormNode> formNodes = processDefinition.getChildren(FormNode.class);
        for (FormNode formNode : formNodes) {
            if (formNode.hasForm()) {
                usedFormFiles.add(formNode.getFormFileName());
            }
            if (formNode.hasFormTemplate()) {
                usedFormFiles.add(formNode.getTemplateFileName());
            }
            if (formNode.hasFormValidation()) {
                usedFormFiles.add(formNode.getValidationFileName());
            }
            if (formNode.hasFormScript()) {
                usedFormFiles.add(formNode.getScriptFileName());
            }
        }
    }

    private <T extends IEditorPart> T addNewPage(T editorPart, String title) throws PartInitException {
        int pageIndex = addPage(editorPart, getEditorInput());
        setPageText(pageIndex, Localization.getString(title));
        return editorPart;
    }

}
