/*
 * Copyright 2024 Red Hat, Inc. and/or its affiliates
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

package org.keycloak.models.sessions.infinispan;

import org.keycloak.models.ClientModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.UserSessionModel;
import org.keycloak.models.sessions.infinispan.changes.ClientSessionUpdateTask;
import org.keycloak.models.sessions.infinispan.changes.InfinispanChangelogBasedTransaction;
import org.keycloak.models.sessions.infinispan.changes.SessionEntityWrapper;
import org.keycloak.models.sessions.infinispan.changes.sessions.CrossDCLastSessionRefreshChecker;
import org.keycloak.models.sessions.infinispan.entities.AuthenticatedClientSessionEntity;

import java.util.UUID;

public class CrossDCAuthenticatedClientSessionAdapter extends AuthenticatedClientSessionAdapter {
    private final CrossDCUserSessionProvider provider;
    public CrossDCAuthenticatedClientSessionAdapter(KeycloakSession kcSession, CrossDCUserSessionProvider provider, AuthenticatedClientSessionEntity entity, ClientModel client, UserSessionModel userSession, InfinispanChangelogBasedTransaction<UUID, AuthenticatedClientSessionEntity> clientSessionUpdateTx, boolean offline) {
        super(kcSession, provider, entity, client, userSession, clientSessionUpdateTx, offline);
        this.provider = provider;
    }

    @Override
    public void setTimestamp(int timestamp) {
        ClientSessionUpdateTask task = new ClientSessionUpdateTask() {

            @Override
            public void runUpdate(AuthenticatedClientSessionEntity entity) {
                entity.setTimestamp(timestamp);
            }

            @Override
            public CrossDCMessageStatus getCrossDCMessageStatus(SessionEntityWrapper<AuthenticatedClientSessionEntity> sessionWrapper) {
                return new CrossDCLastSessionRefreshChecker(provider.getLastSessionRefreshStore(), provider.getOfflineLastSessionRefreshStore())
                        .shouldSaveClientSessionToRemoteCache(kcSession, client.getRealm(), sessionWrapper, userSession, offline, timestamp);
            }

            @Override
            public String toString() {
                return "setTimestamp(" + timestamp + ')';
            }

        };

        update(task);
    }
}
