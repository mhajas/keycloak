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

package org.keycloak.testsuite.model;

import org.junit.Assert;
import org.junit.Test;
import org.keycloak.common.util.Time;
import org.keycloak.connections.infinispan.InfinispanConnectionProvider;
import org.keycloak.models.AuthenticatedClientSessionModel;
import org.keycloak.models.ClientModel;
import org.keycloak.models.Constants;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.RealmProvider;
import org.keycloak.models.UserManager;
import org.keycloak.models.UserModel;
import org.keycloak.models.UserProvider;
import org.keycloak.models.UserSessionModel;
import org.keycloak.models.UserSessionProvider;
import org.keycloak.models.UserSessionProviderFactory;
import org.keycloak.models.map.userSession.MapUserSessionAdapter;
import org.keycloak.models.map.userSession.MapUserSessionProvider;
import org.keycloak.models.session.UserSessionPersisterProvider;
import org.keycloak.models.sessions.infinispan.InfinispanUserSessionProvider;
import org.keycloak.services.managers.UserSessionManager;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;

/**
 * @author <a href="mailto:mposolda@redhat.com">Marek Posolda</a>
 * @author <a href="mailto:mabartos@redhat.com">Martin Bartos</a>
 * @author <a href="mailto:mkanis@redhat.com">Martin Kanis</a>
 */
@RequireProvider(UserSessionPersisterProvider.class)
@RequireProvider(UserSessionProvider.class)
@RequireProvider(UserProvider.class)
@RequireProvider(RealmProvider.class)
public class UserSessionInitializerTest extends KeycloakModelTest {
    private String realmId;

    @Override
    public void createEnvironment(KeycloakSession s) {
        RealmModel realm = s.realms().createRealm("test");
        realm.setOfflineSessionIdleTimeout(Constants.DEFAULT_OFFLINE_SESSION_IDLE_TIMEOUT);
        realm.setDefaultRole(s.roles().addRealmRole(realm, Constants.DEFAULT_ROLES_ROLE_PREFIX + "-" + realm.getName()));
        this.realmId = realm.getId();

        s.users().addUser(realm, "user1").setEmail("user1@localhost");
        s.users().addUser(realm, "user2").setEmail("user2@localhost");

        UserSessionPersisterProviderTest.createClients(s, realm);
    }

    @Override
    public void cleanEnvironment(KeycloakSession s) {
        RealmModel realm = s.realms().getRealm(realmId);
        s.sessions().removeUserSessions(realm);

        UserModel user1 = s.users().getUserByUsername(realm, "user1");
        UserModel user2 = s.users().getUserByUsername(realm, "user2");

        UserManager um = new UserManager(s);
        if (user1 != null) {
            um.removeUser(realm, user1);
        }
        if (user2 != null) {
            um.removeUser(realm, user2);
        }

        s.realms().removeRealm(realmId);
    }

    @Test
    public void testUserSessionInitializer() {
        AtomicReference<Integer> startedAtomic = new AtomicReference<>();
        AtomicReference<UserSessionModel[]> origSessionsAtomic = new AtomicReference<>();

        inComittedTransaction(1, (session, i) -> {
            int started = Time.currentTime();
            startedAtomic.set(started);

            UserSessionModel[] origSessions = createSessionsInPersisterOnly();
            origSessionsAtomic.set(origSessions);

            // Load sessions from persister into infinispan/memory
            UserSessionProviderFactory userSessionFactory = (UserSessionProviderFactory) session.getKeycloakSessionFactory().getProviderFactory(UserSessionProvider.class);
            userSessionFactory.loadPersistentSessions(session.getKeycloakSessionFactory(), 1, 2);
        });

        inComittedTransaction(1, (session, i) -> {
            RealmModel realm = session.realms().getRealm(realmId);

            int started = startedAtomic.get();

            UserSessionModel[] origSessions = origSessionsAtomic.get();

            // Assert sessions are in
            ClientModel testApp = realm.getClientByClientId("test-app");
            ClientModel thirdparty = realm.getClientByClientId("third-party");

            assertThat("Count of offline sesions for client 'test-app'", session.sessions().getOfflineSessionsCount(realm, testApp), is((long) 3));
            assertThat("Count of offline sesions for client 'third-party'", session.sessions().getOfflineSessionsCount(realm, thirdparty), is((long) 1));

            List<UserSessionModel> loadedSessions = session.sessions().getOfflineUserSessionsStream(realm, testApp, 0, 10)
                    .collect(Collectors.toList());
            UserSessionPersisterProviderTest.assertSessions(loadedSessions, origSessions);

            assertSessionLoaded(loadedSessions, origSessions[0].getId(), session.users().getUserByUsername(realm, "user1"), "127.0.0.1", started, started, "test-app", "third-party");
            assertSessionLoaded(loadedSessions, origSessions[1].getId(), session.users().getUserByUsername(realm, "user1"), "127.0.0.2", started, started, "test-app");
            assertSessionLoaded(loadedSessions, origSessions[2].getId(), session.users().getUserByUsername(realm, "user2"), "127.0.0.3", started, started, "test-app");
        });
    }

    @Test
    public void testModelCriteriaBuilder() {
        UUID uuid = UUID.randomUUID();
        
        inComittedTransaction(1, (session, i) -> {
            RealmModel realm = session.realms().getRealm(realmId);
            MapUserSessionAdapter s = (MapUserSessionAdapter) session.sessions().createUserSession(realm, session.users().getUserByUsername(realm, "user1"), "user1", "127.0.0.1", "form", true, null, null);

            s.setCorrespondingSessionId(uuid);
        });

        inComittedTransaction(1, (session, i) -> {
            RealmModel realm = session.realms().getRealm(realmId);
            UserSessionModel s = session.sessions().getUserSession(realm, uuid.toString());
            assertNotNull(s);
        });
    }

    @Test
    public void testUserSessionInitializerWithDeletingClient() {
        AtomicReference<Integer> startedAtomic = new AtomicReference<>();
        AtomicReference<UserSessionModel[]> origSessionsAtomic = new AtomicReference<>();

        inComittedTransaction(1, (session, i) -> {
            RealmModel realm = session.realms().getRealm(realmId);

            int started = Time.currentTime();
            startedAtomic.set(started);

            origSessionsAtomic.set(createSessionsInPersisterOnly());

            // Delete one of the clients now. Delete it directly in DB just for the purpose of simulating the issue (normally clients should be removed through ClientManager)
            ClientModel testApp = realm.getClientByClientId("test-app");
            realm.removeClient(testApp.getId());
        });

        inComittedTransaction(1, (session, i) -> {
            // Load sessions from persister into infinispan/memory
            UserSessionProviderFactory userSessionFactory = (UserSessionProviderFactory) session.getKeycloakSessionFactory().getProviderFactory(UserSessionProvider.class);
            userSessionFactory.loadPersistentSessions(session.getKeycloakSessionFactory(), 1, 2);
        });

        inComittedTransaction(1, (session, i) -> {
            RealmModel realm = session.realms().getRealm(realmId);

            int started = startedAtomic.get();

            UserSessionModel[] origSessions = origSessionsAtomic.get();

            // Assert sessions are in
            ClientModel thirdparty = realm.getClientByClientId("third-party");

            assertThat("Count of offline sesions for client 'third-party'", session.sessions().getOfflineSessionsCount(realm, thirdparty), is((long) 1));
            List<UserSessionModel> loadedSessions = session.sessions().getOfflineUserSessionsStream(realm, thirdparty, 0, 10)
                    .collect(Collectors.toList());

            assertThat("Size of loaded Sessions", loadedSessions.size(), is(1));
            assertSessionLoaded(loadedSessions, origSessions[0].getId(), session.users().getUserByUsername(realm, "user1"), "127.0.0.1", started, started, "third-party");

            // Revert client
            realm.addClient("test-app");
        });

    }

    // Create sessions in persister + infinispan, but then delete them from infinispan cache. This is to allow later testing of initializer. Return the list of "origSessions"
    private UserSessionModel[] createSessionsInPersisterOnly() {
        AtomicReference<UserSessionModel[]> origSessionsAtomic = new AtomicReference<>();

        inComittedTransaction(1, (session, i) -> {
            UserSessionModel[] origSessions = UserSessionPersisterProviderTest.createSessions(session, realmId);
            origSessionsAtomic.set(origSessions);
        });

        inComittedTransaction(1, (session, i) -> {
            RealmModel realm = session.realms().getRealm(realmId);
            UserSessionManager sessionManager = new UserSessionManager(session);

            UserSessionModel[] origSessions = origSessionsAtomic.get();

            for (UserSessionModel origSession : origSessions) {
                UserSessionModel userSession = session.sessions().getUserSession(realm, origSession.getId());
                for (AuthenticatedClientSessionModel clientSession : userSession.getAuthenticatedClientSessions().values()) {
                    sessionManager.createOrUpdateOfflineSession(clientSession, userSession);
                }
            }
        });

        inComittedTransaction(1, (session, i) -> {
            RealmModel realm = session.realms().getRealm(realmId);

            // Delete local user cache (persisted sessions are still kept)
            UserSessionProvider provider = session.getProvider(UserSessionProvider.class);
            if (provider instanceof InfinispanUserSessionProvider) {
                ((InfinispanUserSessionProvider) provider).removeLocalUserSessions(realm.getId(), true);
            } else if (provider instanceof MapUserSessionProvider) {
                ((MapUserSessionProvider) provider).removeLocalOfflineUserSessions(realm);
            }

            // Clear ispn cache to ensure initializerState is removed as well
            InfinispanConnectionProvider infinispan = session.getProvider(InfinispanConnectionProvider.class);
            infinispan.getCache(InfinispanConnectionProvider.WORK_CACHE_NAME).clear();
        });

        inComittedTransaction(1, (session, i) -> {
            RealmModel realm = session.realms().getRealm(realmId);

            ClientModel testApp = realm.getClientByClientId("test-app");
            ClientModel thirdparty = realm.getClientByClientId("third-party");
            assertThat("Count of offline sessions for client 'test-app'", session.sessions().getOfflineSessionsCount(realm, testApp), is((long) 0));
            assertThat("Count of offline sessions for client 'third-party'", session.sessions().getOfflineSessionsCount(realm, thirdparty), is((long) 0));
        });

        return origSessionsAtomic.get();
    }

    private void assertSessionLoaded(List<UserSessionModel> sessions, String id, UserModel user, String ipAddress, int started, int lastRefresh, String... clients) {
        for (UserSessionModel session : sessions) {
            if (session.getId().equals(id)) {
                UserSessionPersisterProviderTest.assertSession(session, user, ipAddress, started, lastRefresh, clients);
                return;
            }
        }
        Assert.fail("Session with ID " + id + " not found in the list");
    }
}

