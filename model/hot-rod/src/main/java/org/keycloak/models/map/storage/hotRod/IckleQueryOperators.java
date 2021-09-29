package org.keycloak.models.map.storage.hotRod;

import org.keycloak.models.map.storage.ModelCriteriaBuilder;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public class IckleQueryOperators {
    private static final String UNWANTED_CHARACTERS_REGEX = "[^a-zA-Z\\d]";
    public static final String C = "c";
    private static final Map<ModelCriteriaBuilder.Operator, String> OPERATOR_TO_STRING = new HashMap<>();
    private static final Map<ModelCriteriaBuilder.Operator, ExpressionCombinator> OPERATOR_TO_EXPRESSION_COMBINATORS = new HashMap<>();

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

    @FunctionalInterface
    public interface ExpressionCombinator {
        String combine(String fieldName, Object[] values, Map<String, Object> parameters);
    }

    private static String exists(String modelField, Object[] values, Map<String, Object> parameters) {
        String field = C + "." + modelField;
        return field + " IS NOT NULL AND " + field + " IS NOT EMPTY";
    }

    private static String notExists(String modelField, Object[] values, Map<String, Object> parameters) {
        String field = C + "." + modelField;
        return field + " IS NULL OR " + field + " IS EMPTY";
    }

    private static String in(String modelField, Object[] values, Map<String, Object> parameters) {
        if (values == null || values.length == 0) {
            return "false";
        }

        final Collection<?> operands;
        if (values.length == 1) {
            final Object value0 = values[0];
            if (value0 instanceof Collection) {
                operands = (Collection) value0;
            } else if (value0 instanceof Stream) {
                try (Stream valueS = (Stream) value0) {
                    operands = (Set) valueS.collect(Collectors.toSet());
                }
            } else {
                operands = Collections.singleton(value0);
            }
        } else {
            operands = new HashSet<>(Arrays.asList(values));
        }

        return C + "." + modelField + " IN (" + operands.stream()
                .map(operand -> {
                    String namedParam = findAvailableNamedParam(parameters.keySet(), modelField);
                    parameters.put(namedParam, operand);
                    return ":" + namedParam;
                })
                .collect(Collectors.joining(", ")) +
                ")";
    }

    private static String removeForbiddenCharactersFromNamedParameter(String name) {
        return name.replaceAll(UNWANTED_CHARACTERS_REGEX, "");
    }

    public static String findAvailableNamedParam(Set<String> existingNames, String namePrefix) {
        String namePrefixCleared = removeForbiddenCharactersFromNamedParameter(namePrefix);
        return IntStream.iterate(0, i -> i + 1)
                .boxed()
                .map(num -> namePrefixCleared + num)
                .filter(name -> !existingNames.contains(name))
                .findFirst().orElseThrow(() -> new IllegalArgumentException("Cannot create Parameter name for " + namePrefix));
    }

    private static ExpressionCombinator singleValueOperator(ModelCriteriaBuilder.Operator op) {
        return (modelFieldName, values, parameters) -> {
            if (values.length != 1) throw new RuntimeException("Invalid arguments, expected (" + modelFieldName + "), got: " + Arrays.toString(values));

            String namedParameter = findAvailableNamedParam(parameters.keySet(), modelFieldName);

            parameters.put(namedParameter, values[0]);
            return C + "." + modelFieldName + " " + IckleQueryOperators.operatorToString(op) + " :" + namedParameter;
        };
    }

    private static String operatorToString(ModelCriteriaBuilder.Operator op) {
        return OPERATOR_TO_STRING.get(op);
    }

    public static ExpressionCombinator operatorToExpressionCombinator(ModelCriteriaBuilder.Operator op) {
        return OPERATOR_TO_EXPRESSION_COMBINATORS.getOrDefault(op, singleValueOperator(op));
    }

    public static String combineExpressions(ModelCriteriaBuilder.Operator op, String filedName, Object[] values, Map<String, Object> parameters) {
        return operatorToExpressionCombinator(op).combine(filedName, values, parameters);
    }

}