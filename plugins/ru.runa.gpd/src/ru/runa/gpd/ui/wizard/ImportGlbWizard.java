package ru.runa.gpd.ui.wizard;

import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.wizard.Wizard;
import org.eclipse.ui.IImportWizard;
import org.eclipse.ui.IWorkbench;

import ru.runa.gpd.Localization;

public class ImportGlbWizard extends Wizard implements IImportWizard {

    private ImportGlbWizardPage mainPage;

    @Override
    public boolean performFinish() {
        return mainPage.performFinish();
    }

    @Override
    public void init(IWorkbench workbench, IStructuredSelection selection) {
        setWindowTitle(Localization.getString("ImportGlbWizard.wizard.title"));
        mainPage = new ImportGlbWizardPage(Localization.getString("ImportGlbWizard.wizard.title"), selection);
    }

    @Override
    public void addPages() {
        addPage(mainPage);
    }

}
