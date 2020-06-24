package ru.runa.gpd.aspects;

import org.eclipse.gef.EditPart;
import org.eclipse.graphiti.mm.pictograms.PictogramElement;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.PlatformUI;
import ru.runa.gpd.editor.CopyAction;
import ru.runa.gpd.editor.PasteAction;
import ru.runa.gpd.editor.ProcessEditorBase;
import ru.runa.gpd.editor.GlobalSectionEditorBase;
import ru.runa.gpd.editor.graphiti.DiagramEditorPage;
import ru.runa.gpd.lang.model.GraphElement;
import ru.runa.gpd.lang.model.ProcessDefinition;
import ru.runa.gpd.ui.action.Save;

public aspect ProcessEditorUserActivity extends UserActivity {

    after(ProcessEditorBase editor) returning : execution(public void init(..)) && this(editor) {
        startEditingSession(editor);
    }
    
    after(ProcessEditorBase editor) throwing(Exception e) : execution(public void init(..)) && this(editor) {
        startEditingSession(editor);
        log(editor.getDefinition(), UserAction.Exception.asString(e));
    }
    
    after(Save action) returning : execution(public void run(..)) && this(action) {
        IEditorPart editorPart = PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage().getActiveEditor();
        if (editorPart instanceof ProcessEditorBase) {
            ProcessEditorBase editor = (ProcessEditorBase) editorPart;
            startEditingSession(editor);
            log(editor.getDefinition(), UserAction.MM_Save.asString());
        }
    }
    
    after(Save action) throwing(Exception e) : execution(public void run(..)) && this(action) {
        IEditorPart editorPart = PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage().getActiveEditor();
        if (editorPart instanceof ProcessEditorBase) {
            ProcessEditorBase editor = (ProcessEditorBase) editorPart;
            startEditingSession(editor);
            log(editor.getDefinition(), UserAction.MM_Save.asString(e));
        }
    }
    
    after(ProcessEditorBase editor) : execution(public void dispose()) && this(editor) {
        stopEditingSession(editor.getDefinition());
    }
    
    // Diagram Element Selection

    after(ProcessEditorBase editor, ISelection selection) : execution(public void selectionChanged(.., ISelection)) && this(editor) && args(.., selection) {
        if (selection instanceof IStructuredSelection) {
            IStructuredSelection structuredSelection = (IStructuredSelection) selection;
            if (structuredSelection.size() > 0) {
                Object lastSelected = structuredSelection.toArray()[structuredSelection.size() - 1];
                if (lastSelected instanceof EditPart) {
                    EditPart editPart = (EditPart) lastSelected;
                    if (editPart.getModel() instanceof PictogramElement && !(editor instanceof GlobalSectionEditorBase)) {
                        GraphElement ge = (GraphElement) ((DiagramEditorPage) editor.getDiagramEditorPage()).getDiagramTypeProvider()
                                .getFeatureProvider().getBusinessObjectForPictogramElement((PictogramElement) editPart.getModel());
                        if (ge != null) {
                            if (structuredSelection.size() == 1) {
                                log(editor.getDefinition(), UserAction.GE_Select.asString(ge));
                            } else {
                                log(editor.getDefinition(), UserAction.GE_AddToSelection.asString(ge));
                            }
                        }
                    }
                }
            }
        }
    }

    after() returning : execution(public void ru.runa.gpd.editor.CopyAction.run()) {
        log(((CopyAction) thisJoinPoint.getThis()).processEditor.getDefinition(), UserAction.TB_Copy.asString());
    }

    after() throwing(Exception e) : execution(public void ru.runa.gpd.editor.CopyAction.run()) {
        log(((CopyAction) thisJoinPoint.getThis()).processEditor.getDefinition(), UserAction.TB_Copy.asString(e));
    }

    // Copy

    private ProcessEditorBase CopyAction.processEditor;

    after(ProcessEditorBase editor) : execution(ru.runa.gpd.editor.CopyAction.new(ProcessEditorBase)) && args(editor) {
        ((CopyAction) thisJoinPoint.getThis()).processEditor = editor;
    }

    after() returning : execution(public void ru.runa.gpd.editor.CopyAction.run()) {
        log(((CopyAction) thisJoinPoint.getThis()).processEditor.getDefinition(), UserAction.TB_Copy.asString());
    }

    after() throwing(Exception e) : execution(public void ru.runa.gpd.editor.CopyAction.run()) {
        log(((CopyAction) thisJoinPoint.getThis()).processEditor.getDefinition(), UserAction.TB_Copy.asString(e));
    }

    // Paste

    private ProcessEditorBase PasteAction.processEditor;

    after(ProcessEditorBase editor) : execution(ru.runa.gpd.editor.PasteAction.new(ProcessEditorBase)) && args(editor) {
        ((PasteAction) thisJoinPoint.getThis()).processEditor = editor;
    }

    after() returning : execution(public void ru.runa.gpd.editor.PasteAction.run()) {
        log(((PasteAction) thisJoinPoint.getThis()).processEditor.getDefinition(), UserAction.TB_Paste.asString());
    }

    after() throwing(Exception e) : execution(public void ru.runa.gpd.editor.PasteAction.run()) {
        log(((PasteAction) thisJoinPoint.getThis()).processEditor.getDefinition(), UserAction.TB_Paste.asString(e));
    }

    // Graph element change property

    pointcut graphElementProperyChange(String propertyName, Object oldValue, Object newValue) : 
        call(public void firePropertyChange(..)) && target(ru.runa.gpd.lang.model.GraphElement) && args(propertyName, oldValue, newValue) 
        && !cflow(call(public static ProcessDefinition ru.runa.gpd.ProcessCache.getProcessDefinition(..)))
        && !cflow(call(public static int ru.runa.gpd.lang.par.ProcessDefinitionValidator.validateDefinition(..)));

    after(String propertyName, Object oldValue, Object newValue) : graphElementProperyChange(propertyName, oldValue, newValue) {
        if (isStarted()) {
            GraphElement graphElement = (GraphElement) thisJoinPoint.getThis();
            log(graphElement.getProcessDefinition(), UserAction.GE_ChangeProperty.asString(graphElement, graphElement.getId(), propertyName, oldValue, newValue));
        }
    }

}
