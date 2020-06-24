package ru.runa.gpd.editor.graphiti;

import java.beans.PropertyChangeEvent;
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
            if (editor instanceof GraphitiGlobalSectionEditor) {
                ((GraphitiGlobalSectionEditor) editor).getDiagramEditorPage().applyStyles();
                ((GraphitiGlobalSectionEditor) editor).getDiagramEditorPage().refreshConnections();
            }
        }
    }

    @Override
    protected GraphicalEditor createGraphPage() {
        return new DiagramEditorPage(this);
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

    @Override
    public void propertyChange(PropertyChangeEvent evt) {
        super.propertyChange(evt);
        if (PropertyNames.PROPERTY_SHOW_ACTIONS.equals(evt.getPropertyName())) {
            getDiagramEditorPage().getDiagramBehavior().refreshPalette();
            getDiagramEditorPage().refreshActions();
            // getDiagramEditorPage().getContentEditPart().refresh();
            // getRootFigure().getUpdateManager().performUpdate();
            getDiagramEditorPage().getDiagramBehavior().refreshContent();
        }
    }

	@Override
	protected void selectGraphElement(GraphElement model) {
		// TODO Auto-generated method stub
		
	}

	@Override
	protected void updateGridLayerVisibility(boolean enabled) {
		// TODO Auto-generated method stub
		
	}

}
