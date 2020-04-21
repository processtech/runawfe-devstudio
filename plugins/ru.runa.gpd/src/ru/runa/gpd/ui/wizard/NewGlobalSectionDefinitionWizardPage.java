package ru.runa.gpd.ui.wizard;

import java.util.List;
import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IFolder;
import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.wizard.WizardPage;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.ModifyEvent;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Text;
import ru.runa.gpd.Activator;
import ru.runa.gpd.Localization;
import ru.runa.gpd.form.FormCSSTemplate;
import ru.runa.gpd.form.FormCSSTemplateRegistry;
import ru.runa.gpd.lang.Language;
import ru.runa.gpd.lang.model.ProcessDefinition;
import ru.runa.gpd.settings.PrefConstants;
import ru.runa.gpd.ui.custom.FileNameChecker;
import ru.runa.gpd.util.IOUtils;
import ru.runa.gpd.util.SwimlaneDisplayMode;

public class NewGlobalSectionDefinitionWizardPage extends WizardPage {
    private Combo projectCombo;
    private Text processText;
    private Combo languageCombo;
    private Combo bpmnDisplaySwimlaneCombo;
    private Combo cssTemplateCombo;
    private final IContainer initialSelection;
    private final List<IContainer> processContainers;
    private final ProcessDefinition parentProcessDefinition;

    public NewGlobalSectionDefinitionWizardPage(IStructuredSelection selection, ProcessDefinition parentProcessDefinition) {
        super(Localization.getString("NewProcessDefinitionWizardPage.page.name"));
        setTitle(Localization.getString("NewGlobalSectionDefinitionWizardPage.page.title"));
        setDescription(Localization.getString("NewGlobalSectionWizardPage.page.description"));
        this.initialSelection = (IContainer) IOUtils.getProcessSelectionResource(selection);
        this.processContainers = IOUtils.getAllProcessContainers();
        this.parentProcessDefinition = parentProcessDefinition;
    }

    @Override
    public void createControl(Composite parent) {
        initializeDialogUnits(parent);
        Composite composite = new Composite(parent, SWT.NONE);
        GridLayout layout = new GridLayout();
        layout.marginWidth = 0;
        layout.marginHeight = 0;
        layout.numColumns = 2;
        composite.setLayout(layout);
        createProjectField(composite);
        createProcessNameField(composite);
        createLanguageCombo(composite);
        createBpmnDisplaySwimlaneCombo(composite);
        createCssTemplateCombo(composite);
        setControl(composite);
        Dialog.applyDialogFont(composite);
        setPageComplete(false);
        processText.setFocus();
    }

    private void createProjectField(Composite parent) {
        Label label = new Label(parent, SWT.NONE);
        label.setText(Localization.getString("label.project"));
        projectCombo = new Combo(parent, SWT.SINGLE | SWT.READ_ONLY | SWT.BORDER);
        for (IContainer container : processContainers) {
            projectCombo.add(IOUtils.getProcessContainerName(container));
        }
        projectCombo.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
        if (initialSelection != null) {
            projectCombo.setText(IOUtils.getProcessContainerName(initialSelection));
        }
        projectCombo.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                verifyContentsValid();
            }
        });
        if (parentProcessDefinition != null) {
            projectCombo.setText(parentProcessDefinition.getName());
            projectCombo.setEnabled(false);
        }
    }

    private void createProcessNameField(Composite parent) {
        Label label = new Label(parent, SWT.NONE);
        label.setText(Localization.getString("label.section_name"));
        processText = new Text(parent, SWT.BORDER);
        processText.addModifyListener(new ModifyListener() {
            @Override
            public void modifyText(ModifyEvent e) {
                verifyContentsValid();
            }
        });
        processText.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
    }

    private void createLanguageCombo(Composite parent) {
        Label label = new Label(parent, SWT.NONE);
        label.setText(Localization.getString("label.language"));
        languageCombo = new Combo(parent, SWT.SINGLE | SWT.READ_ONLY | SWT.BORDER);
        for (Language language : Language.values()) {
            languageCombo.add(language.name());
        }
        String defaultLanguage = Activator.getPrefString(PrefConstants.P_DEFAULT_LANGUAGE);
        languageCombo.setText(defaultLanguage);
        languageCombo.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
        languageCombo.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                boolean enabled = languageCombo.getSelectionIndex() == 1;
                bpmnDisplaySwimlaneCombo.setEnabled(enabled);
                if (!enabled) {
                    bpmnDisplaySwimlaneCombo.select(0);
                }
            }
        });
        if (parentProcessDefinition != null) {
            languageCombo.setText(parentProcessDefinition.getLanguage().name());
            languageCombo.setEnabled(false);
        }
        label.setVisible(false);
        languageCombo.setVisible(false);
    }

    private void createBpmnDisplaySwimlaneCombo(Composite parent) {
        Label label = new Label(parent, SWT.NONE);
        label.setText(Localization.getString("label.bpmn.display.swimlane"));
        bpmnDisplaySwimlaneCombo = new Combo(parent, SWT.SINGLE | SWT.READ_ONLY | SWT.BORDER);
        for (SwimlaneDisplayMode mode : SwimlaneDisplayMode.values()) {
            bpmnDisplaySwimlaneCombo.add(mode.getLabel());
        }
        bpmnDisplaySwimlaneCombo.select(0);
        bpmnDisplaySwimlaneCombo.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
        if (parentProcessDefinition != null) {
            bpmnDisplaySwimlaneCombo.setText(SwimlaneDisplayMode.none.getLabel());
            bpmnDisplaySwimlaneCombo.setEnabled(false);
        }
        label.setVisible(false);
        bpmnDisplaySwimlaneCombo.setVisible(false);
    }

    private void createCssTemplateCombo(Composite parent) {
        Label label = new Label(parent, SWT.NONE);
        label.setText(Localization.getString("label.form.css.template"));
        cssTemplateCombo = new Combo(parent, SWT.SINGLE | SWT.READ_ONLY | SWT.BORDER);
        cssTemplateCombo.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
        if (parentProcessDefinition != null) {
            cssTemplateCombo.add(Localization.getString("inherited"));
            cssTemplateCombo.setEnabled(false);
        } else {
            cssTemplateCombo.add(Localization.getString("none"));
            for (FormCSSTemplate template : FormCSSTemplateRegistry.getTemplates()) {
                cssTemplateCombo.add(template.getName());
            }
        }
        cssTemplateCombo.select(0);
        label.setVisible(false);
        cssTemplateCombo.setVisible(false);
    }

    private void verifyContentsValid() {
        if (projectCombo.getText().length() == 0) {
            setErrorMessage(Localization.getString("error.choose_project"));
            setPageComplete(false);
        } else if (processText.getText().length() == 0) {
            setErrorMessage(Localization.getString("error.no_process_name"));
            setPageComplete(false);
        } else if (!FileNameChecker.isValid(processText.getText())) {
            setErrorMessage(Localization.getString("error.process_name_not_valid"));
            setPageComplete(false);
        } else if (isProcessExists()) {
            setErrorMessage(Localization.getString("error.process_already_exists"));
            setPageComplete(false);
        } else {
            setErrorMessage(null);
            setPageComplete(true);
        }
    }

    private boolean isProcessExists() {
        if (parentProcessDefinition != null) {
            return parentProcessDefinition.getEmbeddedSubprocessByName(getProcessName()) != null;
        } else {
            return getProcessFolder().exists();
        }
    }

    public String getProcessName() {
        return processText.getText();
    }

    public Language getLanguage() {
        return Language.valueOf(languageCombo.getText());
    }

    public SwimlaneDisplayMode getSwimlaneDisplayMode() {
        return SwimlaneDisplayMode.values()[bpmnDisplaySwimlaneCombo.getSelectionIndex()];
    }

    public String getFormCSSTemplateName() {
        if (cssTemplateCombo.getSelectionIndex() == 0) {
            return null;
        }
        return cssTemplateCombo.getText();
    }

    public IFolder getProcessFolder() {
        IContainer container = processContainers.get(projectCombo.getSelectionIndex());
        if (parentProcessDefinition != null) {
            return (IFolder) container;
        } else {
            return IOUtils.getProcessFolder(container, getProcessName());
        }
    }
}
