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

package org.keycloak.models.map.user;

import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;
import org.keycloak.models.UserProvider;
import org.keycloak.models.UserProviderFactory;
import org.keycloak.models.map.common.AbstractMapProviderFactory;
import org.keycloak.provider.ProviderEvent;
import org.keycloak.provider.ProviderEventListener;

/**
 *
 * @author mhajas
 */
public class MapUserProviderFactory extends AbstractMapProviderFactory<UserProvider, MapUserEntity, UserModel> implements UserProviderFactory, ProviderEventListener {

    private Runnable onClose;

    public MapUserProviderFactory() {
        super(UserModel.class);
    }

    @Override
    public void postInit(KeycloakSessionFactory factory) {
        factory.register(this);
        onClose = () -> factory.unregister(this);
    }

    @Override
    public UserProvider create(KeycloakSession session) {
        return new MapUserProvider(session, getStorage(session));
    }

    @Override
    public void close() {
        super.close();
        onClose.run();
    }

    @Override
    public String getHelpText() {
        return "User provider";
    }

    @Override
    public void onEvent(ProviderEvent event) {
        if (event instanceof RealmModel.RealmPreRemoveEvent) {
            RealmModel.RealmPreRemoveEvent e = ((RealmModel.RealmPreRemoveEvent) event);
            e.getKeycloakSession().users().preRemove(e.getRealm());
        }
    }
}
