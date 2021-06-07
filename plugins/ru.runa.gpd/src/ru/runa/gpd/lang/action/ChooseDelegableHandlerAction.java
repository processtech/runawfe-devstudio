package ru.runa.gpd.lang.action;

import org.eclipse.jface.action.IAction;
import ru.runa.gpd.editor.graphiti.ChangeDelegationClassNameFeature;
import ru.runa.gpd.editor.graphiti.UndoRedoUtil;
import ru.runa.gpd.lang.model.Delegable;
import ru.runa.gpd.ui.dialog.ChooseHandlerClassDialog;

public class ChooseDelegableHandlerAction extends BaseModelActionDelegate {

    @Override
    public void run(IAction action) {
        Delegable delegable = (Delegable) getSelection();
        ChooseHandlerClassDialog dialog = new ChooseHandlerClassDialog(delegable.getDelegationType(), delegable.getDelegationClassName());
        String className = dialog.openDialog();
        if (className != null) {
            UndoRedoUtil.executeFeature(new ChangeDelegationClassNameFeature(delegable, className));
        }
    }
}
