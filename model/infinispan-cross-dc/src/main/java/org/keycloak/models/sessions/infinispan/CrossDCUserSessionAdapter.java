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

import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;
import org.keycloak.models.sessions.infinispan.changes.InfinispanChangelogBasedTransaction;
import org.keycloak.models.sessions.infinispan.changes.SessionEntityWrapper;
import org.keycloak.models.sessions.infinispan.changes.UserSessionUpdateTask;
import org.keycloak.models.sessions.infinispan.changes.sessions.CrossDCLastSessionRefreshChecker;
import org.keycloak.models.sessions.infinispan.changes.sessions.CrossDCLastSessionRefreshListener;
import org.keycloak.models.sessions.infinispan.entities.AuthenticatedClientSessionEntity;
import org.keycloak.models.sessions.infinispan.entities.UserSessionEntity;

import java.util.UUID;

public class CrossDCUserSessionAdapter extends UserSessionAdapter {
    private final CrossDCUserSessionProvider provider;

    public CrossDCUserSessionAdapter(KeycloakSession session, UserModel user, CrossDCUserSessionProvider provider, InfinispanChangelogBasedTransaction<String, UserSessionEntity> userSessionUpdateTx, InfinispanChangelogBasedTransaction<UUID, AuthenticatedClientSessionEntity> clientSessionUpdateTx, RealmModel realm, UserSessionEntity entity, boolean offline) {
        super(session, user, provider, userSessionUpdateTx, clientSessionUpdateTx, realm, entity, offline);
        this.provider = provider;
    }

    @Override
    public void setLastSessionRefresh(int lastSessionRefresh) {
        if (offline) {
            // Received the message from the other DC that we should update the lastSessionRefresh in local cluster. Don't update DB in that case.
            // The other DC already did.
            Boolean ignoreRemoteCacheUpdate = (Boolean) session.getAttribute(CrossDCLastSessionRefreshListener.IGNORE_REMOTE_CACHE_UPDATE);
            if (ignoreRemoteCacheUpdate == null || !ignoreRemoteCacheUpdate) {
                provider.getPersisterLastSessionRefreshStore().putLastSessionRefresh(session, entity.getId(), realm.getId(), lastSessionRefresh);
            }
        }

        UserSessionUpdateTask task = new UserSessionUpdateTask() {

            @Override
            public void runUpdate(UserSessionEntity entity) {
                entity.setLastSessionRefresh(lastSessionRefresh);
            }

            @Override
            public CrossDCMessageStatus getCrossDCMessageStatus(SessionEntityWrapper<UserSessionEntity> sessionWrapper) {
                return new CrossDCLastSessionRefreshChecker(provider.getLastSessionRefreshStore(), provider.getOfflineLastSessionRefreshStore())
                        .shouldSaveUserSessionToRemoteCache(session, realm, sessionWrapper, offline, lastSessionRefresh);
            }

            @Override
            public String toString() {
                return "setLastSessionRefresh(" + lastSessionRefresh + ')';
            }
        };

        update(task);
    }
}
