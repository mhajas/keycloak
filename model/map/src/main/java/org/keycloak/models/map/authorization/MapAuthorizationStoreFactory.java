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

package org.keycloak.models.map.authorization;

import org.keycloak.Config;
import org.keycloak.authorization.AuthorizationProvider;
import org.keycloak.authorization.model.PermissionTicket;
import org.keycloak.authorization.model.Policy;
import org.keycloak.authorization.model.Resource;
import org.keycloak.authorization.model.ResourceServer;
import org.keycloak.authorization.model.Scope;
import org.keycloak.authorization.store.AuthorizationStoreFactory;
import org.keycloak.authorization.store.StoreFactory;
import org.keycloak.common.Profile;
import org.keycloak.component.AmphibianProviderFactory;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.models.RealmModel;
import org.keycloak.models.map.common.AbstractMapProviderFactory;
import org.keycloak.models.map.storage.MapStorage;
import org.keycloak.models.map.storage.MapStorageProvider;
import org.keycloak.models.map.storage.MapStorageProviderFactory;
import org.keycloak.models.map.storage.MapStorageSpi;
import org.keycloak.provider.EnvironmentDependentProviderFactory;
import static org.keycloak.models.utils.KeycloakModelUtils.getComponentFactory;
import org.keycloak.provider.ProviderEvent;
import org.keycloak.provider.ProviderEventListener;

/**
 * @author mhajas
 */
public class MapAuthorizationStoreFactory implements AmphibianProviderFactory<StoreFactory>, AuthorizationStoreFactory, EnvironmentDependentProviderFactory, ProviderEventListener {

    public static final String PROVIDER_ID = AbstractMapProviderFactory.PROVIDER_ID;

    private Config.Scope storageConfigScope;
    private Runnable onClose;

    @Override
    public StoreFactory create(KeycloakSession session) {
        MapStorageProviderFactory storageProviderFactory = (MapStorageProviderFactory) getComponentFactory(session.getKeycloakSessionFactory(),
          MapStorageProvider.class, storageConfigScope, MapStorageSpi.NAME);
        final MapStorageProvider mapStorageProvider = storageProviderFactory.create(session);
        AuthorizationProvider provider = session.getProvider(AuthorizationProvider.class);


        MapStorage permissionTicketStore;
        MapStorage policyStore;
        MapStorage resourceServerStore;
        MapStorage resourceStore;
        MapStorage scopeStore;

        permissionTicketStore = mapStorageProvider.getStorage(PermissionTicket.class);
        policyStore = mapStorageProvider.getStorage(Policy.class);
        resourceServerStore = mapStorageProvider.getStorage(ResourceServer.class);
        resourceStore = mapStorageProvider.getStorage(Resource.class);
        scopeStore = mapStorageProvider.getStorage(Scope.class);
        
        return new MapAuthorizationStore(session,
                    permissionTicketStore,
                    policyStore,
                    resourceServerStore,
                    resourceStore,
                    scopeStore,
                    provider
                );
    }

    @Override
    public void init(org.keycloak.Config.Scope config) {
        this.storageConfigScope = config.scope("storage");
    }

    @Override
    public void postInit(KeycloakSessionFactory factory) {
        registerSynchronizationListeners(factory);
        factory.register(this);
        onClose = () -> factory.unregister(this);
    }

    @Override
    public void close() {
        onClose.run();
    }

    @Override
    public String getId() {
        return PROVIDER_ID;
    }

    @Override
    public String getHelpText() {
        return "Authorization store provider";
    }

    @Override
    public boolean isSupported() {
        return Profile.isFeatureEnabled(Profile.Feature.MAP_STORAGE);
    }

    @Override
    public void onEvent(ProviderEvent event) {
        if (event instanceof RealmModel.RealmPreRemoveEvent) {
            RealmModel.RealmPreRemoveEvent e = ((RealmModel.RealmPreRemoveEvent) event);
            MapAuthorizationStore authorizationStore = (MapAuthorizationStore) e.getKeycloakSession().getProvider(StoreFactory.class);

            ((MapScopeStore) authorizationStore.getScopeStore()).preRemove(e.getRealm());
            ((MapPolicyStore) authorizationStore.getPolicyStore()).preRemove(e.getRealm());
            ((MapResourceStore) authorizationStore.getResourceStore()).preRemove(e.getRealm());
            ((MapPermissionTicketStore) authorizationStore.getPermissionTicketStore()).preRemove(e.getRealm());
            ((MapResourceServerStore) authorizationStore.getResourceServerStore()).preRemove(e.getRealm());

        } else if (event instanceof ResourceServer.ResourceServerPreRemoveEvent) {
            ResourceServer.ResourceServerPreRemoveEvent e = ((ResourceServer.ResourceServerPreRemoveEvent) event);
            MapAuthorizationStore authorizationStore = (MapAuthorizationStore) e.getKeycloakSession().getProvider(StoreFactory.class);

            ((MapScopeStore) authorizationStore.getScopeStore()).preRemove(e.getResourceServer());
            ((MapPolicyStore) authorizationStore.getPolicyStore()).preRemove(e.getResourceServer());
            ((MapResourceStore) authorizationStore.getResourceStore()).preRemove(e.getResourceServer());
            ((MapPermissionTicketStore) authorizationStore.getPermissionTicketStore()).preRemove(e.getResourceServer());
        }
    }
}
