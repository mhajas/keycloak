package org.keycloak.models.map.storage;

import org.keycloak.storage.SearchableModelField;

public class QueryParameters<M> {

    protected final Integer firstResult;
    protected final Integer maxResults;
    protected final SearchableModelField<M> orderByField;
    protected final ModelCriteriaBuilder.Order order;

    public QueryParameters(Integer firstResult, Integer maxResults, SearchableModelField<M> orderByField, ModelCriteriaBuilder.Order order) {
        this.firstResult = firstResult;
        this.maxResults = maxResults;
        this.orderByField = orderByField;
        this.order = order;
    }

    public Integer getFirstResult() {
        return firstResult;
    }

    public Integer getMaxResults() {
        return maxResults;
    }

    public SearchableModelField<M> getOrderByField() {
        return orderByField;
    }

    public ModelCriteriaBuilder.Order getOrder() {
        return order;
    }

    public static class Builder<M> {

        private Integer firstResult;
        private Integer maxResults;
        private SearchableModelField<M> orderByField;
        private ModelCriteriaBuilder.Order order;

        public static <M> Builder<M> create(Class<M> mClass) {
            return new Builder<>();
        }

        public Builder<M> pagination(Integer firstResult, Integer maxResults, SearchableModelField<M> orderByField) {
            return firstResult(firstResult)
                    .maxResults(maxResults)
                    .orderBy(orderByField);
        }

        public Builder<M> firstResult(Integer firstResult) {
            this.firstResult = firstResult;
            return this;
        }

        public Builder<M> maxResults(Integer maxResults) {
            this.maxResults = maxResults;
            return this;
        }

        public Builder<M> orderBy(SearchableModelField<M> modelField) {
            return orderBy(modelField, ModelCriteriaBuilder.Order.ASCENDING);
        }

        public Builder<M> orderBy(SearchableModelField<M> modelField, ModelCriteriaBuilder.Order order) {
            this.orderByField = modelField;
            this.order = order;

            return this;
        }

        public QueryParameters<M> build() {
            return new QueryParameters<>(firstResult, maxResults, orderByField, order);
        }
    }
}
