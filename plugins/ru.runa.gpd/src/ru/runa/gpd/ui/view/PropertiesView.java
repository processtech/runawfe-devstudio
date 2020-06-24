package ru.runa.gpd.ui.view;

import com.google.common.base.Objects;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import org.eclipse.core.runtime.IAdaptable;
import org.eclipse.draw2d.ColorConstants;
import org.eclipse.jface.viewers.CellEditor;
import org.eclipse.jface.viewers.ComboBoxCellEditor;
import org.eclipse.jface.viewers.ICellEditorListener;
import org.eclipse.jface.viewers.ICellEditorValidator;
import org.eclipse.jface.viewers.ILabelProvider;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.swt.SWT;
import org.eclipse.swt.SWTException;
import org.eclipse.swt.custom.TreeEditor;
import org.eclipse.swt.events.ControlAdapter;
import org.eclipse.swt.events.ControlEvent;
import org.eclipse.swt.events.MouseAdapter;
import org.eclipse.swt.events.MouseEvent;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Tree;
import org.eclipse.swt.widgets.TreeColumn;
import org.eclipse.swt.widgets.TreeItem;
import org.eclipse.ui.IPartListener;
import org.eclipse.ui.ISelectionListener;
import org.eclipse.ui.IViewSite;
import org.eclipse.ui.IWorkbenchPart;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.internal.views.properties.PropertiesMessages;
import org.eclipse.ui.part.ViewPart;
import org.eclipse.ui.views.properties.IPropertyDescriptor;
import org.eclipse.ui.views.properties.IPropertySource;
import ru.runa.gpd.editor.graphiti.GraphitiProcessEditor;
import ru.runa.gpd.editor.graphiti.GraphitiGlobalSectionEditor;
import ru.runa.gpd.lang.model.GraphElement;
import ru.runa.gpd.util.UiUtil;

public class PropertiesView extends ViewPart implements ISelectionListener, PropertyChangeListener, IPartListener {
    public static final String ID = "ru.runa.gpd.propertiesView";
    public static final String CELL_EDITOR_KEY = "CellEditor";
    public static final String CELL_EDITOR_LISTENER_KEY = "CellEditorListener";
    private static final String TREE_EDITOR_KEY = "TreeEditor";
    public static final String PROPERTY_DESCRIPTOR_KEY = "PropertyDescriptor";
    private Tree tree;
    private static String[] columnLabels = { PropertiesMessages.PropertyViewer_property, PropertiesMessages.PropertyViewer_value };
    private final int columnToEdit = 1;
    private IPropertySource source = null;
    private IWorkbenchPart sourcePart = null;
    private ISelection originalSelection;
    private static final String BROWSER_TYPE = System.getProperty("org.eclipse.swt.browser.DefaultType");

    @Override
    public void setFocus() {
        // tree.setFocus();
    }

    private boolean isMozilla() {
        if ("mozilla".equalsIgnoreCase(BROWSER_TYPE)) {
            return true;
        }
        return false;
    }

    @Override
    public void createPartControl(Composite parent) {
        UiUtil.hideToolBar(getViewSite());
        tree = new Tree(parent, SWT.FULL_SELECTION | SWT.SINGLE | SWT.HIDE_SELECTION);
        tree.setLinesVisible(true);
        tree.setHeaderVisible(true);
        for (int i = 0; i < columnLabels.length; i++) {
            TreeColumn column = new TreeColumn(tree, 0, i);
            column.setText(columnLabels[i]);
        }
        tree.addControlListener(new ControlAdapter() {
            @Override
            public void controlResized(ControlEvent e) {
                Rectangle area = tree.getClientArea();
                TreeColumn[] columns = tree.getColumns();
                if (area.width > 0) {
                    columns[0].setWidth(area.width * 40 / 100);
                    columns[1].setWidth(area.width - columns[0].getWidth() - 4);
                    tree.removeControlListener(this);
                }
            }
        });
        // Handle selections in the Tree
        // Part1: Double click only (allow traversal via keyboard without
        // activation
        tree.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                handleSelect((TreeItem) e.item);
            }
        });
        // Part2: handle single click activation of cell editor
        tree.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseDown(MouseEvent event) {
                // only activate if there is a cell editor
                Point pt = new Point(event.x, event.y);
                TreeItem item = tree.getItem(pt);
                if (item != null) {
                    handleSelect(item);
                }
            }
        });
    }

    private void handleSelect(TreeItem item) {
        for (TreeItem treeItem : tree.getItems()) {
            CellEditor cellEditor = (CellEditor) treeItem.getData(CELL_EDITOR_KEY);
            if (cellEditor != null && !cellEditor.getControl().isDisposed() && !cellEditor.getControl().isVisible()) {
                cellEditor.getControl().setVisible(true);
            }
        }
        CellEditor cellEditor = (CellEditor) item.getData(CELL_EDITOR_KEY);
        if (cellEditor != null) {
            cellEditor.setFocus();
        }
    }

    @Override
    public void init(IViewSite site) throws PartInitException {
        super.init(site);
        getSite().getWorkbenchWindow().getSelectionService().addSelectionListener(this);
        getSite().getWorkbenchWindow().getPartService().addPartListener(this);
    }

    @Override
    public void dispose() {
        if (source instanceof GraphElement) {
            ((GraphElement) source).removePropertyChangeListener(this);
        }
        getSite().getWorkbenchWindow().getPartService().removePartListener(this);
        getSite().getWorkbenchWindow().getSelectionService().removeSelectionListener(this);
        super.dispose();
    }

    @Override
    public void selectionChanged(IWorkbenchPart part, ISelection selection) {
        this.originalSelection = selection;
        this.sourcePart = part;

        IPropertySource newSource = getSource(selection);
        if (part instanceof GraphitiProcessEditor) {
            newSource = ((GraphitiProcessEditor) part).translateSelection(selection);
        }
        else if (part instanceof GraphitiGlobalSectionEditor) {
            newSource = ((GraphitiGlobalSectionEditor) part).translateSelection(selection);
        }
        if (Objects.equal(source, newSource)) {
            return;
        }
        if (newSource != null) {
            changeSource(newSource);
        } else {
            final IPropertySource newSrc = newSource;
            Display.getDefault().asyncExec(() -> {
                if (getSite().getWorkbenchWindow().getActivePage().getEditorReferences().length == 0) {
                    changeSource(newSrc);
                }
            });
        }
    }

    private void changeSource(IPropertySource newSource) {
        if (source instanceof GraphElement) {
            ((GraphElement) source).removePropertyChangeListener(this);
        }
        if (isMozilla()) {
            apply();// it`s a hook for mozilla
        }
        source = newSource;
        if (source instanceof GraphElement) {
            ((GraphElement) source).addPropertyChangeListener(this);
        }
        updateChildren();
    }

    private IPropertySource getSource(ISelection selection) {
        IPropertySource source = null;
        if (selection instanceof IStructuredSelection) {
            IStructuredSelection structuredSelection = (IStructuredSelection) selection;
            if (!structuredSelection.isEmpty()) {
                Object sel = structuredSelection.getFirstElement();
                if (sel instanceof IPropertySource) {
                    source = (IPropertySource) sel;
                } else if (sel instanceof IAdaptable) {
                    source = ((IAdaptable) sel).getAdapter(IPropertySource.class);
                }
            }
        }
        return source;
    }

    @Override
    public void partActivated(IWorkbenchPart part) {
    }

    @Override
    public void partBroughtToTop(IWorkbenchPart part) {
    }

    @Override
    public void partClosed(IWorkbenchPart part) {
        if (sourcePart != null && sourcePart.equals(part)) {
            selectionChanged(null, null);
        }
    }

    @Override
    public void partDeactivated(IWorkbenchPart part) {
    }

    @Override
    public void partOpened(IWorkbenchPart part) {
    }

    @Override
    public void propertyChange(PropertyChangeEvent evt) {
        boolean propFound = false;
        // if (tree.isDisposed()) {
        // // TODO remove; seems like bug already is fixed
        // PluginLogger.logErrorWithoutDialog("tree is disposed in part.propertyChange",
        // new Exception());
        // return;
        // }
        for (TreeItem item : tree.getItems()) {
            IPropertyDescriptor descriptor = (IPropertyDescriptor) item.getData(PROPERTY_DESCRIPTOR_KEY);
            if (descriptor != null && Objects.equal(descriptor.getId(), evt.getPropertyName())) {
                CellEditorListener cellEditorListener = (CellEditorListener) item.getData(CELL_EDITOR_LISTENER_KEY);
                Object value = source.getPropertyValue(descriptor.getId());
                if (cellEditorListener != null) {
                    cellEditorListener.updateValueInView(value);
                } else {
                    item.setText(1, getStringValue(descriptor, value));
                }
                propFound = true;
            }
        }
        if (propFound) {
            tree.redraw();
        } else {
            updateChildren();
        }
    }

    private void updateChildren() {
        if (tree.isDisposed()) {
            return;
        }
        for (TreeItem item : tree.getItems()) {
            TreeEditor treeEditor = (TreeEditor) item.getData(TREE_EDITOR_KEY);
            if (treeEditor != null) {
                treeEditor.getEditor().dispose();
            }
            item.dispose();
        }
        if (source == null) {
            return;
        }
        IPropertyDescriptor[] descriptors = source.getPropertyDescriptors();
        for (int i = 0; i < descriptors.length; i++) {
            IPropertyDescriptor descriptor = descriptors[i];
            Object value = source.getPropertyValue(descriptor.getId());
            final TreeItem item = new TreeItem(tree, SWT.NONE, i);
            item.setText(0, descriptor.getDisplayName());
            item.setText(1, getStringValue(descriptor, value));
            CellEditor cellEditor = descriptor.createPropertyEditor(tree);
            CellEditorListener cellEditorListener = null;
            if (cellEditor != null) {
                cellEditor.setValue(value);
                cellEditorListener = new CellEditorListener(item, descriptor, cellEditor);
                cellEditor.addListener(cellEditorListener);
                CellEditor.LayoutData layout = cellEditor.getLayoutData();
                TreeEditor treeEditor = new TreeEditor(tree);
                treeEditor.horizontalAlignment = layout.horizontalAlignment;
                treeEditor.grabHorizontal = layout.grabHorizontal;
                treeEditor.minimumWidth = layout.minimumWidth;
                if (cellEditor instanceof ComboBoxCellEditor) {
                    cellEditor.getControl().setBackground(ColorConstants.white);
                    cellEditor.setValidator(new ICellEditorValidator() {
                        @Override
                        public String isValid(Object object) {
                            if (object instanceof Integer && ((Integer) object).intValue() > -1) {
                                return null;
                            }
                            return "empty";
                        }
                    });
                }
                treeEditor.setEditor(cellEditor.getControl(), item, columnToEdit);
                item.setData(CELL_EDITOR_KEY, cellEditor);
                item.setData(TREE_EDITOR_KEY, treeEditor);
            }
            item.setData(PROPERTY_DESCRIPTOR_KEY, descriptor);
            item.setData(CELL_EDITOR_LISTENER_KEY, cellEditorListener);
        }
        tree.setRedraw(true);
    }

    private void apply() {
        if (tree == null || tree.isDisposed()) {
            return;
        }
        for (TreeItem item : tree.getItems()) {
            CellEditorListener listener = (CellEditorListener) item.getData(CELL_EDITOR_LISTENER_KEY);
            if (listener != null) {
                listener.applyEditorValue();
            }
        }
    }

    public class CellEditorListener implements ICellEditorListener {
        private final TreeItem item;
        private final CellEditor cellEditor;
        private final IPropertyDescriptor descriptor;
        private Object initialValue;

        public CellEditorListener(TreeItem item, IPropertyDescriptor descriptor, CellEditor cellEditor) {
            this.item = item;
            this.descriptor = descriptor;
            this.cellEditor = cellEditor;
            this.initialValue = cellEditor.getValue();
        }

        @Override
        public void cancelEditor() {
        }

        @Override
        public void editorValueChanged(boolean oldValidState, boolean newValidState) {
        }

        @Override
        public void applyEditorValue() {
            if (source == null) {
                return;
            }
            if (!cellEditor.isValueValid()) {
                cellEditor.setValue(initialValue);
                return;
            }

            Object value = cellEditor.getValue();
            try {
                boolean valueChanged = !Objects.equal(initialValue, value);
                if (valueChanged) {
                    source.setPropertyValue(descriptor.getId(), value);
                    updateValueInView(value);
                    cellEditor.getControl().setVisible(true);
                }
            } catch (Throwable th) {
                // ignoring Widget is disposed
            }
        }

        public void updateValueInView(Object value) {
            try {
                item.setText(1, getStringValue(descriptor, value));
                this.cellEditor.setValue(value);
                this.initialValue = value;
            } catch (SWTException e) {
                // ignoring Widget is disposed
            }
        }
    }

    private String getStringValue(IPropertyDescriptor descriptor, Object value) {
        ILabelProvider labelProvider = descriptor.getLabelProvider();
        String stringValue;
        if (value == null) {
            stringValue = "";
        } else if (labelProvider != null) {
            stringValue = labelProvider.getText(value);
        } else {
            stringValue = value.toString();
        }
        return stringValue;
    }

}
