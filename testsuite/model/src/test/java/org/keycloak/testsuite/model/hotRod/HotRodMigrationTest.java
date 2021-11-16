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

package org.keycloak.testsuite.model.hotRod;

import org.infinispan.client.hotrod.RemoteCache;
import org.junit.Assume;
import org.junit.Test;
import org.keycloak.models.ClientModel;
import org.keycloak.models.ClientProvider;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.RealmProvider;
import org.keycloak.models.map.client.HotRodClientEntity;
import org.keycloak.models.map.client.MapClientProviderFactory;
import org.keycloak.models.map.connections.HotRodConnectionProvider;
import org.keycloak.models.map.storage.MapStorageProvider;
import org.keycloak.models.map.storage.hotRod.HotRodMapStorageProviderFactory;
import org.keycloak.testsuite.model.KeycloakModelTest;
import org.keycloak.testsuite.model.RequireProvider;

import java.util.UUID;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;

@RequireProvider(value = ClientProvider.class, only = {MapClientProviderFactory.PROVIDER_ID})
@RequireProvider(RealmProvider.class)
@RequireProvider(value = MapStorageProvider.class, only = {HotRodMapStorageProviderFactory.PROVIDER_ID})
@RequireProvider(value = HotRodConnectionProvider.class)
public class HotRodMigrationTest extends KeycloakModelTest {

    private RemoteCache<String, HotRodClientEntity> cache;
    private HotRodClientEntity client;
    private String realmId;

    @Override
    public void createEnvironment(KeycloakSession session) {
        HotRodConnectionProvider connectionProvider = session.getProvider(HotRodConnectionProvider.class);

        RealmModel myRealm = session.realms().createRealm("myRealm");
        realmId = myRealm.getId();

        cache = connectionProvider.getRemoteCache("clients");
        client = new HotRodClientEntity();
        client.setId(UUID.randomUUID().toString());
        client.setRealmId(realmId);
        client.oldClientId = "myClientId";
        client.entityVersion = 0;
        cache.put(client.getId(), client);
    }

    @Override
    protected void cleanEnvironment(KeycloakSession s) {
        s.realms().removeRealm(realmId);
    }

    @Test
    public void testReading() {
        withRealm(realmId, ((keycloakSession, realmModel) -> {
            ClientModel clientById = keycloakSession.clients().getClientById(realmModel, client.id);
            assertThat(clientById.getClientId(), is(equalTo("myClientId")));

            return null;
        }));

        // Test the entity remained the same
        HotRodClientEntity hotRodClientEntity = cache.get(client.id);
        assertThat(hotRodClientEntity.entityVersion, is(0));
        assertThat(hotRodClientEntity.clientId, is(nullValue()));
    }

    @Test
    public void testWritingToExistingEntity() {
        withRealm(realmId, ((keycloakSession, realmModel) -> {
            ClientModel clientById = keycloakSession.clients().getClientById(realmModel, client.id);
            clientById.setBaseUrl("http://my-test-url.com");
            return null;
        }));

        // Test entity was updated to new version, but remained backward compatible
        HotRodClientEntity hotRodClientEntity = cache.get(client.id);
        assertThat(hotRodClientEntity.entityVersion, is(1));
        assertThat(hotRodClientEntity.clientId, is(equalTo("myClientId")));
        assertThat(hotRodClientEntity.oldClientId, is(equalTo("myClientId")));
    }

    @Test
    public void testCreatingNewEntity() {
        String newClient1 = withRealm(realmId, ((keycloakSession, realmModel) -> {
            ClientModel newClient = keycloakSession.clients().addClient(realmModel, "newClient");
            return newClient.getId();
        }));

        // Test new entity created with new version
        HotRodClientEntity hotRodClientEntity = cache.get(newClient1);
        assertThat(hotRodClientEntity.entityVersion, is(1));
        assertThat(hotRodClientEntity.clientId, is(equalTo("newClient")));
        // Test backward compatible
        assertThat(hotRodClientEntity.oldClientId, is(equalTo("newClient")));

        // Test old entity remained the same
        hotRodClientEntity = cache.get(client.getId());
        assertThat(hotRodClientEntity.entityVersion, is(0));
        assertThat(hotRodClientEntity.clientId, nullValue());
        assertThat(hotRodClientEntity.oldClientId, is(equalTo("myClientId")));
    }

    @Test
    public void testQueryMigration() {
        withRealm(realmId, ((keycloakSession, realmModel) -> {
            ClientModel model = keycloakSession.clients().getClientByClientId(realmModel, "myClientId");

            // Test old entity was found based on the old clientId
            assertThat(model, notNullValue());
            assertThat(model.getClientId(), is(equalTo("myClientId")));
            return null;
        }));

        // Test entity remained the same
        HotRodClientEntity hotRodClientEntity = cache.get(client.getId());
        assertThat(hotRodClientEntity.entityVersion, is(0));
        assertThat(hotRodClientEntity.clientId, nullValue());
        assertThat(hotRodClientEntity.oldClientId, is(equalTo("myClientId")));
    }

}