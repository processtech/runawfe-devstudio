package ru.runa.gpd.editor.gef.part.tree;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

import org.eclipse.gef.editparts.AbstractTreeEditPart;

import ru.runa.gpd.PropertyNames;
import ru.runa.gpd.lang.model.GlobalSectionDefinition;
import ru.runa.gpd.lang.model.GraphElement;

public class ElementTreeEditPart extends AbstractTreeEditPart implements PropertyChangeListener, PropertyNames {

    public ElementTreeEditPart() {

    }

    public ElementTreeEditPart(GraphElement element) {
        setModel(element);
    }

    @Override
    public GraphElement getModel() {
        return (GraphElement) super.getModel();
    }

    @Override
    public void activate() {
        if (!isActive()) {
            getModel().addPropertyChangeListener(this);
            super.activate();
        }
    }

    @Override
    public void deactivate() {
        if (isActive()) {
            getModel().removePropertyChangeListener(this);
            super.deactivate();
        }
    }

    @Override
    protected void refreshVisuals() {
    	if (getModel() instanceof GlobalSectionDefinition && getModel().getLabel().length() >= 1) {
    		setWidgetText(getModel().getLabel().substring(1));
    	}
        setWidgetImage(getModel().getEntryImage());
    }

    @Override
    public void propertyChange(PropertyChangeEvent evt) {
        String messageId = evt.getPropertyName();
        if (PROPERTY_CHILDREN_CHANGED.equals(messageId)) {
            refreshChildren();
        } else if (PROPERTY_NAME.equals(messageId)) {
            refreshVisuals();
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    public Object getAdapter(Class key) {
        if (GraphElement.class.isAssignableFrom(key)) {
            GraphElement element = getModel();
            if (key.isAssignableFrom(element.getClass())) {
                return element;
            }
        }
        return super.getAdapter(key);
    }

}
