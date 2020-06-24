package ru.runa.gpd.ui.dialog;

import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.ModifyEvent;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;

import com.google.common.base.Strings;

import ru.runa.gpd.Localization;
import ru.runa.gpd.lang.model.ProcessDefinition;
import ru.runa.gpd.lang.model.Swimlane;
import ru.runa.gpd.ui.custom.VariableNameChecker;
import ru.runa.gpd.util.IOUtils;
import ru.runa.gpd.util.VariableUtils;

public class UpdateSwimlaneNameDialog extends Dialog {
    private String name;
    private final ProcessDefinition processDefinition;
    private final boolean createMode;
    private final Swimlane swimlane;
    private Button renameInVarButton;
    private boolean proceedRefactoring;
    private Text scriptingNameField;
    private String scriptingName;

    public UpdateSwimlaneNameDialog(ProcessDefinition processDefinition, Swimlane swimlane) {
        super(Display.getCurrent().getActiveShell());
        this.processDefinition = processDefinition;
        this.name = swimlane != null ? swimlane.getName() : processDefinition.getNextSwimlaneName();
        this.createMode = swimlane == null;
        this.swimlane = swimlane;
        if (swimlane != null && swimlane.getScriptingName() != null) {
            this.scriptingName = swimlane.getScriptingName();
        } else {
            this.scriptingName = VariableUtils.generateNameForScripting(processDefinition, name, swimlane);
        }
    }

    @Override
    protected Control createDialogArea(Composite parent) {
        Composite area = (Composite) super.createDialogArea(parent);
        GridLayout layout = new GridLayout(1, false);
        area.setLayout(layout);
        final Label labelTitle = new Label(area, SWT.NO_BACKGROUND);
        final GridData labelData = new GridData();
        labelTitle.setLayoutData(labelData);
        labelTitle.setText(Localization.getString(createMode ? "SwimlaneWizard.create.message" : "SwimlaneWizard.update.message"));
        final Composite composite = new Composite(area, SWT.NONE);
        final GridLayout gridLayout = new GridLayout();
        gridLayout.numColumns = 2;
        composite.setLayout(gridLayout);
        composite.setLayoutData(new GridData());
        Label labelName = new Label(composite, SWT.NONE);
        labelName.setLayoutData(new GridData(GridData.HORIZONTAL_ALIGN_END));
        labelName.setText(Localization.getString("property.name") + ":");
        final Text nameField = new Text(composite, SWT.BORDER);
        GridData nameTextData = new GridData(GridData.FILL_HORIZONTAL);
        nameTextData.minimumWidth = 200;
        nameField.setText(name);
        nameField.addKeyListener(new SwimlaneNameChecker());
        nameField.setLayoutData(nameTextData);
        nameField.addModifyListener(new ModifyListener() {
            @Override
            public void modifyText(ModifyEvent e) {
                name = nameField.getText();
                scriptingName = VariableUtils.generateNameForScripting(processDefinition, name, swimlane);
                updateButtons();
                scriptingNameField.setText(scriptingName);
            }
        });
        if (!createMode) {
            renameInVarButton = new Button(area, SWT.CHECK);
            renameInVarButton.setLayoutData(new GridData());
            renameInVarButton.setText(Localization.getString("SwimlaneWizard.renameInVariables"));
            renameInVarButton.setSelection(true);
            renameInVarButton.addSelectionListener(new SelectionAdapter() {
                @Override
                public void widgetSelected(SelectionEvent e) {
                    updateButtons();
                }
            });
        }
        if (createMode) {
            nameField.selectAll();
        }
        new Label(composite, SWT.NONE);
        scriptingNameField = new Text(composite, SWT.BORDER);
        scriptingNameField.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
        scriptingNameField.setEditable(false);
        scriptingNameField.setText(scriptingName);
        if (!createMode) {
            Text saveAllEditorsLabel = new Text(composite, SWT.MULTI | SWT.READ_ONLY);
            GridData gridData = new GridData(GridData.GRAB_HORIZONTAL);
            gridData.horizontalSpan = 2;
            saveAllEditorsLabel.setLayoutData(gridData);
            saveAllEditorsLabel.setText(Localization.getString("warning.allEditorsWillBeSaved"));
        }
        return area;
    }

    private void updateButtons() {
        boolean allowCreation = !Strings.isNullOrEmpty(name) && !processDefinition.getVariableNames(true).contains(name)
                && SwimlaneNameChecker.isValid(name, processDefinition);
        getButton(IDialogConstants.OK_ID).setEnabled(allowCreation);
    }

    @Override
    protected void createButtonsForButtonBar(Composite parent) {
        super.createButtonsForButtonBar(parent);
        updateButtons();
    }

    @Override
    protected void configureShell(Shell newShell) {
        super.configureShell(newShell);
        newShell.setText(Localization.getString(createMode ? "SwimlaneWizard.create.title" : "SwimlaneWizard.update.title"));
    }

    public String getName() {
        return name;
    }

    public String getScriptingName() {
        return scriptingName;
    }

    public boolean isProceedRefactoring() {
        return proceedRefactoring;
    }

    @Override
    protected void okPressed() {
        proceedRefactoring = renameInVarButton != null ? renameInVarButton.getSelection() : false;
        super.okPressed();
    }

}

class SwimlaneNameChecker extends VariableNameChecker {
    public static boolean isValid(String string, ProcessDefinition processDefinition) {
        //���� �� ��� ������� ����������� ����� ��� �������������� � �������� ���� ��������� ���� ���� ������� Global_
    	//������ ��� �� ���� ��������������� ����, ����� ��� ��� �� ����������, �� ������� �������. 
    	//������� ������ true � ����� ������
    	return true;
    	/*
    	if (VariableNameChecker.isValid(string)) {
            if (processDefinition.getName().startsWith(".") || !string.toLowerCase().startsWith(IOUtils.GLOBAL_ROLE_REF_PREFIX.toLowerCase())) {
                return true;
            }
        }
        return false;*/
    }

}
