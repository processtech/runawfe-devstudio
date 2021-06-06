package ru.runa.gpd.office.store.externalstorage;

import com.google.common.base.Strings;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import ru.runa.gpd.office.store.StorageConstraintsModel;

abstract class AbstractOperatingVariableComboBasedConstraintsCompositeBuilder extends AbstractConstraintsCompositeBuilder {

    public AbstractOperatingVariableComboBasedConstraintsCompositeBuilder(Composite parent, int style, StorageConstraintsModel constraintsModel,
            VariableProvider variableProvider, String variableTypeName) {
        super(parent, style, constraintsModel, variableProvider, variableTypeName);
    }

    protected Combo combo;

    @Override
    public void build() {
        new Label(getParent(), SWT.NONE).setText(getComboTitle());
        addCombo();
    }

    protected void addCombo() {
        combo = new Combo(getParent(), SWT.READ_ONLY);
        variableNamesByVariableTypeName(variableTypeName).forEach(combo::add);
        
        combo.addSelectionListener(SelectionListener.widgetSelectedAdapter(e -> {
            final String text = combo.getText();
            if (Strings.isNullOrEmpty(text)) {
                return;
            }
            constraintsModel.setVariableName(text);
            onWidgetSelected(text);
        }));

        if (constraintsModel.getVariableName() != null) {
            combo.setText(constraintsModel.getVariableName());
        }
    }

    protected void onWidgetSelected(String text) {
    }

    protected abstract String getComboTitle();

}
