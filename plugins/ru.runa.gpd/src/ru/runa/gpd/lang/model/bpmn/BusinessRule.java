package ru.runa.gpd.lang.model.bpmn;

import java.util.List;

import ru.runa.gpd.extension.HandlerArtifact;
import ru.runa.gpd.lang.model.Delegable;
import ru.runa.gpd.lang.model.Node;
import ru.runa.gpd.lang.model.Transition;
import ru.runa.wfe.extension.handler.var.BusinessRuleHandler;

public class BusinessRule extends Node implements Delegable {

    public BusinessRule() {
        setDelegationClassName(BusinessRuleHandler.class.getName());
    }

    @Override
    public String getDelegationType() {
        return HandlerArtifact.ACTION;
    }

    @Override
    public boolean testAttribute(Object target, String name, String value) {
        if (name.equals("delegableEditHandler")) {
            return false;
        }
        return super.testAttribute(target, name, value);
    }

    @Override
    protected boolean allowLeavingTransition(List<Transition> transitions) {
        return super.allowLeavingTransition(transitions) && transitions.size() == 0;
    }

}
