package ru.runa.gpd.editor;

import com.google.common.base.Charsets;
import java.beans.PropertyChangeEvent;
import java.io.ByteArrayInputStream;
import java.util.List;
import org.eclipse.core.internal.resources.Folder;
import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IResource;
import org.eclipse.gef.ui.actions.Clipboard;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.ITableLabelProvider;
import org.eclipse.jface.viewers.LabelProvider;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.ltk.ui.refactoring.RefactoringWizardOpenOperation;
import org.eclipse.search.ui.NewSearchUI;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.SashForm;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.ide.IDE;
import ru.runa.gpd.Localization;
import ru.runa.gpd.ProcessCache;
import ru.runa.gpd.PropertyNames;
import ru.runa.gpd.editor.gef.command.ProcessDefinitionRemoveSwimlaneCommand;
import ru.runa.gpd.extension.DelegableProvider;
import ru.runa.gpd.extension.HandlerRegistry;
import ru.runa.gpd.lang.NodeRegistry;
import ru.runa.gpd.lang.model.FormNode;
import ru.runa.gpd.lang.model.ProcessDefinition;
import ru.runa.gpd.lang.model.GlobalSectionDefinition;
import ru.runa.gpd.lang.model.SubprocessDefinition;
import ru.runa.gpd.lang.model.Swimlane;
import ru.runa.gpd.lang.model.SwimlanedNode;
import ru.runa.gpd.lang.par.ParContentProvider;
import ru.runa.gpd.ltk.RenameRefactoringWizard;
import ru.runa.gpd.ltk.RenameVariableRefactoring;
import ru.runa.gpd.search.ElementMatch;
import ru.runa.gpd.search.SearchResult;
import ru.runa.gpd.search.VariableSearchQuery;
import ru.runa.gpd.ui.custom.Dialogs;
import ru.runa.gpd.ui.custom.DragAndDropAdapter;
import ru.runa.gpd.ui.custom.LoggingSelectionAdapter;
import ru.runa.gpd.ui.custom.TableViewerLocalDragAndDropSupport;
import ru.runa.gpd.ui.dialog.UpdateSwimlaneNameDialog;
import ru.runa.gpd.util.IOUtils;
import ru.runa.gpd.util.SwimlaneDisplayMode;
import ru.runa.gpd.util.WorkspaceOperations;

public class SwimlaneEditorPage extends EditorPartBase<Swimlane> {

    private TableViewer tableViewer;
    private Button createButton;
    private Button searchButton;
    private Button moveUpButton;
    private Button moveDownButton;
    private Button renameButton;
    private Button changeButton;
    private Button deleteButton;
    private Button copyButton;
    private Button pasteButton;
    private final boolean swimlanesCreateDeleteEnabled;

    public SwimlaneEditorPage(ProcessEditorBase editor) {
        super(editor);
        swimlanesCreateDeleteEnabled = editor.getDefinition().getSwimlaneDisplayMode() == SwimlaneDisplayMode.none;
    }

    @Override
    public void createPartControl(Composite parent) {
        SashForm sashForm = createSashForm(parent, SWT.VERTICAL, "DesignerSwimlaneEditorPage.label.swimlanes");

        Composite allSwimlanesComposite = createSection(sashForm, "DesignerSwimlaneEditorPage.label.all_swimlanes");

        tableViewer = createMainViewer(allSwimlanesComposite, SWT.MULTI | SWT.BORDER | SWT.V_SCROLL | SWT.FULL_SELECTION);
        tableViewer.setLabelProvider(new TableViewerLabelProvider());
        TableViewerLocalDragAndDropSupport.enable(tableViewer, new DragAndDropAdapter<Swimlane>() {

            @Override
            public void onDropElement(Swimlane beforeElement, Swimlane swimlane) {
                editor.getDefinition().changeChildIndex(swimlane, beforeElement);
            }
        });

        createTable(tableViewer, new DataViewerComparator<>(new ValueComparator<Swimlane>() {
            @Override
            public int compare(Swimlane o1, Swimlane o2) {
                int result = 0;
                switch (getColumn()) {
                case 0:
                    result = o1.getName().compareTo(o2.getName());
                    break;
                }
                return result;
            }
        }), new TableColumnDescription("property.name", 200, SWT.LEFT), new TableColumnDescription("swimlane.initializer", 400, SWT.LEFT));

        Composite buttonsBar = createActionBar(allSwimlanesComposite);
        createButton = addButton(buttonsBar, "button.create", new CreateSwimlaneSelectionListener(), false);
        createButton.setEnabled(swimlanesCreateDeleteEnabled);
        renameButton = addButton(buttonsBar, "button.rename", new RenameSwimlaneSelectionListener(), true);
        changeButton = addButton(buttonsBar, "button.change", new ChangeSwimlaneSelectionListener(), true);
        copyButton = addButton(buttonsBar, "button.copy", new CopySwimlaneSelectionListener(), true);
        pasteButton = addButton(buttonsBar, "button.paste", new PasteSwimlaneSelectionListener(), true);
        searchButton = addButton(buttonsBar, "button.search", new SearchSwimlaneUsageSelectionListener(), true);
        moveUpButton = addButton(buttonsBar, "button.up", new MoveSwimlaneSelectionListener(true), true);
        moveDownButton = addButton(buttonsBar, "button.down", new MoveSwimlaneSelectionListener(false), true);
        deleteButton = addButton(buttonsBar, "button.delete", new RemoveSwimlaneSelectionListener(), true);

        updateViewer();
    }

    @Override
    public void dispose() {
        for (Swimlane swimlane : getDefinition().getSwimlanes()) {
            swimlane.removePropertyChangeListener(this);
        }
        super.dispose();
    }

    @Override
    public void propertyChange(PropertyChangeEvent event) {
        String type = event.getPropertyName();
        if (PropertyNames.PROPERTY_CHILDREN_CHANGED.equals(type)) {
            updateViewer();
        } else if (event.getSource() instanceof Swimlane) {
            if (PropertyNames.PROPERTY_NAME.equals(type) || PropertyNames.PROPERTY_CONFIGURATION.equals(type)) {
                tableViewer.refresh(event.getSource());
            }
        }
    }

    @Override
    protected void updateUI() {
    	updateViewer();
    }

    private boolean withoutGlobals(List<Swimlane> list) {
        for (Swimlane swimlane : list) {
            if (swimlane.isGlobal()) {
                return false;
            }
        }
        return true;
    }

    private void updateViewer() {
        List<Swimlane> swimlane_list = getDefinition().getSwimlanes();
        tableViewer.setInput(swimlane_list);
        for (Swimlane swimlane : swimlane_list) {
            swimlane.addPropertyChangeListener(this);
        }
        List<Swimlane> swimlanes = (List<Swimlane>) tableViewer.getInput();
        List<Swimlane> selected = ((IStructuredSelection) tableViewer.getSelection()).toList();
        boolean withoutGlobals = withoutGlobals(selected);
        enableAction(searchButton, withoutGlobals && selected.size() == 1);
        enableAction(changeButton, withoutGlobals && selected.size() == 1);
        enableAction(moveUpButton, withoutGlobals && selected.size() == 1 && swimlanes.indexOf(selected.get(0)) > 0);
        enableAction(moveDownButton, withoutGlobals && selected.size() == 1 && swimlanes.indexOf(selected.get(0)) < swimlanes.size() - 1);
        enableAction(deleteButton, withoutGlobals && swimlanesCreateDeleteEnabled && selected.size() > 0);
        enableAction(renameButton, withoutGlobals && selected.size() == 1);
        enableAction(copyButton, withoutGlobals && selected.size() > 0);
        boolean pasteEnabled = false;
        if (Clipboard.getDefault().getContents() instanceof List) {
            List<?> list = (List<?>) Clipboard.getDefault().getContents();
            if (list.size() > 0 && list.get(0) instanceof Swimlane) {
                pasteEnabled = true;
            }
        }
        enableAction(pasteButton, swimlanesCreateDeleteEnabled && pasteEnabled);
    }

    private void delete(Swimlane swimlane) throws Exception {
        boolean confirmationRequired = false;
        StringBuffer confirmationInfo = new StringBuffer();
        List<FormNode> nodesWithVar = ParContentProvider.getFormsWhereVariableUsed(editor.getDefinitionFile(), getDefinition(), swimlane.getName());
        if (nodesWithVar.size() > 0) {
            confirmationInfo.append(Localization.getString("Swimlane.ExistInForms")).append("\n");
            for (FormNode node : nodesWithVar) {
                confirmationInfo.append(" - ").append(node.getName()).append("\n");
            }
            confirmationInfo.append(Localization.getString("Variable.WillBeRemovedFromFormAuto")).append("\n\n");
            confirmationRequired = true;
        }
        VariableSearchQuery query = new VariableSearchQuery(editor.getDefinitionFile(), getDefinition(), swimlane);
        NewSearchUI.runQueryInForeground(PlatformUI.getWorkbench().getActiveWorkbenchWindow(), query);
        SearchResult searchResult = query.getSearchResult();
        if (searchResult.getMatchCount() > 0) {
            confirmationInfo.append(Localization.getString("Swimlane.ExistInProcess")).append("\n");
            for (Object element : searchResult.getElements()) {
                confirmationInfo.append(" - ").append(element instanceof ElementMatch ? ((ElementMatch) element).toString(searchResult) : element)
                        .append("\n");
            }
            confirmationRequired = true;
        }
        if (!confirmationRequired || Dialogs.confirm(Localization.getString("confirm.delete"), confirmationInfo.toString())) {
            // clear swimlanes
            ProcessDefinition mainProcessDefinition = getDefinition().getMainProcessDefinition();
            for (SwimlanedNode node : mainProcessDefinition.getChildren(SwimlanedNode.class)) {
                if (swimlane.getName().equals(node.getSwimlaneName())) {
                    node.setSwimlane(null);
                }
            }
            for (SubprocessDefinition subprocessDefinition : mainProcessDefinition.getEmbeddedSubprocesses().values()) {
                for (SwimlanedNode node : subprocessDefinition.getChildren(SwimlanedNode.class)) {
                    if (swimlane.getName().equals(node.getSwimlaneName())) {
                        node.setSwimlane(null);
                    }
                }
            }
            // TODO remove variable from form validations in
            // EmbeddedSubprocesses
            ParContentProvider.rewriteFormValidationsRemoveVariable(editor.getDefinitionFile(), nodesWithVar, swimlane.getName());
            ProcessDefinitionRemoveSwimlaneCommand command = new ProcessDefinitionRemoveSwimlaneCommand();
            command.setProcessDefinition(getDefinition());
            command.setSwimlane(swimlane);
            // TODO Ctrl+Z support (form validation)
            // editor.getCommandStack().execute(command);
            command.execute();
            if (editor.getPartName().startsWith(".")) { // globals
                replaceAllReferences(swimlane.getName(), null, null);
            }
        }
    }

    private class MoveSwimlaneSelectionListener extends SelectionAdapter {
        private final boolean up;

        public MoveSwimlaneSelectionListener(boolean up) {
            this.up = up;
        }

        @Override
        public void widgetSelected(SelectionEvent e) {
            IStructuredSelection selection = (IStructuredSelection) tableViewer.getSelection();
            Swimlane swimlane = (Swimlane) selection.getFirstElement();
            List<Swimlane> children = swimlane.getParent().getChildren(Swimlane.class);
            int index = children.indexOf(swimlane);
            swimlane.getParent().swapChildren(swimlane, up ? children.get(index - 1) : children.get(index + 1));
            tableViewer.setSelection(selection);
        }
    }

    private class SearchSwimlaneUsageSelectionListener extends LoggingSelectionAdapter {
        @Override
        protected void onSelection(SelectionEvent e) throws Exception {
            IStructuredSelection selection = (IStructuredSelection) tableViewer.getSelection();
            Swimlane swimlane = (Swimlane) selection.getFirstElement();
            VariableSearchQuery query = new VariableSearchQuery(editor.getDefinitionFile(), getDefinition(), swimlane);
            NewSearchUI.runQueryInBackground(query);
        }
    }

    private class RenameSwimlaneSelectionListener extends LoggingSelectionAdapter {
        @Override
        protected void onSelection(SelectionEvent e) throws Exception {
            IStructuredSelection selection = (IStructuredSelection) tableViewer.getSelection();
            Swimlane swimlane = (Swimlane) selection.getFirstElement();
            UpdateSwimlaneNameDialog renameDialog = new UpdateSwimlaneNameDialog(swimlane.getProcessDefinition(), swimlane);
            int result = renameDialog.open();
            String newName = renameDialog.getName();
            boolean useLtk = renameDialog.isProceedRefactoring();
            if (result != IDialogConstants.OK_ID) {
                return;
            }
            IResource projectRoot = editor.getDefinitionFile().getParent();
            IDE.saveAllEditors(new IResource[] { projectRoot }, false);
            String newScriptingName = renameDialog.getScriptingName();
            if (useLtk) {
                RenameVariableRefactoring ref = new RenameVariableRefactoring(editor.getDefinitionFile(), editor.getDefinition(), swimlane, newName,
                        newScriptingName);
                useLtk &= ref.isUserInteractionNeeded();
                if (useLtk) {
                    RenameRefactoringWizard wizard = new RenameRefactoringWizard(ref);
                    wizard.setDefaultPageTitle(Localization.getString("Refactoring.variable.name"));
                    RefactoringWizardOpenOperation op = new RefactoringWizardOpenOperation(wizard);
                    result = op.run(Display.getCurrent().getActiveShell(), "");
                    if (result != IDialogConstants.OK_ID) {
                        return;
                    }
                }
            }
            String oldName = swimlane.getName();
            // update name
            swimlane.setName(newName);
            swimlane.setScriptingName(newScriptingName);
            if (useLtk && editor.getDefinition().getEmbeddedSubprocesses().size() > 0) {
                IDE.saveAllEditors(new IResource[] { projectRoot }, false);
                for (SubprocessDefinition subprocessDefinition : editor.getDefinition().getEmbeddedSubprocesses().values()) {
                    WorkspaceOperations.saveProcessDefinition(subprocessDefinition);
                }
            }
            if (editor.getPartName().startsWith(".")) { // globals
                replaceAllReferences(oldName, swimlane.getName(), null);
            }
        }
    }

    private void replaceAllReferences(String oldName, String newName, IContainer parent) throws Exception {
        if (parent == null) {
            parent = editor.getDefinitionFile().getParent().getParent();
        }
        for (IResource resource : parent.members()) {
            if (resource instanceof Folder) {
                IFile processDefinitionFile = ((Folder) resource).getFile(ParContentProvider.PROCESS_DEFINITION_FILE_NAME);
                if (processDefinitionFile.exists()) {
                    if (!resource.getName().startsWith(".")) {
                        String content = IOUtils.readStream(processDefinitionFile.getContents());
                        String oldReference = IOUtils.GLOBAL_ROLE_REF_PREFIX + oldName;
                        if (content.contains(oldReference)) {
                            content = content.replaceAll(oldReference, newName == null ? "" : IOUtils.GLOBAL_ROLE_REF_PREFIX + newName);
                            processDefinitionFile.setContents(new ByteArrayInputStream(content.getBytes(Charsets.UTF_8)), true, true, null);
                            ProcessCache.invalidateProcessDefinition(processDefinitionFile);
                        }
                    }
                } else {
                    replaceAllReferences(oldName, newName, (Folder) resource);
                }
            }
        }
    }

    private class RemoveSwimlaneSelectionListener extends LoggingSelectionAdapter {
        @Override
        protected void onSelection(SelectionEvent e) throws Exception {
            IStructuredSelection selection = (IStructuredSelection) tableViewer.getSelection();
            @SuppressWarnings("unchecked")
            List<Swimlane> swimlanes = selection.toList();
            for (Swimlane swimlane : swimlanes) {
            	if (getDefinition() instanceof GlobalSectionDefinition) {
            		((GlobalSectionDefinition)getDefinition()).removeGlobalSwimlaneInAllProcess(swimlane, getDefinition().getFile().getParent().getParent());
            	}
                delete(swimlane);
            }
        }
    }

    private class CreateSwimlaneSelectionListener extends LoggingSelectionAdapter {
        @Override
        protected void onSelection(SelectionEvent e) throws Exception {
            UpdateSwimlaneNameDialog dialog = new UpdateSwimlaneNameDialog(getDefinition(), null);
            if (dialog.open() == IDialogConstants.OK_ID) {
                Swimlane newSwimlane = NodeRegistry.getNodeTypeDefinition(Swimlane.class).createElement(getDefinition(), false);
                newSwimlane.setName(dialog.getName());
                newSwimlane.setScriptingName(dialog.getScriptingName());
                getDefinition().addChild(newSwimlane);
                tableViewer.setSelection(new StructuredSelection(newSwimlane));
            }
        }
    }

    private class ChangeSwimlaneSelectionListener extends LoggingSelectionAdapter {
        @Override
        protected void onSelection(SelectionEvent e) throws Exception {
            IStructuredSelection selection = (IStructuredSelection) tableViewer.getSelection();
            Swimlane swimlane = (Swimlane) selection.getFirstElement();
            DelegableProvider provider = HandlerRegistry.getProvider(swimlane.getDelegationClassName());
            String configuration = provider.showConfigurationDialog(swimlane);
            if (configuration != null) {
                swimlane.setDelegationConfiguration(configuration);
                tableViewer.setSelection(selection);
            }
        }
    }

    private class CopySwimlaneSelectionListener extends LoggingSelectionAdapter {
        @Override
        protected void onSelection(SelectionEvent e) throws Exception {
            IStructuredSelection selection = (IStructuredSelection) tableViewer.getSelection();
            Clipboard.getDefault().setContents(selection.toList());
        }
    }

    private class PasteSwimlaneSelectionListener extends LoggingSelectionAdapter {
        @Override
        protected void onSelection(SelectionEvent e) throws Exception {
            @SuppressWarnings("unchecked")
            List<Swimlane> newSwimlanes = (List<Swimlane>) Clipboard.getDefault().getContents();
            for (Swimlane swimlane : newSwimlanes) {
                boolean add = false;
                Swimlane newSwimlane = getDefinition().getSwimlaneByName(swimlane.getName());
                if (newSwimlane == null) {
                    newSwimlane = NodeRegistry.getNodeTypeDefinition(Swimlane.class).createElement(getDefinition(), false);
                    newSwimlane.setName(swimlane.getName());
                    newSwimlane.setScriptingName(swimlane.getScriptingName());
                    if (newSwimlane.getName() == null) {
                        // variable of that name already exists
                        continue;
                    }
                    add = true;
                }
                newSwimlane.setDelegationClassName(swimlane.getDelegationClassName());
                newSwimlane.setDelegationConfiguration(swimlane.getDelegationConfiguration());
                if (add) {
                    getDefinition().addChild(newSwimlane);
                }
            }
        }
    }

    private static class TableViewerLabelProvider extends LabelProvider implements ITableLabelProvider {
        @Override
        public String getColumnText(Object element, int index) {
            Swimlane swimlane = (Swimlane) element;
            switch (index) {
            case 0:
                return swimlane.getName();
            case 1:
                return swimlane.getDelegationConfiguration();
            default:
                return "unknown " + index;
            }
        }

        @Override
        public String getText(Object element) {
            Swimlane swimlane = (Swimlane) element;
            return swimlane.getName();
        }

        @Override
        public Image getColumnImage(Object element, int columnIndex) {
            return null;
        }
    }
}
