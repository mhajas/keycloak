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

package org.keycloak.models.map.client.migration;

import org.keycloak.models.map.client.HotRodClientEntity;
import org.keycloak.models.map.client.MapClientEntity;
import org.keycloak.models.map.common.Migration;

import java.util.LinkedList;
import java.util.List;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;

public class ClientEntityMigration extends Migration<MapClientEntity> {

    private static List<UnaryOperator<HotRodClientEntityDelegate>> migrations = new LinkedList<>();

    static {
        migrations.add(HotRodClientEntityMigratorToVersion1::new);
    }

    public ClientEntityMigration() {
        super(migrations.stream().map(ClientEntityMigration::onClientInterface).collect(Collectors.toList()));
    }

    @Override
    public MapClientEntity makeBackwardCompatible(MapClientEntity entity) {
        if (!(entity instanceof HotRodClientEntity)) {
            return entity;
        }
        HotRodClientEntity e = (HotRodClientEntity) entity;
        e.oldClientId = e.getClientId();
        return entity;
    }

    private static UnaryOperator<MapClientEntity> onClientInterface(UnaryOperator<HotRodClientEntityDelegate> delegateUnaryOperator) {
        return entity -> {
            if (entity instanceof HotRodClientEntityDelegate) {
                return delegateUnaryOperator.apply((HotRodClientEntityDelegate) entity);
            }

            if (entity instanceof HotRodClientEntity) {
                return delegateUnaryOperator.apply(new HotRodClientEntityDelegate((HotRodClientEntity) entity));
            }

            return entity;
        };
    }

}
