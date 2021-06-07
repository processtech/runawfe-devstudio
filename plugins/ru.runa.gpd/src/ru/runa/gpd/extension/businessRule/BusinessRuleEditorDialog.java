package ru.runa.gpd.extension.businessRule;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.jface.window.Window;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Label;

import ru.runa.gpd.Localization;
import ru.runa.gpd.PluginLogger;
import ru.runa.gpd.SharedImages;
import ru.runa.gpd.extension.businessRule.BusinessRuleModel.IfExpr;
import ru.runa.gpd.extension.decision.GroovyEditorDialogType;
import ru.runa.gpd.extension.decision.GroovyTypeSupport;
import ru.runa.gpd.extension.decision.Operation;
import ru.runa.gpd.extension.handler.FormulaCellEditorProvider;
import ru.runa.gpd.lang.model.ProcessDefinition;
import ru.runa.gpd.lang.model.Variable;
import ru.runa.gpd.ui.custom.LoggingSelectionAdapter;
import ru.runa.gpd.ui.custom.TypedUserInputCombo;
import ru.runa.gpd.ui.dialog.FilterBox;
import ru.runa.gpd.ui.dialog.UserInputDialog;
import ru.runa.gpd.util.VariableUtils;

public class BusinessRuleEditorDialog extends GroovyEditorDialogType {
    private static final Image addImage = SharedImages.getImage("icons/add_obj.gif");
    private static final Image downImage = SharedImages.getImage("icons/down.png");
    private List<ExprLine> exprLines = new ArrayList();
    private String defaultFunction;

    public BusinessRuleEditorDialog(ProcessDefinition definition, String initValue) {
        super(definition, initValue);
        if (this.initValue.length() > 0) {
            try {
                initModel = new BusinessRuleModel(initValue, variables);
            } catch (Throwable e) {
                initErrorMessage = e.getMessage();
                PluginLogger.logErrorWithoutDialog("", e);
            }
        }
    }

    @Override
    protected void createConstructorView() {
        constructor.setLayout(new GridLayout(6, false));
        if (initModel != null) {
            defaultFunction = ((BusinessRuleModel) initModel).getDefaultFunction();
            for (int i = 0; i < ((BusinessRuleModel) initModel).getIfExprs().size(); i++) {
                createLines();
            }
        } else {
            createLines();
        }

        createBottomComposite();
    }

    private Composite createBottomComposite() {
        Composite bottomComposite = new Composite(constructor, SWT.NONE);
        bottomComposite.setLayout(new GridLayout(2, true));
        GridData data = new GridData(GridData.FILL_HORIZONTAL);
        data.horizontalSpan = 6;
        bottomComposite.setLayoutData(data);
        Label defaultLabel = new Label(bottomComposite, SWT.NONE);
        defaultLabel.setText(Localization.getString("GroovyEditor.allOtherCases") + ":");
        Button defaultButton = new Button(bottomComposite, SWT.NONE);
        defaultButton.setLayoutData(getGridData());
        if (defaultFunction != null) {
            defaultButton.setText(defaultFunction);
        }
        defaultButton.addSelectionListener(new LoggingSelectionAdapter() {
            @Override
            protected void onSelection(SelectionEvent e) throws Exception {
                FormulaCellEditorProvider.ConfigurationDialog dialog = new FormulaCellEditorProvider.ConfigurationDialog(defaultButton.getText(),
                        variableNames);
                if (dialog.open() == Window.OK) {
                    defaultButton.setText(dialog.getResult());
                    defaultFunction = defaultButton.getText();
                }
            }
        });
        bottomComposite.pack();
        return bottomComposite;
    }

    public class SyncFilterBoxHandler extends SelectionAdapter {
        @Override
        public void widgetSelected(SelectionEvent e) {
            FilterBox fb = ((FilterBox) e.widget);
            FilterBox targetFB;
            int[] indexData = (int[]) fb.getData(DATA_INDEX_KEY);
            int varNum = indexData[2] == 0 ? 0 : 1;
            if (indexData[1] == 0) {
                targetFB = exprLines.get(indexData[0]).getVariableBoxes().get(1)[varNum];
            } else {
                targetFB = exprLines.get(indexData[0]).getVariableBoxes().get(0)[varNum];
            }
            refresh(fb);
            targetFB.setSelectedItem(fb.getText());
            refresh(targetFB);
        }
    }

    public class SyncOperComboHandler extends SelectionAdapter {
        @Override
        public void widgetSelected(SelectionEvent e) {
            Combo combo = ((Combo) e.widget);
            Combo targetCombo;
            int[] indexData = (int[]) combo.getData(DATA_INDEX_KEY);
            if (indexData[1] == 0) {
                targetCombo = exprLines.get(indexData[0]).getOperationBoxes().get(1);
            } else {
                targetCombo = exprLines.get(indexData[0]).getOperationBoxes().get(0);
            }
            targetCombo.select(combo.getSelectionIndex());
            refresh(combo);
            refresh(targetCombo);
        }
    }

    private void createExpr(Composite parent, int indexLine) {
        ExprLine exprLine = exprLines.get(indexLine);

        Composite exprComposite = new Composite(parent, SWT.NONE);
        GridData data = new GridData(GridData.FILL_HORIZONTAL);
        if (parent == constructor) {
            exprComposite.setLayout(new GridLayout(3, true));
            data.horizontalSpan = 3;
        } else {
            exprComposite.setLayout(new GridLayout(4, true));
            data.horizontalSpan = 4;
        }
        exprComposite.setLayoutData(data);

        FilterBox[] variable = new FilterBox[2];
        exprLine.setVariableBox(variable);
        int indexExpr = exprLine.getVariableBoxes().indexOf(variable);
        variable[0] = new FilterBox(exprComposite, VariableUtils.getVariableNamesForScripting(variables));
        variable[0].setData(DATA_INDEX_KEY, new int[] { indexLine, indexExpr, 0 });
        variable[0].setSelectionListener(new FilterBoxSelectionHandler());
        if (indexExpr == 0 || indexExpr == 1) {
            variable[0].setSelectionListener(new SyncFilterBoxHandler());
        }
        variable[0].setLayoutData(getGridData());
        variable[0].setSize(100, 20);

        Combo operation = new Combo(exprComposite, SWT.READ_ONLY);
        exprLine.setOperationBox(operation);
        operation.setData(DATA_INDEX_KEY, new int[] { indexLine, indexExpr, 1 });
        operation.addSelectionListener(new ComboSelectionHandler());
        if (indexExpr == 0 || indexExpr == 1) {
            operation.addSelectionListener(new SyncOperComboHandler());
        }
        operation.setLayoutData(getGridData());

        variable[1] = new FilterBox(exprComposite, null);
        variable[1].setData(DATA_INDEX_KEY, new int[] { indexLine, indexExpr, 2 });
        variable[1].setSelectionListener(new FilterBoxSelectionHandler());
        if (indexExpr == 0 || indexExpr == 1) {
            variable[1].setSelectionListener(new SyncFilterBoxHandler());
        }
        variable[1].setLayoutData(getGridData());
        variable[1].setSize(100, 20);

        Combo logicBox = new Combo(exprComposite, SWT.READ_ONLY);
        exprLine.setLogicBox(logicBox);
        logicBox.setItems("null", "and", "or");
        logicBox.setLayoutData(getGridData());
        logicBox.select(0);
        logicBox.setData(logicBox.getText());
        logicBox.addSelectionListener(new LoggingSelectionAdapter() {
            @Override
            protected void onSelection(SelectionEvent e) throws Exception {
                Combo combo = (Combo) e.getSource();
                if (combo.getData().equals("null") && !combo.getText().equals("null")) {
                    combo.setData(combo.getText());
                    createExpr(parent, indexLine);
                    constructor.layout();
                } else if (combo.getText().equals("null") && !combo.getData().equals("null")) {
                    combo.setData(combo.getText());
                    int indexExpr = exprLine.getLogicBoxes().indexOf(combo);
                    while (indexExpr < parent.getChildren().length) {
                        parent.getChildren()[indexExpr].dispose();
                        exprLine.getVariableBoxes().remove(indexExpr + 1);
                        exprLine.getOperationBoxes().remove(indexExpr + 1);
                        exprLine.getLogicBoxes().remove(indexExpr + 1);
                        constructor.layout();
                    }
                }
            }
        });
        if (parent == constructor) {
            logicBox.setVisible(false);
            ((GridData) logicBox.getLayoutData()).exclude = true;
        }
        if (exprLine.getVariableBoxes().size() > 2) {
            exprLine.getExprButton().setBackground(new Color(Display.getCurrent(), 255, 219, 222));
        }

        exprComposite.pack();
    }

    private void createLines() {
        ExprLine exprLine = new ExprLine();
        exprLines.add(exprLine);
        createExpr(constructor, exprLines.size() - 1);

        Button functionButton = new Button(constructor, SWT.NONE);
        exprLine.setFunctionButton(functionButton);
        functionButton.setLayoutData(getGridData());
        functionButton.setData(exprLines.size() - 1);
        functionButton.setText(Localization.getString("GroovyEditor.functionButton") + exprLines.size());
        functionButton.addSelectionListener(new LoggingSelectionAdapter() {
            @Override
            protected void onSelection(SelectionEvent e) throws Exception {
                FormulaCellEditorProvider.ConfigurationDialog dialog = new FormulaCellEditorProvider.ConfigurationDialog(functionButton.getText(),
                        variableNames);
                if (dialog.open() == Window.OK) {
                    functionButton.setText(dialog.getResult());
                }
            }
        });

        Button addFunctionButton = new Button(constructor, SWT.PUSH);
        addFunctionButton.setImage(addImage);
        addFunctionButton.addSelectionListener(new LoggingSelectionAdapter() {
            @Override
            protected void onSelection(SelectionEvent e) throws Exception {
                constructor.getChildren()[constructor.getChildren().length - 1].dispose();
                createLines();
                createBottomComposite();
                constructor.layout();
            }
        });

        Button addExprButton = new Button(constructor, SWT.TOGGLE);
        exprLine.setExprButton(addExprButton);
        addExprButton.moveAbove(addFunctionButton);
        addExprButton.setToolTipText(Localization.getString("GroovyEditor.complexCondition"));
        addExprButton.setImage(downImage);
        addExprButton.setData(exprLines.size() - 1);
        addExprButton.addSelectionListener(new LoggingSelectionAdapter() {
            @Override
            protected void onSelection(SelectionEvent e) throws Exception {
                if (addExprButton.getSelection()) {
                    Composite complexExpression = exprLines.get((int) addExprButton.getData()).getComplexExpression();
                    complexExpression.setVisible(true);
                    ((GridData) complexExpression.getLayoutData()).exclude = false;
                    complexExpression.moveBelow(addFunctionButton);
                    constructor.layout();
                } else {
                    Composite complexExpression = exprLines.get((int) addExprButton.getData()).getComplexExpression();
                    complexExpression.setVisible(false);
                    ((GridData) complexExpression.getLayoutData()).exclude = true;
                    constructor.layout();
                }
            }
        });

        exprLine.createComplexExpressionBody();
    }

    @Override
    protected void initConstructorView() {
        List<IfExpr> ifExprs = ((BusinessRuleModel) initModel).getIfExprs();
        for (int i = 0; i < ifExprs.size(); i++) {
            IfExpr ifExpr = ifExprs.get(i);
            if (ifExpr != null) {
                ExprLine exprLine = exprLines.get(i);
                exprLine.getFunctionButton().setText(ifExpr.getFunction());
                for (int j = 0; j < ifExpr.getVariable1().size(); j++) {
                    Variable variable = ifExpr.getVariable1().get(j);
                    exprLine.getLogicBoxes().get(j).select(ifExpr.getLogicExprs().indexOf(ifExpr.getLogicExprs().get(j)));
                    exprLine.getLogicBoxes().get(j).setData(ifExpr.getLogicExprs().get(j));
                    int index = variables.indexOf(variable);
                    if (index == -1) {
                        // required variable was deleted in process definition
                        continue;
                    }
                    exprLine.getVariableBoxes().get(j)[0].select(index);
                    refresh(exprLine.getVariableBoxes().get(j)[0]);
                    GroovyTypeSupport typeSupport = GroovyTypeSupport.get(variable.getJavaClassName());
                    index = Operation.getAll(typeSupport).indexOf(ifExpr.getOperation().get(j));
                    if (index == -1) {
                        // required operation was deleted !!!
                        continue;
                    }
                    exprLine.getOperationBoxes().get(j).select(index);
                    refresh(exprLine.getOperationBoxes().get(j));
                    String lexem2Text = ifExpr.getLexem2TextValue(j);
                    int var2index = 0;
                    if (VariableUtils.getVariableByScriptingName(variables, lexem2Text) != null
                            && (VariableUtils.getVariableByScriptingName(variables, lexem2Text).getJavaClassName())
                                    .equals(variable.getJavaClassName())) {
                        var2index = getVariable2Names(variable).indexOf(lexem2Text);
                    } else {
                        int predefinedIndex = typeSupport.getPredefinedValues(ifExpr.getOperation().get(j)).indexOf(lexem2Text);
                        if (predefinedIndex >= 0) {
                            var2index = getVariable2Names(variable).size() + predefinedIndex;
                        } else {
                            exprLine.getVariableBoxes().get(j)[1].add(lexem2Text, 0);
                            exprLine.getVariableBoxes().get(j)[1].setData(DATA_USER_INPUT_KEY, lexem2Text);
                        }
                    }
                    exprLine.getVariableBoxes().get(j)[1].select(var2index);
                }
            }
        }
    }

    @Override
    protected void refresh(FilterBox filterBox) {
        try {
            int[] indexes = (int[]) filterBox.getData(DATA_INDEX_KEY);
            if (indexes[2] == 2) {
                // indexes[1] == 2 - second variable
                if (TypedUserInputCombo.INPUT_VALUE.equals(filterBox.getSelectedItem())) {
                    String oldUserInput = (String) filterBox.getData(DATA_USER_INPUT_KEY);
                    Variable variable1 = (Variable) exprLines.get(indexes[0]).getVariableBoxes().get(indexes[1])[0].getData(DATA_VARIABLE_KEY);
                    GroovyTypeSupport typeSupport = GroovyTypeSupport.get(variable1.getJavaClassName());
                    UserInputDialog inputDialog = typeSupport.createUserInputDialog();
                    inputDialog.setInitialValue(oldUserInput);
                    if (OK == inputDialog.open()) {
                        String userInput = inputDialog.getUserInput();
                        if (oldUserInput != null) {
                            filterBox.remove(0);
                        }
                        filterBox.setData(DATA_USER_INPUT_KEY, userInput);
                        filterBox.add(userInput, 0);
                        filterBox.select(0);
                    } else {
                        filterBox.deselectAll();
                    }
                } else {
                    Variable variable = VariableUtils.getVariableByScriptingName(variables, filterBox.getText());
                    if (variable != null) {
                        filterBox.setData(DATA_VARIABLE_KEY, variable);
                    }
                }
                return;
            }
            if (indexes[2] == 0) {
                // indexes[1] == 0 - first variable
                Combo operCombo = exprLines.get(indexes[0]).getOperationBoxes().get(indexes[1]);
                operCombo.setItems(new String[0]);
                Variable variable = VariableUtils.getVariableByScriptingName(variables, filterBox.getText());
                filterBox.setData(DATA_VARIABLE_KEY, variable);
                if (variable != null) {
                    GroovyTypeSupport typeSupport = GroovyTypeSupport.get(variable.getJavaClassName());
                    for (Operation operation : Operation.getAll(typeSupport)) {
                        operCombo.add(operation.getVisibleName());
                    }
                }
            }
        } catch (RuntimeException e) {
            PluginLogger.logError(e);
        }
    }

    @Override
    protected void refresh(Combo operCombo) {
        try {
            int[] indexes = (int[]) operCombo.getData(DATA_INDEX_KEY);
            Variable variable1 = (Variable) exprLines.get(indexes[0]).getVariableBoxes().get(indexes[1])[0].getData(DATA_VARIABLE_KEY);
            if (variable1 != null) {
                FilterBox targetCombo = exprLines.get(indexes[0]).getVariableBoxes().get(indexes[1])[1];
                targetCombo.setItems(new String[0]);
                GroovyTypeSupport typeSupport = GroovyTypeSupport.get(variable1.getJavaClassName());
                Operation operation = Operation.getByName(operCombo.getText(), typeSupport);
                operCombo.setData(DATA_OPERATION_KEY, operation);
                for (String variableName : getVariable2Names(variable1)) {
                    targetCombo.add(variableName);
                }
                for (String pv : typeSupport.getPredefinedValues(operation)) {
                    targetCombo.add(pv);
                }
                if (typeSupport.hasUserInputEditor()) {
                    targetCombo.add(TypedUserInputCombo.INPUT_VALUE);
                }
            }
        } catch (RuntimeException e) {
            PluginLogger.logError(e);
        }
    }

    @Override
    protected void toCode() {
        for (int i = 0; i < exprLines.size(); i++) {
            ExprLine exprLine = exprLines.get(i);
            for (int j = 0; j < exprLine.getVariableBoxes().size(); j++) {
                if (exprLine.getVariableBoxes().get(j)[0].getText().length() == 0 || exprLine.getVariableBoxes().get(j)[1].getText().length() == 0
                        && !exprLine.getFunctionButton().getText().equals(defaultFunction)) {
                    setErrorLabelText(Localization.getString("GroovyEditor.fillAll"));
                    // we cannot construct while all data not filled
                    return;
                }
            }
        }
        clearErrorLabelText();
        try {
            BusinessRuleModel model = new BusinessRuleModel();
            if (defaultFunction != null) {
                model.setDefaultFunction(defaultFunction);
            }
            for (int i = 0; i < exprLines.size(); i++) {
                ExprLine exprLine = exprLines.get(i);
                IfExpr ifExpr;
                List<Variable> variable1list = new ArrayList();
                List<Object> lexem2list = new ArrayList();
                List<Operation> operationNames = new ArrayList();
                List<String> logicExprs = new ArrayList();
                for (int j = 0; j < exprLine.getVariableBoxes().size(); j++) {
                    Variable variable1 = (Variable) exprLine.getVariableBoxes().get(j)[0].getData(DATA_VARIABLE_KEY);
                    String operationName = exprLine.getOperationBoxes().get(j).getItem(exprLine.getOperationBoxes().get(j).getSelectionIndex());
                    String lexem2Text = exprLine.getVariableBoxes().get(j)[1].getText();
                    Object lexem2;
                    Variable variable2 = VariableUtils.getVariableByScriptingName(variables, lexem2Text);
                    if (variable2 != null) {
                        if (variable2.getJavaClassName().equals(variable1.getJavaClassName())) {
                            lexem2 = variable2;
                        } else {
                            lexem2 = lexem2Text;
                        }
                    } else {
                        lexem2 = lexem2Text;
                    }
                    GroovyTypeSupport typeSupport = GroovyTypeSupport.get(variable1.getJavaClassName());

                    variable1list.add(variable1);
                    lexem2list.add(lexem2);
                    operationNames.add(Operation.getByName(operationName, typeSupport));
                    logicExprs.add(exprLine.getLogicBoxes().get(j).getText());
                }
                ifExpr = new IfExpr(exprLine.getFunctionButton().getText(), variable1list, lexem2list, operationNames, logicExprs);
                model.addIfExpr(ifExpr);
            }
            styledText.setText(model.toString());
        } catch (RuntimeException e1) {
            PluginLogger.logError(e1);
            setErrorLabelText(Localization.getString("GroovyEditor.error.construct"));
        }
    }

    private class ExprLine {
        private Composite complexExpression;
        private Button functionButton;
        private Button addExprButton;
        private List<FilterBox[]> variableBoxes = new ArrayList();
        private List<Combo> operationBoxes = new ArrayList();
        private List<Combo> logicBoxes = new ArrayList();
        private int indexLine;

        public ExprLine() {
            complexExpression = new Composite(constructor, SWT.BORDER);
            GridData data = new GridData(GridData.FILL_HORIZONTAL);
            data.horizontalSpan = 6;
            complexExpression.setLayoutData(data);
            complexExpression.setLayout(new GridLayout(1, true));
            complexExpression.setVisible(false);
            data.exclude = true;
        }

        public void createComplexExpressionBody() {
            indexLine = exprLines.indexOf(this);
            createExpr(complexExpression, indexLine);
            if (initModel != null) {
                List<IfExpr> ifExprs = ((BusinessRuleModel) initModel).getIfExprs();
                if (ifExprs.size() > indexLine) {
                    IfExpr ifExpr = ifExprs.get(indexLine);
                    for (int j = 2; j < ifExpr.getVariable1().size(); j++) {
                        createExpr(complexExpression, indexLine);
                    }
                }
            }
        }

        public Composite getComplexExpression() {
            return complexExpression;
        }

        public Button getFunctionButton() {
            return functionButton;
        }

        public void setFunctionButton(Button functionButton) {
            this.functionButton = functionButton;
        }

        public Button getExprButton() {
            return addExprButton;
        }

        public void setExprButton(Button button) {
            addExprButton = button;
        }

        public void setVariableBox(FilterBox[] filterBox) {
            variableBoxes.add(filterBox);
        }

        public void setOperationBox(Combo operationCombo) {
            operationBoxes.add(operationCombo);
        }

        public void setLogicBox(Combo logicBox) {
            logicBoxes.add(logicBox);
        }

        public List<FilterBox[]> getVariableBoxes() {
            return variableBoxes;
        }

        public List<Combo> getOperationBoxes() {
            return operationBoxes;
        }

        public List<Combo> getLogicBoxes() {
            return logicBoxes;
        }

    }

}
