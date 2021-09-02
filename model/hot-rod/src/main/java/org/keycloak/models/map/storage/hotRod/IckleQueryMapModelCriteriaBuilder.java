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

import static org.keycloak.models.map.storage.hotRod.IckleQueryOperators.C;
import static org.keycloak.models.map.storage.hotRod.IckleQueryUtils.escapeIfNecessary;

public class IckleQueryMapModelCriteriaBuilder<K, V extends AbstractEntity, M> implements ModelCriteriaBuilder<M> {

    private static final int INITIAL_BUILDER_CAPACITY = 250;
    private final StringBuilder whereClauseBuilder = new StringBuilder(INITIAL_BUILDER_CAPACITY);
    private MapModelCriteriaBuilder<K, V, M> modelCriteria;
    public static final Map<SearchableModelField<?>, String> INFINISPAN_NAMES = new HashMap<>();

    static {
        INFINISPAN_NAMES.put(ClientModel.SearchableFields.SCOPE_MAPPING_ROLE, "scopeMappings");
        INFINISPAN_NAMES.put(ClientModel.SearchableFields.ATTRIBUTE, "attributes");
    }

    public IckleQueryMapModelCriteriaBuilder(StringBuilder whereClauseBuilder, MapModelCriteriaBuilder<K, V, M> modelCriteria) {
        this.whereClauseBuilder.append(whereClauseBuilder);
        this.modelCriteria = modelCriteria;
    }

    public IckleQueryMapModelCriteriaBuilder(StringKeyConvertor<K> keyConvertor, Class<M> modelClass) {
        modelCriteria = new MapModelCriteriaBuilder<>(keyConvertor, MapFieldPredicates.getPredicates(modelClass));
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
        newBuilder.append(whereClauseProducer.produceWhereClause(getFieldName(modelField), op, value));

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
