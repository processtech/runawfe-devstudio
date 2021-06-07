package ru.runa.gpd.editor.graphiti.add;

import org.eclipse.graphiti.features.context.IAddContext;
import org.eclipse.graphiti.mm.GraphicsAlgorithmContainer;
import org.eclipse.graphiti.mm.algorithms.Image;
import org.eclipse.graphiti.mm.pictograms.ContainerShape;
import org.eclipse.graphiti.services.Graphiti;

import ru.runa.gpd.editor.graphiti.GaProperty;
import ru.runa.gpd.lang.model.Node;

public class AddBusinessRuleNodeFeature extends AddStateNodeFeature {

    @Override
    protected void addCustomGraphics(Node node, IAddContext context, GraphicsAlgorithmContainer container, ContainerShape containerShape) {
        Image image = Graphiti.getGaService().createImage(container, "br.png");
        image.getProperties().add(new GaProperty(GaProperty.ID, GaProperty.SCRIPT));
        Graphiti.getGaService().setLocationAndSize(image, GRID_SIZE, GRID_SIZE, ICON_WIDTH, ICON_HEIGHT);
    }

}
