package ru.runa.gpd.office.store.externalstorage.predicate;

import com.google.common.base.Strings;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Label;
import org.eclipse.ui.forms.events.HyperlinkEvent;
import ru.runa.gpd.lang.model.Variable;
import ru.runa.gpd.lang.model.VariableUserType;
import ru.runa.gpd.office.Messages;
import ru.runa.gpd.office.store.StorageConstraintsModel;
import ru.runa.gpd.office.store.externalstorage.VariableProvider;
import ru.runa.gpd.ui.custom.LoggingHyperlinkAdapter;
import ru.runa.gpd.ui.custom.SwtUtils;

public class PredicateComposite extends Composite {
	protected final StorageConstraintsModel constraintsModel;
    protected final VariableProvider variableProvider;
    protected final VariableUserType variableUserType;

    protected final Group group;
    private final List<Label> labels = new ArrayList<>(5);

    private final PredicateTree predicateTree = new PredicateTree();

    public PredicateComposite(Composite parent, int style, StorageConstraintsModel constraintsModel, String variableTypeName,
            VariableProvider variableProvider) {
        super(parent, style);
        this.constraintsModel = constraintsModel;
        this.variableUserType = variableProvider.getUserType(variableTypeName);
        this.variableProvider = variableProvider;

        setLayout(new GridLayout(1, false));
        setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, false, 2, 1));

        group = new Group(this, SWT.None);
        group.setLayout(new GridLayout(5, false));
        group.setLayoutData(new GridData(GridData.FILL_BOTH));
    }

    public void build() {
        if (!Strings.isNullOrEmpty(constraintsModel.getQueryString())) {
            final PredicateParser predicateParser = new PredicateParser(constraintsModel.getQueryString(), variableUserType, variableProvider);
            predicateTree.setHead(predicateParser.parse());
            if (!predicateTree.isEmpty()) {
                buildLabels();
                constructPredicateView();
            }
        }
    }

    public void addPredicate() {
        final VariablePredicate variablePredicate = new VariablePredicate();
        final int index = predicateTree.add(variablePredicate, expression -> buildCompoundTypeCombo(
                new OnConstructedPredicateDelegate<Object, VariablePredicate>(expression, this::onPredicateConstructed)));
        buildLabels();
        buildVariablePredicate(new OnConstructedPredicateDelegate<Variable, Variable>(variablePredicate, this::onPredicateConstructed), index);
    }

    private <X, Y> void constructPredicateView() {
        int currentVariablePredicateIndex = 0;
        for (ConstraintsPredicate<?, ?> predicate : predicateTree) {
            if (predicate instanceof VariablePredicate) {
                buildVariablePredicate(
                        new OnConstructedPredicateDelegate<Variable, Variable>((VariablePredicate) predicate, this::onPredicateConstructed),
                        currentVariablePredicateIndex++);
            } else {
                buildCompoundTypeCombo(new OnConstructedPredicateDelegate<X, Y>((ExpressionPredicate<?>) predicate, this::onPredicateConstructed));
            }
        }
    }

    private void buildCompoundTypeCombo(ConstraintsPredicate<?, ?> predicate) {
        final Combo compoundTypeCombo = new Combo(group, SWT.READ_ONLY);
        predicate.applicableOperationTypeNames().forEach(compoundTypeCombo::add);
        compoundTypeCombo.addSelectionListener(SelectionListener.widgetSelectedAdapter(e -> {
            final String text = compoundTypeCombo.getText();
            if (!Strings.isNullOrEmpty(text)) {
                predicate.setType(PredicateOperationType.byCode(text).orElse(null));
            }
        }));
        if (predicate.getType() != null) {
            compoundTypeCombo.setText(predicate.getType().code);
        }
    }

    public void buildVariablePredicate(OnConstructedPredicateDelegate<Variable, Variable> predicate, int index) {
        final Combo subjectCombo = new Combo(group, SWT.READ_ONLY);
        final Combo predicateOperationTypeCombo = new Combo(group, SWT.READ_ONLY);
        final Combo compareWithCombo = new Combo(group, SWT.READ_ONLY);

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
                variableProvider.variableNamesAccordingToType(var.getFormatClassName()).forEach(compareWithCombo::add);
            });
        }));
        if (predicate.getLeft() != null) {
            subjectCombo.setText(predicate.getLeft().getName());
            variableProvider.variableNamesAccordingToType(predicate.getLeft().getFormatClassName()).forEach(compareWithCombo::add);
        }

        compareWithCombo.addSelectionListener(SelectionListener.widgetSelectedAdapter((e) -> {
            final String text = compareWithCombo.getText();
            if (!Strings.isNullOrEmpty(text)) {
                variableProvider.variableByName(text).ifPresent(predicate::setRight);
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

    private void onPredicateConstructed() {
        constraintsModel.setQueryString(predicateTree.head() != null ? predicateTree.head().toString().trim() : "");
    }

    public void onDeletePredicate(int index) {
        predicateTree.removeVariablePredicateBy(index);
        onPredicateConstructed();

        labels.clear();
        for (Control c : group.getChildren()) {
            c.dispose();
        }
        build();
        getParent().layout(true, true);
    }

    private void buildLabels() {
        if (!labels.isEmpty()) {
            if (predicateTree.head() instanceof ExpressionPredicate<?>) {
                labels.get(4).setText(Messages.getString("label.LogicOperation"));
            }
            return;
        } else {
            labels.add(SwtUtils.createLabel(group, Messages.getString("label.DBTableField")));
            labels.add(SwtUtils.createLabel(group, Messages.getString("label.ComparisonOperation")));
            labels.add(SwtUtils.createLabel(group, Messages.getString("label.variable")));
            labels.add(SwtUtils.createLabel(group, ""));
            labels.add(SwtUtils.createLabel(group,
                    (predicateTree.head() instanceof ExpressionPredicate<?>) ? Messages.getString("label.LogicOperation") : ""));
        }
        getParent().layout(true, true);
    }
}
