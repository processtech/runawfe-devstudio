package ru.runa.gpd.extension.handler;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.StyleRange;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.ui.forms.events.HyperlinkEvent;
import org.eclipse.ui.forms.widgets.Hyperlink;

import ru.runa.gpd.Localization;
import ru.runa.gpd.extension.DelegableConfigurationDialog;
import ru.runa.gpd.extension.DelegableProvider;
import ru.runa.gpd.lang.model.Delegable;
import ru.runa.gpd.lang.model.GraphElement;
import ru.runa.gpd.lang.model.Variable;
import ru.runa.gpd.ui.custom.HelpDialog;
import ru.runa.gpd.ui.custom.HighlightTextStyling;
import ru.runa.gpd.ui.custom.LoggingHyperlinkAdapter;
import ru.runa.gpd.ui.custom.SwtUtils;
import ru.runa.gpd.ui.dialog.ChooseItemDialog;
import ru.runa.gpd.ui.dialog.ChooseVariableNameDialog;

import com.google.common.base.Strings;
import com.google.common.collect.Lists;

public class FormulaCellEditorProvider extends DelegableProvider {

    @Override
    protected DelegableConfigurationDialog createConfigurationDialog(Delegable delegable) {
        return new ConfigurationDialog(delegable.getDelegationConfiguration(), delegable.getVariableNames(true));
    }

    @Override
    public List<String> getUsedVariableNames(Delegable delegable) {
        String configuration = delegable.getDelegationConfiguration();
        if (Strings.isNullOrEmpty(configuration)) {
            return Lists.newArrayList();
        }
        List<String> result = Lists.newArrayList();
        for (Variable variable : ((GraphElement) delegable).getProcessDefinition().getVariables(true, true)) {
            String variableName = variable.getName();
            if (variableName.indexOf(" ") != -1 || variableName.indexOf("-") != -1) {
                variableName = "'" + variableName + "'";
            }
            if (configuration.contains(variableName)) {
                result.add(variable.getName());
            }
        }
        return result;
    }

    @Override
    public String getConfigurationOnVariableRename(Delegable delegable, Variable currentVariable, Variable previewVariable) {
        String currentVariableName = currentVariable.getName();
        if (currentVariableName.indexOf(" ") != -1 || currentVariableName.indexOf("-") != -1) {
            currentVariableName = "'" + currentVariableName + "'";
        }
        String previewVariableName = previewVariable.getName();
        if (previewVariableName.indexOf(" ") != -1 || previewVariableName.indexOf("-") != -1) {
            previewVariableName = "'" + previewVariableName + "'";
        }
        return delegable.getDelegationConfiguration().replaceAll(Pattern.quote(currentVariableName), Matcher.quoteReplacement(previewVariableName));
    }

    public static class ConfigurationDialog extends DelegableConfigurationDialog {
        private final List<String> variableNames;

        public ConfigurationDialog(String initialValue, List<String> variableNames) {
            super(initialValue);
            this.variableNames = variableNames;
        }

        @Override
        protected void createDialogHeader(Composite parent) {
            Composite composite = new Composite(parent, SWT.NONE);
            composite.setLayout(new GridLayout(3, false));
            composite.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));

            Hyperlink hl1 = SwtUtils.createLink(composite, Localization.getString("help"), new LoggingHyperlinkAdapter() {
                @Override
                public void onLinkActivated(HyperlinkEvent e) throws IOException {
                    HelpDialog dialog = new HelpDialog(FormulaCellEditorProvider.class);
                    dialog.open();
                }
            });
            hl1.setLayoutData(new GridData(GridData.GRAB_HORIZONTAL | GridData.HORIZONTAL_ALIGN_END));

            Hyperlink hl2 = SwtUtils.createLink(composite, Localization.getString("button.insert_function"), new LoggingHyperlinkAdapter() {
                @Override
                public void onLinkActivated(HyperlinkEvent e) {
                    ChooseFunctionDialog dialog = new ChooseFunctionDialog();
                    String function = dialog.openDialog();
                    if (function != null) {
                        styledText.insert(function);
                        styledText.setFocus();
                        styledText.setCaretOffset(styledText.getCaretOffset() + function.length());
                    }
                }
            });
            hl2.setLayoutData(new GridData(GridData.HORIZONTAL_ALIGN_END));

            Hyperlink hl3 = SwtUtils.createLink(composite, Localization.getString("button.insert_variable"), new LoggingHyperlinkAdapter() {
                @Override
                public void onLinkActivated(HyperlinkEvent e) {
                    ChooseVariableNameDialog dialog = new ChooseVariableNameDialog(variableNames);
                    String variableName = dialog.openDialog();
                    if (variableName != null) {
                        if (variableName.indexOf(" ") != -1 || variableName.indexOf("-") != -1) {
                            variableName = "'" + variableName + "'";
                        }
                        styledText.insert(variableName);
                        styledText.setFocus();
                        styledText.setCaretOffset(styledText.getCaretOffset() + variableName.length());
                    }
                }
            });
            hl3.setLayoutData(new GridData(GridData.HORIZONTAL_ALIGN_END));
        }

        @Override
        protected void createDialogFooter(Composite composite) {
            styledText.addLineStyleListener(new FormulaConfigurationStyling(variableNames));
        }
    }

    public static class FormulaConfigurationStyling extends HighlightTextStyling {
        private static final Color VARIABLE_COLOR = new Color(null, 155, 155, 255);

        public FormulaConfigurationStyling(List<String> variables) {
            super(new ArrayList<RegexpHighlight>());
            for (String variableName : variables) {
                StyleRange styleRange = new StyleRange(0, 0, VARIABLE_COLOR, null, SWT.NORMAL);
                addHighlightDefinition(new RegexpHighlight(variableName, variableName, true, styleRange));
            }
        }

    }

    private static class ChooseFunctionDialog extends ChooseItemDialog<String> {
        private final static List<String> functions = new ArrayList<String>();
        static {
            functions.add("get_process_id()");
            functions.add("current_date()");
            functions.add("current_time()");
            functions.add("current_date_time()");
            functions.add("date(d1)");
            functions.add("time(d1)");
            functions.add("hours_round_up(n1)");
            functions.add("round(n1)");
            functions.add("round(n1, n2)");
            functions.add("round_down(n1)");
            functions.add("round_down(n1, n2)");
            functions.add("round_up(n1)");
            functions.add("round_up(n1, n2)");
            functions.add("number_to_string_ru(n1)");
            functions.add("number_to_string_ru(n1, s2, s3, s4, s5)");
            functions.add("number_to_short_string_ru(n1, s2, s3, s4, s5)");
            functions.add("FIO_case_ru(fio, caseNumber, mode)");
            functions.add("isExecutorInGroup(group, executor)");
            functions.add("ListToString(list)");
            functions.add("GetListMatchedIndexes(list, argument)");
            functions.add("GetListMismatchedIndexes(list1, list2)");
            functions.add("CreateSubListByIndexes(list, indexes)");
            functions.add("DeleteListElementsByIndexes(list, indexes)");
            functions.add("ToList(argument1[, argumentN])");
            functions.add("GetSize(container)");
        }

        public ChooseFunctionDialog() {
            super(Localization.getString("ChooseFunction.title"), functions, false, Localization.getString("ChooseFunction.message"), false);
        }
    }

}
