package ru.runa.gpd.office.store.externalstorage;

import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.ui.forms.events.HyperlinkEvent;

import com.google.common.base.Strings;

import ru.runa.gpd.lang.model.Delegable;
import ru.runa.gpd.office.Messages;
import ru.runa.gpd.office.store.StorageConstraintsModel;
import ru.runa.gpd.office.store.externalstorage.predicate.PredicateComposite;
import ru.runa.gpd.ui.custom.LoggingHyperlinkAdapter;
import ru.runa.gpd.ui.custom.SwtUtils;

public class PredicateBotCompositeDelegateBuilder extends PredicateCompositeDelegateBuilder {
	
	private PredicateBotComposite predicateBotComposite;
	private Delegable taskDelegable;
	
	public PredicateBotCompositeDelegateBuilder(Composite parent, int style, StorageConstraintsModel constraintsModel,
			VariableProvider variableProvider, String variableTypeName, ConstraintsCompositeBuilder delegate,
			Delegable delegable) {
		super(parent, style, constraintsModel, variableProvider, variableTypeName, delegate);
		this.taskDelegable = delegable;
		
		// TODO Auto-generated constructor stub
	}
	
	@Override
    public void build() {
        delegate.build();

        if (!Strings.isNullOrEmpty(variableTypeName)) {
            SwtUtils.createLabel(parent, "");
            SwtUtils.createLink(parent, Messages.getString("label.AddPredicate"), new LoggingHyperlinkAdapter() {
                @Override
                protected void onLinkActivated(HyperlinkEvent e) throws Exception {
                    if (predicateBotComposite == null) {
                        return;
                    }
                    predicateBotComposite.addPredicate();
                    parent.layout(true, true);
                }
            }).setLayoutData(new GridData(GridData.GRAB_HORIZONTAL | GridData.HORIZONTAL_ALIGN_END));

            predicateBotComposite = new PredicateBotComposite(parent, style, constraintsModel, variableTypeName, variableProvider, taskDelegable);
            predicateBotComposite.build();
        }

        parent.layout(true, true);
    }

}
