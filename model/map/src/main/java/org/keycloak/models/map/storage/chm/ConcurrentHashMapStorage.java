/*
 * Copyright 2020 Red Hat, Inc. and/or its affiliates
 * and other contributors as indicated by the @author tags.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.keycloak.models.map.storage.chm;

import org.keycloak.models.AuthenticatedClientSessionModel;
import org.keycloak.models.UserLoginFailureModel;
import org.keycloak.models.UserSessionModel;
import org.keycloak.models.map.common.StringKeyConvertor;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.map.common.AbstractEntity;
import org.keycloak.models.map.common.DeepCloner;
import org.keycloak.models.map.common.UpdatableEntity;
import org.keycloak.models.map.storage.MapStorage;
import org.keycloak.models.map.storage.ModelCriteriaBuilder;
import org.keycloak.models.map.storage.QueryParameters;
import org.keycloak.storage.SearchableModelField;

import java.util.Comparator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;
import org.keycloak.models.map.storage.chm.MapModelCriteriaBuilder.UpdatePredicatesFunc;
import org.keycloak.utils.StreamsUtil;

import java.util.Objects;
import java.util.function.Predicate;

import static org.keycloak.utils.StreamsUtil.paginatedStream;

/**
 *
 * @author hmlnarik
 */
public class ConcurrentHashMapStorage<K, V extends AbstractEntity & UpdatableEntity, M> implements MapStorage<V, M> {

    protected final ConcurrentMap<K, V> store;

    protected final Map<SearchableModelField<M>, UpdatePredicatesFunc<K, V, M>> fieldPredicates;
    protected final StringKeyConvertor<K> keyConvertor;
    protected final DeepCloner cloner;
    protected final Class<M> modelClass;
    protected ConcurrentHashMapKeycloakTransaction<K, V, M> tx;
    
    @SuppressWarnings("unchecked")
    public ConcurrentHashMapStorage(KeycloakSession session, ConcurrentMap<K, V> store, Class<M> modelClass, StringKeyConvertor<K> keyConvertor, DeepCloner cloner) {
        this.store = store;
        this.fieldPredicates = MapFieldPredicates.getPredicates(modelClass);
        this.keyConvertor = keyConvertor;
        this.cloner = cloner;
        this.modelClass = modelClass;
        this.tx = createMyTransaction(session);

        if (modelClass == UserLoginFailureModel.class || modelClass == AuthenticatedClientSessionModel.class || modelClass == UserSessionModel.class) {
            session.getTransactionManager().enlistAfterCompletion(tx);
        } else {
            session.getTransactionManager().enlist(tx);
        }
    }

    @Override
    public V create(V value) {
        K key = keyConvertor.fromStringSafe(value.getId());
        if (key == null) {
            key = keyConvertor.yieldNewUniqueKey();
            value = cloner.from(keyConvertor.keyToString(key), value);
        }

        return tx.addCreateTask(value);
    }
    
    protected V createNonTx(V value) {
        store.putIfAbsent(keyConvertor.fromStringSafe(value.getId()), value);
        return value;
    }

    @Override
    public V read(String key) {
        if (key == null) return null;

        V value = tx.readValueFromTransaction(key);
        
        if (value != null) return value;

        value = store.get(keyConvertor.fromStringSafe(key));

        return value != null && tx.testNonRemovedByBulkDelete(value) ? tx.registerEntityForChanges(value) : null;
    }

    @Override
    public V update(V value) {
        return tx.updateIfChanged(value, v -> true);
    }

    @Override
    public boolean delete(String key) {
        return tx.addDeleteTask(key);
    }

    @Override
    public long delete(QueryParameters<M> queryParameters) {
        return tx.addBulkDeleteTask(queryParameters, this::bulkDelete) + getCount(queryParameters);
    }

    private void bulkDelete(QueryParameters<M> queryParameters) {
        ModelCriteriaBuilder<M> criteria = queryParameters.getModelCriteriaBuilder();

        if (criteria == null) {
            store.clear();
            return;
        }

        @SuppressWarnings("unchecked")
        MapModelCriteriaBuilder<K, V, M> b = criteria.unwrap(MapModelCriteriaBuilder.class);
        if (b == null) {
            throw new IllegalStateException("Incompatible class: " + criteria.getClass());
        }
        Predicate<? super K> keyFilter = b.getKeyFilter();
        Predicate<? super V> entityFilter = b.getEntityFilter();
        Stream<Entry<K, V>> storeStream = store.entrySet().stream();
        final AtomicLong res = new AtomicLong(0);

        if (!queryParameters.getOrderBy().isEmpty()) {
            Comparator<V> comparator = MapFieldPredicates.getComparator(queryParameters.getOrderBy().stream());
            storeStream = storeStream.sorted((entry1, entry2) -> comparator.compare(entry1.getValue(), entry2.getValue()));
        }

        paginatedStream(storeStream.filter(next -> keyFilter.test(next.getKey()) && entityFilter.test(next.getValue()))
                , queryParameters.getOffset(), queryParameters.getLimit())
                .peek(item -> {res.incrementAndGet();})
                .map(Entry::getKey)
                .forEach(store::remove);
    }

    @Override
    public ModelCriteriaBuilder<M> createCriteriaBuilder() {
        return new MapModelCriteriaBuilder<>(keyConvertor, fieldPredicates);
    }

    @SuppressWarnings("unchecked")
    private ConcurrentHashMapKeycloakTransaction<K, V, M> createMyTransaction(KeycloakSession session) {
        ConcurrentHashMapKeycloakTransaction<K, V, M> sessionTransaction = session.getAttribute("map-transaction-" + hashCode(), ConcurrentHashMapKeycloakTransaction.class);
        return sessionTransaction == null ? new ConcurrentHashMapKeycloakTransaction<>(store, keyConvertor, cloner) : sessionTransaction;
    }

    public StringKeyConvertor<K> getKeyConvertor() {
        return keyConvertor;
    }

    @Override
    public Stream<V> read(QueryParameters<M> queryParameters) {
        Stream<V> updatedAndNotRemovedObjectsStream = filteredStream(queryParameters)
                .filter(tx::testNonRemovedByBulkDelete)
                .map(tx::getUpdated)      // If the object has been removed, tx.get will return null, otherwise it will return me.getValue()
                .filter(Objects::nonNull);


        ModelCriteriaBuilder<M> mcb = queryParameters.getModelCriteriaBuilder();

        // In case of created values stored in MapKeycloakTransaction, we need filter those according to the filter
        MapModelCriteriaBuilder<K, V, M> mapMcb = mcb.unwrap(MapModelCriteriaBuilder.class);
        Stream<V> res = mapMcb == null
                ? updatedAndNotRemovedObjectsStream
                : Stream.concat(
                tx.createdValuesStream(queryParameters),
                updatedAndNotRemovedObjectsStream
        );

        if (!queryParameters.getOrderBy().isEmpty()) {
            res = res.sorted(MapFieldPredicates.getComparator(queryParameters.getOrderBy().stream()));
        }

        return StreamsUtil.paginatedStream(res, queryParameters.getOffset(), queryParameters.getLimit());
    }

    private Stream<V> filteredStream(QueryParameters<M> queryParameters) {
        ModelCriteriaBuilder<M> criteria = queryParameters.getModelCriteriaBuilder();

        if (criteria == null) {
            return Stream.empty();
        }
        Stream<Entry<K, V>> stream = store.entrySet().stream();

        @SuppressWarnings("unchecked")
        MapModelCriteriaBuilder<K, V, M> b = criteria.unwrap(MapModelCriteriaBuilder.class);
        if (b == null) {
            throw new IllegalStateException("Incompatible class: " + criteria.getClass());
        }
        Predicate<? super K> keyFilter = b.getKeyFilter();
        Predicate<? super V> entityFilter = b.getEntityFilter();
        stream = stream.filter(me -> keyFilter.test(me.getKey()) && entityFilter.test(me.getValue()));

        return stream.map(Map.Entry::getValue);
    }

    @Override
    public long getCount(QueryParameters<M> queryParameters) {
        return read(queryParameters).count();
    }

}
