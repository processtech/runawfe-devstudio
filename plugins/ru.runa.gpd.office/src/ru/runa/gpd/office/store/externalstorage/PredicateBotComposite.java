package ru.runa.gpd.office.store.externalstorage;

import java.util.Optional;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.ui.forms.events.HyperlinkEvent;

import com.google.common.base.Strings;

import ru.runa.gpd.PluginLogger;
import ru.runa.gpd.extension.VariableFormatArtifact;
import ru.runa.gpd.extension.VariableFormatRegistry;
import ru.runa.gpd.lang.model.Delegable;
import ru.runa.gpd.lang.model.Variable;
import ru.runa.gpd.office.store.StorageConstraintsModel;
import ru.runa.gpd.office.store.externalstorage.predicate.OnConstructedPredicateDelegate;
import ru.runa.gpd.office.store.externalstorage.predicate.PredicateComposite;
import ru.runa.gpd.office.store.externalstorage.predicate.PredicateOperationType;
import ru.runa.gpd.ui.custom.LoggingHyperlinkAdapter;
import ru.runa.gpd.ui.custom.SwtUtils;

public class PredicateBotComposite extends PredicateComposite {
	private Delegable taskDelegable;
	public PredicateBotComposite(Composite parent, int style, StorageConstraintsModel constraintsModel,
			String variableTypeName, VariableProvider variableProvider, Delegable delegable) {
		super(parent, style, constraintsModel, variableTypeName, variableProvider);
		this.taskDelegable = delegable;		
		
	}
	
	@Override
	public void buildVariablePredicate(OnConstructedPredicateDelegate<Variable, Variable> predicate, int index) {
        final Combo subjectCombo = new Combo(group, SWT.READ_ONLY);
        final Combo predicateOperationTypeCombo = new Combo(group, SWT.READ_ONLY);
        final Combo compareWithCombo = new Combo(group, SWT.READ_ONLY);
        Variable Result;
        predicate.applicableOperationTypeNames().forEach(predicateOperationTypeCombo::add);
        predicateOperationTypeCombo.addSelectionListener(SelectionListener.widgetSelectedAdapter(e -> {
            final String text = predicateOperationTypeCombo.getText();
            if (!Strings.isNullOrEmpty(text)) {
                predicate.setType(PredicateOperationType.byCode(text).orElse(null));
            }
        }));
        if (predicate.getType() != null) {
            predicateOperationTypeCombo.setText(predicate.getType().code);
        }

        variableUserType.getAttributes().stream().map(Variable::getName).forEach(subjectCombo::add);
        subjectCombo.addSelectionListener(SelectionListener.widgetSelectedAdapter((e) -> {
            final String text = subjectCombo.getText();
            if (Strings.isNullOrEmpty(text)) {
                return;
            }
            final Optional<Variable> variable = variableUserType.getAttributes().stream().filter(var -> var.getName().equals(text)).findAny();
            variable.ifPresent(var -> {
                predicate.setLeft(var);
                compareWithCombo.removeAll();                
                taskDelegable.getVariableNames(true,predicate.getLeft().getJavaClassName()).forEach(compareWithCombo::add);                
            });
        }));
        if (predicate.getLeft() != null) {
            subjectCombo.setText(predicate.getLeft().getName());
            taskDelegable.getVariableNames(true,predicate.getLeft().getJavaClassName()).forEach(compareWithCombo::add);
        }

        compareWithCombo.addSelectionListener(SelectionListener.widgetSelectedAdapter((e) -> {
            final String text = compareWithCombo.getText();
            if (!Strings.isNullOrEmpty(text)) {
                Variable rightVariable = new Variable();
                rightVariable.setName(text);
                rightVariable.setScriptingName(text);
                rightVariable.setFormat(predicate.getLeft().getFormat());                
                predicate.setRight(rightVariable);
            }
        }));
        if (predicate.getRight() != null) {
            compareWithCombo.setText(predicate.getRight().getName());
        }

        SwtUtils.createLink(group, "[X]", new LoggingHyperlinkAdapter() {
            @Override
            protected void onLinkActivated(HyperlinkEvent e) throws Exception {
                onDeletePredicate(index);
            }
        });

   }
	
	
}

