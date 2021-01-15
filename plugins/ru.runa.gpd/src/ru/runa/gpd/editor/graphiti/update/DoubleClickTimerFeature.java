package ru.runa.gpd.editor.graphiti.update;

import com.google.common.base.Objects;
import org.eclipse.graphiti.features.context.ICustomContext;
import org.eclipse.swt.widgets.Display;
import ru.runa.gpd.editor.graphiti.ChangeTimerDelayFeature;
import ru.runa.gpd.editor.graphiti.UndoRedoUtil;
import ru.runa.gpd.lang.model.Timer;
import ru.runa.gpd.ui.dialog.DurationEditDialog;
import ru.runa.gpd.util.Duration;

public class DoubleClickTimerFeature extends DoubleClickElementFeature {

    @Override
    public boolean canExecute(ICustomContext context) {
        return getBusinessObject(context) instanceof Timer && super.canExecute(context);
    }

    @Override
    public void execute(ICustomContext context) {
    	Timer timer = (Timer) getBusinessObject(context);
        Duration oldDelay = timer.getDelay();
        DurationEditDialog dialog = new DurationEditDialog(timer.getProcessDefinition(), timer.getDelay());
        Duration result = (Duration) dialog.openDialog();
        if (result != null) {
            if (!Objects.equal(result, oldDelay)) {
                Display.getDefault().asyncExec(new Runnable() {
                    @Override
                    public void run() {
                    	UndoRedoUtil.executeFeature(new ChangeTimerDelayFeature(timer, result));
                    }
                });
            }
        }
    }

}
