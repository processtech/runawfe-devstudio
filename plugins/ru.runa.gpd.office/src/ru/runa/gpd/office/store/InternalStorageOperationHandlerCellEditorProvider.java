package ru.runa.gpd.office.store;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.collect.Iterables;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.dom4j.Document;
import org.dom4j.Element;
import org.eclipse.core.resources.IFile;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.ScrolledComposite;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import ru.runa.gpd.PluginLogger;
import ru.runa.gpd.ProcessCache;
import ru.runa.gpd.extension.handler.XmlBasedConstructorProvider;
import ru.runa.gpd.lang.ValidationError;
import ru.runa.gpd.lang.model.Delegable;
import ru.runa.gpd.lang.model.GraphElement;
import ru.runa.gpd.lang.model.GraphElementAware;
import ru.runa.gpd.lang.model.ProcessDefinition;
import ru.runa.gpd.lang.model.ProcessDefinitionAware;
import ru.runa.gpd.lang.model.StorageAware;
import ru.runa.gpd.lang.model.VariableContainer;
import ru.runa.gpd.lang.model.VariableUserType;
import ru.runa.gpd.lang.model.VariableUserTypeNameAware;
import ru.runa.gpd.lang.par.VariablesXmlContentProvider;
import ru.runa.gpd.office.FilesSupplierMode;
import ru.runa.gpd.office.Messages;
import ru.runa.gpd.office.store.externalstorage.ConstraintsCompositeBuilder;
import ru.runa.gpd.office.store.externalstorage.DeleteBotConstraintsComposite;
import ru.runa.gpd.office.store.externalstorage.DeleteConstraintsComposite;
import ru.runa.gpd.office.store.externalstorage.InsertConstraintsComposite;
import ru.runa.gpd.office.store.externalstorage.InternalStorageDataModel;
import ru.runa.gpd.office.store.externalstorage.PredicateBotCompositeDelegateBuilder;
import ru.runa.gpd.office.store.externalstorage.PredicateCompositeDelegateBuilder;
import ru.runa.gpd.office.store.externalstorage.ProcessDefinitionVariableProvider;
import ru.runa.gpd.office.store.externalstorage.SelectBotConstraintsComposite;
import ru.runa.gpd.office.store.externalstorage.SelectConstraintsComposite;
import ru.runa.gpd.office.store.externalstorage.UpdateConstraintsComposite;
import ru.runa.gpd.office.store.externalstorage.VariableProvider;
import ru.runa.gpd.ui.custom.SwtUtils;
import ru.runa.gpd.util.EmbeddedFileUtils;
import ru.runa.gpd.util.XmlUtil;

public class InternalStorageOperationHandlerCellEditorProvider extends XmlBasedConstructorProvider<InternalStorageDataModel> {
    public static final String INTERNAL_STORAGE_DATASOURCE_PATH = "datasource:InternalStorage";
    public static final String INTERNAL_STORAGE_GLOBALSECTION_PATH = "datasource:";
    private Delegable delegable;
    private ProcessDefinition  definition;
    @Override
    public void onDelete(Delegable delegable) {
        try {
        	this.delegable = delegable;        	
            final InternalStorageDataModel model = fromXml(delegable.getDelegationConfiguration());            
            EmbeddedFileUtils.deleteProcessFile(model.getInOutModel().inputPath);
        } catch (Exception e) {
            PluginLogger.logErrorWithoutDialog("Template file deletion", e);
        }
    }
    @Override
    protected String getTitle() {
        return Messages.getString("InternalStorageHandlerConfig.title");
    }
    @Override
    protected Composite createConstructorComposite(Composite parent, Delegable delegable, InternalStorageDataModel model) {
        final boolean isUseExternalStorageIn = (delegable instanceof StorageAware) ? ((StorageAware) delegable).isUseExternalStorageIn() : false;
        final boolean isUseExternalStorageOut = (delegable instanceof StorageAware) ? ((StorageAware) delegable).isUseExternalStorageOut() : false;       
        Optional<ProcessDefinition> processDefinition = Optional.empty();
        if (delegable instanceof ProcessDefinitionAware) {
            processDefinition = Optional.ofNullable(((ProcessDefinitionAware) delegable).getProcessDefinition());
        }
        if (!processDefinition.isPresent() && delegable instanceof VariableContainer) {
            processDefinition = ((VariableContainer) delegable).getVariables(false, true).stream().map(variable -> variable.getProcessDefinition())
                    .findAny();
        }
        delegable.getDelegationClassName();
        if (delegable instanceof StorageAware) {
            if (delegable instanceof VariableUserTypeNameAware) {
                return new ConstructorView(parent, delegable, model,
                        new ProcessDefinitionVariableProvider(
                                processDefinition.orElseThrow(() -> new IllegalStateException("process definition unavailable"))),
                        isUseExternalStorageIn, isUseExternalStorageOut,
                        new VariableUserTypeInfo(true, ((VariableUserTypeNameAware) delegable).getUserTypeName())).build();
            }
            return new ConstructorView(parent, delegable, model,
                    new ProcessDefinitionVariableProvider(
                            processDefinition.orElseThrow(() -> new IllegalStateException("process definition unavailable"))),
                    isUseExternalStorageIn, isUseExternalStorageOut).build();
        } else {
        	ProcessDefinition globalDefinition = ProcessCache.getProcessDefinition(getGlobalFile(delegable));
        	if (globalDefinition!= null) {
        		this.definition = globalDefinition;        		
        	}      	
        	
        	return new BotConstructorView(parent, delegable, model, definition).build();
            
        }
    }

	private IFile getGlobalFile(Delegable delegable) { 
		String configText = delegable.getDelegationConfiguration();
    	Element input;
    	if(!XmlUtil.isXml(configText)) {
    		return null;
    	}    		
    	Document doc = XmlUtil.parseWithoutValidation(configText);
    	input = doc.getRootElement();
    	String globalName = input.element("input").attributeValue("variable");
	    for (IFile file : ProcessCache.getAllProcessDefinitionsMap().keySet()) {
	    		
	    	if(file.getParent().getName().equals(globalName) ) {
	            	return file;
	            }
	    	
    	}
		return null;
	}

	@Override
    protected InternalStorageDataModel createDefault() {
        return new InternalStorageDataModel(FilesSupplierMode.BOTH);
    }
	
    @Override
    protected InternalStorageDataModel fromXml(String xml) throws Exception {
    	
        return InternalStorageDataModel.fromXml(xml);
    }

    @Override
    public boolean validateValue(Delegable delegable, List<ValidationError> errors) throws Exception {
        final String configuration = delegable.getDelegationConfiguration();
        if (configuration.trim().isEmpty()) {
            errors.add(ValidationError.createError(
                    delegable instanceof GraphElementAware ? ((GraphElementAware) delegable).getGraphElement() : ((GraphElement) delegable),
                    Messages.getString("model.validation.xlsx.constraint.variable.empty")));
            return false;
        }
        return super.validateValue(delegable, errors);
    }

    @Override
    protected boolean validateModel(Delegable delegable, InternalStorageDataModel model, List<ValidationError> errors) {
        final GraphElement graphElement = delegable instanceof GraphElementAware ? ((GraphElementAware) delegable).getGraphElement()
                : ((GraphElement) delegable);
        if (delegable instanceof GraphElementAware) {
            model.setMode(FilesSupplierMode.IN);
        }
        model.validate(graphElement, errors);
        return super.validateModel(delegable, model, errors);
    }

    @Override
    protected Point getDialogInitialSize() {
        return new Point(800, 600);
    }

    private class ConstructorView extends ConstructorComposite {
        private final VariableProvider variableProvider;
        
        private final boolean isUseExternalStorageIn;
        private final boolean isUseExternalStorageOut;

        private StorageConstraintsModel constraintsModel;
        private VariableUserTypeInfo variableUserTypeInfo = new VariableUserTypeInfo(false, "");

        private ConstraintsCompositeBuilder constraintsCompositeBuilder;
         
        public ConstructorView(Composite parent, Delegable delegable, InternalStorageDataModel model, VariableProvider variableProvider,
                boolean isUseExternalStorageIn, boolean isUseExternalStorageOut) {
            super(parent, delegable, model);
            this.variableProvider = variableProvider;
            this.isUseExternalStorageIn = isUseExternalStorageIn;
            this.isUseExternalStorageOut = isUseExternalStorageOut;
            model.getInOutModel().inputPath = INTERNAL_STORAGE_DATASOURCE_PATH;
            setLayout(new GridLayout(2, false));
        }

        public ConstructorView(Composite parent, Delegable delegable, InternalStorageDataModel model, VariableProvider variableProvider,
                boolean isUseExternalStorageIn, boolean isUseExternalStorageOut, VariableUserTypeInfo variableUserTypeInfo) {
            this(parent, delegable, model, variableProvider, isUseExternalStorageIn, isUseExternalStorageOut);
            this.variableUserTypeInfo = variableUserTypeInfo;
        }

        public ConstructorView build() {
            buildFromModel();
            return this;
        }

        @Override
        protected void buildFromModel() {
            initConstraintsModel();
            for (Control control : getChildren()) {
                control.dispose();
            }

            if (constraintsModel.getSheetName() != null && !constraintsModel.getSheetName().isEmpty()) {
                final VariableUserType userType = variableProvider.getUserType(constraintsModel.getSheetName());
                variableUserTypeInfo.setVariableTypeName(userType != null ? userType.getName() : "");

                if (variableUserTypeInfo.isImmutable() && !variableUserTypeInfo.getVariableTypeName().equals(constraintsModel.getSheetName())) {
                    constraintsModel.setQueryString("");
                }
            }

            new Label(this, SWT.NONE).setText(Messages.getString("label.ExecutionAction"));
            if (isUseExternalStorageIn) {
                SwtUtils.createLabel(this, QueryType.SELECT.name());
                constraintsModel.setQueryType(QueryType.SELECT);
                model.setMode(FilesSupplierMode.BOTH);
            } else {
            	addActionCombo(isUseExternalStorageIn, isUseExternalStorageOut);
            }

            new Label(this, SWT.NONE).setText(Messages.getString("label.DataType"));
            if (variableUserTypeInfo.isImmutable()) {
                SwtUtils.createLabel(this, variableUserTypeInfo.getVariableTypeName());
                constraintsModel.setSheetName(variableUserTypeInfo.getVariableTypeName());
                constraintsModel.setVariableName(null);
                model.setMode(FilesSupplierMode.IN);
            } else {
                addDataTypeCombo();
            }
            
            initConstraintsCompositeBuilder();
            if (constraintsCompositeBuilder != null) {
                constraintsCompositeBuilder.clearConstraints();
                new Label(this, SWT.NONE);
                constraintsCompositeBuilder.build();
            }

            ((ScrolledComposite) getParent()).setMinSize(computeSize(getSize().x, SWT.DEFAULT));
            this.layout(true, true);
            this.redraw();
        }

        private void initConstraintsModel() {
            if (constraintsModel != null && model.constraints.get(0) == constraintsModel) {
                return;
            }

            if (!model.constraints.isEmpty()) {
                Preconditions.checkState(model.constraints.size() == 1, "Expected model.constraints.size() == 1, actual " + model.constraints.size());
                constraintsModel = Iterables.getOnlyElement(model.constraints);
            } else {
                constraintsModel = new StorageConstraintsModel(StorageConstraintsModel.ATTR, QueryType.SELECT);
                model.constraints.add(constraintsModel);
            }
        }

        private void initConstraintsCompositeBuilder() {
            if (constraintsModel.getQueryType() != null) {
                switch (constraintsModel.getQueryType()) {
                case INSERT:
                    constraintsCompositeBuilder = new InsertConstraintsComposite(this, SWT.NONE, constraintsModel, variableProvider,
                            variableUserTypeInfo.getVariableTypeName());
                    break;
                case SELECT:
                    constraintsCompositeBuilder = new PredicateCompositeDelegateBuilder(this, SWT.NONE, constraintsModel, variableProvider,
                            variableUserTypeInfo.getVariableTypeName(), new SelectConstraintsComposite(this, SWT.NONE, constraintsModel,
                                    variableProvider, variableUserTypeInfo, model.getInOutModel()));
                    break;
                case UPDATE:
                    constraintsCompositeBuilder = new PredicateCompositeDelegateBuilder(this, SWT.NONE, constraintsModel, variableProvider,
                            variableUserTypeInfo.getVariableTypeName(), new UpdateConstraintsComposite(this, SWT.NONE, constraintsModel,
                                    variableProvider, variableUserTypeInfo.getVariableTypeName()));
                    break;
                case DELETE:
                    constraintsCompositeBuilder = new PredicateCompositeDelegateBuilder(this, SWT.NONE, constraintsModel, variableProvider,
                            variableUserTypeInfo.getVariableTypeName(), new DeleteConstraintsComposite(this, SWT.NONE, constraintsModel,
                                    variableProvider, variableUserTypeInfo.getVariableTypeName()));
                    break;
                }
            }
        }

        private void addDataTypeCombo() {
            final Combo combo = new Combo(this, SWT.READ_ONLY);
            variableProvider.complexUserTypeNames().collect(Collectors.toSet()).forEach(combo::add);
            combo.addSelectionListener(SelectionListener.widgetSelectedAdapter(e -> {
                final String text = combo.getText();
                if (Strings.isNullOrEmpty(text)) {
                    return;
                }
                variableUserTypeInfo.setVariableTypeName(text);
                constraintsModel.setSheetName(variableUserTypeInfo.getVariableTypeName());
                if (constraintsCompositeBuilder != null) {
                    constraintsCompositeBuilder.onChangeVariableTypeName(variableUserTypeInfo.getVariableTypeName());
                }
                model.getInOutModel();
                buildFromModel();
            }));

            final VariableUserType userType = variableProvider.getUserType(constraintsModel.getSheetName());
            if (userType != null) {
                combo.setText(userType.getName());
                variableUserTypeInfo.setVariableTypeName(userType.getName());
            }
        }
               

        private void addActionCombo(boolean isUseExternalStorageIn, boolean isUseExternalStorageOut) {
            final Combo combo = new Combo(this, SWT.READ_ONLY);
            final List<QueryType> types = QueryType.byIntent(isUseExternalStorageIn, isUseExternalStorageOut);
            for (QueryType type : types) {
                combo.add(type.name());
            }

            combo.addSelectionListener(SelectionListener.widgetSelectedAdapter(e -> {
                String text = combo.getText();
                if (Strings.isNullOrEmpty(text)) {
                    return;
                }
                constraintsModel.setQueryType(QueryType.valueOf(combo.getText()));
                model.setMode(constraintsModel.getQueryType().equals(QueryType.SELECT) ? FilesSupplierMode.BOTH : FilesSupplierMode.IN);
                model.getInOutModel().outputVariable = null;
                buildFromModel();
                if (constraintsCompositeBuilder != null) {
                    constraintsCompositeBuilder.clearConstraints();
                }
            }));

            if (constraintsModel.getQueryType() != null && types.contains(constraintsModel.getQueryType())) {
                combo.setText(constraintsModel.getQueryType().name());
            } else {
                combo.setText(types.get(0).name());
                constraintsModel.setQueryType(types.get(0));
            }
        }
    }

    private class BotConstructorView extends ConstructorComposite {
    	private ProcessDefinition globalSectionDefinition;
        private VariableProvider variableProvider;        
        private ConstraintsCompositeBuilder constraintsCompositeBuilder;
        private StorageConstraintsModel constraintsModel;
        private String dataTypeName = null;  
        private String VariableTypeName = null; 
        private VariableUserType Type = null;
        private VariableUserTypeInfo variableUserTypeInfo = new VariableUserTypeInfo(false, "");        
        private QueryType queryType;
        
        
        public BotConstructorView(Composite parent, Delegable delegable, InternalStorageDataModel model, ProcessDefinition GlobalSection) {
            super(parent, delegable, model);
            this.globalSectionDefinition = GlobalSection;             
            this.variableProvider = new ProcessDefinitionVariableProvider(GlobalSection);
            model.getInOutModel().inputPath = INTERNAL_STORAGE_GLOBALSECTION_PATH;
            model.getInOutModel().inputVariable = GlobalSection.getName();
            setLayout(new GridLayout(2, false));
        }

        public BotConstructorView build() {
        	
            buildFromModel();
            return this;
        }
        
        @Override
        protected void buildFromModel() {
        	VariablesXmlContentProvider VariableXmlProvider = new VariablesXmlContentProvider();
            initConstraintsModel();
            for (Control control : getChildren()) {
                control.dispose();
            }
            model.getInOutModel().inputPath = INTERNAL_STORAGE_GLOBALSECTION_PATH+variableUserTypeInfo.variableTypeName;
            if (constraintsModel.getSheetName() != null && !constraintsModel.getSheetName().isEmpty()) {
                final VariableUserType userType = variableProvider.getUserType(constraintsModel.getSheetName());
                variableUserTypeInfo.setVariableTypeName(userType != null ? userType.getName() : "");

                if (variableUserTypeInfo.isImmutable() && !variableUserTypeInfo.getVariableTypeName().equals(constraintsModel.getSheetName())) {
                    constraintsModel.setQueryString("");
                }
            }
            
            new Label(this, SWT.NONE).setText(Messages.getString("label.ExecutionAction"));            
            addActionCombo();
            model.setMode(FilesSupplierMode.IN);
            new Label(this, SWT.NONE).setText(Messages.getString("label.DataType"));
            if (variableUserTypeInfo.isImmutable()) {
                SwtUtils.createLabel(this, variableUserTypeInfo.getVariableTypeName());
                constraintsModel.setSheetName(variableUserTypeInfo.getVariableTypeName());                
                constraintsModel.setVariableName(VariableTypeName);
                model.setMode(FilesSupplierMode.IN);
            } else {
                addDataTypeCombo();
            }            
            initConstraintsCompositeBuilder();
            constraintsModel.setVariableName(VariableTypeName);
            if (constraintsCompositeBuilder != null) {
                //constraintsCompositeBuilder.clearConstraints();
                new Label(this, SWT.NONE);
                constraintsCompositeBuilder.build();
            }
            if(constraintsModel.getQueryType() == QueryType.SELECT) {
            	model.setMode(FilesSupplierMode.BOTH);
            }            
            ((ScrolledComposite) getParent()).setMinSize(computeSize(getSize().x, SWT.DEFAULT));
            String configText = delegable.getDelegationConfiguration();        	
            this.layout(true, true);
            this.redraw();
        }

        private void initConstraintsModel() {
            if (constraintsModel != null && model.constraints.get(0) == constraintsModel) {
                return;
            }

            if (!model.constraints.isEmpty()) {
                Preconditions.checkState(model.constraints.size() == 1, "Expected model.constraints.size() == 1, actual " + model.constraints.size());
                constraintsModel = Iterables.getOnlyElement(model.constraints);
            } else {
                constraintsModel = new StorageConstraintsModel(StorageConstraintsModel.ATTR, QueryType.SELECT);
                model.constraints.add(constraintsModel);
            }
        }

        private void initConstraintsCompositeBuilder() {
            if (constraintsModel.getQueryType() != null) {
                switch (constraintsModel.getQueryType()) {
                case INSERT:
                    constraintsCompositeBuilder = new InsertConstraintsComposite(this, SWT.NONE, constraintsModel, variableProvider,
                            variableUserTypeInfo.getVariableTypeName());
                    break;
                case SELECT:
                    constraintsCompositeBuilder = new PredicateBotCompositeDelegateBuilder(this, SWT.NONE, constraintsModel, variableProvider,
                            variableUserTypeInfo.getVariableTypeName(), new SelectBotConstraintsComposite(this, SWT.NONE, constraintsModel,
                                    variableProvider, variableUserTypeInfo, model.getInOutModel(),delegable),delegable);
                    break;
                case UPDATE:
                    constraintsCompositeBuilder = new PredicateBotCompositeDelegateBuilder(this, SWT.NONE, constraintsModel, variableProvider,
                            variableUserTypeInfo.getVariableTypeName(), new UpdateConstraintsComposite(this, SWT.NONE, constraintsModel,
                                    variableProvider, variableUserTypeInfo.getVariableTypeName()),delegable);
                    break;
                case DELETE:
                    constraintsCompositeBuilder = new PredicateBotCompositeDelegateBuilder(this, SWT.NONE, constraintsModel, variableProvider,
                            variableUserTypeInfo.getVariableTypeName(), new DeleteBotConstraintsComposite(this, SWT.NONE, constraintsModel,
                                    variableProvider, variableUserTypeInfo.getVariableTypeName()),delegable);
                    break;
                }
            }
        }
        
        private void addDataTypeCombo() {
        	final Combo combo = new Combo(this, SWT.READ_ONLY);        	
        	List <VariableUserType> Types = globalSectionDefinition.getVariableUserTypes();
        	for(VariableUserType Type : Types) {
        		combo.add(Type.getName());
        	}        	
        	if(variableProvider== null) {				
	            return;
			}       	 		
        	
			combo.addSelectionListener(SelectionListener.widgetSelectedAdapter(e -> {
                final String text = combo.getText();
                if (Strings.isNullOrEmpty(text)) {
                    return;
                }
                dataTypeName =  text;             
                
	            variableUserTypeInfo.setVariableTypeName(combo.getText());
	            Type = Types.get(combo.getSelectionIndex());	            
	            buildFromModel(); 
            }));			
            if(dataTypeName != null) {
            	combo.setText(dataTypeName);
            }
        }
        private void addActionCombo() {
            final Combo combo = new Combo(this, SWT.READ_ONLY);
            for (QueryType action : QueryType.values()) {
                combo.add(action.toString());
            }
            combo.addSelectionListener(new SelectionAdapter() {
                @Override
                public void widgetSelected(SelectionEvent e) {
                    String text = combo.getText();
                    if (Strings.isNullOrEmpty(text)) {
                        return;
                    }
                    
                    queryType = QueryType.valueOf(combo.getText());
                    for (StorageConstraintsModel m : model.constraints) {
                        m.setQueryType(queryType);
                    }
                    buildFromModel();
                }
            });
            if (queryType == null) {
                for (StorageConstraintsModel m : model.constraints) {
                    queryType = m.getQueryType();
                    break;
                }
            }
            if (queryType != null) {
                combo.setText(queryType.toString());
            }
        } 
        protected String[] getTypeFilters() {
            return new String[] { List.class.getName() };
        }
                
    }
    public class VariableUserTypeInfo {
        private final boolean isImmutable;
        private String variableTypeName;

        public VariableUserTypeInfo(boolean isConst, String variableTypeName) {
            this.isImmutable = isConst;
            this.variableTypeName = variableTypeName;
        }

        public String getVariableTypeName() {
            return variableTypeName;
        }

        public void setVariableTypeName(String variableTypeName) {
            if (isImmutable) {
                return;
            }
            this.variableTypeName = variableTypeName;
        }

        public boolean isImmutable() {
            return isImmutable;
        }
    }
}
