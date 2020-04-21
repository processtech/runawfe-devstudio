package ru.runa.gpd.editor;

import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.base.Objects;
import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import java.beans.PropertyChangeEvent;
import java.util.List;
import java.util.Map;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IResource;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.ITableLabelProvider;
import org.eclipse.jface.viewers.LabelProvider;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.jface.window.Window;
import org.eclipse.ltk.core.refactoring.Change;
import org.eclipse.ltk.ui.refactoring.RefactoringWizardOpenOperation;
import org.eclipse.search.ui.NewSearchUI;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.SashForm;
import org.eclipse.swt.dnd.Clipboard;
import org.eclipse.swt.dnd.Transfer;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.ide.IDE;
import org.eclipse.ui.part.FileEditorInput;
import ru.runa.gpd.Localization;
import ru.runa.gpd.PropertyNames;
import ru.runa.gpd.editor.clipboard.VariableTransfer;
import ru.runa.gpd.editor.clipboard.VariableUserTypeTransfer;
import ru.runa.gpd.lang.NodeRegistry;
import ru.runa.gpd.lang.model.FormNode;
import ru.runa.gpd.lang.model.NamedGraphElement;
import ru.runa.gpd.lang.model.SubprocessDefinition;
import ru.runa.gpd.lang.model.Variable;
import ru.runa.gpd.lang.model.VariableUserType;
import ru.runa.gpd.lang.par.ParContentProvider;
import ru.runa.gpd.ltk.MoveUserTypeAttributeRefactoring;
import ru.runa.gpd.ltk.RenameRefactoringWizard;
import ru.runa.gpd.ltk.RenameUserTypeAttributeRefactoring;
import ru.runa.gpd.search.ElementMatch;
import ru.runa.gpd.search.MultiVariableSearchQuery;
import ru.runa.gpd.ui.custom.DragAndDropAdapter;
import ru.runa.gpd.ui.custom.LoggingSelectionAdapter;
import ru.runa.gpd.ui.custom.TableViewerLocalDragAndDropSupport;
import ru.runa.gpd.ui.dialog.ChooseUserTypeDialog;
import ru.runa.gpd.ui.dialog.ChooseVariableDialog;
import ru.runa.gpd.ui.dialog.ErrorDialog;
import ru.runa.gpd.ui.dialog.UpdateVariableNameDialog;
import ru.runa.gpd.ui.dialog.VariableUserTypeDialog;
import ru.runa.gpd.ui.wizard.CompactWizardDialog;
import ru.runa.gpd.ui.wizard.VariableWizard;
import ru.runa.gpd.util.VariableUtils;
import ru.runa.gpd.util.WorkspaceOperations;

public class VariableTypeEditorPage extends EditorPartBase<VariableUserType> {

    private TableViewer typeTableViewer;
    private Button renameTypeButton;
    private Button moveUpTypeButton;
    private Button moveDownTypeButton;
    private Button deleteTypeButton;

    private TableViewer attributeTableViewer;
    private Button createAttributeButton;
    private Button changeAttributeButton;
    private Button searchAttributeButton;
    private Button renameAttributeButton;
    private Button mergeAttributesButton;
    private Button moveUpAttributeButton;
    private Button moveDownAttributeButton;
    private Button deleteAttributeButton;
    private Button moveToTypeAttributeButton;

    public VariableTypeEditorPage(EditorBase editor) {
        super(editor);
    }

    @Override
    public void createPartControl(Composite parent) {
        SashForm sashForm = createSashForm(parent, SWT.HORIZONTAL, "VariableUserType.collection.desc");

        Composite leftComposite = createSection(sashForm, "VariableUserType.collection");

        typeTableViewer = createMainViewer(leftComposite, SWT.BORDER | SWT.V_SCROLL | SWT.FULL_SELECTION);
        typeTableViewer.setLabelProvider(new TypeLabelProvider());
        TableViewerLocalDragAndDropSupport.enable(typeTableViewer, new DragAndDropAdapter<VariableUserType>() {

            @Override
            public void onDropElement(VariableUserType beforeElement, VariableUserType element) {
                int index = getDefinition().getVariableUserTypes().indexOf(beforeElement);
                getDefinition().changeVariableUserTypePosition(element, index);
            }
        });

        createTable(typeTableViewer, new DataViewerComparator<>(new ValueComparator<VariableUserType>() {
            @Override
            public int compare(VariableUserType o1, VariableUserType o2) {
                int result = 0;
                if (getColumn() == 0) {
                    result = o1.getName().compareTo(o2.getName());
                }
                return result;
            }
        }), new TableColumnDescription("property.name", 200, SWT.LEFT));

        Composite typeButtonsBar = createActionBar(leftComposite);
        addButton(typeButtonsBar, "button.create", new CreateTypeSelectionListener(), false);
        renameTypeButton = addButton(typeButtonsBar, "button.rename", new RenameTypeSelectionListener(), true);
        moveUpTypeButton = addButton(typeButtonsBar, "button.up", new MoveTypeSelectionListener(true), true);
        moveDownTypeButton = addButton(typeButtonsBar, "button.down", new MoveTypeSelectionListener(false), true);
        deleteTypeButton = addButton(typeButtonsBar, "button.delete", new RemoveTypeSelectionListener(), true);
        addButton(typeButtonsBar, "button.copy", new CopyTypeSelectionListener(), true);
        addButton(typeButtonsBar, "button.paste", new PasteTypeSelectionListener(), true);

        Composite rightComposite = createSection(sashForm, "VariableUserType.attributes");
        rightComposite.setLayoutData(new GridData(GridData.FILL_BOTH));

        attributeTableViewer = createTableViewer(rightComposite, SWT.BORDER | SWT.V_SCROLL | SWT.FULL_SELECTION | SWT.MULTI);
        attributeTableViewer.setLabelProvider(new AttributeLabelProvider());
        TableViewerLocalDragAndDropSupport.enable(attributeTableViewer, new DragAndDropAdapter<Variable>() {
            @Override
            public void onDropElement(Variable beforeElement, Variable element) {
                VariableUserType userType = getSelection();
                List<Variable> attributes = userType.getAttributes();
                userType.changeAttributePosition(element, attributes.indexOf(beforeElement));
            }
        });

        createTable(attributeTableViewer, new DataViewerComparator<>(new ValueComparator<Variable>() {
            @Override
            public int compare(Variable o1, Variable o2) {
                int result = 0;
                switch (getColumn()) {
                case 0:
                    result = o1.getName().compareTo(o2.getName());
                    break;
                case 1:
                    result = o1.getFormatLabel().compareTo(o2.getFormatLabel());
                    break;
                }
                return result;
            }
        }), new TableColumnDescription("property.name", 200, SWT.LEFT), new TableColumnDescription("Variable.property.format", 200, SWT.LEFT),
                new TableColumnDescription("Variable.property.defaultValue", 200, SWT.LEFT, false));

        Composite attributeButtonsBar = createActionBar(rightComposite);
        createAttributeButton = addButton(attributeButtonsBar, "button.create", new CreateAttributeSelectionListener(), false);
        changeAttributeButton = addButton(attributeButtonsBar, "button.change", new ChangeAttributeSelectionListener(), true);
        searchAttributeButton = addButton(attributeButtonsBar, "button.search", new SearchAttributeSelectionListener(), true);
        renameAttributeButton = addButton(attributeButtonsBar, "button.rename", new RenameAttributeSelectionListener(), true);
        mergeAttributesButton = addButton(attributeButtonsBar, "button.merge", new MergeAttributesSelectionListener(), true);
        moveUpAttributeButton = addButton(attributeButtonsBar, "button.up", new MoveAttributeSelectionListener(true), true);
        moveDownAttributeButton = addButton(attributeButtonsBar, "button.down", new MoveAttributeSelectionListener(false), true);
        deleteAttributeButton = addButton(attributeButtonsBar, "button.delete", new DeleteAttributeSelectionListener(), true);
        moveToTypeAttributeButton = addButton(attributeButtonsBar, "button.move", new MoveToTypeAttributeSelectionListener(), true);
        addButton(attributeButtonsBar, "button.copy", new CopyAttributeSelectionListener(), true);
        addButton(attributeButtonsBar, "button.paste", new PasteAttributeSelectionListener(), true);

        updateViewer();
    }

    @Override
    public void dispose() {
        for (VariableUserType userType : getDefinition().getVariableUserTypes()) {
            userType.removePropertyChangeListener(this);
        }
        super.dispose();
    }

    @Override
    public void propertyChange(PropertyChangeEvent evt) {
        String type = evt.getPropertyName();
        if (PropertyNames.PROPERTY_USER_TYPES_CHANGED.equals(type)) {
            updateViewer();
        } else if (evt.getSource() instanceof VariableUserType) {
            if (PropertyNames.PROPERTY_NAME.equals(type)) {
                typeTableViewer.refresh(evt.getSource());
            }
            if (PropertyNames.PROPERTY_CHILDREN_CHANGED.equals(type)) {
                updateAttributeViewer();
            }
        }
    }

    @Override
    protected void updateUI() {
        VariableUserType selectedType = getSelection();
        enableAction(deleteTypeButton, selectedType != null);
        enableAction(renameTypeButton, selectedType != null);
        enableAction(moveUpTypeButton, selectedType != null && getDefinition().getVariableUserTypes().indexOf(selectedType) > 0);
        enableAction(moveDownTypeButton, selectedType != null
                && getDefinition().getVariableUserTypes().indexOf(selectedType) < getDefinition().getVariableUserTypes().size() - 1);

        enableAction(createAttributeButton, selectedType != null);
        @SuppressWarnings("unchecked")
        List<Variable> attributes = ((IStructuredSelection) attributeTableViewer.getSelection()).toList();
        enableAction(changeAttributeButton, attributes.size() == 1);
        enableAction(searchAttributeButton, attributes.size() == 1);
        enableAction(renameAttributeButton, attributes.size() == 1);
        enableAction(mergeAttributesButton, attributes.size() == 2);
        enableAction(moveUpAttributeButton, selectedType != null && attributes.size() == 1
                && selectedType.getAttributes().indexOf(attributes.get(0)) > 0);
        enableAction(moveDownAttributeButton,
                selectedType != null && attributes.size() == 1
                        && selectedType.getAttributes().indexOf(attributes.get(0)) < selectedType.getAttributes().size() - 1);
        enableAction(deleteAttributeButton, attributes.size() > 0);
        enableAction(moveToTypeAttributeButton, attributes.size() == 1);

        updateAttributeViewer();
    }

    private void updateViewer() {
        List<VariableUserType> userTypes = getDefinition().getVariableUserTypes();
        typeTableViewer.setInput(userTypes);
        for (VariableUserType userType : userTypes) {
            userType.addPropertyChangeListener(this);
        }
        updateAttributeViewer();
        updateUI();
    }

    private Variable getAttributeSelection() {
        return (Variable) ((IStructuredSelection) attributeTableViewer.getSelection()).getFirstElement();
    }

    private void updateAttributeViewer() {
        updateAttributeViewer(null);
    }

    private void updateAttributeViewer(Variable variable) {
        VariableUserType type = getSelection();
        if (type != null) {
            attributeTableViewer.setInput(type.getAttributes());
        } else {
            attributeTableViewer.setInput(new Object[0]);
        }
        if (variable != null) {
            attributeTableViewer.setSelection(new StructuredSelection(variable));
        }
    }

    private class CreateTypeSelectionListener extends LoggingSelectionAdapter {
        @Override
        protected void onSelection(SelectionEvent e) throws Exception {
            VariableUserTypeDialog dialog = new VariableUserTypeDialog(getDefinition(), null);
            if (dialog.open() == Window.OK) {
                VariableUserType type = new VariableUserType(dialog.getName());
                getDefinition().addVariableUserType(type);
                typeTableViewer.setSelection(new StructuredSelection(type));
            }
        }
    }

    private class RenameTypeSelectionListener extends LoggingSelectionAdapter {
        @Override
        protected void onSelection(SelectionEvent e) throws Exception {
            VariableUserType type = getSelection();
            VariableUserTypeDialog dialog = new VariableUserTypeDialog(getDefinition(), type);
            if (dialog.open() == Window.OK) {
                VariableUtils.renameUserType(getDefinition(), type, dialog.getName());
            }
        }
    }

    private class CopyTypeSelectionListener extends LoggingSelectionAdapter {
        @Override
        protected void onSelection(SelectionEvent e) throws Exception {
            Clipboard clipboard = new Clipboard(getDisplay());
            @SuppressWarnings("unchecked")
            List<VariableUserType> list = ((IStructuredSelection) typeTableViewer.getSelection()).toList();
            clipboard.setContents(new Object[] { list }, new Transfer[] { VariableUserTypeTransfer.getInstance() });
            clipboard.dispose();
        }
    }

    private class PasteTypeSelectionListener extends LoggingSelectionAdapter {
        @Override
        protected void onSelection(SelectionEvent e) throws Exception {
            Clipboard clipboard = new Clipboard(getDisplay());
            @SuppressWarnings("unchecked")
            List<VariableUserType> data = (List<VariableUserType>) clipboard.getContents(VariableUserTypeTransfer.getInstance(getDefinition()));
            if (data != null) {
                for (VariableUserType type : data) {
                    boolean nameAllowed = true;
                    VariableUserType existing = getDefinition().getVariableUserType(type.getName());
                    if (existing != null) {
                        VariableUserTypeDialog dialog = new VariableUserTypeDialog(getDefinition(), type);
                        nameAllowed = dialog.open() == Window.OK;
                        if (nameAllowed) {
                            type.setName(dialog.getName());
                        }
                    }
                    if (nameAllowed) {
                        copyUserTypeRecursive(type);
                    }
                }
            }
            clipboard.dispose();
        }

        private void copyUserTypeRecursive(VariableUserType sourceUserType) {
            if (getDefinition().getVariableUserType(sourceUserType.getName()) == null) {
                VariableUserType userType = sourceUserType.getCopy();
                getDefinition().addVariableUserType(userType);
            }
            for (Variable attribute : sourceUserType.getAttributes()) {
                if (attribute.isComplex()) {
                    copyUserTypeRecursive(attribute.getUserType());
                }
            }
        }
    }

    private class MoveTypeSelectionListener extends LoggingSelectionAdapter {
        private final boolean up;

        public MoveTypeSelectionListener(boolean up) {
            this.up = up;
        }

        @Override
        protected void onSelection(SelectionEvent e) throws Exception {
            VariableUserType userType = getSelection();
            int index = getDefinition().getVariableUserTypes().indexOf(userType);
            getDefinition().changeVariableUserTypePosition(userType, up ? index - 1 : index + 1);
            typeTableViewer.setSelection(new StructuredSelection(userType));
        }
    }

    private enum RemoveAction {
        NONE(""),
        OK("UserDefinedVariableType.deletion.NoUsageFound"),
        VAR_USAGE("UserDefinedVariableType.deletion.VariablesWillBeRemoved"),
        TYPE_USAGE("UserDefinedVariableType.deletion.UserTypeIsUsed");

        private final String messageKey;

        RemoveAction(String messageKey) {
            this.messageKey = messageKey;
        }

        public String getMessage() {
            return Localization.getString(messageKey);
        }
    }

    private class RemoveTypeSelectionListener extends LoggingSelectionAdapter {
        @Override
        protected void onSelection(SelectionEvent e) throws Exception {
            VariableUserType type = getSelection();
            StringBuilder info = new StringBuilder();

            RemoveAction action = RemoveAction.OK;
            String newLine = System.getProperty("line.separator");

            List<Variable> variables = getDefinition().getVariables(false, false, type.getName());
            if (variables.size() > 0) {
                for (Variable variable : variables) {
                    info.append(" - ").append(variable.getName()).append(newLine);
                }
                action = RemoveAction.VAR_USAGE;
            }

            for (VariableUserType userType : getDefinition().getVariableUserTypes()) {
                // XXX: exclude same VariableUserType object
                if (!type.equals(userType)) {
                    if (isUserTypeUsed(type, userType)) {
                        action = RemoveAction.TYPE_USAGE;
                        break;
                    }
                }
            }

            info.insert(0, newLine);
            info.insert(0, action.getMessage());

            if (!MessageDialog.openConfirm(Display.getCurrent().getActiveShell(), Localization.getString("confirm.delete"), info.toString())) {
                action = RemoveAction.NONE;
            }

            switch (action) {
            case TYPE_USAGE:
                // prohibited
                break;
            case VAR_USAGE:
                for (Variable variable : variables) {
                    getDefinition().removeChild(variable);
                }
                // fall through to remove type
            case OK:
                getDefinition().removeVariableUserType(type);
                break;
            case NONE:
                // skip
                break;
            }
        }

        private boolean isUserTypeUsed(VariableUserType type, VariableUserType userType) {
            boolean result = type.equals(userType);
            if (!result && userType != null) {
                for (Variable var : userType.getAttributes()) {
                    if (var.getUserType() == null) {
                        for (String formatComponent : var.getFormatComponentClassNames()) {
                            if (Objects.equal(type.getName(), formatComponent)) {
                                result = true;
                                break;
                            }
                        }
                    } else {
                        result = result || isUserTypeUsed(type, var.getUserType());
                    }
                    if (result) {
                        break;
                    }
                }
            }
            return result;
        }
    }

    private static class TypeLabelProvider extends LabelProvider implements ITableLabelProvider {
        @Override
        public String getColumnText(Object element, int index) {
            VariableUserType type = (VariableUserType) element;
            switch (index) {
            case 0:
                return type.getName();
            default:
                return "unknown " + index;
            }
        }

        @Override
        public String getText(Object element) {
            return getColumnText(element, 0);
        }

        @Override
        public Image getColumnImage(Object element, int columnIndex) {
            return null;
        }
    }

    private class CreateAttributeSelectionListener extends LoggingSelectionAdapter {
        @Override
        protected void onSelection(SelectionEvent e) throws Exception {
            VariableUserType type = getSelection();
            VariableWizard wizard = new VariableWizard(getDefinition(), type, null, true, true, false);
            CompactWizardDialog dialog = new CompactWizardDialog(wizard);
            if (dialog.open() == Window.OK) {
                Variable variable = wizard.getVariable();
                type.addAttribute(variable);
                attributeTableViewer.setSelection(new StructuredSelection(variable));
            }
        }
    }

    private class ChangeAttributeSelectionListener extends LoggingSelectionAdapter {
        @Override
        protected void onSelection(SelectionEvent e) throws Exception {
            VariableUserType type = getSelection();
            Variable variable = getAttributeSelection();
            VariableWizard wizard = new VariableWizard(getDefinition(), type, variable, false, true, false);
            CompactWizardDialog dialog = new CompactWizardDialog(wizard);
            if (dialog.open() == Window.OK) {
                variable.setFormat(wizard.getVariable().getFormat());
                variable.setUserType(wizard.getVariable().getUserType());
                variable.setDefaultValue(wizard.getVariable().getDefaultValue());
                variable.setStoreType(wizard.getVariable().getStoreType());
                getDefinition().setDirty();
                updateAttributeViewer(variable);
            }
        }
    }

    private class SearchAttributeSelectionListener extends LoggingSelectionAdapter {

        @Override
        protected void onSelection(SelectionEvent e) throws Exception {
            VariableUserType searchType = getSelection();
            Variable searchAttribute = getAttributeSelection();
            List<Variable> result = Lists.newArrayList();
            searchInVariables(result, searchType, searchAttribute, null, getDefinition().getVariables(false, false));
            String searchText = Joiner.on(", ").join(Lists.transform(result, new Function<Variable, String>() {

                @Override
                public String apply(Variable variable) {
                    return variable.getName();
                }

            }));
            MultiVariableSearchQuery query = new MultiVariableSearchQuery(searchText, editor.getDefinitionFile(), getDefinition(), result);
            NewSearchUI.runQueryInBackground(query);
        }
    }

    // same as VariableUtils.searchInVariables, but with expandComplexVariable
    private void searchInVariables(List<Variable> result, VariableUserType searchType, Variable searchAttribute, Variable parent,
            List<Variable> children) {
        for (Variable variable : children) {
            if (variable.getUserType() == null) {
                continue;
            }
            String syntheticName = (parent != null ? (parent.getName() + VariableUserType.DELIM) : "") + variable.getName();
            String syntheticScriptingName = (parent != null ? (parent.getScriptingName() + VariableUserType.DELIM) : "")
                    + variable.getScriptingName();
            if (Objects.equal(variable.getUserType(), searchType)) {
                Variable syntheticVariable = new Variable(syntheticName + VariableUserType.DELIM + searchAttribute.getName(), syntheticScriptingName
                        + VariableUserType.DELIM + searchAttribute.getScriptingName(), searchAttribute);
                result.add(syntheticVariable);
                if (searchAttribute.isComplex()) {
                    result.addAll(VariableUtils.expandComplexVariable(syntheticVariable, searchAttribute));
                }
            } else {
                Variable syntheticVariable = new Variable(syntheticName, syntheticScriptingName, variable);
                searchInVariables(result, searchType, searchAttribute, syntheticVariable, variable.getUserType().getAttributes());
            }
        }
    }

    private class RenameAttributeSelectionListener extends LoggingSelectionAdapter {
        @Override
        protected void onSelection(SelectionEvent e) throws Exception {
            VariableUserType type = getSelection();
            Variable attribute = getAttributeSelection();
            UpdateVariableNameDialog dialog = new UpdateVariableNameDialog(type, attribute);
            int result = dialog.open();
            if (result != IDialogConstants.OK_ID) {
                return;
            }

            IResource projectRoot = editor.getDefinitionFile().getParent();
            IDE.saveAllEditors(new IResource[] { projectRoot }, false);

            String newAttributeName = dialog.getName();
            String newAttributeScriptingName = dialog.getScriptingName();
            RenameUserTypeAttributeRefactoring refactoring = new RenameUserTypeAttributeRefactoring(editor.getDefinitionFile(),
                    editor.getDefinition(), type, attribute, newAttributeName, newAttributeScriptingName);
            boolean useLtk = refactoring.isUserInteractionNeeded();
            List<IFile> affectedFiles = Lists.newArrayList();
            Change[] changes = refactoring.createChange(null).getChildren();
            for (Change change : changes) {
                if (change.getAffectedObjects() != null) {
                    for (Object o : change.getAffectedObjects()) {
                        if (o instanceof IFile) {
                            affectedFiles.add((IFile) o);
                        }
                    }
                }
            }
            if (useLtk) {
                RenameRefactoringWizard wizard = new RenameRefactoringWizard(refactoring);
                wizard.setDefaultPageTitle(Localization.getString("Refactoring.variable.name"));
                RefactoringWizardOpenOperation operation = new RefactoringWizardOpenOperation(wizard);
                result = operation
                        .run(Display.getCurrent().getActiveShell(), Localization.getString("VariableTypeEditorPage.attribute.rename.title"));
                if (result != IDialogConstants.OK_ID) {
                    return;
                }
            }
            // update attribute
            attribute.setName(newAttributeName);
            attribute.setScriptingName(newAttributeScriptingName);

            getDefinition().setDirty();
            updateAttributeViewer(attribute);

            IWorkbenchPage page = PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage();
            for (IFile file : affectedFiles) {
                IEditorPart editor = page.findEditor(new FileEditorInput(file));
                if (editor != null) {
                    IEditorPart activeEditor = page.getActiveEditor();
                    page.closeEditor(editor, false);
                    IDE.openEditor(page, file);
                    page.activate(activeEditor);
                }
            }
            if (useLtk && editor.getDefinition().getEmbeddedSubprocesses().size() > 0) {
                IDE.saveAllEditors(new IResource[] { projectRoot }, false);
                for (SubprocessDefinition subprocessDefinition : editor.getDefinition().getEmbeddedSubprocesses().values()) {
                    WorkspaceOperations.saveProcessDefinition(subprocessDefinition);
                }
            }
        }
    }

    private class MergeAttributesSelectionListener extends LoggingSelectionAdapter {
        @Override
        protected void onSelection(SelectionEvent e) throws Exception {
            IResource projectRoot = editor.getDefinitionFile().getParent();
            IDE.saveAllEditors(new IResource[] { projectRoot }, false);

            VariableUserType type = getSelection();
            @SuppressWarnings("unchecked")
            List<Variable> attributes = ((IStructuredSelection) attributeTableViewer.getSelection()).toList();
            RenameUserTypeAttributeRefactoring refactoring = new RenameUserTypeAttributeRefactoring(editor.getDefinitionFile(),
                    editor.getDefinition(), type, attributes.get(1), attributes.get(0).getName(), attributes.get(0).getScriptingName());
            boolean useLtk = refactoring.isUserInteractionNeeded();
            if (useLtk) {
                RenameRefactoringWizard wizard = new RenameRefactoringWizard(refactoring);
                wizard.setDefaultPageTitle(Localization.getString("Refactoring.variable.name"));
                RefactoringWizardOpenOperation operation = new RefactoringWizardOpenOperation(wizard);
                int result = operation.run(Display.getCurrent().getActiveShell(),
                        Localization.getString("VariableTypeEditorPage.attribute.merge.title"));
                if (result != IDialogConstants.OK_ID) {
                    return;
                }
            }
            // delete attribute
            type.removeAttribute(attributes.get(1));

            updateAttributeViewer(attributes.get(0));

            if (useLtk && editor.getDefinition().getEmbeddedSubprocesses().size() > 0) {
                IDE.saveAllEditors(new IResource[] { projectRoot }, false);
                for (SubprocessDefinition subprocessDefinition : editor.getDefinition().getEmbeddedSubprocesses().values()) {
                    WorkspaceOperations.saveProcessDefinition(subprocessDefinition);
                }
            }
        }
    }

    private class MoveAttributeSelectionListener extends LoggingSelectionAdapter {
        private final boolean up;

        public MoveAttributeSelectionListener(boolean up) {
            this.up = up;
        }

        @Override
        protected void onSelection(SelectionEvent e) throws Exception {
            VariableUserType userType = getSelection();
            Variable attribute = getAttributeSelection();
            int index = userType.getAttributes().indexOf(attribute);
            userType.changeAttributePosition(attribute, up ? index - 1 : index + 1);
            attributeTableViewer.setSelection(new StructuredSelection(attribute));
        }
    }

    private class DeleteAttributeSelectionListener extends LoggingSelectionAdapter {
        @Override
        protected void onSelection(SelectionEvent e) throws Exception {
            @SuppressWarnings("unchecked")
            List<Variable> attributes = ((IStructuredSelection) attributeTableViewer.getSelection()).toList();
            for (Variable attribute : attributes) {
                List<Variable> result = Lists.newArrayList();
                searchInVariables(result, getSelection(), attribute, null, getDefinition().getVariables(false, false));
                String searchText = Joiner.on(", ").join(Lists.transform(result, new Function<Variable, String>() {
                    @Override
                    public String apply(Variable variable) {
                        return variable.getName();
                    }
                }));
                MultiVariableSearchQuery query = new MultiVariableSearchQuery(searchText, editor.getDefinitionFile(), getDefinition(), result);
                query.run(null);
                Object[] elements = query.getSearchResult().getElements();
                if (elements.length > 0) {
                    List<String> elementLabels = Lists.newArrayList();
                    for (Object element : elements) {
                        NamedGraphElement nge = (NamedGraphElement) ((ElementMatch) element).getGraphElement();
                        elementLabels.add(NodeRegistry.getNodeTypeDefinition(nge.getClass()).getLabel() + ": " + nge.getName() + " ("
                                + ((ElementMatch) element).getFile().getName() + ")");
                    }
                    StringBuilder formNames = new StringBuilder(Localization.getString("Variable.ExistInElements")).append("\n\n");
                    elementLabels.stream().sorted().forEach(label -> formNames.append("- ").append(label).append("\n"));
                    formNames.append("\n").append(Localization.getString("Variable.WillBeRemovedFromFormAuto"));
                    if (!MessageDialog.openConfirm(Display.getCurrent().getActiveShell(), Localization.getString("confirm.delete"),
                            formNames.toString())) {
                        continue;
                    }
                }
                Map<String, List<FormNode>> variableFormNodesMapping = Maps.newHashMap();
                for (Variable variable : VariableUtils.findVariablesOfTypeWithAttributeExpanded(getDefinition(), getSelection(), attribute)) {
                    variableFormNodesMapping.put(variable.getName(),
                            ParContentProvider.getFormsWhereUserTypeAttributeUsed(editor.getDefinitionFile(), getDefinition(), getSelection(),
                                    attribute));
                }
                // remove variable from form validations
                for (Map.Entry<String, List<FormNode>> entry : variableFormNodesMapping.entrySet()) {
                    ParContentProvider.rewriteFormValidationsRemoveVariable(editor.getDefinitionFile(), entry.getValue(), entry.getKey());
                }
                getSelection().removeAttribute(attribute);
            }
        }
    }

    private class MoveToTypeAttributeSelectionListener extends LoggingSelectionAdapter {
        private final VariableUserType TOP_LEVEL = new VariableUserType("("
                + Localization.getString("VariableTypeEditorPage.attribute.move.to.variables") + ")");

        @Override
        protected void onSelection(SelectionEvent e) throws Exception {
            List<VariableUserType> userTypes = Lists.newArrayList(TOP_LEVEL);
            userTypes.addAll(getDefinition().getVariableUserTypes());
            ChooseUserTypeDialog dialog = new ChooseUserTypeDialog(userTypes);
            VariableUserType newType = dialog.openDialog();
            if (newType == null) {
                return;
            }

            VariableUserType type = getSelection();
            Variable attribute = getAttributeSelection();
            if (TOP_LEVEL == newType) {
                moveToTopLevelVariable(type, attribute);
            } else {
                moveToUserType(type, attribute, newType);
            }
        }

        private void moveToTopLevelVariable(VariableUserType oldType, Variable attribute) throws Exception {
            if (getDefinition().getVariableNames(true).contains(attribute.getName())) {
                ErrorDialog.open(Localization.getString("VariableTypeEditorPage.error.attribute.move.toplevelvariable.exists"));
                return;
            }

            IResource projectRoot = editor.getDefinitionFile().getParent();
            IDE.saveAllEditors(new IResource[] { projectRoot }, false);

            MoveUserTypeAttributeRefactoring refactoring = new MoveUserTypeAttributeRefactoring(editor.getDefinitionFile(), editor.getDefinition(),
                    oldType, attribute);
            boolean useLtk = refactoring.isUserInteractionNeeded();
            if (useLtk) {
                RenameRefactoringWizard wizard = new RenameRefactoringWizard(refactoring);
                wizard.setDefaultPageTitle(Localization.getString("Refactoring.variable.name"));
                RefactoringWizardOpenOperation operation = new RefactoringWizardOpenOperation(wizard);
                int result = operation.run(Display.getCurrent().getActiveShell(),
                        Localization.getString("VariableTypeEditorPage.attribute.move.title"));
                if (result != IDialogConstants.OK_ID) {
                    return;
                }
            }
            getDefinition().addChild(attribute);
            getSelection().removeAttribute(attribute);

            if (useLtk && editor.getDefinition().getEmbeddedSubprocesses().size() > 0) {
                IDE.saveAllEditors(new IResource[] { projectRoot }, false);
                for (SubprocessDefinition subprocessDefinition : editor.getDefinition().getEmbeddedSubprocesses().values()) {
                    WorkspaceOperations.saveProcessDefinition(subprocessDefinition);
                }
            }
        }

        private void moveToUserType(VariableUserType oldType, Variable attribute, VariableUserType newType) throws Exception {
            if (!newType.canUseAsAttribute(attribute)) {
                ErrorDialog.open(Localization.getString("VariableTypeEditorPage.error.attribute.move.loop"));
                return;
            }

            IResource projectRoot = editor.getDefinitionFile().getParent();

            List<Variable> variables = editor.getDefinition().getVariables(false, false, newType.getName());
            if (variables.size() == 0) {
                List<Variable> result = VariableUtils.findVariablesOfTypeWithAttributeExpanded(getDefinition(), oldType, attribute);
                if (result.size() > 0) {
                    ErrorDialog.open(Localization.getString("VariableTypeEditorPage.error.variable.move.without.substitution.variable"));
                    return;
                }
            }

            boolean useLtk = false;
            if (variables.size() > 0) {
                Variable substitutionVariable;
                if (variables.size() > 1) {
                    ChooseVariableDialog variableDialog = new ChooseVariableDialog(variables);
                    substitutionVariable = variableDialog.openDialog();
                } else {
                    substitutionVariable = variables.get(0);
                }
                if (substitutionVariable == null) {
                    return;
                }

                IDE.saveAllEditors(new IResource[] { projectRoot }, false);

                MoveUserTypeAttributeRefactoring refactoring = new MoveUserTypeAttributeRefactoring(editor.getDefinitionFile(),
                        editor.getDefinition(), oldType, attribute, substitutionVariable);
                useLtk = refactoring.isUserInteractionNeeded();
                if (useLtk) {
                    RenameRefactoringWizard wizard = new RenameRefactoringWizard(refactoring);
                    wizard.setDefaultPageTitle(Localization.getString("Refactoring.variable.name"));
                    RefactoringWizardOpenOperation operation = new RefactoringWizardOpenOperation(wizard);
                    int result = operation.run(Display.getCurrent().getActiveShell(),
                            Localization.getString("VariableTypeEditorPage.attribute.move.title"));
                    if (result != IDialogConstants.OK_ID) {
                        return;
                    }
                }
            }
            newType.addAttribute(attribute);

            getSelection().removeAttribute(attribute);

            if (useLtk && editor.getDefinition().getEmbeddedSubprocesses().size() > 0) {
                IDE.saveAllEditors(new IResource[] { projectRoot }, false);
                for (SubprocessDefinition subprocessDefinition : editor.getDefinition().getEmbeddedSubprocesses().values()) {
                    WorkspaceOperations.saveProcessDefinition(subprocessDefinition);
                }
            }
        }
    }

    private class CopyAttributeSelectionListener extends LoggingSelectionAdapter {
        @Override
        protected void onSelection(SelectionEvent e) throws Exception {
            Clipboard clipboard = new Clipboard(getDisplay());
            @SuppressWarnings("unchecked")
            List<Variable> list = ((IStructuredSelection) attributeTableViewer.getSelection()).toList();
            clipboard.setContents(new Object[] { list }, new Transfer[] { VariableTransfer.getInstance() });
        }
    }

    private class PasteAttributeSelectionListener extends LoggingSelectionAdapter {
        @Override
        protected void onSelection(SelectionEvent e) throws Exception {
            VariableUserType type = getSelection();
            if (type != null) {
                Clipboard clipboard = new Clipboard(getDisplay());
                @SuppressWarnings("unchecked")
                List<Variable> data = (List<Variable>) clipboard.getContents(VariableTransfer.getInstance(getDefinition()));
                if (data != null) {
                    for (Variable variable : data) {
                        boolean nameAllowed = true;
                        Variable newVariable = VariableUtils.getVariableByName(type, variable.getName());
                        if (newVariable == null) {
                            newVariable = new Variable(variable);
                        } else {
                            UpdateVariableNameDialog dialog = new UpdateVariableNameDialog(type, newVariable);
                            nameAllowed = dialog.open() == Window.OK;
                            if (nameAllowed) {
                                newVariable = new Variable(variable);
                                newVariable.setName(dialog.getName());
                                newVariable.setScriptingName(dialog.getScriptingName());
                            }
                        }

                        boolean typeAllowed = isCurrentTypeAllowedForInsert(type, variable.getUserType());

                        if (nameAllowed && typeAllowed) {
                            type.addAttribute(newVariable);
                            if (newVariable.isComplex()) {
                                copyUserTypeRecursive(newVariable.getUserType());
                                newVariable.setUserType(getDefinition().getVariableUserTypeNotNull(newVariable.getUserType().getName()));
                            }
                        }

                        attributeTableViewer.setSelection(new StructuredSelection(variable));
                    }
                }
                clipboard.dispose();
            }
        }

        private boolean isCurrentTypeAllowedForInsert(VariableUserType selectedType, VariableUserType variableType) {
            boolean result = true;

            if (variableType != null) {
                if (isEquals(selectedType, variableType)) {
                    result = false;
                } else {
                    for (Variable var : variableType.getVariables(false, true)) {
                        result = result && isCurrentTypeAllowedForInsert(selectedType, var.getUserType());
                        if (!result) {
                            break;
                        }
                    }
                }
            }
            return result;
        }

        private boolean isEquals(VariableUserType leftType, VariableUserType rightType) {
            boolean result = Objects.equal(leftType.getName(), rightType.getName());
            result = result && (leftType.getAttributes() == null ? rightType.getAttributes() == null : false);
            if (leftType.getAttributes() != null && rightType.getAttributes() != null) {
                // XXX: fault accumulate "result &&" - wrong set "false"
                result = leftType.getAttributes().size() == rightType.getAttributes().size();
                if (result) {
                    for (int i = 0; i < leftType.getAttributes().size(); i++) {
                        Variable var1 = leftType.getAttributes().get(i);
                        Variable var2 = rightType.getAttributes().get(i);
                        result = result && stringEqual(var1.getName(), var2.getName());
                        result = result && stringEqual(var1.getScriptingName(), var2.getScriptingName());
                        result = result && stringEqual(var1.getDefaultValue(), var2.getDefaultValue());
                        result = result && stringEqual(var1.getDescription(), var2.getDescription());
                        if (!result) {
                            break;
                        }
                    }
                }
            }
            return result;
        }

        private boolean stringEqual(String left, String right) {
            return Objects.equal(Strings.nullToEmpty(left), Strings.nullToEmpty(right));
        }

        private void copyUserTypeRecursive(VariableUserType sourceUserType) {
            if (getDefinition().getVariableUserType(sourceUserType.getName()) == null) {
                VariableUserType userType = sourceUserType.getCopy();
                getDefinition().addVariableUserType(userType);
            }
            for (Variable attribute : sourceUserType.getAttributes()) {
                if (attribute.isComplex()) {
                    copyUserTypeRecursive(attribute.getUserType());
                }
            }
        }
    }

    private static class AttributeLabelProvider extends LabelProvider implements ITableLabelProvider {
        @Override
        public String getColumnText(Object element, int index) {
            Variable variable = (Variable) element;
            switch (index) {
            case 0:
                return variable.getName();
            case 1:
                return variable.getFormatLabel();
            case 2:
                return Strings.nullToEmpty(variable.getDefaultValue());
            default:
                return "unknown " + index;
            }
        }

        @Override
        public String getText(Object element) {
            return getColumnText(element, 0);
        }

        @Override
        public Image getColumnImage(Object element, int columnIndex) {
            return null;
        }
    }

}
