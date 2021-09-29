package org.keycloak.models.map.storage.hotRod;

import org.keycloak.models.ClientModel;
import org.keycloak.models.map.common.AbstractEntity;
import org.keycloak.models.map.common.StringKeyConvertor;
import org.keycloak.models.map.storage.ModelCriteriaBuilder;
import org.keycloak.models.map.storage.chm.MapFieldPredicates;
import org.keycloak.models.map.storage.chm.MapModelCriteriaBuilder;
import org.keycloak.storage.SearchableModelField;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static org.keycloak.models.map.storage.hotRod.IckleQueryOperators.C;

public class IckleQueryMapModelCriteriaBuilder<K, V extends AbstractEntity, M> implements ModelCriteriaBuilder<M> {

    private static final int INITIAL_BUILDER_CAPACITY = 250;
    private final StringBuilder whereClauseBuilder = new StringBuilder(INITIAL_BUILDER_CAPACITY);
    private final Map<String, Object> parameters = new HashMap<>();
    private MapModelCriteriaBuilder<K, V, M> mapModelCriteria;
    public static final Map<SearchableModelField<?>, String> INFINISPAN_NAMES = new HashMap<>();

    static {
        INFINISPAN_NAMES.put(ClientModel.SearchableFields.SCOPE_MAPPING_ROLE, "scopeMappings");
        INFINISPAN_NAMES.put(ClientModel.SearchableFields.ATTRIBUTE, "attributes");
    }

    public IckleQueryMapModelCriteriaBuilder(StringBuilder whereClauseBuilder, MapModelCriteriaBuilder<K, V, M> mapModelCriteria, Map<String, Object> parameters) {
        this.whereClauseBuilder.append(whereClauseBuilder);
        this.mapModelCriteria = mapModelCriteria;
        this.parameters.putAll(parameters);
    }

    public IckleQueryMapModelCriteriaBuilder(StringKeyConvertor<K> keyConvertor, Class<M> modelClass) {
        mapModelCriteria = new MapModelCriteriaBuilder<>(keyConvertor, MapFieldPredicates.getPredicates(modelClass));
    }

    public static String getFieldName(SearchableModelField<?> modelField) {
        return INFINISPAN_NAMES.getOrDefault(modelField, modelField.getName());
    }

    @Override
    public ModelCriteriaBuilder<M> compare(SearchableModelField<M> modelField, Operator op, Object... value) {
        StringBuilder newBuilder = new StringBuilder(INITIAL_BUILDER_CAPACITY);
        newBuilder.append("(");

        if (whereClauseBuilder.length() != 0) {
            newBuilder.append(whereClauseBuilder).append(" AND (");
        }

        IckleQueryWhereClauses.WhereClauseProducer whereClauseProducer = IckleQueryWhereClauses.whereClauseProducerForModelField(modelField);
        newBuilder.append(whereClauseProducer.produceWhereClause(getFieldName(modelField), op, value, parameters));

        if (whereClauseBuilder.length() != 0) {
            newBuilder.append(")");
        }

        return new IckleQueryMapModelCriteriaBuilder<>(newBuilder.append(")"), mapModelCriteria.compare(modelField, op, value), parameters);
    }

    private StringBuilder joinBuilders(ModelCriteriaBuilder<M>[] builders, String delimiter) {
        return new StringBuilder(INITIAL_BUILDER_CAPACITY).append("(").append(Arrays.stream(builders).map(mcb -> mcb.unwrap(IckleQueryMapModelCriteriaBuilder.class).getWhereClauseBuilder()).collect(Collectors.joining(delimiter))).append(")");
    }

    private Map<String, Object> joinParameters(ModelCriteriaBuilder<M>[] builders) {
        return Arrays.stream(builders).map(mcb -> mcb.unwrap(IckleQueryMapModelCriteriaBuilder.class))
                .map(IckleQueryMapModelCriteriaBuilder::getParameters)
                .map(params -> (Set<Map.Entry<String, Object>>) params.entrySet())
                .flatMap(Collection::stream)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    @Override
    public ModelCriteriaBuilder<M> and(ModelCriteriaBuilder<M>... builders) {
        return new IckleQueryMapModelCriteriaBuilder<>(joinBuilders(builders, " AND "),
                mapModelCriteria.and(builders),
                joinParameters(builders));
    }

    @Override
    public ModelCriteriaBuilder<M> or(ModelCriteriaBuilder<M>... builders) {
        return new IckleQueryMapModelCriteriaBuilder<>(joinBuilders(builders, " OR "),
                mapModelCriteria.or(builders),
                joinParameters(builders));
    }

    @Override
    public ModelCriteriaBuilder<M> not(ModelCriteriaBuilder<M> builder) {
        StringBuilder newBuilder = new StringBuilder(INITIAL_BUILDER_CAPACITY);
        IckleQueryMapModelCriteriaBuilder<?, ?, M> iqcb = builder.unwrap(IckleQueryMapModelCriteriaBuilder.class);
        StringBuilder originalBuilder = iqcb.getWhereClauseBuilder();

        if (originalBuilder.length() != 0) {
            newBuilder.append("not").append(originalBuilder);
        }

        return new IckleQueryMapModelCriteriaBuilder<>(newBuilder, mapModelCriteria.not(builder), iqcb.getParameters());
    }

    public StringBuilder getWhereClauseBuilder() {
        return whereClauseBuilder;
    }

    public Predicate<? super K> getKeyFilter() {
        return mapModelCriteria.getKeyFilter();
    }

    public Predicate<? super V> getEntityFilter() {
        return mapModelCriteria.getEntityFilter();
    }

    public String getIckleQuery() {
        return "FROM org.keycloak.models.map.storage.hotrod.HotRodClientEntity " + C + ((whereClauseBuilder.length() != 0) ? " WHERE " + whereClauseBuilder : "");
    }

    public Map<String, Object> getParameters() {
        return parameters;
    }
}
