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
import org.keycloak.models.map.client.MapClientEntityDelegate;
import org.keycloak.models.map.common.Versioned;
import org.keycloak.models.map.common.delegate.SimpleDelegateProvider;

public class HotRodClientEntityDelegate extends MapClientEntityDelegate implements Versioned {
    private HotRodClientEntity originalEntity;
    private int version;

    public HotRodClientEntityDelegate(HotRodClientEntityDelegate originalEntity, int version) {
        super(new SimpleDelegateProvider<HotRodClientEntityDelegate>(originalEntity));
        this.originalEntity = originalEntity.getOriginalEntity();
        this.version = version;
    }

    public HotRodClientEntityDelegate(HotRodClientEntity originalEntity) {
        super(new SimpleDelegateProvider<MapClientEntity>(originalEntity));
        this.originalEntity = originalEntity;
        this.version = originalEntity.getEntityVersion();
    }

    protected HotRodClientEntity getOriginalEntity() {
        return originalEntity;
    }

    @Override
    public int getEntityVersion() {
        return version;
    }
}
