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

import org.jboss.logging.Logger;
import org.keycloak.models.KeycloakTransaction;
import org.keycloak.models.map.common.AbstractEntity;
import org.keycloak.models.map.common.DeepCloner;
import org.keycloak.models.map.common.StringKeyConvertor;
import org.keycloak.models.map.common.UpdatableEntity;
import org.keycloak.models.map.storage.ModelCriteriaBuilder;
import org.keycloak.models.map.storage.QueryParameters;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class ConcurrentHashMapKeycloakTransaction<K, V extends AbstractEntity & UpdatableEntity, M> implements KeycloakTransaction {

    private final static Logger log = Logger.getLogger(ConcurrentHashMapKeycloakTransaction.class);

    protected boolean active;
    protected boolean rollback;
    protected final Map<String, MapTaskWithValue> tasks = new LinkedHashMap<>();
    protected final Map<K, V> map;
    protected final StringKeyConvertor<K> keyConvertor;
    protected final DeepCloner cloner;

    enum MapOperation {
        CREATE, UPDATE, DELETE,
    }

    public ConcurrentHashMapKeycloakTransaction(Map<K, V> map, StringKeyConvertor<K> keyConvertor, DeepCloner cloner) {
        this.map = map;
        this.keyConvertor = keyConvertor;
        this.cloner = cloner;
    }

    @Override
    public void begin() {
        active = true;
    }

    @Override
    public void commit() {
        if (rollback) {
            throw new RuntimeException("Rollback only!");
        }

        if (! tasks.isEmpty()) {
            log.tracef("Commit - %s", map);
            for (MapTaskWithValue value : tasks.values()) {
                value.execute();
            }
        }
    }

    @Override
    public void rollback() {
        tasks.clear();
    }

    @Override
    public void setRollbackOnly() {
        rollback = true;
    }

    @Override
    public boolean getRollbackOnly() {
        return rollback;
    }

    @Override
    public boolean isActive() {
        return active;
    }

    /**
     * Adds a given task if not exists for the given key
     */
    protected void addTask(String key, MapTaskWithValue task) {
        log.tracef("Adding operation %s for %s @ %08x", task.getOperation(), key, System.identityHashCode(task.getValue()));

        tasks.merge(key, task, MapTaskCompose::new);
    }

    /**
     * Returns a deep clone of an entity. If the clone is already in the transaction, returns this one.
     * <p>
     * Usually used before giving an entity from a source back to the caller,
     * to prevent changing it directly in the data store, but to keep transactional properties.
     * @param origEntity Original entity
     * @return
     */
    public V registerEntityForChanges(V origEntity) {
        final String key = origEntity.getId();
        // If the entity is listed in the transaction already, return it directly
        if (tasks.containsKey(key)) {
            MapTaskWithValue current = tasks.get(key);
            return current.getValue();
        }
        // Else enlist its copy in the transaction. Never return direct reference to the underlying map
        final V res = cloner.from(origEntity);
        return updateIfChanged(res, e -> e.isUpdated());
    }

    public V read(String key, Function<K, V> defaultValueFunc) {
        MapTaskWithValue current = tasks.get(key);
        // If the key exists, then it has entered the "tasks" after bulk delete that could have 
        // removed it, so looking through bulk deletes is irrelevant
        if (tasks.containsKey(key)) {
            return current.getValue();
        }

        // If the key does not exist, then it would be read fresh from the storage, but then it
        // could have been removed by some bulk delete in the existing tasks. Check it.
        final V value = defaultValueFunc.apply(keyConvertor.fromStringSafe(key));
        for (MapTaskWithValue val : tasks.values()) {
            if (val instanceof ConcurrentHashMapKeycloakTransaction.BulkDeleteOperation) {
                final BulkDeleteOperation delOp = (BulkDeleteOperation) val;
                if (! delOp.getFilterForNonDeletedObjects().test(value)) {
                    return null;
                }
            }
        }

        return registerEntityForChanges(value);
    }

    public V updateIfChanged(V value, Predicate<V> shouldPut) {
        String key = value.getId();
        log.tracef("Adding operation UPDATE_IF_CHANGED for %s @ %08x", key, System.identityHashCode(value));

        String taskKey = key;
        MapTaskWithValue op = new MapTaskWithValue(value) {
            @Override
            public void execute() {
                V value = getValue();
                if (shouldPut.test(value)) {
                    map.replace(keyConvertor.fromStringSafe(value.getId()), value);
                }
            }
            @Override public MapOperation getOperation() { return MapOperation.UPDATE; }
        };
        return tasks.merge(taskKey, op, this::merge).getValue();
    }

    public V getUpdated(V orig) {
        MapTaskWithValue current = orig == null ? null : tasks.get(orig.getId());
        return current == null ? orig : current.getValue();
    }

    public V addCreateTask(V value) {
        addTask(value.getId(), new CreateOperation(value));
        return value;
    }

    public boolean addDeleteTask(String key) {
        addTask(key, new DeleteOperation(key));
        return true;
    }

    public long addBulkDeleteTask(QueryParameters<M> queryParameters, Consumer<QueryParameters<M>> bulkDeleteExecution) {
        log.tracef("Adding operation DELETE_BULK");

        K artificialKey = keyConvertor.yieldNewUniqueKey();

        // Remove all tasks that create / update / delete objects deleted by the bulk removal.
        final BulkDeleteOperation bdo = new BulkDeleteOperation(queryParameters) {
            @Override
            public void execute() {
                bulkDeleteExecution.accept(queryParameters);
            }
        };

        Predicate<V> filterForNonDeletedObjects = bdo.getFilterForNonDeletedObjects();
        long res = 0;
        for (Iterator<Entry<String, MapTaskWithValue>> it = tasks.entrySet().iterator(); it.hasNext();) {
            Entry<String, MapTaskWithValue> me = it.next();
            if (! filterForNonDeletedObjects.test(me.getValue().getValue())) {
                log.tracef(" [DELETE_BULK] removing %s", me.getKey());
                it.remove();
                res++;
            }
        }

        tasks.put(keyConvertor.keyToString(artificialKey), bdo);

        return res;
    }

    public V readValueFromTransaction(String key) {
        MapTaskWithValue current = tasks.get(key);
        // If the key exists, then it has entered the "tasks" after bulk delete that could have 
        // removed it, so looking through bulk deletes is irrelevant
        if (tasks.containsKey(key)) {
            return current.getValue();
        }
        
        return null;
    }
    
    public boolean testNonRemovedByBulkDelete(V value) {
        for (MapTaskWithValue val : tasks.values()) {
            if (val instanceof ConcurrentHashMapKeycloakTransaction.BulkDeleteOperation) {
                final BulkDeleteOperation delOp = (BulkDeleteOperation) val;
                if (! delOp.getFilterForNonDeletedObjects().test(value)) {
                    return false;
                }
            }
        }
        
        return true;
    }

    public Stream<V> createdValuesStream(QueryParameters<M> queryParameters) {
        ModelCriteriaBuilder<M> mcb = queryParameters.getModelCriteriaBuilder();

        // In case of created values stored in MapKeycloakTransaction, we need filter those according to the filter
        MapModelCriteriaBuilder<K, V, M> mapMcb = mcb.unwrap(MapModelCriteriaBuilder.class);
        if (mapMcb == null) return Stream.empty();

        Predicate<? super K> keyFilter = mapMcb.getKeyFilter();
        Predicate<? super V> entityFilter = mapMcb.getEntityFilter();

        return this.tasks.entrySet().stream()
                .filter(me -> keyFilter.test(keyConvertor.fromStringSafe(me.getKey())))
                .map(Map.Entry::getValue)
                .filter(v -> v.containsCreate() && ! v.isReplace())
                .map(MapTaskWithValue::getValue)
                .filter(Objects::nonNull)
                .filter(entityFilter)
                // make a snapshot
                .collect(Collectors.toList()).stream();
    }

    private MapTaskWithValue merge(MapTaskWithValue oldValue, MapTaskWithValue newValue) {
        switch (newValue.getOperation()) {
            case DELETE:
                return oldValue.containsCreate() ? null : newValue;
            default:
                return new MapTaskCompose(oldValue, newValue);
        }
    }

    protected abstract class MapTaskWithValue {
        protected final V value;

        public MapTaskWithValue(V value) {
            this.value = value;
        }

        public V getValue() {
            return value;
        }

        public boolean containsCreate() {
            return MapOperation.CREATE == getOperation();
        }

        public boolean containsRemove() {
            return MapOperation.DELETE == getOperation();
        }

        public boolean isReplace() {
            return false;
        }

        public abstract MapOperation getOperation();
        public abstract void execute();
   }

    private class MapTaskCompose extends MapTaskWithValue {

        private final MapTaskWithValue oldValue;
        private final MapTaskWithValue newValue;

        public MapTaskCompose(MapTaskWithValue oldValue, MapTaskWithValue newValue) {
            super(null);
            this.oldValue = oldValue;
            this.newValue = newValue;
        }

        @Override
        public void execute() {
            oldValue.execute();
            newValue.execute();
        }

        @Override
        public V getValue() {
            return newValue.getValue();
        }

        @Override
        public MapOperation getOperation() {
            return null;
        }

        @Override
        public boolean containsCreate() {
            return oldValue.containsCreate() || newValue.containsCreate();
        }

        @Override
        public boolean containsRemove() {
            return oldValue.containsRemove() || newValue.containsRemove();
        }

        @Override
        public boolean isReplace() {
            return (newValue.getOperation() == MapOperation.CREATE && oldValue.containsRemove()) ||
              (oldValue instanceof ConcurrentHashMapKeycloakTransaction.MapTaskCompose && ((MapTaskCompose) oldValue).isReplace());
        }
    }

    private class CreateOperation extends MapTaskWithValue {
        public CreateOperation(V value) {
            super(value);
        }

        @Override public void execute() { 
            V value = getValue();
            map.putIfAbsent(keyConvertor.fromStringSafe(value.getId()), value);
        }
        @Override public MapOperation getOperation() { return MapOperation.CREATE; }
    }

    private class DeleteOperation extends MapTaskWithValue {
        private final String key;

        public DeleteOperation(String key) {
            super(null);
            this.key = key;
        }

        @Override public void execute() { map.remove(keyConvertor.fromStringSafe(key)); }
        @Override public MapOperation getOperation() { return MapOperation.DELETE; }
    }

    private abstract class BulkDeleteOperation extends MapTaskWithValue {

        private final QueryParameters<M> queryParameters;

        public BulkDeleteOperation(QueryParameters<M> queryParameters) {
            super(null);
            this.queryParameters = queryParameters;
        }

        public Predicate<V> getFilterForNonDeletedObjects() {
            if (! (queryParameters.getModelCriteriaBuilder() instanceof MapModelCriteriaBuilder)) {
                return t -> true;
            }

            @SuppressWarnings("unchecked")
            final MapModelCriteriaBuilder<K, V, M> mmcb = queryParameters.getModelCriteriaBuilder().unwrap(MapModelCriteriaBuilder.class);
            
            Predicate<? super V> entityFilter = mmcb.getEntityFilter();
            Predicate<? super K> keyFilter = mmcb.getKeyFilter();
            return v -> v == null || ! (keyFilter.test(keyConvertor.fromStringSafe(v.getId())) && entityFilter.test(v));
        }

        @Override
        public MapOperation getOperation() {
            return MapOperation.DELETE;
        }
    }
}
