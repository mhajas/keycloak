package org.keycloak.models.map.storage.hotRod;

import org.keycloak.models.map.storage.ModelCriteriaBuilder;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.keycloak.models.map.storage.hotRod.IckleQueryUtils.escapeIfNecessary;

public class IckleQueryOperators {
    public static final String C = "c";
    private static final Map<ModelCriteriaBuilder.Operator, String> OPERATOR_TO_STRING = new HashMap<>();
    private static final Map<ModelCriteriaBuilder.Operator, BiFunction<String, Object[], String>> OPERATOR_TO_EXPRESSION_COMBINATORS = new HashMap<>();

    static {
        OPERATOR_TO_EXPRESSION_COMBINATORS.put(ModelCriteriaBuilder.Operator.IN, IckleQueryOperators::in);
        OPERATOR_TO_EXPRESSION_COMBINATORS.put(ModelCriteriaBuilder.Operator.EXISTS, IckleQueryOperators::exists);
        OPERATOR_TO_EXPRESSION_COMBINATORS.put(ModelCriteriaBuilder.Operator.NOT_EXISTS, IckleQueryOperators::notExists);

        OPERATOR_TO_STRING.put(ModelCriteriaBuilder.Operator.EQ, "=");
        OPERATOR_TO_STRING.put(ModelCriteriaBuilder.Operator.NE, "!=");
        OPERATOR_TO_STRING.put(ModelCriteriaBuilder.Operator.LT, "<");
        OPERATOR_TO_STRING.put(ModelCriteriaBuilder.Operator.LE, "<=");
        OPERATOR_TO_STRING.put(ModelCriteriaBuilder.Operator.GT, ">");
        OPERATOR_TO_STRING.put(ModelCriteriaBuilder.Operator.GE, ">=");
        OPERATOR_TO_STRING.put(ModelCriteriaBuilder.Operator.LIKE, "LIKE");
        OPERATOR_TO_STRING.put(ModelCriteriaBuilder.Operator.ILIKE, "LIKE");
        OPERATOR_TO_STRING.put(ModelCriteriaBuilder.Operator.IN, "IN");
        OPERATOR_TO_STRING.put(ModelCriteriaBuilder.Operator.EXISTS, "LIKE");
        OPERATOR_TO_STRING.put(ModelCriteriaBuilder.Operator.NOT_EXISTS, "LIKE");
    }

    private static String exists(String modelField, Object[] values) {
        String field = C + "." + modelField;
        return field + " IS NOT NULL AND " + field + " IS NOT EMPTY";
    }

    private static String notExists(String modelField, Object[] values) {
        String field = C + "." + modelField;
        return field + " IS NULL OR " + field + " IS EMPTY";
    }

    private static String in(String modelField, Object[] values) {
        if (values == null || values.length == 0) {
            return "false";
        }

        final Collection<?> operand;
        if (values.length == 1) {
            final Object value0 = values[0];
            if (value0 instanceof Collection) {
                operand = (Collection) value0;
            } else if (value0 instanceof Stream) {
                try (Stream valueS = (Stream) value0) {
                    operand = (Set) valueS.collect(Collectors.toSet());
                }
            } else {
                operand = Collections.singleton(value0);
            }
        } else {
            operand = new HashSet<>(Arrays.asList(values));
        }

        return C + "." + modelField + " IN (" + operand.stream()
                .map(IckleQueryUtils::escapeIfNecessary)
                .collect(Collectors.joining(", ")) +
                ")";
    }

    private static BiFunction<String, Object[], String> singleValueOperator(ModelCriteriaBuilder.Operator op) {
        return (modelFieldName, values) -> {
            if (values.length != 1) throw new RuntimeException("Invalid arguments, expected (" + modelFieldName + "), got: " + Arrays.toString(values));

            return C + "." + modelFieldName + " " + IckleQueryOperators.operatorToString(op) + " " + escapeIfNecessary(values[0]);
        };
    }

    private static String operatorToString(ModelCriteriaBuilder.Operator op) {
        return OPERATOR_TO_STRING.get(op);
    }

    public static BiFunction<String, Object[], String> operatorToExpressionCombinator(ModelCriteriaBuilder.Operator op) {
        return OPERATOR_TO_EXPRESSION_COMBINATORS.getOrDefault(op, singleValueOperator(op));
    }

    public static String combineExpressions(ModelCriteriaBuilder.Operator op, String filedName, Object[] values) {
        return operatorToExpressionCombinator(op).apply(filedName, values);
    }

}