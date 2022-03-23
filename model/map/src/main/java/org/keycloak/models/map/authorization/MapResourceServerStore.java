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

import org.jboss.logging.Logger;
import org.keycloak.authorization.AuthorizationProvider;
import org.keycloak.authorization.model.ResourceServer;
import org.keycloak.authorization.model.ResourceServer.SearchableFields;
import org.keycloak.authorization.store.ResourceServerStore;
import org.keycloak.models.ClientModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.ModelDuplicateException;
import org.keycloak.models.ModelException;
import org.keycloak.models.RealmModel;
import org.keycloak.models.map.authorization.adapter.MapResourceServerAdapter;
import org.keycloak.models.map.authorization.entity.MapResourceServerEntity;
import org.keycloak.models.map.authorization.entity.MapResourceServerEntityImpl;
import org.keycloak.models.map.storage.MapKeycloakTransaction;
import org.keycloak.models.map.storage.MapStorage;
import org.keycloak.models.map.storage.ModelCriteriaBuilder.Operator;
import org.keycloak.models.map.storage.criteria.DefaultModelCriteria;
import org.keycloak.models.utils.KeycloakModelUtils;
import org.keycloak.storage.StorageId;

import static org.keycloak.common.util.StackUtil.getShortStackTrace;
import static org.keycloak.models.map.storage.QueryParameters.withCriteria;
import static org.keycloak.models.map.storage.criteria.DefaultModelCriteria.criteria;

public class MapResourceServerStore implements ResourceServerStore {

    private static final Logger LOG = Logger.getLogger(MapResourceServerStore.class);
    private final AuthorizationProvider authorizationProvider;
    final MapKeycloakTransaction<MapResourceServerEntity, ResourceServer> tx;

    public MapResourceServerStore(KeycloakSession session, MapStorage<MapResourceServerEntity, ResourceServer> resourceServerStore, AuthorizationProvider provider) {
        this.tx = resourceServerStore.createTransaction(session);
        this.authorizationProvider = provider;
        session.getTransactionManager().enlist(tx);
    }

    private ResourceServer entityToAdapter(MapResourceServerEntity origEntity) {
        if (origEntity == null) return null;
        // Clone entity before returning back, to avoid giving away a reference to the live object to the caller
        return new MapResourceServerAdapter(origEntity, authorizationProvider.getStoreFactory());
    }

    @Override
    public ResourceServer create(ClientModel client) {
        LOG.tracef("create(%s)%s", client.getClientId(), getShortStackTrace());

        String clientId = client.getId();
        if (clientId == null) return null;

        if (!StorageId.isLocalStorage(clientId)) {
            throw new ModelException("Creating resource server from federated ClientModel not supported");
        }

        if (findByClient(client) != null) {
            throw new ModelDuplicateException("Resource server assiciated with client : " + client.getClientId() + " already exists.");
        }

        MapResourceServerEntity entity = new MapResourceServerEntityImpl();
        entity.setClientId(clientId);
        entity.setRealmId(client.getRealm().getId());

        entity = tx.create(entity);
        return entityToAdapter(entity);
    }

    @Override
    public void delete(ClientModel client) {
        LOG.tracef("delete(%s)%s", client.getClientId(), getShortStackTrace());

        ResourceServer resourceServer = findByClient(client);
        if (resourceServer == null) return;

        // TODO: Sending an event should be extracted to store layer
        authorizationProvider.getKeycloakSession().getKeycloakSessionFactory().publish(new ResourceServer.ResourceServerPreRemoveEvent() {
            @Override
            public ResourceServer getResourceServer() {
                return resourceServer;
            }

            @Override
            public KeycloakSession getKeycloakSession() {
                return authorizationProvider.getKeycloakSession();
            }
        });
        // TODO: ^^^^^^^ Up to here

        tx.delete(resourceServer.getId());
    }

    @Override
    public ResourceServer findById(String id) {
        LOG.tracef("findById(%s)%s", id, getShortStackTrace());

        if (id == null) {
            return null;
        }

        MapResourceServerEntity entity = tx.read(id);
        return entity == null ? null : entityToAdapter(entity);
    }

    @Override
    public ResourceServer findByClient(ClientModel client) {
        LOG.tracef("findByClient(%s) in realm(%s)%s", client.getClientId(), client.getRealm().getName(), getShortStackTrace());

        DefaultModelCriteria<ResourceServer> mcb = criteria();
        mcb = mcb.compare(SearchableFields.CLIENT_ID, Operator.EQ, client.getId());

        return tx.read(withCriteria(mcb)).map(this::entityToAdapter).findFirst().orElse(null);
    }

    public void preRemove(RealmModel realm) {
        LOG.tracef("preRemove(%s)%s", realm, getShortStackTrace());

        DefaultModelCriteria<ResourceServer> mcb = criteria();
        mcb = mcb.compare(SearchableFields.REALM_ID, Operator.EQ, realm.getId());

        tx.delete(withCriteria(mcb));
    }
}
