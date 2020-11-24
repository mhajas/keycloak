/*
 * Copyright 2020 Red Hat, Inc. and/or its affiliates
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
package org.keycloak.model;

import org.keycloak.component.ComponentModel;
import org.keycloak.models.GroupModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.RealmProvider;
import org.keycloak.models.UserModel;
import org.keycloak.models.UserProvider;
import org.keycloak.storage.UserStorageProvider;
import org.keycloak.storage.UserStorageProviderFactory;
import org.keycloak.storage.UserStorageProviderModel;
import org.keycloak.storage.user.UserLookupProvider;
import org.keycloak.storage.user.UserRegistrationProvider;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.hamcrest.Matchers;
import org.junit.Test;
import org.keycloak.testsuite.federation.UserMapStorage;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeThat;

/**
 *
 * @author hmlnarik
 */
@RequireProvider(UserProvider.class)
@RequireProvider(RealmProvider.class)
public class UserModelTest extends KeycloakModelTest {

    protected static final int NUM_GROUPS = 100;

    private String realmId;
    private final List<String> groupIds = new ArrayList<>(NUM_GROUPS);
    private String userFederationId;

    @Override
    public void createEnvironment(KeycloakSession s) {
        RealmModel realm = s.realms().createRealm("realm");
        this.realmId = realm.getId();

        this.userFederationId = registerUserFederationIfAvailable(realm);

        IntStream.range(0, NUM_GROUPS).forEach(i -> {
            groupIds.add(s.groups().createGroup(realm, "group-" + i).getId());
        });
    }

    @Override
    public void cleanEnvironment(KeycloakSession s) {
        s.realms().removeRealm(realmId);
    }

    private void addRemoveUser(KeycloakSession session, int i) {
        RealmModel realm = session.realms().getRealmByName("realm");

        UserModel user = session.users().addUser(realm, "user-" + i);

        IntStream.range(0, NUM_GROUPS / 20).forEach(gIndex -> {
            user.joinGroup(session.groups().getGroupById(realm, groupIds.get((i + gIndex) % NUM_GROUPS)));
        });

        final UserModel obtainedUser = session.users().getUserById(user.getId(), realm);

        assertThat(obtainedUser, Matchers.notNullValue());
        assertThat(obtainedUser.getUsername(), is("user-" + i));
        Set<String> userGroupIds = obtainedUser.getGroupsStream().map(GroupModel::getName).collect(Collectors.toSet());
        assertThat(userGroupIds, hasSize(NUM_GROUPS / 20));
        assertThat(userGroupIds, hasItem("group-" + i));
        assertThat(userGroupIds, hasItem("group-" + (i - 1 + (NUM_GROUPS / 20)) % NUM_GROUPS));

        assertTrue(session.users().removeUser(realm, user));
        assertFalse(session.users().removeUser(realm, user));
    }

    @Test
    public void testAddRemoveUser() {
        inRolledBackTransaction(1, this::addRemoveUser);
    }

    @Test
    public void testAddRemoveUserConcurrent() {
        IntStream.range(0,100).parallel().forEach(i -> inComittedTransaction(i, this::addRemoveUser));
    }

    @Test
    public void testAddRemoveUsersInTheSameGroupConcurrent() {
        final ConcurrentSkipListSet<String> userIds = new ConcurrentSkipListSet<>();
        String groupId = groupIds.get(0);

        // Create users and let them join first group
        IntStream.range(0, 100).parallel().forEach(index -> inComittedTransaction(index, (session, i) -> {
            final RealmModel realm = session.realms().getRealm(realmId);
            final UserModel user = session.users().addUser(realm, "user-" + i);
            user.joinGroup(session.groups().getGroupById(realm, groupId));
            userIds.add(user.getId());
        }));

        inComittedTransaction(1, (session, i) -> {
            final RealmModel realm = session.realms().getRealm(realmId);
            final GroupModel group = session.groups().getGroupById(realm, groupId);
            assertThat(session.users().getGroupMembersStream(realm, group).count(), is(100L));
        });

        // Some of the transactions may fail due to conflicts as there are many parallel request, so repeat until all users are removed
        Set<String> remainingUserIds = new HashSet<>();
        do {
            userIds.stream().parallel().forEach(index -> inComittedTransaction(index, (session, userId) -> {
                final RealmModel realm = session.realms().getRealm(realmId);
                final UserModel user = session.users().getUserById(userId, realm);
                log.debugf("Remove user %s: %s", userId, session.users().removeUser(realm, user));
            }, null, (session, userId) -> remainingUserIds.add(userId) ));

            userIds.clear();
            userIds.addAll(remainingUserIds);
            remainingUserIds.clear();
        } while (! userIds.isEmpty());

        inComittedTransaction(1, (session, i) -> {
            final RealmModel realm = session.realms().getRealm(realmId);
            final GroupModel group = session.groups().getGroupById(realm, groupId);
            assertThat(session.users().getGroupMembersStream(realm, group).collect(Collectors.toList()), Matchers.empty());
        });
    }

    @Test
    public void testAddDirtyRemoveFederationUsersInTheSameGroupConcurrent() {
        assumeThat("Test for federated providers only", userFederationId, Matchers.notNullValue());

        final ConcurrentSkipListSet<String> userIds = new ConcurrentSkipListSet<>();
        String groupId = groupIds.get(0);

        // Create users and let them join first group
        IntStream.range(0, 100).parallel().forEach(index -> inComittedTransaction(index, (session, i) -> {
            final RealmModel realm = session.realms().getRealm(realmId);
            final UserModel user = session.users().addUser(realm, "user-" + i);
            user.joinGroup(session.groups().getGroupById(realm, groupId));
            userIds.add(user.getId());
        }));

        inComittedTransaction(1, (session, i) -> {
            final RealmModel realm = session.realms().getRealm(realmId);
            final GroupModel group = session.groups().getGroupById(realm, groupId);

            assertThat(session.users().getGroupMembersStream(realm, group).count(), is(100L));
        });

        // Remove users _from the federation_, simulates eg. user being removed from LDAP without Keycloak knowing
        inComittedTransaction(1, (session, i) -> {
            final RealmModel realm = session.realms().getRealm(realmId);
            UserStorageProvider instance = (UserStorageProvider)session.getAttribute(userFederationId);

            if (instance == null) {
                ComponentModel model = realm.getComponent(userFederationId);
                UserStorageProviderModel storageModel = new UserStorageProviderModel(model);
                UserStorageProviderFactory factory = (UserStorageProviderFactory)session.getKeycloakSessionFactory().getProviderFactory(UserStorageProvider.class, model.getProviderId());
                instance = factory.create(session, model);
                if (instance == null) {
                    throw new RuntimeException("UserStorageProvideFactory (of type " + factory.getClass().getName() + ") produced a null instance");
                }
                session.enlistForClose(instance);
                session.setAttribute(userFederationId, instance);
            }

            final UserMapStorage lambdaInstance = (UserMapStorage) instance;
            log.debugf("Removing selected users from backend");

            IntStream.range(FIRST_DELETED_USER_INDEX, LAST_DELETED_USER_INDEX).forEach(j -> {
                lambdaInstance.removeUserByName("user-" + j);
                //final UserModel user = ((UserLookupProvider) lambdaInstance).getUserByUsername("user-" + j, realm);
                //((UserRegistrationProvider) lambdaInstance).removeUser(realm, user);
            });
        });

        IntStream.range(0, 7).parallel().forEach(index -> inComittedTransaction(index, (session, i) -> {
            final RealmModel realm = session.realms().getRealm(realmId);
            final GroupModel group = session.groups().getGroupById(realm, groupId);
            assertThat(session.users().getGroupMembersStream(realm, group).count(), is(100L - DELETED_USER_COUNT));
        }));

//        // Now delete the users, and count those that were not found to be deleted. This should be equal to the number
//        // of users removed directly in the user federation.
//        // Some of the transactions may fail due to conflicts as there are many parallel request, so repeat until all users are removed
//        AtomicInteger notFoundUsers = new AtomicInteger();
//        Set<String> remainingUserIds = new HashSet<>();
//        do {
//            userIds.stream().parallel().forEach(index -> inComittedTransaction(index, (session, userId) -> {
//                final RealmModel realm = session.realms().getRealm(realmId);
//                final UserModel user = session.users().getUserById(userId, realm);
//                if (user != null) {
//                    log.debugf("Deleting user: %s", userId);
//                    session.users().removeUser(realm, user);
//                } else {
//                    log.debugf("Failed deleting user: %s", userId);
//                    notFoundUsers.incrementAndGet();
//                }
//            }, null, (session, userId) -> {
//                log.debugf("Could not delete user %s", userId);
//                remainingUserIds.add(userId);
//            }));
//
//            userIds.clear();
//            userIds.addAll(remainingUserIds);
//            remainingUserIds.clear();
//        } while (! userIds.isEmpty());
//
//        assertThat(notFoundUsers.get(), is(DELETED_USER_COUNT));
//
//        inComittedTransaction(1, (session, i) -> {
//            final RealmModel realm = session.realms().getRealm(realmId);
//            final GroupModel group = session.groups().getGroupById(realm, groupId);
//            assertThat(session.users().getGroupMembersStream(realm, group).collect(Collectors.toList()), Matchers.empty());
//        });
    }
    private static final int FIRST_DELETED_USER_INDEX = 10;
    private static final int LAST_DELETED_USER_INDEX = 90;
    private static final int DELETED_USER_COUNT = LAST_DELETED_USER_INDEX - FIRST_DELETED_USER_INDEX;

    @Test
    public void removeUserAttributeTest() {
        inComittedTransaction(1, (session, i) -> {
            RealmModel realm = session.realms().getRealm(realmId);
            UserModel john = session.users().addUser(realm, "john");
            john.setSingleAttribute("foo", "val1");

            UserModel john2 = session.users().addUser(realm, "john2");
            john2.setAttribute("foo", Arrays.asList("val1", "val2"));
        });

        final CountDownLatch readAttrLatch = new CountDownLatch(4);
        IntStream.range(0, 4).parallel().forEach(index -> inComittedTransaction(index, (session, i) -> {
            try {
                RealmModel realm = session.realms().getRealm(realmId);
                UserModel john = session.users().getUserByUsername("john", realm);
                String attrVal = john.getFirstAttribute("foo");

                UserModel john2 = session.users().getUserByUsername("john2", realm);
                String attrVal2 = john2.getFirstAttribute("foo");

                // Wait until it's read in all threads
                readAttrLatch.countDown();
                readAttrLatch.await();


                // KEYCLOAK-3296 : Remove user attribute in both threads
                john.removeAttribute("foo");

                // KEYCLOAK-3494 : Set single attribute in both threads
                john2.setSingleAttribute("foo", "bar");
            } catch (Exception e) {
                throw new RuntimeException(e);
            } finally {
                readAttrLatch.countDown();
            }
        }));
    }
}
