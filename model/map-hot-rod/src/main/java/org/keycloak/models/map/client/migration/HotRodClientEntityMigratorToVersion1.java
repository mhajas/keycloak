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

public class HotRodClientEntityMigratorToVersion1 extends HotRodClientEntityDelegate {
    public HotRodClientEntityMigratorToVersion1(HotRodClientEntityDelegate previousEntityDelegate) {
        super(previousEntityDelegate, 1);
    }

    @Override
    public String getClientId() {
        if (super.getClientId() == null) {
            return getOriginalEntity().oldClientId;
        }

        return super.getClientId();
    }

    @Override
    public int getEntityVersion() {
        return 1;
    }
}
