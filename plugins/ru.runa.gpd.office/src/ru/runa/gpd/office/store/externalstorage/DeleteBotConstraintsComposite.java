package ru.runa.gpd.office.store.externalstorage;

import java.util.function.Predicate;
import org.eclipse.swt.widgets.Composite;
import ru.runa.gpd.lang.model.Variable;
import ru.runa.gpd.office.Messages;
import ru.runa.gpd.office.store.StorageConstraintsModel;

public class DeleteBotConstraintsComposite extends AbstractOperatingVariableComboBasedConstraintsCompositeBuilder {
    
	public DeleteBotConstraintsComposite(Composite parent, int style, StorageConstraintsModel constraintsModel, VariableProvider variableProvider,
            String variableTypeName) {
        super(parent, style, constraintsModel, variableProvider, variableTypeName);
    }

    @Override
    protected Predicate<? super Variable> getFilterPredicate(String variableTypeName) {
        return variable -> variable.getUserType().getName().equals(variableTypeName);
    }
    
    @Override
    protected String getComboTitle() {    	
        return Messages.getString("label.UpdateVariable");
    }
	
}
