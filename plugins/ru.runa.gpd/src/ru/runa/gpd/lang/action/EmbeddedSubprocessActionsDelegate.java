package ru.runa.gpd.lang.action;

import java.util.Collections;
import java.util.List;

import org.eclipse.core.resources.IFile;
import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.ActionContributionItem;
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.MenuItem;
import org.eclipse.ui.part.FileEditorInput;

import ru.runa.gpd.Localization;
import ru.runa.gpd.lang.model.ProcessDefinition;
import ru.runa.gpd.lang.model.Subprocess;
import ru.runa.gpd.lang.model.SubprocessDefinition;
import ru.runa.gpd.util.WorkspaceOperations;
import ru.runa.wfe.definition.ProcessDefinitionAccessType;

import com.google.common.base.Objects;
import com.google.common.collect.Lists;

public class EmbeddedSubprocessActionsDelegate extends BaseModelDropDownActionDelegate {
    private String selectedName = "";
    private ProcessDefinition definition;
    private Subprocess subprocess;
    private IFile definitionFile;

    @Override
    public void selectionChanged(IAction action, ISelection selection) {
        super.selectionChanged(action, selection);
        subprocess = getSelection();
        if (subprocess != null) {
            if (subprocess.getClass() == Subprocess.class) {
                definition = subprocess.getProcessDefinition();
                selectedName = subprocess.getSubProcessName();
                action.setChecked(subprocess.isEmbedded());
                definitionFile = ((FileEditorInput) getActiveEditor().getEditorInput()).getFile();
                action.setEnabled(true);
            } else {
                action.setEnabled(false);
            }
        }
    }    

    /**
     * Fills the menu with applicable launch shortcuts
     * 
     * @param menu
     *            The menu to fill
     */
    @Override
    protected void fillMenu(Menu menu) {
        if (subprocess.getClass() != Subprocess.class) {
            return;
        }
        List<String> allNames = Lists.newArrayList();
        ProcessDefinition mainProcessDefinition = definition.getMainProcessDefinition();
        for (SubprocessDefinition subprocessDefinition : mainProcessDefinition.getEmbeddedSubprocesses().values()) {
            allNames.add(subprocessDefinition.getName());
        }
        List<String> usedNames = Lists.newArrayList();
        usedNames.add(definition.getName());
        for (Subprocess subprocess : mainProcessDefinition.getChildren(Subprocess.class)) {
            if (subprocess.isEmbedded() && !Objects.equal(subprocess, this.subprocess)) {
                usedNames.add(subprocess.getSubProcessName());
            }
        }
        for (SubprocessDefinition subprocessDefinition : mainProcessDefinition.getEmbeddedSubprocesses().values()) {
            for (Subprocess subprocess : subprocessDefinition.getChildren(Subprocess.class)) {
                if (subprocess.isEmbedded() && !Objects.equal(subprocess, this.subprocess)) {
                    usedNames.add(subprocess.getSubProcessName());
                }
            }
        }
        Collections.sort(allNames);
        for (String subprocessName : allNames) {
            Action action = new SetEmbeddedSubprocessAction();
            action.setText(subprocessName);
            if (subprocess.isEmbedded() && Objects.equal(selectedName, subprocessName)) {
                action.setChecked(true);
            }
            action.setEnabled(!usedNames.contains(subprocessName));
            ActionContributionItem item = new ActionContributionItem(action);
            item.fill(menu, -1);
        }
        new MenuItem(menu, SWT.SEPARATOR);
        Action action;
        ActionContributionItem item;
        action = new CreateEmbeddedSubprocessAction();
        item = new ActionContributionItem(action);
        item.fill(menu, -1);
    }

    private void createEmbeddedSubprocess() {
        IStructuredSelection selection = new StructuredSelection(definitionFile.getParent());
        ProcessDefinition created = WorkspaceOperations.createNewProcessDefinition(selection, ProcessDefinitionAccessType.EmbeddedSubprocess);
        if (created != null) {
            setEmbeddedSubprocess(created.getName());
        }
    }

    private void setEmbeddedSubprocess(String name) {
        subprocess.setAsync(false);
        subprocess.setEmbedded(true);
        subprocess.setSubProcessName(name);
    }

    private class SetEmbeddedSubprocessAction extends Action {
        @Override
        public void run() {
            setEmbeddedSubprocess(getText());
            if(selectedName.equals(getText())) {
                WorkspaceOperations.openSubprocessDefinition(subprocess);
            }
        }
    }

    private class CreateEmbeddedSubprocessAction extends Action {
        public CreateEmbeddedSubprocessAction() {
            setText(Localization.getString("ExplorerTreeView.menu.label.newEmbeddedSubprocess"));
        }

        @Override
        public void run() {
            createEmbeddedSubprocess();
        }
    }

}
