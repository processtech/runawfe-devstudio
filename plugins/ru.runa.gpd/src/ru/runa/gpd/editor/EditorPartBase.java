package ru.runa.gpd.editor;

import java.beans.PropertyChangeListener;
import java.util.Comparator;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jface.viewers.ArrayContentProvider;
import org.eclipse.jface.viewers.ISelectionChangedListener;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.SelectionChangedEvent;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.jface.viewers.ViewerComparator;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.SashForm;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.MenuItem;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableColumn;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.IEditorSite;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.forms.widgets.ExpandableComposite;
import org.eclipse.ui.forms.widgets.Form;
import org.eclipse.ui.forms.widgets.FormToolkit;
import org.eclipse.ui.forms.widgets.Section;
import org.eclipse.ui.part.EditorPart;

import ru.runa.gpd.Localization;
import ru.runa.gpd.lang.model.ProcessDefinition;

public abstract class EditorPartBase<T> extends EditorPart implements PropertyChangeListener {

    protected final EditorBase editor;

    private Menu menu;
    private FormToolkit toolkit;
    private TableViewer mainViewer;

    public EditorPartBase(EditorBase editor) {
        this.editor = editor;
    }

    @Override
    public void init(IEditorSite site, IEditorInput input) throws PartInitException {
        setSite(site);
        setInput(input);
        editor.getDefinition().addPropertyChangeListener(this);
    }

    @Override
    public void dispose() {
        editor.getDefinition().removePropertyChangeListener(this);
        super.dispose();
    }

    @Override
    public void setFocus() {
        updateUI();
    }

    @Override
    public void doSave(IProgressMonitor monitor) {
        // no action
    }

    @Override
    public void doSaveAs() {
        // no action
    }

    @Override
    public boolean isDirty() {
        return false;
    }

    @Override
    public boolean isSaveAsAllowed() {
        return false;
    }

    public FormToolkit getToolkit() {
        if (toolkit == null) {
            toolkit = new FormToolkit(Display.getDefault());
        }
        return toolkit;
    }

    public void select(T variable) {
        if (mainViewer != null) {
            mainViewer.setSelection(new StructuredSelection(variable));
        }
    }

    @SuppressWarnings("unchecked")
    protected T getSelection() {
        return mainViewer == null ? null : (T) ((IStructuredSelection) mainViewer.getSelection()).getFirstElement();
    }

    protected Composite createActionBar(Composite leftComposite) {
        Composite actionBar = getToolkit().createComposite(leftComposite);
        actionBar.setLayout(new GridLayout(1, false));
        GridData gridData = new GridData();
        gridData.horizontalAlignment = SWT.LEFT;
        gridData.verticalAlignment = SWT.TOP;
        actionBar.setLayoutData(gridData);
        return actionBar;
    }

    protected TableViewer createMainViewer(Composite parent, int style) {
        TableViewer result = createTableViewer(parent, style);
        mainViewer = result;
        return result;
    }

    protected TableViewer createTableViewer(Composite parent, int style) {
        TableViewer result = new TableViewer(parent, style);
        result.getControl().setLayoutData(new GridData(GridData.FILL_BOTH));
        result.setContentProvider(new ArrayContentProvider());
        result.addSelectionChangedListener(new ISelectionChangedListener() {
            @Override
            public void selectionChanged(SelectionChangedEvent event) {
                updateUI();
            }
        });
        getToolkit().adapt(result.getControl(), false, false);
        getSite().setSelectionProvider(result);

        createContextMenu(result.getControl());

        return result;
    }

    protected void updateUI() {
        // no action
    }

    protected <S> void createTable(TableViewer viewer, DataViewerComparator<S> comparator, TableColumnDescription... column) {
        viewer.setComparator(comparator);

        Table table = viewer.getTable();
        table.setHeaderVisible(true);
        table.setLinesVisible(true);
        int index = 0;
        for (TableColumnDescription col : column) {
            TableColumn tableColumn = new TableColumn(table, col.style);
            tableColumn.setText(Localization.getString(col.titleKey));
            tableColumn.setWidth(col.width);
            if (col.sort) {
                tableColumn.addSelectionListener(createSelectionListener(viewer, comparator, tableColumn, index));
            }
            index++;
        }
    }

    protected Display getDisplay() {
        Display result = Display.getCurrent();
        if (result == null) {
            result = Display.getDefault();
        }
        return result;
    }

    protected void createContextMenu(Control control) {
        menu = new Menu(control.getShell(), SWT.POP_UP);
        control.setMenu(menu);
    }

    protected ProcessDefinition getDefinition() {
        return editor.getDefinition();
    }

    protected Button addButton(Composite parent, String buttonKey, SelectionAdapter selectionListener, boolean addToMenu) {
        String title = Localization.getString(buttonKey);
        GridData gridData = new GridData(GridData.FILL_HORIZONTAL);
        Button button = toolkit.createButton(parent, title, SWT.PUSH);
        button.setLayoutData(gridData);
        button.addSelectionListener(selectionListener);
        if (addToMenu) {
            MenuItem item = new MenuItem(menu, SWT.NONE);
            item.setText(title);
            item.addSelectionListener(selectionListener);
            button.setData("menuItem", item);
        }
        return button;
    }

    protected void enableAction(Button button, boolean enabled) {
        button.setEnabled(enabled);
        MenuItem menuItem = ((MenuItem) button.getData("menuItem"));
        if (menuItem != null) {
            menuItem.setEnabled(enabled);
        }
    }

    protected SashForm createSashForm(Composite parent, int style, String titleKey) {
        Form form = getToolkit().createForm(parent);
        form.setText(Localization.getString(titleKey));
        GridLayout layout = new GridLayout();
        layout.marginWidth = 0;
        layout.marginHeight = 0;
        form.getBody().setLayout(layout);

        SashForm sashForm = new SashForm(form.getBody(), style);
        getToolkit().adapt(sashForm, false, false);
        sashForm.setLayoutData(new GridData(GridData.FILL_BOTH));

        return sashForm;
    }

    protected Composite createSection(SashForm sashForm, String sectionTitleKey) {
        Section section = getToolkit().createSection(sashForm, ExpandableComposite.TITLE_BAR);
        section.marginHeight = 5;
        section.marginWidth = 5;
        section.setText(Localization.getString(sectionTitleKey));

        Composite clientArea = getToolkit().createComposite(section);
        section.setClient(clientArea);
        getToolkit().paintBordersFor(clientArea);
        GridLayout layoutRight = new GridLayout();
        layoutRight.marginWidth = 2;
        layoutRight.marginHeight = 2;
        layoutRight.numColumns = 2;
        clientArea.setLayout(layoutRight);

        return clientArea;
    }

    private <S> SelectionListener createSelectionListener(final TableViewer viewer, final DataViewerComparator<S> comparator,
            final TableColumn column, final int index) {
        SelectionAdapter selectionAdapter = new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                comparator.setColumn(index);
                viewer.getTable().setSortDirection(comparator.getDirection());
                viewer.getTable().setSortColumn(column);
                viewer.refresh();
            }
        };
        return selectionAdapter;
    }

    protected final static class TableColumnDescription {
        private final String titleKey;
        private final int width;
        private final int style;
        private final boolean sort;

        TableColumnDescription(String titleKey, int width, int style) {
            this(titleKey, width, style, true);
        }

        TableColumnDescription(String titleKey, int width, int style, boolean sort) {
            this.titleKey = titleKey;
            this.width = width;
            this.style = style;
            this.sort = sort;
        }
    }

    /**
     * Facade with 3 sorting states.
     * 
     * @author KuchmaMA
     */
    protected final static class DataViewerComparator<V> extends ViewerComparator {
        private int propertyIndex;
        private int direction;
        private final ValueComparator<V> comparable;

        protected DataViewerComparator(ValueComparator<V> comparable) {
            this.propertyIndex = 0;
            this.comparable = comparable;
            direction = SWT.NONE;
        }

        private int getDirection() {
            return direction;
        }

        private void setColumn(int column) {
            if (column == propertyIndex) {
                switch (direction) {
                case SWT.UP:
                    direction = SWT.DOWN;
                    break;
                case SWT.DOWN:
                    direction = SWT.NONE;
                    break;
                case SWT.NONE:
                    direction = SWT.UP;
                    break;
                }
            } else {
                propertyIndex = column;
                direction = SWT.UP;
            }
            comparable.setColumn(column);
        }

        @SuppressWarnings("unchecked")
        @Override
        public int compare(Viewer viewer, Object e1, Object e2) {
            int result = 0;

            switch (direction) {
            case SWT.NONE:
                break;
            case SWT.UP:
                result = comparable.compare((V) e1, (V) e2);
                break;
            case SWT.DOWN:
                result = -comparable.compare((V) e1, (V) e2);
                break;
            }

            return result;
        }
    }

    protected abstract static class ValueComparator<V> implements Comparator<V> {

        private int column;

        public int getColumn() {
            return column;
        }

        public void setColumn(int column) {
            this.column = column;
        }

    }
}
