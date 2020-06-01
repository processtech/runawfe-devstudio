package ru.runa.gpd.office;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

import org.eclipse.core.resources.IFile;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.ModifyEvent;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Text;

import ru.runa.gpd.lang.model.BotTask;
import ru.runa.gpd.lang.model.Delegable;
import ru.runa.gpd.lang.model.GraphElement;
import ru.runa.gpd.ui.custom.LoggingModifyTextAdapter;
import ru.runa.gpd.ui.custom.LoggingSelectionAdapter;
import ru.runa.gpd.util.DataSourceUtils;
import ru.runa.gpd.util.EmbeddedFileUtils;
import ru.runa.wfe.InternalApplicationException;
import ru.runa.wfe.datasource.DataSourceStuff;
import ru.runa.wfe.var.file.FileVariable;

import com.google.common.base.Strings;

public class InputOutputComposite extends Composite {
    public final InputOutputModel model;
    private final Delegable delegable;
    private final String fileExtension;

    public InputOutputComposite(Composite parent, Delegable delegable, final InputOutputModel model, FilesSupplierMode mode, String fileExtension) {
        super(parent, SWT.NONE);
        this.model = model;
        this.delegable = delegable;
        this.fileExtension = fileExtension;
        GridData data = new GridData(GridData.FILL_HORIZONTAL);
        data.horizontalSpan = 3;
        setLayoutData(data);
        setLayout(new FillLayout(SWT.VERTICAL));
        if (mode.isInSupported()) {
            Group inputGroup = new Group(this, SWT.NONE);
            inputGroup.setText(Messages.getString("label.input"));
            inputGroup.setLayout(new GridLayout(2, false));
            new ChooseStringOrFile(inputGroup, model.inputPath, model.inputVariable, FilesSupplierMode.IN) {
                @Override
                public void setFileName(String fileName) {
                    model.inputPath = fileName;
                    model.inputVariable = "";
                }

                @Override
                public void setVariable(String variable) {
                    model.inputPath = "";
                    model.inputVariable = variable;
                }
            };
        }
        if (mode.isOutSupported()) {
            Group outputGroup = new Group(this, SWT.NONE);
            outputGroup.setText(Messages.getString("label.output"));
            outputGroup.setLayout(new GridLayout(2, false));
            Label fileNameLabel = new Label(outputGroup, SWT.NONE);
            fileNameLabel.setText(Messages.getString("label.fileName"));
            final Text fileNameText = new Text(outputGroup, SWT.BORDER);
            fileNameText.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
            if (model.outputFilename != null) {
                fileNameText.setText(model.outputFilename);
            }
            fileNameText.addModifyListener(new LoggingModifyTextAdapter() {

                @Override
                protected void onTextChanged(ModifyEvent e) throws Exception {
                    model.outputFilename = fileNameText.getText();
                }
            });
            new ChooseStringOrFile(outputGroup, model.outputDir, model.outputVariable, FilesSupplierMode.OUT) {
                @Override
                public void setFileName(String fileName) {
                    model.outputDir = fileName;
                    model.outputVariable = "";
                }

                @Override
                public void setVariable(String variable) {
                    model.outputDir = "";
                    model.outputVariable = variable;
                    if (Strings.isNullOrEmpty(fileNameText.getText())) {
                        fileNameText.setText(".docx");
                    }
                }
            };
        }
        layout(true, true);
    }

    private abstract class ChooseStringOrFile implements PropertyChangeListener {
        public abstract void setFileName(String fileName);

        public abstract void setVariable(String variable);

        private Control control = null;
        private final Composite composite;
        private Runnable[] eventHandlers = new Runnable[5];

        public ChooseStringOrFile(Composite composite, String fileName, String variableName, FilesSupplierMode mode) {
            this.composite = composite;
            final Combo combo = new Combo(composite, SWT.READ_ONLY);

            int numHandlers = 0;
            int fileNameHandler, variableNameHandler;
            int processFileHandler = -1;
            int dataSourceNameHandler = -1;
            int dataSourceVariableNameHandler = -1;

            if (mode == FilesSupplierMode.IN) {
                combo.add(Messages.getString("label.processDefinitionFile"));
                combo.add(Messages.getString("label.fileVariable"));
                combo.add(Messages.getString("label.filePath"));
                eventHandlers[processFileHandler = numHandlers++] = () -> showEmbeddedFile(null);
                eventHandlers[variableNameHandler = numHandlers++] = () -> showVariable(null);
                eventHandlers[fileNameHandler = numHandlers++] = () -> showFileName(null);
            } else {
                combo.add(Messages.getString("label.fileVariable"));
                combo.add(Messages.getString("label.fileDir"));
                eventHandlers[variableNameHandler = numHandlers++] = () -> showVariable(null);
                eventHandlers[fileNameHandler = numHandlers++] = () -> showFileName(null);
            }

            if (model.canWorkWithDataSource) {
                combo.add(Messages.getString("label.dataSourceName"));
                combo.add(Messages.getString("label.dataSourceNameVariable"));
                eventHandlers[dataSourceNameHandler = numHandlers++] = () -> showDataSource(null);
                eventHandlers[dataSourceVariableNameHandler = numHandlers++] = () -> showDataSourceVariable(null);
            }

            // preference: 1. file variable (if exists), 2. in-process file (if exists or null),
            // 3. data source (if exists), 4. plain file or directory
            if (!Strings.isNullOrEmpty(variableName)) {
                combo.select(variableNameHandler);
                showVariable(variableName);
            } else if (processFileHandler >= 0
                    && (Strings.isNullOrEmpty(fileName) || (delegable instanceof GraphElement && EmbeddedFileUtils.isProcessFile(fileName))
                            || (delegable instanceof BotTask && EmbeddedFileUtils.isBotTaskFile(fileName)))) {
                combo.select(processFileHandler);
                showEmbeddedFile(fileName);
            } else if (dataSourceNameHandler >= 0 && !Strings.isNullOrEmpty(fileName)
                    && fileName.startsWith(DataSourceStuff.PATH_PREFIX_DATA_SOURCE)) {
                combo.select(dataSourceNameHandler);
                showDataSource(fileName);
            } else if (dataSourceVariableNameHandler >= 0 && !Strings.isNullOrEmpty(fileName)
                    && fileName.startsWith(DataSourceStuff.PATH_PREFIX_DATA_SOURCE_VARIABLE)) {
                combo.select(dataSourceVariableNameHandler);
                showDataSourceVariable(fileName);
            } else {
                combo.select(fileNameHandler);
                showFileName(fileName);
            }
            combo.addSelectionListener(new LoggingSelectionAdapter() {
                @Override
                protected void onSelection(SelectionEvent e) throws Exception {
                    int selectionIndex = combo.getSelectionIndex();
                    if (selectionIndex >= eventHandlers.length) {
                        return;
                    }
                    if (eventHandlers[selectionIndex] == null) {
                        return;
                    }
                    eventHandlers[selectionIndex].run();
                }
            });
        }

        private void showFileName(String filename) {
            if (control != null) {
                if (!Text.class.isInstance(control)) {
                    control.dispose();
                } else {
                    return;
                }
            }
            final Text text = new Text(composite, SWT.BORDER);
            if (filename != null) {
                text.setText(filename);
            }
            text.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
            text.addModifyListener(new LoggingModifyTextAdapter() {

                @Override
                protected void onTextChanged(ModifyEvent e) throws Exception {
                    setFileName(text.getText());
                }
            });
            control = text;
            composite.layout(true, true);
        }

        private void showVariable(String variable) {
            if (control != null) {
                control.dispose();
            }
            final Combo combo = new Combo(composite, SWT.READ_ONLY);
            combo.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
            for (String variableName : delegable.getVariableNames(false, FileVariable.class.getName())) {
                combo.add(variableName);
            }
            if (variable != null) {
                combo.setText(variable);
            }
            combo.addSelectionListener(new LoggingSelectionAdapter() {

                @Override
                protected void onSelection(SelectionEvent e) throws Exception {
                    setVariable(combo.getText());
                }
            });
            combo.addModifyListener(new LoggingModifyTextAdapter() {

                @Override
                protected void onTextChanged(ModifyEvent e) throws Exception {
                    setVariable(combo.getText());
                }
            });
            control = combo;
            composite.layout(true, true);
        }

        private void showDataSource(String dataSource) {
            if (control != null) {
                control.dispose();
            }
            final Combo combo = new Combo(composite, SWT.NONE);
            combo.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
            for (IFile dsFile : DataSourceUtils.getAllDataSources()) {
                String dsName = dsFile.getName();
                combo.add(dsName.substring(0, dsName.length() - DataSourceStuff.DATA_SOURCE_FILE_SUFFIX.length()));
            }
            if (dataSource != null) {
                combo.setText(dataSource.substring(dataSource.indexOf(':') + 1));
            }
            combo.addSelectionListener(new LoggingSelectionAdapter() {

                @Override
                protected void onSelection(SelectionEvent e) throws Exception {
                    setFileName(DataSourceStuff.PATH_PREFIX_DATA_SOURCE + combo.getText());
                }
            });
            combo.addModifyListener(new LoggingModifyTextAdapter() {

                @Override
                protected void onTextChanged(ModifyEvent e) throws Exception {
                    setFileName(DataSourceStuff.PATH_PREFIX_DATA_SOURCE + combo.getText());
                }
            });
            control = combo;
            composite.layout(true, true);
        }

        private void showDataSourceVariable(String variable) {
            if (control != null) {
                control.dispose();
            }
            final Combo combo = new Combo(composite, SWT.READ_ONLY);
            combo.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
            for (String variableName : delegable.getVariableNames(false, String.class.getName())) {
                combo.add(variableName);
            }
            if (variable != null) {
                combo.setText(variable.substring(variable.indexOf(':') + 1));
            }
            combo.addSelectionListener(new LoggingSelectionAdapter() {

                @Override
                protected void onSelection(SelectionEvent e) throws Exception {
                    setFileName(DataSourceStuff.PATH_PREFIX_DATA_SOURCE_VARIABLE + combo.getText());
                }
            });
            combo.addModifyListener(new LoggingModifyTextAdapter() {

                @Override
                protected void onTextChanged(ModifyEvent e) throws Exception {
                    setFileName(DataSourceStuff.PATH_PREFIX_DATA_SOURCE_VARIABLE + combo.getText());
                }
            });
            control = combo;
            composite.layout(true, true);
        }

        private void showEmbeddedFile(String path) {
            if (control != null) {
                if (TemplateFileComposite.class != control.getClass()) {
                    control.dispose();
                } else {
                    return;
                }
            }
            String fileName;

            if (delegable instanceof GraphElement) {
                if (EmbeddedFileUtils.isProcessFile(path)) {
                    fileName = EmbeddedFileUtils.getProcessFileName(path);
                } else {
                    fileName = EmbeddedFileUtils.generateEmbeddedFileName(delegable, fileExtension);
                }
            } else if (delegable instanceof BotTask) {
                if (EmbeddedFileUtils.isBotTaskFile(path)) {
                    fileName = EmbeddedFileUtils.getBotTaskFileName(path);
                } else {
                    fileName = EmbeddedFileUtils.generateEmbeddedFileName(delegable, fileExtension);
                }
            } else {
                throw new InternalApplicationException("Unexpected classtype " + delegable);
            }

            // http://sourceforge.net/p/runawfe/bugs/628/
            updateEmbeddedFileName(fileName);

            control = new TemplateFileComposite(composite, fileName, fileExtension);
            ((TemplateFileComposite) control).getEventSupport().addPropertyChangeListener(this);
            composite.layout(true, true);
        }

        @Override
        public void propertyChange(PropertyChangeEvent event) {
            updateEmbeddedFileName((String) event.getNewValue());
        }

        private void updateEmbeddedFileName(String fileName) {
            if (delegable instanceof GraphElement) {
                setFileName(EmbeddedFileUtils.getProcessFilePath(fileName));
            } else if (delegable instanceof BotTask) {
                setFileName(EmbeddedFileUtils.getBotTaskFilePath(fileName));
            } else {
                throw new InternalApplicationException("Unexpected classtype " + delegable);
            }
        }

    }

}
