package org.keycloak.models.map.storage.hotRod;

import org.keycloak.models.ClientModel;
import org.keycloak.models.map.storage.CriterionNotSupportedException;
import org.keycloak.models.map.storage.ModelCriteriaBuilder;
import org.keycloak.storage.SearchableModelField;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public class IckleQueryWhereClauses {
    private static final Map<SearchableModelField<?>, WhereClauseProducer> WHERE_CLAUSE_PRODUCER_OVERRIDES = new HashMap<>();

    static {
        WHERE_CLAUSE_PRODUCER_OVERRIDES.put(ClientModel.SearchableFields.ATTRIBUTE, IckleQueryWhereClauses::whereClauseForClientsAttributes);
    }

    @FunctionalInterface
    public interface WhereClauseProducer {
        String produceWhereClause(String modelFieldName, ModelCriteriaBuilder.Operator op, Object[] values, Map<String, Object> parameters);
    }

    private static String produceWhereClause(String modelFieldName, ModelCriteriaBuilder.Operator op, Object[] values, Map<String, Object> parameters) {
        return IckleQueryOperators.operatorToExpressionCombinator(op).combine(modelFieldName, values, parameters);
    }

    public static WhereClauseProducer whereClauseProducerForModelField(SearchableModelField<?> modelField) {
        return WHERE_CLAUSE_PRODUCER_OVERRIDES.getOrDefault(modelField, IckleQueryWhereClauses::produceWhereClause);
    }

    private static String whereClauseForClientsAttributes(String modelFieldName, ModelCriteriaBuilder.Operator op, Object[] values, Map<String, Object> parameters) {
        if (values == null || values.length != 2) {
            throw new CriterionNotSupportedException(ClientModel.SearchableFields.ATTRIBUTE, op, "Invalid arguments, expected attribute_name-value pair, got: " + Arrays.toString(values));
        }

        final Object attrName = values[0];
        if (! (attrName instanceof String)) {
            throw new CriterionNotSupportedException(ClientModel.SearchableFields.ATTRIBUTE, op, "Invalid arguments, expected (String attribute_name), got: " + Arrays.toString(values));
        }

        String attrNameS = (String) attrName;
        Object[] realValues = new Object[values.length - 1];
        System.arraycopy(values, 1, realValues, 0, values.length - 1);

        // Clause for searching attribute name
        String nameClause = IckleQueryOperators.combineExpressions(ModelCriteriaBuilder.Operator.EQ, modelFieldName + ".name", new Object[]{attrNameS}, parameters);
        String valueClause = IckleQueryOperators.combineExpressions(op, modelFieldName + ".values", realValues, parameters);

        return "(" + nameClause + ")" + " AND " + "(" + valueClause + ")";
    }
}
