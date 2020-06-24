package ru.runa.gpd.lang.model;

import java.util.List;
import org.eclipse.core.resources.IFile;
import org.eclipse.swt.graphics.Image;
import org.eclipse.ui.views.properties.IPropertyDescriptor;
import ru.runa.gpd.SharedImages;
import ru.runa.gpd.extension.HandlerArtifact;
import ru.runa.gpd.extension.orgfunction.OrgFunctionDefinition;
import ru.runa.gpd.lang.ValidationError;
import ru.runa.gpd.swimlane.SwimlaneInitializer;
import ru.runa.gpd.swimlane.SwimlaneInitializerParser;
import ru.runa.wfe.extension.assign.DefaultAssignmentHandler;
import ru.runa.wfe.var.format.ExecutorFormat;

public class Swimlane extends Variable implements Delegable {

    public static final String DEFAULT_DELEGATION_CLASS_NAME = DefaultAssignmentHandler.class.getName();
    private String editorPath;

    public Swimlane() {
        setFormat(ExecutorFormat.class.getName());
        setDelegationClassName(DEFAULT_DELEGATION_CLASS_NAME);
    }

    public String getEditorPath() {
        return editorPath;
    }

    public void setEditorPath(String editorPath) {
        String old = this.getEditorPath();
        this.editorPath = editorPath;
        firePropertyChange(PROPERTY_EDITOR_PATH, old, this.getEditorPath());
    }

    @Override
    public String getDelegationType() {
        return HandlerArtifact.ASSIGNMENT;
    }

    @Override
    public void setName(String name) {
        if (getProcessDefinition() != null && getProcessDefinition().getSwimlaneByName(name) != null) {
            return;
        }
        super.setName(name);
    }

    @Override
    public void validate(List<ValidationError> errors, IFile definitionFile) {
        super.validate(errors, definitionFile);
        try {
            if (DEFAULT_DELEGATION_CLASS_NAME.equals(getDelegationClassName())) {
                SwimlaneInitializer swimlaneInitializer = SwimlaneInitializerParser.parse(getDelegationConfiguration());
                if (swimlaneInitializer != null) {
                    swimlaneInitializer.validate(this, errors);
                }
            }
        } catch (RuntimeException e) {
            if (e.getMessage() != null && e.getMessage().startsWith(OrgFunctionDefinition.MISSED_DEFINITION)) {
                errors.add(ValidationError.createLocalizedWarning(this, "orgfunction.missed"));
            } else {
                errors.add(ValidationError.createLocalizedError(this, "orgfunction.broken"));
            }
        }
    }

    @Override
    public Image getEntryImage() {
        return SharedImages.getImage("icons/obj/swimlane.gif");
    }

    @Override
    protected void fillCopyCustomFields(GraphElement copy) {
        ((NamedGraphElement) copy).setName(getName());
        super.fillCopyCustomFields(copy);
        copy.setDelegationClassName(getDelegationClassName());
        copy.setDelegationConfiguration(getDelegationConfiguration());
    }

    @Override
    protected void fillCustomPropertyDescriptors(List<IPropertyDescriptor> descriptors) {
    }

}
