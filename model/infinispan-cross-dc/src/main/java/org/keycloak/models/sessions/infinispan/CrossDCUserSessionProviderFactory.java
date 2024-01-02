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

import org.infinispan.Cache;
import org.infinispan.client.hotrod.RemoteCache;
import org.infinispan.persistence.remote.RemoteStore;
import org.jboss.logging.Logger;
import org.keycloak.common.util.Time;
import org.keycloak.connections.infinispan.InfinispanConnectionProvider;
import org.keycloak.connections.infinispan.InfinispanUtil;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.models.KeycloakSessionTask;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;
import org.keycloak.models.UserSessionProvider;
import org.keycloak.models.sessions.infinispan.changes.SessionEntityWrapper;
import org.keycloak.models.sessions.infinispan.changes.sessions.CrossDCLastSessionRefreshStore;
import org.keycloak.models.sessions.infinispan.changes.sessions.CrossDCLastSessionRefreshStoreFactory;
import org.keycloak.models.sessions.infinispan.entities.AuthenticatedClientSessionEntity;
import org.keycloak.models.sessions.infinispan.entities.SessionEntity;
import org.keycloak.models.sessions.infinispan.entities.UserSessionEntity;
import org.keycloak.models.sessions.infinispan.initializer.InfinispanCacheInitializer;
import org.keycloak.models.sessions.infinispan.remotestore.RemoteCacheInvoker;
import org.keycloak.models.sessions.infinispan.remotestore.RemoteCacheSessionListener;
import org.keycloak.models.sessions.infinispan.remotestore.RemoteCacheSessionsLoader;
import org.keycloak.models.sessions.infinispan.util.InfinispanKeyGenerator;
import org.keycloak.models.sessions.infinispan.util.SessionTimeouts;
import org.keycloak.models.utils.KeycloakModelUtils;
import org.keycloak.models.utils.PostMigrationEvent;
import org.keycloak.models.utils.ResetTimeOffsetEvent;
import org.keycloak.provider.ProviderEvent;
import org.keycloak.provider.ProviderEventListener;

import java.io.Serializable;
import java.util.Set;
import java.util.UUID;

public class CrossDCUserSessionProviderFactory extends InfinispanUserSessionProviderFactory {

    private static final Logger LOG = Logger.getLogger(CrossDCUserSessionProviderFactory.class);

    public static final String PROVIDER_ID = "crossdc";

    private RemoteCacheInvoker remoteCacheInvoker;
    private CrossDCLastSessionRefreshStore lastSessionRefreshStore;
    private CrossDCLastSessionRefreshStore offlineLastSessionRefreshStore;

    @Override
    public InfinispanUserSessionProvider create(KeycloakSession session) {
        InfinispanConnectionProvider connections = session.getProvider(InfinispanConnectionProvider.class);
        Cache<String, SessionEntityWrapper<UserSessionEntity>> cache = connections.getCache(InfinispanConnectionProvider.USER_SESSION_CACHE_NAME);
        Cache<String, SessionEntityWrapper<UserSessionEntity>> offlineSessionsCache = connections.getCache(InfinispanConnectionProvider.OFFLINE_USER_SESSION_CACHE_NAME);
        Cache<UUID, SessionEntityWrapper<AuthenticatedClientSessionEntity>> clientSessionCache = connections.getCache(InfinispanConnectionProvider.CLIENT_SESSION_CACHE_NAME);
        Cache<UUID, SessionEntityWrapper<AuthenticatedClientSessionEntity>> offlineClientSessionsCache = connections.getCache(InfinispanConnectionProvider.OFFLINE_CLIENT_SESSION_CACHE_NAME);

        return new CrossDCUserSessionProvider(session, remoteCacheInvoker, lastSessionRefreshStore,
                offlineLastSessionRefreshStore, persisterLastSessionRefreshStore, keyGenerator, cache,
                offlineSessionsCache, clientSessionCache, offlineClientSessionsCache, !preloadOfflineSessionsFromDatabase);
    }

    @Override
    public String getId() {
        return PROVIDER_ID;
    }

    @Override
    public void postInit(final KeycloakSessionFactory factory) {
        factory.register(new ProviderEventListener() {

            @Override
            public void onEvent(ProviderEvent event) {
                if (event instanceof PostMigrationEvent) {

                    int preloadTransactionTimeout = getTimeoutForPreloadingSessionsSeconds();
                    LOG.debugf("Will preload sessions with transaction timeout %d seconds", preloadTransactionTimeout);

                    KeycloakModelUtils.runJobInTransactionWithTimeout(factory, (KeycloakSession session) -> {

                        keyGenerator = new InfinispanKeyGenerator();
                        checkRemoteCaches(session);
                        loadPersistentSessions(factory, getMaxErrors(), getSessionsPerSegment());
                        registerClusterListeners(session);
                        loadSessionsFromRemoteCaches(session);

                    }, preloadTransactionTimeout);

                } else if (event instanceof UserModel.UserRemovedEvent) {
                    UserModel.UserRemovedEvent userRemovedEvent = (UserModel.UserRemovedEvent) event;

                    InfinispanUserSessionProvider provider = (InfinispanUserSessionProvider) userRemovedEvent.getKeycloakSession().getProvider(UserSessionProvider.class, getId());
                    provider.onUserRemoved(userRemovedEvent.getRealm(), userRemovedEvent.getUser());
                } else if (event instanceof ResetTimeOffsetEvent) {
                    if (persisterLastSessionRefreshStore != null) {
                        persisterLastSessionRefreshStore.reset();
                    }
                    if (lastSessionRefreshStore != null) {
                        lastSessionRefreshStore.reset();
                    }
                    if (offlineLastSessionRefreshStore != null) {
                        offlineLastSessionRefreshStore.reset();
                    }
                }
            }
        });
    }

    private void loadSessionsFromRemoteCaches(KeycloakSession session) {
        for (String cacheName : remoteCacheInvoker.getRemoteCacheNames()) {
            loadSessionsFromRemoteCache(session.getKeycloakSessionFactory(), cacheName, getSessionsPerSegment(), getMaxErrors());
        }
    }

    protected void checkRemoteCaches(KeycloakSession session) {
        this.remoteCacheInvoker = new RemoteCacheInvoker();

        InfinispanConnectionProvider ispn = session.getProvider(InfinispanConnectionProvider.class);

        Cache<String, SessionEntityWrapper<UserSessionEntity>> sessionsCache = ispn.getCache(InfinispanConnectionProvider.USER_SESSION_CACHE_NAME);
        RemoteCache sessionsRemoteCache = checkRemoteCache(session, sessionsCache, (RealmModel realm) -> {
            // We won't write to the remoteCache during token refresh, so the timeout needs to be longer.
            return Time.toMillis(realm.getSsoSessionMaxLifespan());
        }, SessionTimeouts::getUserSessionLifespanMs, SessionTimeouts::getUserSessionMaxIdleMs);

        if (sessionsRemoteCache != null) {
            lastSessionRefreshStore = new CrossDCLastSessionRefreshStoreFactory().createAndInit(session, sessionsCache, false);
        }

        Cache<UUID, SessionEntityWrapper<AuthenticatedClientSessionEntity>> clientSessionsCache = ispn.getCache(InfinispanConnectionProvider.CLIENT_SESSION_CACHE_NAME);
        checkRemoteCache(session, clientSessionsCache, (RealmModel realm) -> {
            // We won't write to the remoteCache during token refresh, so the timeout needs to be longer.
            return Time.toMillis(realm.getSsoSessionMaxLifespan());
        }, SessionTimeouts::getClientSessionLifespanMs, SessionTimeouts::getClientSessionMaxIdleMs);

        Cache<String, SessionEntityWrapper<UserSessionEntity>> offlineSessionsCache = ispn.getCache(InfinispanConnectionProvider.OFFLINE_USER_SESSION_CACHE_NAME);
        RemoteCache offlineSessionsRemoteCache = checkRemoteCache(session, offlineSessionsCache, (RealmModel realm) -> {
            return Time.toMillis(realm.getOfflineSessionIdleTimeout());
        }, SessionTimeouts::getOfflineSessionLifespanMs, SessionTimeouts::getOfflineSessionMaxIdleMs);

        if (offlineSessionsRemoteCache != null) {
            offlineLastSessionRefreshStore = new CrossDCLastSessionRefreshStoreFactory().createAndInit(session, offlineSessionsCache, true);
        }

        Cache<UUID, SessionEntityWrapper<AuthenticatedClientSessionEntity>> offlineClientSessionsCache = ispn.getCache(InfinispanConnectionProvider.OFFLINE_CLIENT_SESSION_CACHE_NAME);
        checkRemoteCache(session, offlineClientSessionsCache, (RealmModel realm) -> {
            return Time.toMillis(realm.getOfflineSessionIdleTimeout());
        }, SessionTimeouts::getOfflineClientSessionLifespanMs, SessionTimeouts::getOfflineClientSessionMaxIdleMs);
    }

    private <K, V extends SessionEntity> RemoteCache checkRemoteCache(KeycloakSession session, Cache<K, SessionEntityWrapper<V>> ispnCache, RemoteCacheInvoker.MaxIdleTimeLoader maxIdleLoader,
                                                                      SessionFunction<V> lifespanMsLoader, SessionFunction<V> maxIdleTimeMsLoader) {
        Set<RemoteStore> remoteStores = InfinispanUtil.getRemoteStores(ispnCache);

        if (remoteStores.isEmpty()) {
            LOG.debugf("No remote store configured for cache '%s'", ispnCache.getName());
            return null;
        } else {
            LOG.infof("Remote store configured for cache '%s'", ispnCache.getName());

            RemoteCache<K, SessionEntityWrapper<V>> remoteCache = (RemoteCache) remoteStores.iterator().next().getRemoteCache();

            if (remoteCache == null) {
                throw new IllegalStateException("No remote cache available for the infinispan cache: " + ispnCache.getName());
            }

            remoteCacheInvoker.addRemoteCache(ispnCache.getName(), remoteCache, maxIdleLoader);

            RemoteCacheSessionListener hotrodListener = RemoteCacheSessionListener.createListener(session, ispnCache, remoteCache, lifespanMsLoader, maxIdleTimeMsLoader);
            remoteCache.addClientListener(hotrodListener);
            return remoteCache;
        }
    }

    private void loadSessionsFromRemoteCache(final KeycloakSessionFactory sessionFactory, String cacheName, final int sessionsPerSegment, final int maxErrors) {
        LOG.debugf("Check pre-loading sessions from remote cache '%s'", cacheName);

        KeycloakModelUtils.runJobInTransaction(sessionFactory, new KeycloakSessionTask() {

            @Override
            public void run(KeycloakSession session) {
                InfinispanConnectionProvider connections = session.getProvider(InfinispanConnectionProvider.class);
                Cache<String, Serializable> workCache = connections.getCache(InfinispanConnectionProvider.WORK_CACHE_NAME);
                int defaultStateTransferTimeout = (int) (connections.getCache(InfinispanConnectionProvider.USER_SESSION_CACHE_NAME)
                        .getCacheConfiguration().clustering().stateTransfer().timeout() / 1000);

                InfinispanCacheInitializer initializer = new InfinispanCacheInitializer(sessionFactory, workCache,
                        new RemoteCacheSessionsLoader(cacheName, sessionsPerSegment), "remoteCacheLoad::" + cacheName, sessionsPerSegment, maxErrors,
                        getStalledTimeoutInSeconds(defaultStateTransferTimeout));

                initializer.initCache();
                initializer.loadSessions();
            }

        });

        LOG.debugf("Pre-loading sessions from remote cache '%s' finished", cacheName);
    }

}
