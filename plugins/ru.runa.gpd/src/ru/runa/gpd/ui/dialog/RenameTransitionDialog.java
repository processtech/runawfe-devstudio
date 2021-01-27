package ru.runa.gpd.ui.dialog;

import com.google.common.base.Strings;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.dialogs.IInputValidator;
import org.eclipse.jface.dialogs.InputDialog;
import org.eclipse.swt.events.ModifyEvent;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;
import ru.runa.gpd.Localization;

public class RenameTransitionDialog extends InputDialog {

    private final String transitionName;

    public RenameTransitionDialog(String currentTransitionName) {
        super(Display.getDefault().getActiveShell(), Localization.getString("RenameTransitionDialog.update.title"),
                Localization.getString("property.name") + ":", currentTransitionName, new IInputValidator() {

                    @Override
                    public String isValid(String value) {
                        return null;
                    }
                });
        transitionName = currentTransitionName;
        setBlockOnOpen(true);
    }

    @Override
    protected Control createDialogArea(Composite parent) {
        Control control = super.createDialogArea(parent);
        getText().addModifyListener(new ModifyListener() {

            @Override
            public void modifyText(ModifyEvent e) {
                updateButtons();

            }
        });
        return control;
    }

    private void updateButtons() {
        final String newTransitionName = getText().getText();
        getButton(IDialogConstants.OK_ID).setEnabled(!Strings.isNullOrEmpty(newTransitionName) && !newTransitionName.equals(transitionName));
    }

    @Override
    protected void configureShell(Shell newShell) {
        super.configureShell(newShell);
        newShell.setText(Localization.getString("RenameTransitionDialog.update.title"));
    }
}
