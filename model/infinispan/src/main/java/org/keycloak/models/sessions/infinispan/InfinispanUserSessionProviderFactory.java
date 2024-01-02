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
import org.jboss.logging.Logger;
import org.keycloak.Config;
import org.keycloak.cluster.ClusterProvider;
import org.keycloak.common.util.Environment;
import org.keycloak.connections.infinispan.InfinispanConnectionProvider;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.models.KeycloakSessionTask;
import org.keycloak.models.UserModel;
import org.keycloak.models.UserSessionProvider;
import org.keycloak.models.UserSessionProviderFactory;
import org.keycloak.models.sessions.infinispan.changes.SessionEntityWrapper;
import org.keycloak.models.sessions.infinispan.changes.sessions.PersisterLastSessionRefreshStore;
import org.keycloak.models.sessions.infinispan.changes.sessions.PersisterLastSessionRefreshStoreFactory;
import org.keycloak.models.sessions.infinispan.entities.AuthenticatedClientSessionEntity;
import org.keycloak.models.sessions.infinispan.entities.UserSessionEntity;
import org.keycloak.models.sessions.infinispan.events.AbstractUserSessionClusterListener;
import org.keycloak.models.sessions.infinispan.events.ClientRemovedSessionEvent;
import org.keycloak.models.sessions.infinispan.events.RealmRemovedSessionEvent;
import org.keycloak.models.sessions.infinispan.events.RemoveUserSessionsEvent;
import org.keycloak.models.sessions.infinispan.initializer.CacheInitializer;
import org.keycloak.models.sessions.infinispan.initializer.DBLockBasedCacheInitializer;
import org.keycloak.models.sessions.infinispan.initializer.InfinispanCacheInitializer;
import org.keycloak.models.sessions.infinispan.initializer.OfflinePersistentUserSessionLoader;
import org.keycloak.models.sessions.infinispan.util.InfinispanKeyGenerator;
import org.keycloak.models.utils.KeycloakModelUtils;
import org.keycloak.models.utils.PostMigrationEvent;
import org.keycloak.provider.ProviderEvent;
import org.keycloak.provider.ProviderEventListener;

import java.io.Serializable;
import java.util.UUID;

import static org.keycloak.models.sessions.infinispan.InfinispanAuthenticationSessionProviderFactory.PROVIDER_PRIORITY;

public class InfinispanUserSessionProviderFactory implements UserSessionProviderFactory {

    private static final Logger log = Logger.getLogger(InfinispanUserSessionProviderFactory.class);

    public static final String PROVIDER_ID = "infinispan";

    public static final String REALM_REMOVED_SESSION_EVENT = "REALM_REMOVED_EVENT_SESSIONS";

    public static final String CLIENT_REMOVED_SESSION_EVENT = "CLIENT_REMOVED_SESSION_SESSIONS";

    public static final String REMOVE_USER_SESSIONS_EVENT = "REMOVE_USER_SESSIONS_EVENT";

    protected boolean preloadOfflineSessionsFromDatabase;

    private Config.Scope config;
    protected InfinispanKeyGenerator keyGenerator;
    protected PersisterLastSessionRefreshStore persisterLastSessionRefreshStore;


    @Override
    public InfinispanUserSessionProvider create(KeycloakSession session) {
        InfinispanConnectionProvider connections = session.getProvider(InfinispanConnectionProvider.class);
        Cache<String, SessionEntityWrapper<UserSessionEntity>> cache = connections.getCache(InfinispanConnectionProvider.USER_SESSION_CACHE_NAME);
        Cache<String, SessionEntityWrapper<UserSessionEntity>> offlineSessionsCache = connections.getCache(InfinispanConnectionProvider.OFFLINE_USER_SESSION_CACHE_NAME);
        Cache<UUID, SessionEntityWrapper<AuthenticatedClientSessionEntity>> clientSessionCache = connections.getCache(InfinispanConnectionProvider.CLIENT_SESSION_CACHE_NAME);
        Cache<UUID, SessionEntityWrapper<AuthenticatedClientSessionEntity>> offlineClientSessionsCache = connections.getCache(InfinispanConnectionProvider.OFFLINE_CLIENT_SESSION_CACHE_NAME);

        return new InfinispanUserSessionProvider(session, persisterLastSessionRefreshStore, keyGenerator, cache,
                offlineSessionsCache, clientSessionCache, offlineClientSessionsCache,!preloadOfflineSessionsFromDatabase);
    }

    @Override
    public void init(Config.Scope config) {
        this.config = config;
        preloadOfflineSessionsFromDatabase = config.getBoolean("preloadOfflineSessionsFromDatabase", false);
    }

    @Override
    public void postInit(final KeycloakSessionFactory factory) {
        factory.register(new ProviderEventListener() {

            @Override
            public void onEvent(ProviderEvent event) {
                if (event instanceof PostMigrationEvent) {

                    int preloadTransactionTimeout = getTimeoutForPreloadingSessionsSeconds();
                    log.debugf("Will preload sessions with transaction timeout %d seconds", preloadTransactionTimeout);

                    KeycloakModelUtils.runJobInTransactionWithTimeout(factory, (KeycloakSession session) -> {

                        keyGenerator = new InfinispanKeyGenerator();
                        loadPersistentSessions(factory, getMaxErrors(), getSessionsPerSegment());
                        registerClusterListeners(session);
                    }, preloadTransactionTimeout);

                } else if (event instanceof UserModel.UserRemovedEvent) {
                    UserModel.UserRemovedEvent userRemovedEvent = (UserModel.UserRemovedEvent) event;

                    InfinispanUserSessionProvider provider = (InfinispanUserSessionProvider) userRemovedEvent.getKeycloakSession().getProvider(UserSessionProvider.class, getId());
                    provider.onUserRemoved(userRemovedEvent.getRealm(), userRemovedEvent.getUser());
                }
            }
        });
    }

    // Max count of worker errors. Initialization will end with exception when this number is reached
    protected int getMaxErrors() {
        return config.getInt("maxErrors", 20);
    }

    // Count of sessions to be computed in each segment
    protected int getSessionsPerSegment() {
        return config.getInt("sessionsPerSegment", 64);
    }

    protected int getTimeoutForPreloadingSessionsSeconds() {
        Integer timeout = config.getInt("sessionsPreloadTimeoutInSeconds", null);
        return timeout != null ? timeout : Environment.getServerStartupTimeout();
    }

    protected int getStalledTimeoutInSeconds(int defaultTimeout) {
         return config.getInt("sessionPreloadStalledTimeoutInSeconds", defaultTimeout);
    }

    @Override
    public void loadPersistentSessions(final KeycloakSessionFactory sessionFactory, final int maxErrors, final int sessionsPerSegment) {

        KeycloakModelUtils.runJobInTransaction(sessionFactory, new KeycloakSessionTask() {

            @Override
            public void run(KeycloakSession session) {

                if (preloadOfflineSessionsFromDatabase) {
                    // only preload offline-sessions if necessary
                    log.debug("Start pre-loading userSessions from persistent storage");

                    InfinispanConnectionProvider connections = session.getProvider(InfinispanConnectionProvider.class);
                    Cache<String, Serializable> workCache = connections.getCache(InfinispanConnectionProvider.WORK_CACHE_NAME);
                    int defaultStateTransferTimeout = (int) (connections.getCache(InfinispanConnectionProvider.OFFLINE_USER_SESSION_CACHE_NAME)
                      .getCacheConfiguration().clustering().stateTransfer().timeout() / 1000);

                    InfinispanCacheInitializer ispnInitializer = new InfinispanCacheInitializer(sessionFactory, workCache,
                            new OfflinePersistentUserSessionLoader(sessionsPerSegment), "offlineUserSessions", sessionsPerSegment, maxErrors,
                            getStalledTimeoutInSeconds(defaultStateTransferTimeout));

                    // DB-lock to ensure that persistent sessions are loaded from DB just on one DC. The other DCs will load them from remote cache.
                    CacheInitializer initializer = new DBLockBasedCacheInitializer(session, ispnInitializer);

                    initializer.initCache();
                    initializer.loadSessions();

                    log.debug("Pre-loading userSessions from persistent storage finished");
                } else {
                    log.debug("Skipping pre-loading of userSessions from persistent storage");
                }

                // Initialize persister for periodically doing bulk DB updates of lastSessionRefresh timestamps of refreshed sessions
                persisterLastSessionRefreshStore = new PersisterLastSessionRefreshStoreFactory().createAndInit(session, true);
            }

        });


    }

    protected void registerClusterListeners(KeycloakSession session) {
        KeycloakSessionFactory sessionFactory = session.getKeycloakSessionFactory();
        ClusterProvider cluster = session.getProvider(ClusterProvider.class);

        cluster.registerListener(REALM_REMOVED_SESSION_EVENT,
                new AbstractUserSessionClusterListener<RealmRemovedSessionEvent, UserSessionProvider>(sessionFactory, UserSessionProvider.class) {

            @Override
            protected void eventReceived(KeycloakSession session, UserSessionProvider provider, RealmRemovedSessionEvent sessionEvent) {
                if (provider instanceof InfinispanUserSessionProvider) {
                    ((InfinispanUserSessionProvider) provider).onRealmRemovedEvent(sessionEvent.getRealmId());
                }
            }

        });

        cluster.registerListener(CLIENT_REMOVED_SESSION_EVENT,
                new AbstractUserSessionClusterListener<ClientRemovedSessionEvent, UserSessionProvider>(sessionFactory, UserSessionProvider.class) {

            @Override
            protected void eventReceived(KeycloakSession session, UserSessionProvider provider, ClientRemovedSessionEvent sessionEvent) {
                if (provider instanceof InfinispanUserSessionProvider) {
                    ((InfinispanUserSessionProvider) provider).onClientRemovedEvent(sessionEvent.getRealmId(), sessionEvent.getClientUuid());
                }
            }

        });

        cluster.registerListener(REMOVE_USER_SESSIONS_EVENT,
                new AbstractUserSessionClusterListener<RemoveUserSessionsEvent, UserSessionProvider>(sessionFactory, UserSessionProvider.class) {

            @Override
            protected void eventReceived(KeycloakSession session, UserSessionProvider provider, RemoveUserSessionsEvent sessionEvent) {
                if (provider instanceof InfinispanUserSessionProvider) {
                    ((InfinispanUserSessionProvider) provider).onRemoveUserSessionsEvent(sessionEvent.getRealmId());
                }
            }

        });

        log.debug("Registered cluster listeners");
    }

    @Override
    public void close() {
    }

    @Override
    public String getId() {
        return PROVIDER_ID;
    }

    @Override
    public int order() {
        return PROVIDER_PRIORITY;
    }
}

