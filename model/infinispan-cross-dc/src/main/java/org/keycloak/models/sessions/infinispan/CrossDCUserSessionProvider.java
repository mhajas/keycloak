/*
 * Copyright 2016 Red Hat, Inc. and/or its affiliates
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

import org.infinispan.Cache;
import org.keycloak.models.ClientModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;
import org.keycloak.models.UserSessionModel;
import org.keycloak.models.sessions.infinispan.changes.CrossDCChangelogBasedTransaction;
import org.keycloak.models.sessions.infinispan.changes.InfinispanChangelogBasedTransaction;
import org.keycloak.models.sessions.infinispan.changes.SessionEntityWrapper;
import org.keycloak.models.sessions.infinispan.changes.sessions.CrossDCLastSessionRefreshStore;
import org.keycloak.models.sessions.infinispan.changes.sessions.PersisterLastSessionRefreshStore;
import org.keycloak.models.sessions.infinispan.entities.AuthenticatedClientSessionEntity;
import org.keycloak.models.sessions.infinispan.entities.SessionEntity;
import org.keycloak.models.sessions.infinispan.entities.UserSessionEntity;
import org.keycloak.models.sessions.infinispan.remotestore.RemoteCacheInvoker;
import org.keycloak.models.sessions.infinispan.util.InfinispanKeyGenerator;

import java.util.UUID;

/**
 * @author <a href="mailto:sthorger@redhat.com">Stian Thorgersen</a>
 */
public class CrossDCUserSessionProvider extends InfinispanUserSessionProvider {

    protected final CrossDCLastSessionRefreshStore lastSessionRefreshStore;
    protected final CrossDCLastSessionRefreshStore offlineLastSessionRefreshStore;

    protected final RemoteCacheInvoker remoteCacheInvoker;

    public CrossDCUserSessionProvider(KeycloakSession session,
                                      RemoteCacheInvoker remoteCacheInvoker,
                                      CrossDCLastSessionRefreshStore lastSessionRefreshStore,
                                      CrossDCLastSessionRefreshStore offlineLastSessionRefreshStore,
                                      PersisterLastSessionRefreshStore persisterLastSessionRefreshStore,
                                      InfinispanKeyGenerator keyGenerator,
                                      Cache<String, SessionEntityWrapper<UserSessionEntity>> sessionCache,
                                      Cache<String, SessionEntityWrapper<UserSessionEntity>> offlineSessionCache,
                                      Cache<UUID, SessionEntityWrapper<AuthenticatedClientSessionEntity>> clientSessionCache,
                                      Cache<UUID, SessionEntityWrapper<AuthenticatedClientSessionEntity>> offlineClientSessionCache,
                                      boolean loadOfflineSessionsFromDatabase) {
        super(session, persisterLastSessionRefreshStore, keyGenerator, sessionCache, offlineSessionCache, clientSessionCache, offlineClientSessionCache, loadOfflineSessionsFromDatabase);
        this.lastSessionRefreshStore = lastSessionRefreshStore;
        this.offlineLastSessionRefreshStore = offlineLastSessionRefreshStore;
        this.remoteCacheInvoker = remoteCacheInvoker;
    }

    @Override
    protected <K, V extends SessionEntity> InfinispanChangelogBasedTransaction<K, V> createTransaction(Cache<K, SessionEntityWrapper<V>> cacheParameter, SessionFunction<V> lifespanMsLoader, SessionFunction<V> maxIdleTimeMsLoader) {
        return new CrossDCChangelogBasedTransaction<>(session, cacheParameter, remoteCacheInvoker, lifespanMsLoader, maxIdleTimeMsLoader);
    }

    protected CrossDCLastSessionRefreshStore getLastSessionRefreshStore() {
        return lastSessionRefreshStore;
    }

    protected CrossDCLastSessionRefreshStore getOfflineLastSessionRefreshStore() {
        return offlineLastSessionRefreshStore;
    }

    @Override
    protected AuthenticatedClientSessionAdapter createAuthenticatedClientSessionAdapter(KeycloakSession kcSession, AuthenticatedClientSessionEntity entity, ClientModel client, UserSessionModel userSession, InfinispanChangelogBasedTransaction<UUID, AuthenticatedClientSessionEntity> clientSessionUpdateTx, boolean offline) {
        return new CrossDCAuthenticatedClientSessionAdapter(kcSession, this, entity, client, userSession, clientSessionUpdateTx, offline);
    }

    @Override
    protected UserSessionAdapter createUserSessionAdapter(KeycloakSession session, UserModel user, InfinispanChangelogBasedTransaction<String, UserSessionEntity> userSessionUpdateTx, InfinispanChangelogBasedTransaction<UUID, AuthenticatedClientSessionEntity> clientSessionUpdateTx, RealmModel realm, UserSessionEntity entity, boolean offline) {
        return new CrossDCUserSessionAdapter(session, user, this, userSessionUpdateTx, clientSessionUpdateTx, realm, entity, offline);
    }
}
