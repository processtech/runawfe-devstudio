package ru.runa.gpd.extension.regulations.ui;

import java.util.Arrays;
import java.util.Comparator;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.jface.viewers.ColumnWeightData;
import org.eclipse.jface.viewers.ISelectionChangedListener;
import org.eclipse.jface.viewers.IStructuredContentProvider;
import org.eclipse.jface.viewers.ITableLabelProvider;
import org.eclipse.jface.viewers.LabelProvider;
import org.eclipse.jface.viewers.SelectionChangedEvent;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.jface.viewers.TableLayout;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableColumn;
import org.eclipse.ui.part.ViewPart;

import ru.runa.gpd.Localization;
import ru.runa.gpd.PluginConstants;
import ru.runa.gpd.PluginLogger;
import ru.runa.gpd.SharedImages;
import ru.runa.gpd.editor.EditorBase;
import ru.runa.gpd.lang.model.GraphElement;
import ru.runa.gpd.lang.model.ProcessDefinition;
import ru.runa.gpd.util.UiUtil;
import ru.runa.gpd.util.WorkspaceOperations;

public class RegulationsSequenceView extends ViewPart implements ISelectionChangedListener {
    public static final String ID = "ru.runa.gpd.regulationsSequence";
    public static final String ORDER = "order";
    static final String[] COLUMN_NAMES = { Localization.getString("RegulationsSequenceView.Number"),
            Localization.getString("RegulationsSequenceView.Node"), Localization.getString("RegulationsSequenceView.Process") };
    private TableViewer viewer;
    private IMarker[] markers = new IMarker[0];

    @Override
    public void createPartControl(Composite parent) {
        UiUtil.hideToolBar(getViewSite());
        viewer = multiColumnViewer(parent);
        viewer.setContentProvider(new MarkerContentProvider());
        viewer.setLabelProvider(new MarkerLabelProvider());
        viewer.setInput(ResourcesPlugin.getWorkspace());
        viewer.addSelectionChangedListener(this);
    }

    private TableViewer multiColumnViewer(Composite parent) {
        Table table = new Table(parent, SWT.H_SCROLL | SWT.V_SCROLL | SWT.MULTI | SWT.FULL_SELECTION);
        TableLayout layout = new TableLayout();
        table.setLayout(layout);
        table.setLinesVisible(true);
        table.setHeaderVisible(true);
        layout.addColumnData(new ColumnWeightData(1, 20, true));
        new TableColumn(table, SWT.LEFT);
        layout.addColumnData(new ColumnWeightData(5, 40, true));
        TableColumn tc1 = new TableColumn(table, SWT.LEFT);
        tc1.setText(COLUMN_NAMES[0]);
        layout.addColumnData(new ColumnWeightData(10, true));
        TableColumn tc2 = new TableColumn(table, SWT.LEFT);
        tc2.setText(COLUMN_NAMES[1]);
        layout.addColumnData(new ColumnWeightData(10, true));
        TableColumn tc3 = new TableColumn(table, SWT.LEFT);
        tc3.setText(COLUMN_NAMES[2]);
        return new TableViewer(table);
    }

    @Override
    public void setFocus() {
        viewer.getControl().setFocus();
    }

    public void refresh(ProcessDefinition processDefinition) throws CoreException {
        markers = processDefinition.getFile().getProject().findMarkers(RegulationsSequenceView.ID, false, IResource.DEPTH_INFINITE);
        Arrays.sort(markers, OrderComparator.INSTANCE);
        viewer.getControl().getDisplay().asyncExec(new Runnable() {
            @Override
            public void run() {
                try {
                    viewer.refresh();
                } catch (Exception e) {
                    // widget is disposed
                }
            }
        });
    }

    @Override
    public void selectionChanged(SelectionChangedEvent event) {
        IMarker marker = (IMarker) ((StructuredSelection) viewer.getSelection()).getFirstElement();
        if (marker == null) {
            return;
        }
        try {
            IFile definitionFile = (IFile) marker.getResource();
            EditorBase editor = WorkspaceOperations.openProcessDefinition(definitionFile);
            String elementId = marker.getAttribute(PluginConstants.SELECTION_LINK_KEY, null);
            GraphElement graphElement = editor.getDefinition().getGraphElementById(elementId);
            if (graphElement != null) {
                editor.select(graphElement);
            }
        } catch (Exception e) {
            // don't display error to user
            PluginLogger.logErrorWithoutDialog("Unable select element", e);
        }
    }

    static class MarkerLabelProvider extends LabelProvider implements ITableLabelProvider {
        @Override
        public String getColumnText(Object obj, int index) {
            IMarker marker = (IMarker) obj;
            switch (index) {
            case 3:
                return marker.getAttribute(PluginConstants.PROCESS_NAME_KEY, "Undefined");
            case 2:
                return marker.getAttribute(IMarker.MESSAGE, "Undefined");
            case 1:
                return String.valueOf(marker.getAttribute(ORDER, 0));
            default:
                return "";
            }
        }

        @Override
        public Image getColumnImage(Object obj, int columnIndex) {
            if (columnIndex == 0) {
                IMarker marker = (IMarker) obj;
                int severity = marker.getAttribute(IMarker.SEVERITY, IMarker.SEVERITY_INFO);
                if (severity == IMarker.SEVERITY_ERROR) {
                    return SharedImages.getImage("icons/column_error.gif");
                }
                if (severity == IMarker.SEVERITY_WARNING) {
                    return SharedImages.getImage("icons/column_warning.gif");
                }
            }
            return null;
        }
    }

    class MarkerContentProvider implements IStructuredContentProvider {

        @Override
        public Object[] getElements(Object parent) {
            return markers;
        }

        @Override
        public void inputChanged(Viewer v, Object oldInput, Object newInput) {
        }

        @Override
        public void dispose() {
        }

    }

    static class OrderComparator implements Comparator<IMarker> {
        public static final OrderComparator INSTANCE = new OrderComparator();

        @Override
        public int compare(IMarker m1, IMarker m2) {
            return m1.getAttribute(ORDER, 0) - m2.getAttribute(ORDER, 0);
        }

    }
}
