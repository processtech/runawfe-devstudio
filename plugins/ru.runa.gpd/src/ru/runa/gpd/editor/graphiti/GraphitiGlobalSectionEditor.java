package ru.runa.gpd.editor.graphiti;

import org.eclipse.gef.EditPart;
import org.eclipse.gef.ui.parts.GraphicalEditor;
import org.eclipse.graphiti.mm.pictograms.PictogramElement;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IEditorReference;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.views.properties.IPropertySource;
import ru.runa.gpd.PropertyNames;
import ru.runa.gpd.editor.GlobalSectionEditorBase;
import ru.runa.gpd.lang.model.GraphElement;

public class GraphitiGlobalSectionEditor extends GlobalSectionEditorBase {
    public final static String ID = "ru.runa.gpd.GraphitiGlobalSectionEditor";

    public static void refreshAllActiveEditors() {
        IWorkbenchPage page = PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage();
        for (IEditorReference ref : page.getEditorReferences()) {
            IEditorPart editor = ref.getEditor(true);
        }
    }

    public IPropertySource translateSelection(ISelection selection) {
        if (selection instanceof IStructuredSelection) {
            IStructuredSelection structuredSelection = (IStructuredSelection) selection;
            Object object = structuredSelection.getFirstElement();
            if (object instanceof EditPart) {
                EditPart editPart = (EditPart) object;
                if (editPart.getModel() instanceof PictogramElement) {
                    PictogramElement pe = (PictogramElement) editPart.getModel();
                }
            }
            if (object instanceof IPropertySource) {
                return (IPropertySource) object;
            }
        }
        return null;
    }

}
