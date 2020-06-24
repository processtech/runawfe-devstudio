package ru.runa.gpd.ui.wizard;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.runtime.IPath;
import org.eclipse.jface.viewers.IStructuredContentProvider;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.ITreeContentProvider;
import org.eclipse.jface.viewers.LabelProvider;
import org.eclipse.jface.viewers.TreeViewer;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.SashForm;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.FileDialog;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Text;
import org.eclipse.swt.widgets.TreeItem;

import ru.runa.gpd.Localization;
import ru.runa.gpd.PluginLogger;
import ru.runa.gpd.ProcessCache;
import ru.runa.gpd.SharedImages;
import ru.runa.gpd.lang.model.ProcessDefinition;
import ru.runa.gpd.settings.WFEConnectionPreferencePage;
import ru.runa.gpd.ui.custom.Dialogs;
import ru.runa.gpd.ui.custom.SyncUIHelper;
import ru.runa.gpd.util.IOUtils;
import ru.runa.gpd.wfe.ConnectorCallback;
import ru.runa.gpd.wfe.WFEServerProcessDefinitionImporter;
import ru.runa.wfe.definition.dto.WfDefinition;

import com.google.common.base.Objects;
import com.google.common.base.Throwables;
import com.google.common.collect.Lists;

public class ImportGlbWizardPage extends ImportWizardPage {
    private Button importFromFileButton;
    private Composite fileSelectionArea;
    private Text selectedParsLabel;
    private Button selectGlbButton;
    private TreeViewer serverDefinitionViewer;
    private String selectedDirFileName;
    private String[] selectedFileNames;

    public ImportGlbWizardPage(String pageName, IStructuredSelection selection) {
        super(pageName, selection);
        setTitle(Localization.getString("ImportGlbWizardPage.page.title"));
        setDescription(Localization.getString("ImportGlbWizardPage.page.description"));
    }

    @Override
    public void createControl(Composite parent) {
        Composite pageControl = new Composite(parent, SWT.NONE);
        pageControl.setLayout(new GridLayout(1, false));
        pageControl.setLayoutData(new GridData(GridData.FILL_BOTH));
        SashForm sashForm = new SashForm(pageControl, SWT.HORIZONTAL);
        sashForm.setLayoutData(new GridData(GridData.FILL_BOTH));
        createProjectsGroup(sashForm);
        Group importGroup = new Group(sashForm, SWT.NONE);
        importGroup.setLayout(new GridLayout(1, false));
        importGroup.setLayoutData(new GridData(GridData.FILL_BOTH));
        importFromFileButton = new Button(importGroup, SWT.RADIO);
        importFromFileButton.setText(Localization.getString("ImportParWizardPage.page.importFromFileButton"));
        importFromFileButton.setSelection(true);
        importFromFileButton.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                setImportMode();
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
        selectedParsLabel = new Text(fileSelectionArea, SWT.MULTI | SWT.READ_ONLY | SWT.V_SCROLL);
        GridData gridData = new GridData(GridData.GRAB_HORIZONTAL | GridData.FILL_HORIZONTAL);
        gridData.heightHint = 30;
        selectedParsLabel.setLayoutData(gridData);
        selectGlbButton = new Button(fileSelectionArea, SWT.PUSH);
        selectGlbButton.setText(Localization.getString("button.choose"));
        selectGlbButton.setLayoutData(new GridData(GridData.VERTICAL_ALIGN_BEGINNING | GridData.HORIZONTAL_ALIGN_END));
        selectGlbButton.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                FileDialog dialog = new FileDialog(getShell(), SWT.OPEN | SWT.MULTI);
                dialog.setFilterExtensions(new String[] { "*.glb" });
                if (dialog.open() != null) {
                    selectedDirFileName = dialog.getFilterPath();
                    selectedFileNames = dialog.getFileNames();
                    String text = "";
                    for (String fileName : selectedFileNames) {
                        text += fileName + "\n";
                    }
                    selectedParsLabel.setText(text);
                }
            }
        });
        SyncUIHelper.createHeader(importGroup, WFEServerProcessDefinitionImporter.getInstance(), WFEConnectionPreferencePage.class,
                new ConnectorCallback() {

                    @Override
                    public void onSynchronizationFailed(Exception e) {
                        Dialogs.error(Localization.getString("error.Synchronize"), e);
                    }

                    @Override
                    public void onSynchronizationCompleted() {
                        setupServerDefinitionViewer();
                    }
                });
        createServerDefinitionsGroup(importGroup);
        setControl(pageControl);
    }

    private void setImportMode() {
        boolean fromFile = importFromFileButton.getSelection();
        selectGlbButton.setEnabled(fromFile);
        if (fromFile) {
            serverDefinitionViewer.setInput(new Object());
        } else {
            if (WFEServerProcessDefinitionImporter.getInstance().isConfigured()) {
                if (!WFEServerProcessDefinitionImporter.getInstance().hasCachedData()) {
                    long start = System.currentTimeMillis();
                    WFEServerProcessDefinitionImporter.getInstance().synchronize();
                    long end = System.currentTimeMillis();
                    PluginLogger.logInfo("def sync [sec]: " + ((end - start) / 1000));
                }
                setupServerDefinitionViewer();
            }
        }
    }

    private void setupServerDefinitionViewer() {
        Map<WfDefinition, List<WfDefinition>> definitions = WFEServerProcessDefinitionImporter.getInstance().loadCachedData();
        DefinitionTreeNode treeDefinitions = createTree(new TreeMap<>(definitions));
        serverDefinitionViewer.setInput(treeDefinitions);
        serverDefinitionViewer.refresh(true);
    }

    private void createServerDefinitionsGroup(Composite parent) {
        serverDefinitionViewer = new TreeViewer(parent);
        GridData gridData = new GridData(GridData.FILL_BOTH);
        gridData.heightHint = 100;
        serverDefinitionViewer.getControl().setLayoutData(gridData);
        serverDefinitionViewer.setContentProvider(new ViewContentProvider());
        serverDefinitionViewer.setLabelProvider(new ViewLabelProvider());
        serverDefinitionViewer.setInput(new Object());
    }

    public boolean performFinish() {
        List<ProcessDefinitionImportInfo> importInfos = Lists.newArrayList();
        try {
            IContainer container = getSelectedContainer();
            boolean fromFile = importFromFileButton.getSelection();
            if (fromFile) {
                if (selectedDirFileName == null) {
                    throw new Exception(Localization.getString("ImportParWizardPage.error.selectValidPar"));
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
                throw new Exception(Localization.getString("ImportParWizardPage.error.selectValidDefinition"));
            }
            for (ProcessDefinitionImportInfo importInfo : importInfos) {
                IFolder processFolder = IOUtils.getProcessFolder(container, importInfo.getFolderPath());
                if (processFolder.exists()) {
                    throw new Exception(Localization.getString("ImportParWizardPage.error.processWithSameNameExists", importInfo.getFolderPath()));
                }
                IOUtils.createFolder(processFolder);
                IOUtils.extractArchiveToFolder(importInfo.inputStream, processFolder);
                IFile definitionFile = IOUtils.getProcessDefinitionFile(processFolder);
                ProcessDefinition definition = ProcessCache.newProcessDefinitionWasCreated(definitionFile);
                if (definition != null && !Objects.equal(definition.getName(), processFolder.getName())) {
                    // if par name differs from definition name
                    IPath destination = IOUtils.getProcessFolder(container, definition.getName()).getFullPath();
                    processFolder.move(destination, true, false, null);
                    processFolder = IOUtils.getProcessFolder(container, definition.getName());
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

    private DefinitionTreeNode createTree(Map<WfDefinition, List<WfDefinition>> definitions) {
        DefinitionTreeNode rootTreeNode = new DefinitionTreeNode("", "", null, false, false);
        for (Map.Entry<WfDefinition, List<WfDefinition>> entry : definitions.entrySet()) {
            WfDefinition definition = entry.getKey();
            List<WfDefinition> historyDefinitions = entry.getValue();
            rootTreeNode.addElementToTree(rootTreeNode.path, definition.getCategories(), definition, historyDefinitions);
        }
        return rootTreeNode;
    }

    class ViewLabelProvider extends LabelProvider {

        @Override
        public String getText(Object obj) {
            DefinitionTreeNode treeNode = (DefinitionTreeNode) obj;
            String label = treeNode.getName();
            if (treeNode.isHistoryNode() && !treeNode.isGroupNode()) {
                label = treeNode.getDefinition().getVersion().toString();
            }
            return label;
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
        private final String name;
        private final WfDefinition definition;
        private final boolean groupNode;
        private final boolean historyNode;
        private final List<DefinitionTreeNode> children = new ArrayList<DefinitionTreeNode>();

        public DefinitionTreeNode(String parentPath, String name, WfDefinition definition, boolean groupNode, boolean historyNode) {
            this.path = parentPath + File.separator + name;
            this.name = name;
            this.definition = definition;
            this.groupNode = groupNode;
            this.historyNode = historyNode;
        }

        private String getName() {
            return name;
        }

        private boolean isHistoryNode() {
            return historyNode;
        }

        private boolean isGroupNode() {
            return groupNode;
        }

        private WfDefinition getDefinition() {
            return definition;
        }

        private void addElementToTree(String path, String[] categories, WfDefinition definition, List<WfDefinition> historyDefinitions) {
            if (categories.length == 0) {
                DefinitionTreeNode leafNode = new DefinitionTreeNode(path, definition.getName(), definition, false, false);
                if (!historyDefinitions.isEmpty()) {
                    leafNode.getChildren().add(createHistoryGroup(leafNode.path, historyDefinitions));
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
            groupTreeNode.addElementToTree(groupTreeNode.path, newCategories, definition, historyDefinitions);
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
            return name;
        }

        private List<DefinitionTreeNode> getChildren() {
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
                        result.addAll(currentNode.toRecursiveImportInfo(importPath + File.separator + name));
                    }
                }
            } else {
                result.add(toImportInfo(importPath));
            }
            return result;
        }

        private ProcessDefinitionImportInfo toImportInfo(String importPath) throws Exception {
            byte[] par = WFEServerProcessDefinitionImporter.getInstance().loadPar(definition);
            return new ProcessDefinitionImportInfo(definition.getName(), importPath, new ByteArrayInputStream(par));
        }

        private DefinitionTreeNode createHistoryGroup(String path, List<WfDefinition> historyDefinitions) {
            DefinitionTreeNode historyGroup = null;
            if (historyDefinitions != null && !historyDefinitions.isEmpty()) {
                String oldDefinitionVersionsLabel = Localization.getString("ImportParWizardPage.page.oldDefinitionVersions");
                historyGroup = new DefinitionTreeNode(path, oldDefinitionVersionsLabel, null, true, true);
                for (WfDefinition historyDefinition : historyDefinitions) {
                    String name = historyDefinition.getName();
                    DefinitionTreeNode historyDefinitionNode = new DefinitionTreeNode(historyGroup.path, name, historyDefinition, false, true);
                    historyGroup.getChildren().add(historyDefinitionNode);
                }
            }
            return historyGroup;
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
