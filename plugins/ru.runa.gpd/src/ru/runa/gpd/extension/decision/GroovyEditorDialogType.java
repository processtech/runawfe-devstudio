package ru.runa.gpd.extension.decision;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.draw2d.ColorConstants;
import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.ScrolledComposite;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.TabFolder;
import org.eclipse.swt.widgets.TabItem;
import org.eclipse.ui.forms.events.HyperlinkEvent;
import ru.runa.gpd.Localization;
import ru.runa.gpd.lang.model.ProcessDefinition;
import ru.runa.gpd.lang.model.Variable;
import ru.runa.gpd.ui.custom.JavaHighlightTextStyling;
import ru.runa.gpd.ui.custom.LoggingHyperlinkAdapter;
import ru.runa.gpd.ui.custom.SwtUtils;
import ru.runa.gpd.ui.dialog.ChooseGroovyStuffDialog;
import ru.runa.gpd.ui.dialog.ChooseVariableNameDialog;
import ru.runa.gpd.ui.dialog.FilterBox;
import ru.runa.gpd.util.GroovyStuff;
import ru.runa.gpd.util.VariableUtils;
import ru.runa.gpd.util.GroovyStuff.Item;

public abstract class GroovyEditorDialogType extends Dialog {
    protected TabFolder tabFolder;
    protected StyledText styledText;
    protected Composite constructor;
    protected final String initValue;
    protected GroovyModel initModel;
    protected String initErrorMessage;
    protected final List<Variable> variables;
    protected final List<String> variableNames;
    protected ErrorHeaderComposite constructorHeader;
    protected ErrorHeaderComposite sourceHeader;
    protected String result;

    public GroovyEditorDialogType(ProcessDefinition definition, String initValue) {
        super(Display.getCurrent().getActiveShell());
        setShellStyle(getShellStyle() | SWT.RESIZE);
        this.initValue = initValue;
        this.variables = definition.getVariables(true, true);
        this.variableNames = VariableUtils.getVariableNamesForScripting(variables);
    }

    @Override
    protected Point getInitialSize() {
        return new Point(700, 400);
    }

    @Override
    protected Control createDialogArea(Composite parent) {
        getShell().setText(Localization.getString("GroovyEditor.title"));
        tabFolder = new TabFolder(parent, SWT.BORDER);
        tabFolder.setLayoutData(new GridData(GridData.FILL_BOTH));
        tabFolder.addSelectionListener(new TabSelectionHandler());
        Composite constructorView = new Composite(tabFolder, SWT.NONE);
        constructorView.setLayout(new GridLayout());
        TabItem tabItem1 = new TabItem(tabFolder, SWT.NONE);
        tabItem1.setText(Localization.getString("GroovyEditor.title.constructor"));
        tabItem1.setControl(constructorView);
        constructorHeader = new ErrorHeaderComposite(constructorView);
        ScrolledComposite scrolledComposite = new ScrolledComposite(constructorView, SWT.V_SCROLL | SWT.BORDER);
        scrolledComposite.setExpandHorizontal(true);
        scrolledComposite.setExpandVertical(true);
        scrolledComposite.setMinHeight(200);
        scrolledComposite.setLayoutData(new GridData(GridData.FILL_BOTH));
        constructor = new Composite(scrolledComposite, SWT.NONE);
        scrolledComposite.setContent(constructor);
        GridLayout gridLayout = new GridLayout();
        gridLayout.numColumns = 5;
        constructor.setLayout(gridLayout);
        constructor.setLayoutData(new GridData(GridData.FILL_BOTH));
        Composite sourceView = new Composite(tabFolder, SWT.NONE);
        sourceView.setLayout(new GridLayout());
        sourceView.setLayoutData(new GridData(GridData.FILL_BOTH));
        sourceHeader = new ErrorHeaderComposite(sourceView);
        if (GroovyStuff.TYPE.getAll().size() > 0) {
            SwtUtils.createLink(sourceHeader, Localization.getString("Insert.TYPE.link"), new LoggingHyperlinkAdapter() {
                @Override
                public void onLinkActivated(HyperlinkEvent e) {
                    Item item = new ChooseGroovyStuffDialog(GroovyStuff.TYPE).openDialog();
                    if (item != null) {
                        String insert = item.getBody();
                        styledText.insert(insert);
                        styledText.setCaretOffset(styledText.getCaretOffset() + insert.length());
                        styledText.setFocus();
                    }
                }
            }).setLayoutData(new GridData(GridData.HORIZONTAL_ALIGN_END));
        }
        if (GroovyStuff.CONSTANT.getAll().size() > 0) {
            SwtUtils.createLink(sourceHeader, Localization.getString("Insert.CONSTANT.link"), new LoggingHyperlinkAdapter() {
                @Override
                public void onLinkActivated(HyperlinkEvent e) {
                    Item item = new ChooseGroovyStuffDialog(GroovyStuff.CONSTANT).openDialog();
                    if (item != null) {
                        String insert = item.getBody();
                        styledText.insert(insert);
                        styledText.setCaretOffset(styledText.getCaretOffset() + insert.length());
                        styledText.setFocus();
                    }
                }
            }).setLayoutData(new GridData(GridData.HORIZONTAL_ALIGN_END));
        }
        if (GroovyStuff.STATEMENT.getAll().size() > 0) {
            SwtUtils.createLink(sourceHeader, Localization.getString("Insert.STATEMENT.link"), new LoggingHyperlinkAdapter() {
                @Override
                public void onLinkActivated(HyperlinkEvent e) {
                    Item item = new ChooseGroovyStuffDialog(GroovyStuff.STATEMENT).openDialog();
                    if (item != null) {
                        String insert = item.getBody();
                        styledText.insert(insert);
                        styledText.setCaretOffset(styledText.getCaretOffset() + insert.length());
                        styledText.setFocus();
                    }
                }
            }).setLayoutData(new GridData(GridData.HORIZONTAL_ALIGN_END));
        }
        if (GroovyStuff.METHOD.getAll().size() > 0) {
            SwtUtils.createLink(sourceHeader, Localization.getString("Insert.METHOD.link"), new LoggingHyperlinkAdapter() {
                @Override
                public void onLinkActivated(HyperlinkEvent e) {
                    Item item = new ChooseGroovyStuffDialog(GroovyStuff.METHOD).openDialog();
                    if (item != null) {
                        String insert = item.getBody();
                        styledText.insert(insert);
                        styledText.setCaretOffset(styledText.getCaretOffset() + insert.length());
                        styledText.setFocus();
                    }
                }
            }).setLayoutData(new GridData(GridData.HORIZONTAL_ALIGN_END));
        }
        SwtUtils.createLink(sourceHeader, Localization.getString("button.insert_variable"), new LoggingHyperlinkAdapter() {
            @Override
            protected void onLinkActivated(HyperlinkEvent e) throws Exception {
                ChooseVariableNameDialog dialog = new ChooseVariableNameDialog(variableNames);
                String variableName = dialog.openDialog();
                if (variableName != null) {
                    styledText.insert(variableName);
                    styledText.setFocus();
                    styledText.setCaretOffset(styledText.getCaretOffset() + variableName.length());
                }
            }
        }).setLayoutData(new GridData(GridData.HORIZONTAL_ALIGN_END));
        styledText = new StyledText(sourceView, SWT.MULTI | SWT.BORDER | SWT.H_SCROLL | SWT.V_SCROLL);
        styledText.addLineStyleListener(new JavaHighlightTextStyling(variableNames));
        styledText.setText(this.initValue);
        styledText.setLayoutData(new GridData(GridData.FILL_BOTH));
        TabItem tabItem2 = new TabItem(tabFolder, SWT.NONE);
        tabItem2.setText(Localization.getString("GroovyEditor.title.code"));
        tabItem2.setControl(sourceView);
        createConstructorView();
        try {
            if (initModel != null && initValue.equals(initModel.toString())) {
                initConstructorView();
            } else {
                if (this.initValue.length() > 0) {
                    tabFolder.setSelection(1);
                }
                if (initErrorMessage != null) {
                    setErrorLabelText(initErrorMessage);
                }
            }
        } catch (RuntimeException e) {
            // Activate source view if custom code found
            tabFolder.setSelection(1);
        }
        return tabFolder;
    }

    protected void setErrorLabelText(String text) {
        constructorHeader.setErrorText(text);
        sourceHeader.setErrorText(text);
    }

    protected void clearErrorLabelText() {
        constructorHeader.clearErrorText();
        sourceHeader.clearErrorText();
    }

    protected void createConstructorView() {
    };

    protected void initConstructorView() {
    };

    protected static final String DATA_INDEX_KEY = "indexes";
    protected static final String DATA_VARIABLE_KEY = "variable";
    protected static final String DATA_USER_INPUT_KEY = "userInput";
    protected static final String DATA_OPERATION_KEY = "operation";

    protected GridData getGridData() {
        GridData data = new GridData(GridData.FILL_HORIZONTAL);
        data.minimumWidth = 100;
        return data;
    }

    protected void refresh(Combo operCombo) {
    };

    protected void refresh(FilterBox filterBox) {
    };

    protected List<String> getVariable2Names(Variable variable1) {
        List<String> names = new ArrayList<String>();
        GroovyTypeSupport typeSupport1 = GroovyTypeSupport.get(variable1.getJavaClassName());
        for (Variable variable : variables) {
            GroovyTypeSupport typeSupport = GroovyTypeSupport.get(variable.getJavaClassName());
            if (typeSupport1.getClass() == typeSupport.getClass() && variable1 != variable) {
                names.add(variable.getScriptingName());
            }
        }
        return names;
    }

    protected class TabSelectionHandler extends SelectionAdapter {
        @Override
        public void widgetSelected(SelectionEvent e) {
            if (tabFolder.getSelectionIndex() == 1) {
                toCode();
            }
        }
    }

    protected class ErrorHeaderComposite extends Composite {
        private final Label errorLabel;

        public ErrorHeaderComposite(Composite parent) {
            super(parent, SWT.NONE);
            setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
            setLayout(new GridLayout(6, false));
            errorLabel = new Label(this, SWT.NONE);
            errorLabel.setForeground(ColorConstants.red);
            errorLabel.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
        }

        public void setErrorText(String text) {
            errorLabel.setText(text);
        }

        public void clearErrorText() {
            setErrorText("");
        }
    }

    public class ComboSelectionHandler extends SelectionAdapter {
        @Override
        public void widgetSelected(SelectionEvent e) {
            refresh((Combo) e.widget);
        }
    }

    public class FilterBoxSelectionHandler extends SelectionAdapter {
        @Override
        public void widgetSelected(SelectionEvent e) {
            refresh((FilterBox) e.widget);
        }
    }

    protected void toCode() {
    };

    @Override
    protected void okPressed() {
        if (tabFolder.getSelectionIndex() == 0) {
            toCode();
        }
        this.result = styledText.getText();
        super.okPressed();
    }

    public String getResult() {
        return result;
    }

}
