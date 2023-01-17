/*
 * Copyright 2023 Red Hat, Inc. and/or its affiliates
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

package org.keycloak.testsuite.model.hotrod;

import org.junit.Test;
import org.keycloak.events.Event;
import org.keycloak.events.EventStoreProvider;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.testsuite.model.KeycloakModelTest;

import java.util.stream.Collectors;

public class HotRodTimeOffsetTest extends KeycloakModelTest {

    private String realmId;
    @Override
    protected void createEnvironment(KeycloakSession s) {
        RealmModel r = s.realms().createRealm("myRealm");
        r.setEventsExpiration(5);
        realmId = r.getId();
    }

    @Test
    public void testOffset() {
        withRealm(realmId, (session, realmModel) -> {
            EventStoreProvider provider = session.getProvider(EventStoreProvider.class);

            Event e = new Event();
            e.setRealmId(realmId);
            provider.onEvent(e);
            return null;
        });

        withRealm(realmId, (session, realmModel) -> {
            EventStoreProvider provider = session.getProvider(EventStoreProvider.class);
            System.out.println(provider.createQuery().realm(realmId).getResultStream().collect(Collectors.toList()));
            return null;
        });

        advanceTime(5);

        withRealm(realmId, (session, realmModel) -> {
            EventStoreProvider provider = session.getProvider(EventStoreProvider.class);
            System.out.println(provider.createQuery().realm(realmId).getResultStream().collect(Collectors.toList()));
            return null;
        });
    }
}
