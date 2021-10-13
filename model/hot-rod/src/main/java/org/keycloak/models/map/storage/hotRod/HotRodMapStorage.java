package org.keycloak.models.map.storage.hotRod;

import org.infinispan.client.hotrod.RemoteCache;
import org.infinispan.client.hotrod.Search;
import org.infinispan.commons.util.CloseableIterator;
import org.infinispan.query.dsl.Query;
import org.infinispan.query.dsl.QueryFactory;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.map.common.AbstractEntity;
import org.keycloak.models.map.common.HotRodEntityDescriptor;
import org.keycloak.models.map.common.Serialization;
import org.keycloak.models.map.common.StringKeyConvertor;
import org.keycloak.models.map.common.UpdatableEntity;
import org.keycloak.models.map.storage.MapKeycloakTransaction;
import org.keycloak.models.map.storage.MapStorage;
import org.keycloak.models.map.storage.ModelCriteriaBuilder;
import org.keycloak.models.map.storage.QueryParameters;
import org.keycloak.storage.SearchableModelField;

import java.util.Objects;
import java.util.Spliterators;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static org.keycloak.models.map.common.HotRodUtils.paginateQuery;
import static org.keycloak.utils.StreamsUtil.closing;

public class HotRodMapStorage<K, V extends AbstractEntity & UpdatableEntity, M> implements MapStorage<V, M> {

    private final RemoteCache<K, V> remoteCache;
    private final StringKeyConvertor<K> keyConvertor;
    private final HotRodEntityDescriptor<V> storedEntityDescriptor;

    public HotRodMapStorage(RemoteCache<K, V> remoteCache, StringKeyConvertor<K> keyConvertor, HotRodEntityDescriptor<V> storedEntityDescriptor) {
        this.remoteCache = remoteCache;
        this.keyConvertor = keyConvertor;
        this.storedEntityDescriptor = storedEntityDescriptor;
    }

    @Override
    public V create(V value) {
        K key = keyConvertor.fromStringSafe(value.getId());
        if (key == null) {
            value = Serialization.from(value);
            key = keyConvertor.yieldNewUniqueKey();
            value.setId(keyConvertor.keyToString(key));
        }

        remoteCache.putIfAbsent(key, value);

        return value;
    }

    @Override
    public V read(String key) {
        Objects.requireNonNull(key, "Key must be non-null");
        K k = keyConvertor.fromStringSafe(key);
        return remoteCache.get(k);
    }

    @Override
    public V update(V value) {
        K key = keyConvertor.fromStringSafe(value.getId());
        return remoteCache.replace(key, value);
    }

    @Override
    public boolean delete(String key) {
        K k = keyConvertor.fromStringSafe(key);
        return remoteCache.remove(k) != null;
    }

    private static String toOrderString(QueryParameters.OrderBy<?> orderBy) {
        SearchableModelField<?> field = orderBy.getModelField();
        String modelFieldName = IckleQueryMapModelCriteriaBuilder.getFieldName(field);
        String orderString = orderBy.getOrder().equals(QueryParameters.Order.ASCENDING) ? "ASC" : "DESC";

        return modelFieldName + " " + orderString;
    }

    @Override
    public Stream<V> read(QueryParameters<M> queryParameters) {
        IckleQueryMapModelCriteriaBuilder<K, V, M> iqmcb = queryParameters.getModelCriteriaBuilder().unwrap(IckleQueryMapModelCriteriaBuilder.class);
        String queryString = iqmcb.getIckleQuery();

        if (!queryParameters.getOrderBy().isEmpty()) {
            queryString += " ORDER BY " + queryParameters.getOrderBy().stream().map(HotRodMapStorage::toOrderString)
                                            .collect(Collectors.joining(", "));
        }

        System.out.println(queryString);

        QueryFactory queryFactory = Search.getQueryFactory(remoteCache);

        Query<V> query = paginateQuery(queryFactory.create(queryString), queryParameters.getOffset(),
                queryParameters.getLimit());

        query.setParameters(iqmcb.getParameters());

        CloseableIterator<V> iterator = query.iterator();
        return closing(StreamSupport.stream(Spliterators.spliteratorUnknownSize(iterator, 0), false))
                .onClose(iterator::close);
    }

    @Override
    public long getCount(QueryParameters<M> queryParameters) {
        IckleQueryMapModelCriteriaBuilder<K, V, M> iqmcb = queryParameters.getModelCriteriaBuilder().unwrap(IckleQueryMapModelCriteriaBuilder.class);
        String queryString = iqmcb.getIckleQuery();

        if (!queryParameters.getOrderBy().isEmpty()) {
            queryString += " ORDER BY " + queryParameters.getOrderBy().stream().map(HotRodMapStorage::toOrderString)
                    .collect(Collectors.joining(", "));
        }

        System.out.println(queryString);

        QueryFactory queryFactory = Search.getQueryFactory(remoteCache);

        Query<V> query = paginateQuery(queryFactory.create(queryString), queryParameters.getOffset(),
                queryParameters.getLimit());

        query.setParameters(iqmcb.getParameters());

        return query.execute().hitCount().orElse(0);
    }

    @Override
    public long delete(QueryParameters<M> queryParameters) {
        IckleQueryMapModelCriteriaBuilder<K, V, M> iqmcb = queryParameters.getModelCriteriaBuilder().unwrap(IckleQueryMapModelCriteriaBuilder.class);
        String queryString = "SELECT id " + iqmcb.getIckleQuery();

        if (!queryParameters.getOrderBy().isEmpty()) {
            queryString += " ORDER BY " + queryParameters.getOrderBy().stream().map(HotRodMapStorage::toOrderString)
                    .collect(Collectors.joining(", "));
        }

        System.out.println(queryString);

        QueryFactory queryFactory = Search.getQueryFactory(remoteCache);

        Query<V> query = paginateQuery(queryFactory.create(queryString), queryParameters.getOffset(),
                queryParameters.getLimit());

        query.setParameters(iqmcb.getParameters());

        AtomicLong result = new AtomicLong();

        CloseableIterator<V> iterator = query.iterator();
        StreamSupport.stream(Spliterators.spliteratorUnknownSize(iterator, 0), false)
                .peek(e -> {result.incrementAndGet();})
                .map(AbstractEntity::getId)
                .forEach(this::delete);
        iterator.close();

        return result.get();
    }

    @Override
    public ModelCriteriaBuilder<M> createCriteriaBuilder() {
        return new IckleQueryMapModelCriteriaBuilder<K, V, M>(keyConvertor, (Class<M>) storedEntityDescriptor.getModelTypeClass());
    }

    @Override
    public MapKeycloakTransaction<V, M> createTransaction(KeycloakSession session) {
        return new HotRodMapKeycloakTransaction<>(this, keyConvertor);
    }

    @Override
    public V newEntityInstance(String id) {
        if (id == null) {
            id = keyConvertor.keyToString(keyConvertor.yieldNewUniqueKey());
        }
        return storedEntityDescriptor.getEntityProducer().apply(id);
    }
}
