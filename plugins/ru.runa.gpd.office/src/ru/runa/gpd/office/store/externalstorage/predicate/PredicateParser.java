package ru.runa.gpd.office.store.externalstorage.predicate;

import java.util.Optional;
import java.util.StringTokenizer;
import ru.runa.gpd.lang.model.Variable;
import ru.runa.gpd.lang.model.VariableUserType;
import ru.runa.gpd.office.store.externalstorage.VariableProvider;
import ru.runa.wfe.var.VariableDoesNotExistException;

public class PredicateParser {
    public static final char UNICODE_CHARACTER_OVERLINE = '\u203E';

    private final String predicateString;
    private final VariableUserType variableUserType;
    private final VariableProvider variableProvider;

    public PredicateParser(String predicateString, VariableUserType variableUserType, VariableProvider variableProvider) {
        this.variableUserType = variableUserType;
        this.variableProvider = variableProvider;
        this.predicateString = hideSpacesInAttributeNames(predicateString.trim());
    }

    public ConstraintsPredicate<?, ?> parse() {
        final StringTokenizer st = new StringTokenizer(predicateString);
        ConstraintsPredicate<?, ?> result = null;
        String rightField =null;
        while (st.hasMoreTokens()) {
            String token = st.nextToken();
            
            if (token.startsWith("[") && token.endsWith("]")) {
                token = token.replace(UNICODE_CHARACTER_OVERLINE, ' ');
                final String userTypeFieldName = token.substring(1, token.length() - 1);
                final Variable userTypeField = getUserTypeFieldByName(userTypeFieldName)
                        .orElseThrow(() -> new VariableDoesNotExistException(userTypeFieldName));
                rightField = userTypeFieldName;
                if (result != null && result instanceof VariablePredicate) {
                    ((VariablePredicate) result).setLeft(userTypeField);
                    
                } else if (result != null && result instanceof ExpressionPredicate) {
                    VariablePredicate right = ((ExpressionPredicate<?>) result).getRight();
                    if (right != null) {
                        right.setLeft(userTypeField);
                    } else {
                        right = new VariablePredicate(userTypeField, null, null);
                        ((ExpressionPredicate<?>) result).setRight(right);
                        right.setParent(result);
                    }
                } else {
                    result = new VariablePredicate(userTypeField, null, null);
                    
                }
            } else if (PredicateOperationType.codes().contains(token.toLowerCase())) {
                PredicateOperationType type = PredicateOperationType.byCode(token.toLowerCase()).get();
                if (PredicateOperationType.AND.equals(type) || PredicateOperationType.OR.equals(type)) {
                    if (result instanceof VariablePredicate) {
                        result = new ExpressionPredicate<VariablePredicate>((VariablePredicate) result, type, null);
                    } else {
                        result = new ExpressionPredicate<ExpressionPredicate<?>>((ExpressionPredicate<?>) result, type, null);
                    }
                    ((ConstraintsPredicate<?, ?>) result.getLeft()).setParent(result);
                } else if (result != null && result instanceof ExpressionPredicate) {
                    final VariablePredicate right = ((ExpressionPredicate<?>) result).getRight();
                    right.setType(type);
                } else if (result != null && result instanceof VariablePredicate) {
                    ((VariablePredicate) result).setType(type);
                }
            } else if (token.startsWith("@")) {
                final String variableName = token.substring(1);
                final Variable comparingWithVariable = variableProvider.variableByScriptingName(variableName).orElse(newVariable( variableName, rightField ));
                        //.orElseThrow(() -> new VariableDoesNotExistException(variableName));

                if (result != null && result instanceof VariablePredicate) {
                    ((VariablePredicate) result).setRight(comparingWithVariable);
                } else if (result != null && result instanceof ExpressionPredicate) {
                    final VariablePredicate right = ((ExpressionPredicate<?>) result).getRight();
                    right.setRight(comparingWithVariable);
                }
            }
        }

        return result;
    }
    
    private Variable newVariable(String variableName, String typeField) {
    	if(typeField.equals("")) {
    		return null;
    	}
    	Variable newVariable = new Variable();
    	newVariable.setName(variableName);
    	newVariable.setScriptingName(variableName);
    	newVariable.setFormat(getUserTypeFieldByName(typeField).get().getFormat());
    	return newVariable;
    }

    private Optional<Variable> getUserTypeFieldByName(String name) {
        return variableUserType.getAttributes().stream().filter(variable -> variable.getName().equals(name)).findAny();
    }

    private String hideSpacesInAttributeNames(String condition) {
        char[] conditionChars = condition.toCharArray();
        boolean attributeName = false;
        for (int i = 0; i < conditionChars.length; i++) {
            char c = conditionChars[i];
            if (c == '[') {
                attributeName = true;
            } else if (c == ']') {
                attributeName = false;
            } else if (c == ' ') {
                if (attributeName) {
                    conditionChars[i] = UNICODE_CHARACTER_OVERLINE;
                }
            }
        }
        return new String(conditionChars);
    }
}
