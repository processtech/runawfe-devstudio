package ru.runa.gpd.office.store.externalstorage;

import java.util.List;
import java.util.function.Predicate;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;

import com.google.common.base.Strings;

import ru.runa.gpd.Localization;
import ru.runa.gpd.lang.model.Delegable;
import ru.runa.gpd.lang.model.Variable;
import ru.runa.gpd.office.InputOutputModel;
import ru.runa.gpd.office.Messages;
import ru.runa.gpd.office.store.InternalStorageOperationHandlerCellEditorProvider.VariableUserTypeInfo;
import ru.runa.gpd.office.store.StorageConstraintsModel;

public class SelectBotConstraintsComposite extends AbstractOperatingVariableComboBasedConstraintsCompositeBuilder{
	private VariableUserTypeInfo variableTypeInfo;
	private InputOutputModel inOutModel;
	private Delegable delegable;
	private String variableTypeName;
	public SelectBotConstraintsComposite(Composite parent, int style, StorageConstraintsModel constraintsModel, VariableProvider variableProvider,
            VariableUserTypeInfo variableTypeInfo, InputOutputModel inOutModel,Delegable delegable) {        
		super(parent, style, constraintsModel, variableProvider, variableTypeInfo.getVariableTypeName());
		this.variableTypeName = variableTypeInfo.getVariableTypeName();
        this.variableTypeInfo = variableTypeInfo;
        this.delegable = delegable;
    }

    @Override
    protected Predicate<? super Variable> getFilterPredicate(String variableTypeName) {
        return variable -> variable.getUserType().getName().equals(variableTypeName);
    }
    @Override
    public void build() {
        super.build();
        addDataCombo();
    }
    
    protected void addDataCombo() {
    	Label selectLabel = new Label(getParent(), SWT.NONE);
    	selectLabel.setText(Localization.getString("label.SelectResultVariable"));    	
        combo = new Combo(getParent(), SWT.READ_ONLY);
        delegable.getVariableNames(false, new String[] { List.class.getName() }).forEach(combo::add);
        combo.addSelectionListener(SelectionListener.widgetSelectedAdapter(e -> {
            final String text = combo.getText();
            if (Strings.isNullOrEmpty(text)) {
                return;
            }
            inOutModel.outputVariable = text;        
            onWidgetSelected(text);
        }));        
    }

	@Override
	protected String getComboTitle() {
			return Messages.getString("label.SelectVariable");		
	}
}
