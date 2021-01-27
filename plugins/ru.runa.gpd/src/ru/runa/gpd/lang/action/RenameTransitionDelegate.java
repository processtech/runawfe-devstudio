package ru.runa.gpd.lang.action;

import org.eclipse.jface.action.IAction;
import ru.runa.gpd.lang.model.Transition;
import ru.runa.gpd.ui.dialog.RenameTransitionDialog;

public class RenameTransitionDelegate extends BaseModelActionDelegate {

    @Override
    public void run(IAction action) {
        Transition transition = (Transition) getSelectionNotNull();
        RenameTransitionDialog dialog = new RenameTransitionDialog(transition.getName());
        if (dialog.open() == RenameTransitionDialog.OK) {
            String newTransitionName = dialog.getValue().trim();
            transition.setName(newTransitionName);
        }
    }

}
