package ru.runa.gpd.ui.view;

import java.util.List;
import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IResourceChangeEvent;
import org.eclipse.core.resources.IResourceChangeListener;
import org.eclipse.core.resources.IResourceDelta;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.IMenuListener;
import org.eclipse.jface.action.IMenuManager;
import org.eclipse.jface.action.MenuManager;
import org.eclipse.jface.action.Separator;
import org.eclipse.jface.viewers.AbstractTreeViewer;
import org.eclipse.jface.viewers.DoubleClickEvent;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.jface.viewers.TreeViewer;
import org.eclipse.search.ui.NewSearchUI;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.ui.ISelectionListener;
import org.eclipse.ui.IViewSite;
import org.eclipse.ui.IWorkbenchActionConstants;
import org.eclipse.ui.IWorkbenchPart;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.part.ViewPart;
import ru.runa.gpd.Localization;
import ru.runa.gpd.PluginLogger;
import ru.runa.gpd.ProcessCache;
import ru.runa.gpd.SharedImages;
import ru.runa.gpd.editor.ProcessEditorBase;
import ru.runa.gpd.editor.ProcessSaveHistory;
import ru.runa.gpd.lang.model.ProcessDefinition;
import ru.runa.gpd.lang.par.ParContentProvider;
import ru.runa.gpd.search.SubprocessSearchQuery;
import ru.runa.gpd.ui.custom.LoggingDoubleClickAdapter;
import ru.runa.gpd.util.IOUtils;
import ru.runa.gpd.util.UiUtil;
import ru.runa.gpd.util.WorkspaceOperations;
import ru.runa.wfe.definition.ProcessDefinitionAccessType;

public class ProcessExplorerTreeView extends ViewPart implements ISelectionListener {
    private TreeViewer viewer;

    @Override
    public void init(IViewSite site) throws PartInitException {
        super.init(site);
        getSite().getWorkbenchWindow().getSelectionService().addSelectionListener(this);
    }

    @Override
    public void dispose() {
        getSite().getWorkbenchWindow().getSelectionService().removeSelectionListener(this);
        super.dispose();
    }

    @Override
    public void selectionChanged(IWorkbenchPart part, ISelection selection) {
        if (part instanceof ProcessEditorBase) {
            IContainer definitionFolder = ((ProcessEditorBase) part).getDefinitionFile().getParent();
            IContainer projectFolder = definitionFolder.getParent().getParent().getParent();
            viewer.expandToLevel(projectFolder, AbstractTreeViewer.ALL_LEVELS);
            viewer.setSelection(new StructuredSelection(definitionFolder), true);
        }
    }

    private boolean nothingOrMarkersChanged(IResourceChangeEvent event) {
        int status = collectStatus(event.getDelta(), 0);
        return status == IResourceDelta.NO_CHANGE
                || (status & (IResourceDelta.ADDED | IResourceDelta.REMOVED)) == 0 && (status & IResourceDelta.MARKERS) != 0;
    }

    private int collectStatus(IResourceDelta delta, int status) {
        status |= (delta.getKind() | delta.getFlags());
        for (IResourceDelta d : delta.getAffectedChildren()) {
            status = collectStatus(d, status);
        }
        return status;
    }

    @Override
    public void createPartControl(Composite parent) {
        UiUtil.hideToolBar(getViewSite());
        viewer = new TreeViewer(parent, SWT.NONE);
        viewer.setContentProvider(new ProcessExplorerContentProvider());
        viewer.setLabelProvider(new ProcessExplorerLabelProvider());
        viewer.setInput(new Object());
        ResourcesPlugin.getWorkspace().addResourceChangeListener(new IResourceChangeListener() {
            @Override
            public void resourceChanged(IResourceChangeEvent event) {
                try {
                    PlatformUI.getWorkbench().getDisplay().asyncExec(new Runnable() {
                        @Override
                        public void run() {
                            boolean refresh = false;
                            switch (event.getType()) {
                            case IResourceChangeEvent.PRE_CLOSE:
                            case IResourceChangeEvent.PRE_DELETE:
                                refresh = true;
                                break;
                            case IResourceChangeEvent.POST_CHANGE:
                                if (!nothingOrMarkersChanged(event)) {
                                    refresh = true;
                                }
                                break;
                            }
                            if (refresh && !viewer.getControl().isDisposed()) {
                                viewer.refresh();
                            }
                        }
                    });
                } catch (Exception e) {
                    // disposed
                }
            }
        });
        viewer.addDoubleClickListener(new LoggingDoubleClickAdapter() {

            @Override
            protected void onDoubleClick(DoubleClickEvent event) {
                Object element = ((IStructuredSelection) event.getSelection()).getFirstElement();
                openProcessDefinition(element);
            }
        });
        getSite().setSelectionProvider(viewer);
        MenuManager menuMgr = new MenuManager();
        Menu menu = menuMgr.createContextMenu(viewer.getControl());
        menuMgr.add(new Separator(IWorkbenchActionConstants.MB_ADDITIONS));
        menuMgr.setRemoveAllWhenShown(true);
        menuMgr.addMenuListener(new IMenuListener() {
            @Override
            public void menuAboutToShow(IMenuManager manager) {
                ProcessExplorerTreeView.this.fillContextMenu(manager);
            }
        });
        viewer.getControl().setMenu(menu);
    }

    private void openProcessDefinition(Object element) {
        if (element instanceof IFolder) {
            IFile definitionFile = IOUtils.getProcessDefinitionFile((IFolder) element);
            if (definitionFile.exists()) {
                if (((IFolder) element).getName().startsWith(".")) {
                	WorkspaceOperations.openGlobalSectionDefinition(definitionFile);
                }
                else {
                	WorkspaceOperations.openProcessDefinition(definitionFile);
                }
            }
        }
        if (element instanceof IFile) {
            WorkspaceOperations.openProcessDefinition((IFile) element);
        }
    }

    @SuppressWarnings({ "unchecked" })
    protected void fillContextMenu(IMenuManager manager) {
        final IStructuredSelection selection = (IStructuredSelection) viewer.getSelection();
        final Object selectedObject = selection.getFirstElement();
        final boolean menuOnSubprocess = selectedObject instanceof IFile
                && ((IFile) selectedObject).getName().matches("sub.*\\Q" + ParContentProvider.PROCESS_DEFINITION_FILE_NAME);
        final List<IResource> resources = selection.toList();
        boolean menuOnContainer = selectedObject instanceof IProject || selectedObject instanceof IFolder;
        boolean menuOnProcess = selectedObject instanceof IFile;
        if (selectedObject instanceof IFolder) {
            menuOnProcess |= IOUtils.isProcessDefinitionFolder((IFolder) selectedObject);
        }
        //
        if (menuOnProcess) {
            manager.add(new Action(Localization.getString("ExplorerTreeView.menu.label.openProcess")) {
                @Override
                public void run() {
                    openProcessDefinition(selectedObject);
                }
            });
        }
        manager.add(new Action(Localization.getString("ExplorerTreeView.menu.label.newProject"),
                SharedImages.getImageDescriptor("icons/add_project.gif")) {

            @Override
            public void run() {
                WorkspaceOperations.createNewProject();
            }
        });
        if (menuOnContainer && !menuOnProcess) {
            if (IOUtils.isProjectHasProcessNature(((IContainer) selectedObject).getProject())) {
                manager.add(new Action(Localization.getString("ExplorerTreeView.menu.label.newFolder"),
                        SharedImages.getImageDescriptor("icons/add_folder.gif")) {
                    @Override
                    public void run() {
                        WorkspaceOperations.createNewFolder(selection);
                    }
                });
            }
        }
        if (menuOnContainer) {
            manager.add(new Action(Localization.getString("ExplorerTreeView.menu.label.newGlobalSection"),
                    SharedImages.getImageDescriptor("icons/glb.gif")) {

                @Override
                public void run() {
                	WorkspaceOperations.createNewGlobalSectionDefinition(selection, ProcessDefinitionAccessType.Process);
                }
            });
            manager.add(new Action(Localization.getString("ExplorerTreeView.menu.label.newProcess"),
                    SharedImages.getImageDescriptor("icons/process.gif")) {

                @Override
                public void run() {
                    WorkspaceOperations.createNewProcessDefinition(selection, ProcessDefinitionAccessType.Process);
                }
            });
            manager.add(new Action(Localization.getString("ExplorerTreeView.menu.label.importGlobalSection"),
                    SharedImages.getImageDescriptor("icons/import_glb.gif")) {
                @Override
                public void run() {
                    WorkspaceOperations.importGlobalSectionDefinition(selection);
                }
            });
            manager.add(new Action(Localization.getString("ExplorerTreeView.menu.label.importProcess"),
                    SharedImages.getImageDescriptor("icons/import.gif")) {
                @Override
                public void run() {
                    WorkspaceOperations.importProcessDefinition(selection);
                }
            });
        }
        if (menuOnProcess) {
            Action copy = new Action(Localization.getString("ExplorerTreeView.menu.label.copyProcess"),
                    SharedImages.getImageDescriptor("icons/copy.gif")) {
                @Override
                public void run() {
                    WorkspaceOperations.copyProcessDefinition(selection);
                }
            };
            if (menuOnSubprocess) {
                copy.setEnabled(false);
            }
            manager.add(copy);
            manager.add(new Action(Localization.getString("ExplorerTreeView.menu.label.exportGlobalSection"),
                    SharedImages.getImageDescriptor("icons/export_glb.gif")) {
                @Override
                public void run() {
                    WorkspaceOperations.exportGlobalSectionDefinition(selection);
                }
            });
            manager.add(new Action(Localization.getString("ExplorerTreeView.menu.label.exportProcess"),
                    SharedImages.getImageDescriptor("icons/export.gif")) {
                @Override
                public void run() {
                    WorkspaceOperations.exportProcessDefinition(selection);
                }
            });
            manager.add(new Action(Localization.getString("ExplorerTreeView.menu.label.renameProcess"),
                    SharedImages.getImageDescriptor("icons/rename.gif")) {
                @Override
                public void run() {
                    if (menuOnSubprocess) {
                        WorkspaceOperations.renameSubProcessDefinition(selection);
                    } else {
                    	if (((IResource) selectedObject).getName().startsWith(".")) {
                    			WorkspaceOperations.renameGlobalDefinition(selection);
                    	}
                    	else {
                    		WorkspaceOperations.renameProcessDefinition(selection);
                    	}
                    }
                }
            });
        }
        if (menuOnContainer) {
            manager.add(
                    new Action(Localization.getString("ExplorerTreeView.menu.label.refresh"), SharedImages.getImageDescriptor("icons/refresh.gif")) {
                        @Override
                        public void run() {
                            WorkspaceOperations.refreshResources(resources);
                        }
                    });
        }
        if (menuOnProcess) {
            manager.add(new Action(Localization.getString("button.findReferences"), SharedImages.getImageDescriptor("icons/search.gif")) {

                @Override
                public void run() {
                    try {
                        IFile definitionFile;
                        if (selectedObject instanceof IFolder) {
                            definitionFile = IOUtils.getProcessDefinitionFile((IFolder) selectedObject);
                        } else {
                            definitionFile = (IFile) selectedObject;
                        }
                        ProcessDefinition processDefinition = ProcessCache.getProcessDefinition(definitionFile);
                        SubprocessSearchQuery query = new SubprocessSearchQuery(processDefinition.getName());
                        NewSearchUI.runQueryInBackground(query);
                    } catch (Exception ex) {
                        PluginLogger.logError(ex);
                    }
                }
            });
        }
        if (menuOnProcess && ProcessSaveHistory.isActive() && !menuOnSubprocess) {
            manager.add(new Action(Localization.getString("ExplorerTreeView.menu.label.showSaveHistory"),
                    SharedImages.getImageDescriptor("icons/saveall_edit.gif")) {
                @Override
                public void run() {
                    WorkspaceOperations.showProcessSaveHistory(selection);
                }
            });
        }
        manager.add(new Action(Localization.getString("button.delete"), SharedImages.getImageDescriptor("icons/delete.gif")) {

            @Override
            public void run() {
                WorkspaceOperations.deleteResources(resources);
            }

        });
    }

    @Override
    public void setFocus() {
    }
}
