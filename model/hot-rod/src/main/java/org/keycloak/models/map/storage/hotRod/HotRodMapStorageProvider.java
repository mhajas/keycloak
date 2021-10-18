package org.keycloak.models.map.storage.hotRod;

import org.keycloak.models.map.common.AbstractEntity;
import org.keycloak.models.map.common.HotRodEntityDescriptor;
import org.keycloak.models.map.common.StringKeyConvertor;
import org.keycloak.models.map.common.UpdatableEntity;
import org.keycloak.models.map.connections.HotRodConnectionProvider;
import org.keycloak.models.map.storage.MapStorage;
import org.keycloak.models.map.storage.MapStorageProvider;
import org.keycloak.models.map.storage.MapStorageProviderFactory;

public class HotRodMapStorageProvider implements MapStorageProvider {

    private final HotRodMapStorageProviderFactory factory;
    private final HotRodConnectionProvider connectionProvider;

    public HotRodMapStorageProvider(HotRodMapStorageProviderFactory factory, HotRodConnectionProvider connectionProvider) {
        this.factory = factory;
        this.connectionProvider = connectionProvider;
    }

    @Override
    public <V extends AbstractEntity, M> MapStorage<V, M> getStorage(Class<M> modelType, MapStorageProviderFactory.Flag... flags) {
        HotRodMapStorage storage = getHotRodStorage(modelType, flags);
        return storage;
    }

    @SuppressWarnings("unchecked")
    public <V extends AbstractEntity & UpdatableEntity, M> HotRodMapStorage<String, V, M> getHotRodStorage(Class<M> modelType, MapStorageProviderFactory.Flag... flags) {
        HotRodEntityDescriptor<V> entityDescriptor = (HotRodEntityDescriptor<V>) factory.getEntityDescriptor(modelType);
        return new HotRodMapStorage<>(connectionProvider.getRemoteCache(entityDescriptor.getCacheName()), StringKeyConvertor.StringKey.INSTANCE, entityDescriptor);
    }

    @Override
    public void close() {

    }
}
