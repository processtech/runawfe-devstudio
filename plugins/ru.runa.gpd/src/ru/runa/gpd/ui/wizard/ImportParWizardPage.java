package ru.runa.gpd.ui.wizard;

import com.google.common.base.Objects;
import com.google.common.base.Throwables;
import com.google.common.collect.Lists;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jface.dialogs.ProgressMonitorDialog;
import org.eclipse.jface.operation.IRunnableWithProgress;
import org.eclipse.jface.viewers.IStructuredContentProvider;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.ITreeContentProvider;
import org.eclipse.jface.viewers.LabelProvider;
import org.eclipse.jface.viewers.TreeViewer;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.jface.viewers.ViewerFilter;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.SashForm;
import org.eclipse.swt.events.ModifyEvent;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.FileDialog;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;
import org.eclipse.swt.widgets.TreeItem;
import ru.runa.gpd.Localization;
import ru.runa.gpd.PluginLogger;
import ru.runa.gpd.ProcessCache;
import ru.runa.gpd.SharedImages;
import ru.runa.gpd.lang.model.ProcessDefinition;
import ru.runa.gpd.sync.WfeServerConnector;
import ru.runa.gpd.sync.WfeServerConnectorComposite;
import ru.runa.gpd.sync.WfeServerConnectorSynchronizationCallback;
import ru.runa.gpd.sync.WfeServerProcessDefinitionImporter;
import ru.runa.gpd.ui.custom.Dialogs;
import ru.runa.gpd.util.IOUtils;
import ru.runa.wfe.definition.dto.WfDefinition;

public class ImportParWizardPage extends ImportWizardPage {
    private Button importFromFileButton;
    private Composite fileSelectionArea;
    private Text selectedParsText;
    private Button selectParsButton;
    private Button importFromServerButton;
    private WfeServerConnectorComposite serverConnectorComposite;
    private TreeViewer serverDefinitionViewer;
    private Text serverDefinitionFilter;
    private String selectedDirFileName;
    private String[] selectedFileNames;

    public ImportParWizardPage(IStructuredSelection selection) {
        super(ImportParWizardPage.class, selection);
        setTitle(Localization.getString("ImportParWizardPage.page.title"));
    }

    protected IContainer getInitialSelection(IStructuredSelection selection) {
        return (IContainer) IOUtils.getProcessSelectionResource(selection);
    }

    @Override
    public void createControl(Composite parent) {
        Composite pageControl = new Composite(parent, SWT.NONE);
        pageControl.setLayout(new GridLayout(1, false));
        pageControl.setLayoutData(new GridData(GridData.FILL_BOTH));
        SashForm sashForm = new SashForm(pageControl, SWT.HORIZONTAL);
        sashForm.setLayoutData(new GridData(GridData.FILL_BOTH));
        createProjectsGroup(sashForm, IOUtils.getAllProcessContainers(), new LabelProvider() {
            @Override
            public String getText(Object element) {
                return IOUtils.getProcessContainerName((IContainer) element);
            }
        });
        Group importGroup = new Group(sashForm, SWT.NONE);
        importGroup.setLayout(new GridLayout(1, false));
        importGroup.setLayoutData(new GridData(GridData.FILL_BOTH));
        importFromFileButton = new Button(importGroup, SWT.RADIO);
        importFromFileButton.setText(Localization.getString("button.importFromFile"));
        importFromFileButton.setSelection(true);
        importFromFileButton.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                onImportModeChanged();
            }
        });
        fileSelectionArea = new Composite(importGroup, SWT.NONE);
        GridData fileSelectionData = new GridData(GridData.GRAB_HORIZONTAL | GridData.FILL_HORIZONTAL);
        fileSelectionData.heightHint = 30;
        fileSelectionArea.setLayoutData(fileSelectionData);
        GridLayout fileSelectionLayout = new GridLayout();
        fileSelectionLayout.numColumns = 2;
        fileSelectionLayout.makeColumnsEqualWidth = false;
        fileSelectionLayout.marginWidth = 0;
        fileSelectionLayout.marginHeight = 0;
        fileSelectionArea.setLayout(fileSelectionLayout);
        selectedParsText = new Text(fileSelectionArea, SWT.MULTI | SWT.READ_ONLY | SWT.V_SCROLL | SWT.BORDER);
        GridData gridData = new GridData(GridData.GRAB_HORIZONTAL | GridData.FILL_HORIZONTAL);
        gridData.heightHint = 30;
        selectedParsText.setLayoutData(gridData);
        selectParsButton = new Button(fileSelectionArea, SWT.PUSH);
        selectParsButton.setText(Localization.getString("button.choose"));
        selectParsButton.setLayoutData(new GridData(GridData.VERTICAL_ALIGN_BEGINNING | GridData.HORIZONTAL_ALIGN_END));
        selectParsButton.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                FileDialog dialog = new FileDialog(getShell(), SWT.OPEN | SWT.MULTI);
                dialog.setFilterExtensions(new String[] { "*.par" });
                if (dialog.open() != null) {
                    selectedDirFileName = dialog.getFilterPath();
                    selectedFileNames = dialog.getFileNames();
                    String text = "";
                    for (String fileName : selectedFileNames) {
                        text += fileName + "\n";
                    }
                    selectedParsText.setText(text);
                }
            }
        });
        importFromServerButton = new Button(importGroup, SWT.RADIO);
        importFromServerButton.setText(Localization.getString("button.importFromServer"));
        serverConnectorComposite = new WfeServerConnectorComposite(importGroup, WfeServerProcessDefinitionImporter.getInstance(),
                new WfeServerConnectorSynchronizationCallback() {

                    @Override
                    public void onCompleted() {
                        updateServerDefinitionViewer(WfeServerProcessDefinitionImporter.getInstance().getData());
                    }

                    @Override
                    public void onFailed() {
                        updateServerDefinitionViewer(null);
                    }

                });
        createServerDefinitionsGroup(importGroup);
        setControl(pageControl);
        onImportModeChanged();
    }

    private void onImportModeChanged() {
        boolean fromFile = importFromFileButton.getSelection();
        selectedParsText.setEnabled(fromFile);
        selectParsButton.setEnabled(fromFile);
        serverConnectorComposite.setEnabled(!fromFile);
        serverDefinitionViewer.getControl().setEnabled(!fromFile);
        if (fromFile) {
            updateServerDefinitionViewer(null);
        } else {
            updateServerDefinitionViewer(WfeServerProcessDefinitionImporter.getInstance().getData());
        }
    }

    private void updateServerDefinitionViewer(List<WfDefinition> definitions) {
        if (definitions != null) {
            DefinitionTreeNode treeDefinitions = createTree(definitions);
            serverDefinitionViewer.setInput(treeDefinitions);
        } else {
            serverDefinitionViewer.setInput(new Object());
        }
        serverDefinitionViewer.refresh(true);
    }

    private void createServerDefinitionsGroup(Composite parent) {
        serverDefinitionFilter = new Text(parent, SWT.SINGLE);   
        serverDefinitionFilter.addModifyListener(new ModifyListener( ) {

            @Override
            public void modifyText(ModifyEvent e) {
                serverDefinitionViewer.refresh();
            }});
        
        serverDefinitionViewer = new TreeViewer(parent);
        GridData gridData = new GridData(GridData.FILL_BOTH);
        gridData.heightHint = 100;
        serverDefinitionViewer.getControl().setLayoutData(gridData);
        serverDefinitionViewer.setContentProvider(new ViewContentProvider());
        serverDefinitionViewer.setLabelProvider(new ViewLabelProvider());
        serverDefinitionViewer.setInput(new Object());
        serverDefinitionViewer.addFilter(new ViewerFilter() {

            @Override
            public boolean select(Viewer viewer, Object parentElement, Object element) {  
                String searchText = serverDefinitionFilter.getText();
                if (searchText == null || searchText.trim().length() == 0) {
                    return true;
                }
                              
                if ((element instanceof DefinitionTreeNode) ) {
                    if (((DefinitionTreeNode) element).definition == null) {
                        // Always show nodes
                        return true;
                    }
                    
                    // filter leafs only
                    String name = ((DefinitionTreeNode) element).getLabel();
                    if (name.toLowerCase().contains(searchText.trim().toLowerCase())) {
                        return true;
                    }
                }
                return false;
            }});
    }

    public boolean performFinish() {
        List<ProcessDefinitionImportInfo> importInfos = Lists.newArrayList();
        try {
            IContainer selectedProject = getSelectedProject();
            if (selectedProject == null) {
                return false;
            }
            boolean fromFile = importFromFileButton.getSelection();
            if (fromFile) {
                if (selectedDirFileName == null) {
                    throw new Exception(Localization.getString("error.selectValidFile"));
                }
                for (int i = 0; i < selectedFileNames.length; i++) {
                    String definitionName = selectedFileNames[i].substring(0, selectedFileNames[i].length() - ".par".length());
                    String fileName = selectedDirFileName + File.separator + selectedFileNames[i];
                    importInfos.add(new ProcessDefinitionImportInfo(definitionName, "", new FileInputStream(fileName)));
                }
            } else {
                for (TreeItem treeItem : serverDefinitionViewer.getTree().getSelection()) {
                    DefinitionTreeNode treeNode = (DefinitionTreeNode) treeItem.getData();
                    importInfos.addAll(treeNode.toRecursiveImportInfo(""));
                }
            }
            if (importInfos.isEmpty()) {
                setErrorMessage(Localization.getString("ImportParWizardPage.error.selectValidDefinition"));
                return false;
            }
            for (ProcessDefinitionImportInfo importInfo : importInfos) {
                IFolder processFolder = IOUtils.getProcessFolder(selectedProject, importInfo.getFolderPath());
                if (processFolder.exists()) {
                    setErrorMessage(Localization.getString("ImportParWizardPage.error.processWithSameNameExists", importInfo.getFolderPath()));
                    return false;
                }
                IOUtils.createFolder(processFolder);
                IOUtils.extractArchiveToFolder(importInfo.inputStream, processFolder);
                IFile definitionFile = IOUtils.getProcessDefinitionFile(processFolder);
                ProcessDefinition definition = ProcessCache.newProcessDefinitionWasCreated(definitionFile);
                if (definition != null && !Objects.equal(definition.getName(), processFolder.getName())) {
                    // if par name differs from definition name
                    IPath destination = IOUtils.getProcessFolder(selectedProject, definition.getName()).getFullPath();
                    processFolder.move(destination, true, false, null);
                    processFolder = IOUtils.getProcessFolder(selectedProject, definition.getName());
                    IFile movedDefinitionFile = IOUtils.getProcessDefinitionFile(processFolder);
                    ProcessCache.newProcessDefinitionWasCreated(movedDefinitionFile);
                    ProcessCache.invalidateProcessDefinition(definitionFile);
                }
            }
        } catch (Exception exception) {
            PluginLogger.logErrorWithoutDialog("import par", exception);
            setErrorMessage(Throwables.getRootCause(exception).getMessage());
            return false;
        } finally {
            for (ProcessDefinitionImportInfo importInfo : importInfos) {
                try {
                    importInfo.inputStream.close();
                } catch (Exception e) {
                }
            }
        }
        return true;
    }

    private DefinitionTreeNode createTree(List<WfDefinition> definitions) {
        DefinitionTreeNode rootTreeNode = new DefinitionTreeNode("", "", null, false, false);
        for (WfDefinition definition : definitions) {
            rootTreeNode.addElementToTree(rootTreeNode.path, definition.getCategories(), definition);
        }
        return rootTreeNode;
    }

    class ViewLabelProvider extends LabelProvider {

        @Override
        public String getText(Object obj) {
            DefinitionTreeNode treeNode = (DefinitionTreeNode) obj;
            return treeNode.getLabel();
        }

        @Override
        public Image getImage(Object obj) {
            DefinitionTreeNode definitionNode = (DefinitionTreeNode) obj;
            return definitionNode.groupNode ? SharedImages.getImage("icons/project.gif") : SharedImages.getImage("icons/process.gif");
        }

    }

    class ViewContentProvider implements IStructuredContentProvider, ITreeContentProvider {

        @Override
        public void inputChanged(Viewer v, Object oldInput, Object newInput) {
        }

        @Override
        public void dispose() {
        }

        @Override
        public Object[] getElements(Object parent) {
            return getChildren(parent);
        }

        @Override
        public Object getParent(Object child) {
            if (child instanceof DefinitionTreeNode) {
                return ((DefinitionTreeNode) child).getChildren();
            }
            return null;
        }

        @Override
        public Object[] getChildren(Object parent) {
            if (parent instanceof DefinitionTreeNode) {
                return ((DefinitionTreeNode) parent).getChildren().toArray();
            }
            return new Object[0];
        }

        @Override
        public boolean hasChildren(Object parent) {
            if (parent instanceof DefinitionTreeNode) {
                return !((DefinitionTreeNode) parent).getChildren().isEmpty();
            }
            return false;
        }
    }

    class DefinitionTreeNode {
        private final String path;
        private final String label;
        private final WfDefinition definition;
        private final boolean groupNode;
        private final boolean historyNode;
        private final List<DefinitionTreeNode> children = new ArrayList<DefinitionTreeNode>();

        public DefinitionTreeNode(String parentPath, String label, WfDefinition definition, boolean groupNode, boolean historyNode) {
            this.path = parentPath + File.separator + label;
            this.label = label;
            this.definition = definition;
            this.groupNode = groupNode;
            this.historyNode = historyNode;
        }

        private String getLabel() {
            return label;
        }

        private boolean isHistoryNode() {
            return historyNode;
        }

        private boolean isGroupNode() {
            return groupNode;
        }

        private void addElementToTree(String path, String[] categories, WfDefinition definition) {
            if (categories.length == 0) {
                DefinitionTreeNode leafNode = new DefinitionTreeNode(path, definition.getName(), definition, false, false);
                if (WfeServerConnector.getInstance().getSettings().isLoadProcessDefinitionsHistory()) {
                    String label = Localization.getString("ImportParWizardPage.page.oldDefinitionVersions");
                    DefinitionTreeNode historyGroup = new DefinitionTreeNode(leafNode.path, label, definition, true, true);
                    leafNode.getChildren().add(historyGroup);
                }
                children.add(leafNode);
                return;
            }
            DefinitionTreeNode groupTreeNode = new DefinitionTreeNode(path, categories[0], null, true, false);
            int index = children.indexOf(groupTreeNode);
            if (index == -1) {
                // child in new group
                children.add(groupTreeNode);
            } else {
                // child in existed group
                groupTreeNode = children.get(index);
            }
            String[] newCategories = Arrays.copyOfRange(categories, 1, categories.length);
            groupTreeNode.addElementToTree(groupTreeNode.path, newCategories, definition);
        }

        @Override
        public boolean equals(Object obj) {
            if (obj instanceof DefinitionTreeNode) {
                return path.equals(((DefinitionTreeNode) obj).path);
            }
            return super.equals(obj);
        }

        @Override
        public String toString() {
            return label;
        }

        private List<DefinitionTreeNode> getChildren() {
            if (groupNode && historyNode && children.isEmpty()) {
                Shell shell = Display.getCurrent() != null ? Display.getCurrent().getActiveShell() : null;
                final ProgressMonitorDialog monitorDialog = new ProgressMonitorDialog(shell);
                monitorDialog.setCancelable(true);
                final IRunnableWithProgress runnable = new IRunnableWithProgress() {
                    @Override
                    public void run(IProgressMonitor monitor) throws InvocationTargetException, InterruptedException {
                        try {
                            monitor.beginTask(Localization.getString("task.LoadData"), 1);
                            List<WfDefinition> list = WfeServerConnector.getInstance().getProcessDefinitionHistory(definition);
                            if (list.isEmpty()) {
                                String label = Localization.getString("ImportParWizardPage.page.oldDefinitionVersions.empty");
                                DefinitionTreeNode historyDefinitionNode = new DefinitionTreeNode(path, label, null, false, true);
                                children.add(historyDefinitionNode);
                            }
                            for (WfDefinition definition : list) {
                                DefinitionTreeNode historyDefinitionNode = new DefinitionTreeNode(path, String.valueOf(definition.getVersion()),
                                        definition, false,
                                        true);
                                children.add(historyDefinitionNode);
                            }
                            monitor.done();
                        } catch (Exception e) {
                            PluginLogger.logErrorWithoutDialog("error.Synchronize", e);
                            throw new InvocationTargetException(e);
                        } finally {
                            monitor.done();
                        }
                    }
                };
                try {
                    monitorDialog.run(true, false, runnable);
                } catch (InvocationTargetException ex) {
                    Dialogs.error(Localization.getString("error.Synchronize"), ex.getTargetException());
                } catch (InterruptedException consumed) {
                }
            }
            return children;
        }

        private List<ProcessDefinitionImportInfo> toRecursiveImportInfo(String importPath) throws Exception {
            List<ProcessDefinitionImportInfo> result = Lists.newArrayList();
            if (isGroupNode() && isHistoryNode()) {
                return result;
            }
            if (isGroupNode()) {
                for (DefinitionTreeNode currentNode : children) {
                    if (currentNode != null) {
                        result.addAll(currentNode.toRecursiveImportInfo(importPath + File.separator + label));
                    }
                }
            } else if (definition != null) {
                result.add(toImportInfo(importPath));
            }
            return result;
        }

        private ProcessDefinitionImportInfo toImportInfo(String importPath) throws Exception {
            byte[] par = WfeServerProcessDefinitionImporter.getInstance().loadPar(definition);
            return new ProcessDefinitionImportInfo(definition.getName(), importPath, new ByteArrayInputStream(par));
        }

    }

    class ProcessDefinitionImportInfo {
        private final String name;
        private final String path;
        private final InputStream inputStream;

        public ProcessDefinitionImportInfo(String name, String path, InputStream inputStream) {
            this.name = name;
            this.path = path;
            this.inputStream = inputStream;
        }

        private String getFolderPath() {
            if (path.trim().isEmpty()) {
                return name;
            }
            return path + File.separator + name;
        }
    }
}
