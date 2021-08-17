package org.keycloak.models.map.common;

import java.util.List;
import java.util.function.Function;
import java.util.stream.Stream;

public class HotRodEntityDescriptor<EntityType> {
    private final Class<?> modelTypeClass;
    private final Class<EntityType> entityTypeClass;
    private final List<Class<?>> hotRodClasses;
    private final Function<String, EntityType> entityProducer;
    private final String cacheName;

    public HotRodEntityDescriptor(Class<?> modelTypeClass, Class<EntityType> entityTypeClass, List<Class<?>> hotRodClasses, Function<String, EntityType> entityProducer, String cacheName) {
        this.modelTypeClass = modelTypeClass;
        this.entityTypeClass = entityTypeClass;
        this.hotRodClasses = hotRodClasses;
        this.entityProducer = entityProducer;
        this.cacheName = cacheName;
    }

    public Class<?> getModelTypeClass() {
        return modelTypeClass;
    }

    public Class<EntityType> getEntityTypeClass() {
        return entityTypeClass;
    }

    public Stream<Class<?>> getHotRodClasses() {
        return hotRodClasses.stream();
    }

    public Function<String, EntityType> getEntityProducer() {
        return entityProducer;
    }

    public String getCacheName() {
        return cacheName;
    }
}
