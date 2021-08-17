package org.keycloak.models.map.storage.hotRod;

import org.keycloak.models.ClientModel;
import org.keycloak.models.map.common.AbstractEntity;
import org.keycloak.models.map.common.StringKeyConvertor;
import org.keycloak.models.map.storage.CriterionNotSupportedException;
import org.keycloak.models.map.storage.ModelCriteriaBuilder;
import org.keycloak.models.map.storage.chm.MapFieldPredicates;
import org.keycloak.models.map.storage.chm.MapModelCriteriaBuilder;
import org.keycloak.storage.SearchableModelField;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class IckleQueryMapModelCriteriaBuilder<K, V extends AbstractEntity, M> implements ModelCriteriaBuilder<M> {

    protected static final String C = "c";
    private static final int INITIAL_BUILDER_CAPACITY = 250;
    private final StringBuilder whereClauseBuilder = new StringBuilder(INITIAL_BUILDER_CAPACITY);
    private MapModelCriteriaBuilder<K, V, M> modelCriteria;
    private static final Map<Operator, BiFunction<SearchableModelField<?>, Object[], String>> OPERATOR_TO_CLAUSE_PRODUCER = new HashMap<>();
    public static final Map<SearchableModelField<?>, String> INFINISPAN_NAMES = new HashMap<>();
    private static final Map<Operator, String> OPERATOR_TO_STRING = new HashMap<>();

    static {
        OPERATOR_TO_CLAUSE_PRODUCER.put(Operator.IN, IckleQueryMapModelCriteriaBuilder::in);
        OPERATOR_TO_CLAUSE_PRODUCER.put(Operator.EXISTS, IckleQueryMapModelCriteriaBuilder::exists);
        OPERATOR_TO_CLAUSE_PRODUCER.put(Operator.NOT_EXISTS, functionForOperator("LIKE"));

        OPERATOR_TO_STRING.put(Operator.EQ, "=");
        OPERATOR_TO_STRING.put(Operator.NE, "!=");
        OPERATOR_TO_STRING.put(Operator.LT, "<");
        OPERATOR_TO_STRING.put(Operator.LE, "<=");
        OPERATOR_TO_STRING.put(Operator.GT, ">");
        OPERATOR_TO_STRING.put(Operator.GE, ">=");
        OPERATOR_TO_STRING.put(Operator.LIKE, "LIKE");
        OPERATOR_TO_STRING.put(Operator.ILIKE, "LIKE");
        OPERATOR_TO_STRING.put(Operator.IN, "IN");
        OPERATOR_TO_STRING.put(Operator.EXISTS, "LIKE");
        OPERATOR_TO_STRING.put(Operator.NOT_EXISTS, "LIKE");

        INFINISPAN_NAMES.put(ClientModel.SearchableFields.SCOPE_MAPPING_ROLE, "scopeMappings");
    }

    public IckleQueryMapModelCriteriaBuilder(StringBuilder whereClauseBuilder, MapModelCriteriaBuilder<K, V, M> modelCriteria) {
        this.whereClauseBuilder.append(whereClauseBuilder);
        this.modelCriteria = modelCriteria;
    }

    public IckleQueryMapModelCriteriaBuilder(StringKeyConvertor<K> keyConvertor, Class<M> modelClass) {
        modelCriteria = new MapModelCriteriaBuilder<>(keyConvertor, MapFieldPredicates.getPredicates(modelClass));
    }

    private static String escapeIfNecessary(Object o) {
        if (o instanceof String) {
            return "'" + o + "'";
        }

        if (o instanceof Integer) {
            return o.toString();
        }

        throw new IllegalArgumentException("Wrong argument of type " + o.getClass().getName());
    }

    private static BiFunction<SearchableModelField<?>, Object[], String> functionForOperator(String op) {
        return (a, b) -> {throw new UnsupportedOperationException(op);};
    }

    public static String getFieldName(SearchableModelField<?> modelField) {
        return INFINISPAN_NAMES.getOrDefault(modelField, modelField.getName());
    }

    private static BiFunction<SearchableModelField<?>, Object[], String> singleValueOperator(Operator op) {
        return (modelField, values) -> {
            String fieldName = getFieldName(modelField);

            if (values.length != 1) throw new CriterionNotSupportedException(modelField, op,
                    "Invalid arguments, expected (" + fieldName + "), got: " + Arrays.toString(values));

            return C + "." + fieldName + " " + OPERATOR_TO_STRING.get(op) + " " + escapeIfNecessary(values[0]);
        };
    }

    private static String exists(SearchableModelField<?> modelField, Object[] values) {
        String field = C + "." + getFieldName(modelField);
        return field + " IS NOT NULL AND " + field + " IS NOT EMPTY";
    }

    private static String notExists(SearchableModelField<?> modelField, Object[] values) {
        String field = C + "." + getFieldName(modelField);
        return field + " IS NULL OR " + field + " IS EMPTY";
    }

    private static String in(SearchableModelField<?> modelField, Object[] values) {
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

        return C + "." + getFieldName(modelField) + " IN (" + operand.stream()
                .map(IckleQueryMapModelCriteriaBuilder::escapeIfNecessary)
                    .collect(Collectors.joining(", ")) +
                ")";
    }

    @Override
    public ModelCriteriaBuilder<M> compare(SearchableModelField<M> modelField, Operator op, Object... value) {
        StringBuilder newBuilder = new StringBuilder(INITIAL_BUILDER_CAPACITY);
        newBuilder.append("(");

        if (whereClauseBuilder.length() != 0) {
            newBuilder.append(whereClauseBuilder).append(" AND (");
        }

        newBuilder.append(OPERATOR_TO_CLAUSE_PRODUCER.getOrDefault(op, singleValueOperator(op)).apply(modelField, value));

        if (whereClauseBuilder.length() != 0) {
            newBuilder.append(")");
        }

        return new IckleQueryMapModelCriteriaBuilder<>(newBuilder.append(")"), modelCriteria.compare(modelField, op, value));
    }

    private String joinBuilders(ModelCriteriaBuilder<M>[] builders, String delimiter) {
        return Arrays.stream(builders).map(mcb -> mcb.unwrap(IckleQueryMapModelCriteriaBuilder.class).getWhereClauseBuilder()).collect(Collectors.joining(delimiter));
    }

    @Override
    public ModelCriteriaBuilder<M> and(ModelCriteriaBuilder<M>... builders) {
        return new IckleQueryMapModelCriteriaBuilder<>(new StringBuilder(INITIAL_BUILDER_CAPACITY).append("(").append(joinBuilders(builders, " AND ")).append(")"), modelCriteria.and(builders));
    }

    @Override
    public ModelCriteriaBuilder<M> or(ModelCriteriaBuilder<M>... builders) {
        return new IckleQueryMapModelCriteriaBuilder<>(new StringBuilder(INITIAL_BUILDER_CAPACITY).append("(").append(joinBuilders(builders, " OR ")).append(")"), modelCriteria.or(builders));
    }

    @Override
    public ModelCriteriaBuilder<M> not(ModelCriteriaBuilder<M> builder) {
        StringBuilder newBuilder = new StringBuilder(INITIAL_BUILDER_CAPACITY);
        StringBuilder originalBuilder = builder.unwrap(IckleQueryMapModelCriteriaBuilder.class).getWhereClauseBuilder();

        if (originalBuilder.length() != 0) {
            newBuilder.append("not").append(originalBuilder);
        }

        return new IckleQueryMapModelCriteriaBuilder<>(newBuilder, modelCriteria.not(builder));
    }

    public StringBuilder getWhereClauseBuilder() {
        return whereClauseBuilder;
    }

    public Predicate<? super K> getKeyFilter() {
        return modelCriteria.getKeyFilter();
    }

    public Predicate<? super V> getEntityFilter() {
        return modelCriteria.getEntityFilter();
    }

    public String getIckleQuery() {
        return "FROM org.keycloak.models.map.storage.hotrod.HotRodClientEntity " + C + ((whereClauseBuilder.length() != 0) ? " WHERE " + whereClauseBuilder : "");
    }
}
