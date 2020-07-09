package ru.runa.gpd.ui.dialog;

import org.eclipse.core.resources.IFolder;
import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.ModifyEvent;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;

import ru.runa.gpd.Localization;
import ru.runa.gpd.lang.model.ProcessDefinition;
import ru.runa.gpd.ui.custom.FileNameChecker;
import ru.runa.gpd.util.IOUtils;

public class RenameProcessDefinitionDialog extends Dialog {
    private String name;
    private IFolder definitionFolder;
    private ProcessDefinition definition;
    boolean processSaved;

    public RenameProcessDefinitionDialog(IFolder definitionFolder, boolean isProcessSaved) {
        super(Display.getDefault().getActiveShell());
        this.definitionFolder = definitionFolder;
        this.processSaved = isProcessSaved;
    }

    public RenameProcessDefinitionDialog(ProcessDefinition definition, boolean isProcessSaved) {
        super(Display.getDefault().getActiveShell());
        this.definition = definition;
        this.processSaved = isProcessSaved;
    }

    public void setName(String name) {
        this.name = name;
    }    

    @Override
    protected Control createDialogArea(Composite parent) {
        Composite area = (Composite) super.createDialogArea(parent);
        GridLayout layout = new GridLayout(1, false);
        area.setLayout(layout);
        final Label labelTitle = new Label(area, SWT.NO_BACKGROUND);
        final GridData labelData = new GridData();
        labelTitle.setLayoutData(labelData);
        if (!processSaved) {
            labelTitle.setText(Localization.getString("alert.save_before_rename"));
            return area;
        }
        labelTitle.setText(Localization.getString("button.rename"));
        final Composite composite = new Composite(area, SWT.NONE);
        final GridLayout gridLayout = new GridLayout();
        gridLayout.numColumns = 2;
        composite.setLayout(gridLayout);
        GridData nameData = new GridData();
        composite.setLayoutData(nameData);
        Label labelName = new Label(composite, SWT.NONE);
        labelName.setLayoutData(new GridData(GridData.HORIZONTAL_ALIGN_END));
        labelName.setText(Localization.getString("property.name") + ":");
        final Text nameField = new Text(composite, SWT.BORDER);
        GridData nameTextData = new GridData(GridData.FILL_HORIZONTAL);
        nameTextData.minimumWidth = 200;
        nameField.setText(name);
        nameField.setLayoutData(nameTextData);
        nameField.addModifyListener(new ModifyListener() {
            @Override
            public void modifyText(ModifyEvent e) {
                name = nameField.getText();
                updateButtons();
            }
        });
        return area;
    }

    @Override
    protected void createButtonsForButtonBar(Composite parent) {
        createButton(parent, IDialogConstants.OK_ID, IDialogConstants.OK_LABEL, true);
        createButton(parent, IDialogConstants.CANCEL_ID, IDialogConstants.CANCEL_LABEL, true);
        getButton(IDialogConstants.OK_ID).setEnabled(!processSaved);
    }

    public void updateButtons() {
        boolean allowCreation = FileNameChecker.isValid(name);
        if (definitionFolder != null) {
            allowCreation &= !IOUtils.isChildFolderExists(definitionFolder.getParent(), name);
        } else if (definition != null) {
            allowCreation &= definition.getEmbeddedSubprocessByName(name) == null;
        }
        getButton(IDialogConstants.OK_ID).setEnabled(allowCreation);
    }

    @Override
    protected void configureShell(Shell shell) {
        super.configureShell(shell);
        if (processSaved) {
            shell.setText(Localization.getString("RenameProcessDefinitionDialog.title"));
        } else {
            shell.setText(Localization.getString("alert.process_unsaved"));
        }
    }

    public String getName() {
        return name;
    }

}
