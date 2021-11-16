/*
 * Copyright 2021 Red Hat, Inc. and/or its affiliates
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

package org.keycloak.models.map.common;


import org.keycloak.models.map.client.MapClientEntity;
import org.keycloak.models.map.client.migration.ClientEntityMigration;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;

public class Migration<V extends AbstractEntity & UpdatableEntity> {

    public static final Migration<?> NO_MIGRATIONS = new Migration<>(Collections.emptyList());

    private final List<UnaryOperator<V>> migrators;
    private static final Map<Class<?>, Migration<?>> migrations = new HashMap<>();

    static {
        migrations.put(MapClientEntity.class, new ClientEntityMigration());
    }

    public Migration(List<UnaryOperator<V>> migrators) {
        this.migrators = migrators;
    }

    public V migrate(V entity) {
        if (!Versioned.class.isAssignableFrom(entity.getClass())) {
            return entity;
        }
        int version = ((Versioned) entity).getEntityVersion();

        UnaryOperator<V> resultingMigrator =  migrators.stream()
                // skip migrators that are not necessary
                .skip(version)
                // Merge migrators together
                .reduce(UnaryOperator.identity(), (v1, v2) -> e -> v2.apply(v1.apply(e)));

        return resultingMigrator.apply(entity);
    }



    public V makeBackwardCompatible(V entity) {
        return entity;
    }

    public static <V extends AbstractEntity & UpdatableEntity> Migration<V> getMigration(Class<V> entityClass) {
        return (Migration<V>) migrations.getOrDefault(entityClass, NO_MIGRATIONS);
    }

}
