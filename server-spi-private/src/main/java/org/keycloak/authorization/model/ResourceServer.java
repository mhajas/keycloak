/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2016 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.keycloak.authorization.model;

import org.keycloak.models.ClientModel;
import org.keycloak.models.ClientScopeModel;
import org.keycloak.models.ProtocolMapperModel;
import org.keycloak.models.RealmModel;
import org.keycloak.models.RoleModel;
import org.keycloak.representations.idm.authorization.DecisionStrategy;
import org.keycloak.representations.idm.authorization.PolicyEnforcementMode;
import org.keycloak.storage.SearchableModelField;

import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Represents a resource server, whose resources are managed and protected. A resource server is basically an existing
 * client application in Keycloak that will also act as a resource server.
 *
 * @author <a href="mailto:psilva@redhat.com">Pedro Igor</a>
 */
public interface ResourceServer {

    public static class SearchableFields {
        public static final SearchableModelField<ResourceServer> ID =           new SearchableModelField<>("id", String.class);
        /** ID of the client (not the clientId) associated with resource server*/
        public static final SearchableModelField<ResourceServer> CLIENT_ID =    new SearchableModelField<>("clientId", String.class);
        public static final SearchableModelField<ResourceServer> REALM_ID =     new SearchableModelField<>("realmId", String.class);
    }

    /**
     * Returns the unique identifier for this instance.
     *
     * @return the unique identifier for this instance
     */
    String getId();

    /**
     * Indicates if the resource server is allowed to manage its own resources remotely using the Protection API.
     *
     * {@code true} if the resource server is allowed to managed them remotely
     */
    boolean isAllowRemoteResourceManagement();

    /**
     * Indicates if the resource server is allowed to manage its own resources remotely using the Protection API.
     *
     * @param allowRemoteResourceManagement {@code true} if the resource server is allowed to managed them remotely
     */
    void setAllowRemoteResourceManagement(boolean allowRemoteResourceManagement);

    /**
     * Returns the {@code PolicyEnforcementMode} configured for this instance.
     *
     * @return the {@code PolicyEnforcementMode} configured for this instance.
     */
    PolicyEnforcementMode getPolicyEnforcementMode();

    /**
     * Defines a {@code PolicyEnforcementMode} for this instance.
     *
     * @param enforcementMode one of the available options in {@code PolicyEnforcementMode}
     */
    void setPolicyEnforcementMode(PolicyEnforcementMode enforcementMode);

    /**
     * Defines a {@link DecisionStrategy} for this instance, indicating how permissions should be granted depending on the given
     * {@code decisionStrategy}.
     * 
     * @param decisionStrategy the decision strategy
     */
    void setDecisionStrategy(DecisionStrategy decisionStrategy);

    /**
     * Returns the {@link DecisionStrategy} configured for this instance.
     * 
     * @return the decision strategy
     */
    DecisionStrategy getDecisionStrategy();

    /**
     * TODO javadoc
     */
    default String getClientId() {
        return getClient().getId();
    }

    /**
     * TODO: Adapters are currently not able to get clientModel because Realm is not know with findById method call, this should be solved before merging
     * @return
     */
    default ClientModel getClient() {
        final String id = getId();

        return new ClientModel() {
            @Override
            public void updateClient() {

            }

            @Override
            public String getId() {
                return id;
            }

            @Override
            public String getClientId() {
                return null;
            }

            @Override
            public void setClientId(String clientId) {

            }

            @Override
            public String getName() {
                return null;
            }

            @Override
            public void setName(String name) {

            }

            @Override
            public String getDescription() {
                return null;
            }

            @Override
            public void setDescription(String description) {

            }

            @Override
            public boolean isEnabled() {
                return false;
            }

            @Override
            public void setEnabled(boolean enabled) {

            }

            @Override
            public boolean isAlwaysDisplayInConsole() {
                return false;
            }

            @Override
            public void setAlwaysDisplayInConsole(boolean alwaysDisplayInConsole) {

            }

            @Override
            public boolean isSurrogateAuthRequired() {
                return false;
            }

            @Override
            public void setSurrogateAuthRequired(boolean surrogateAuthRequired) {

            }

            @Override
            public Set<String> getWebOrigins() {
                return null;
            }

            @Override
            public void setWebOrigins(Set<String> webOrigins) {

            }

            @Override
            public void addWebOrigin(String webOrigin) {

            }

            @Override
            public void removeWebOrigin(String webOrigin) {

            }

            @Override
            public Set<String> getRedirectUris() {
                return null;
            }

            @Override
            public void setRedirectUris(Set<String> redirectUris) {

            }

            @Override
            public void addRedirectUri(String redirectUri) {

            }

            @Override
            public void removeRedirectUri(String redirectUri) {

            }

            @Override
            public String getManagementUrl() {
                return null;
            }

            @Override
            public void setManagementUrl(String url) {

            }

            @Override
            public String getRootUrl() {
                return null;
            }

            @Override
            public void setRootUrl(String url) {

            }

            @Override
            public String getBaseUrl() {
                return null;
            }

            @Override
            public void setBaseUrl(String url) {

            }

            @Override
            public boolean isBearerOnly() {
                return false;
            }

            @Override
            public void setBearerOnly(boolean only) {

            }

            @Override
            public int getNodeReRegistrationTimeout() {
                return 0;
            }

            @Override
            public void setNodeReRegistrationTimeout(int timeout) {

            }

            @Override
            public String getClientAuthenticatorType() {
                return null;
            }

            @Override
            public void setClientAuthenticatorType(String clientAuthenticatorType) {

            }

            @Override
            public boolean validateSecret(String secret) {
                return false;
            }

            @Override
            public String getSecret() {
                return null;
            }

            @Override
            public void setSecret(String secret) {

            }

            @Override
            public String getRegistrationToken() {
                return null;
            }

            @Override
            public void setRegistrationToken(String registrationToken) {

            }

            @Override
            public String getProtocol() {
                return null;
            }

            @Override
            public void setProtocol(String protocol) {

            }

            @Override
            public void setAttribute(String name, String value) {

            }

            @Override
            public void removeAttribute(String name) {

            }

            @Override
            public String getAttribute(String name) {
                return null;
            }

            @Override
            public Map<String, String> getAttributes() {
                return null;
            }

            @Override
            public String getAuthenticationFlowBindingOverride(String binding) {
                return null;
            }

            @Override
            public Map<String, String> getAuthenticationFlowBindingOverrides() {
                return null;
            }

            @Override
            public void removeAuthenticationFlowBindingOverride(String binding) {

            }

            @Override
            public void setAuthenticationFlowBindingOverride(String binding, String flowId) {

            }

            @Override
            public boolean isFrontchannelLogout() {
                return false;
            }

            @Override
            public void setFrontchannelLogout(boolean flag) {

            }

            @Override
            public boolean isFullScopeAllowed() {
                return false;
            }

            @Override
            public void setFullScopeAllowed(boolean value) {

            }

            @Override
            public boolean isPublicClient() {
                return false;
            }

            @Override
            public void setPublicClient(boolean flag) {

            }

            @Override
            public boolean isConsentRequired() {
                return false;
            }

            @Override
            public void setConsentRequired(boolean consentRequired) {

            }

            @Override
            public boolean isStandardFlowEnabled() {
                return false;
            }

            @Override
            public void setStandardFlowEnabled(boolean standardFlowEnabled) {

            }

            @Override
            public boolean isImplicitFlowEnabled() {
                return false;
            }

            @Override
            public void setImplicitFlowEnabled(boolean implicitFlowEnabled) {

            }

            @Override
            public boolean isDirectAccessGrantsEnabled() {
                return false;
            }

            @Override
            public void setDirectAccessGrantsEnabled(boolean directAccessGrantsEnabled) {

            }

            @Override
            public boolean isServiceAccountsEnabled() {
                return false;
            }

            @Override
            public void setServiceAccountsEnabled(boolean serviceAccountsEnabled) {

            }

            @Override
            public RealmModel getRealm() {
                return null;
            }

            @Override
            public void addClientScope(ClientScopeModel clientScope, boolean defaultScope) {

            }

            @Override
            public void addClientScopes(Set<ClientScopeModel> clientScopes, boolean defaultScope) {

            }

            @Override
            public void removeClientScope(ClientScopeModel clientScope) {

            }

            @Override
            public Map<String, ClientScopeModel> getClientScopes(boolean defaultScope) {
                return null;
            }

            @Override
            public int getNotBefore() {
                return 0;
            }

            @Override
            public void setNotBefore(int notBefore) {

            }

            @Override
            public Map<String, Integer> getRegisteredNodes() {
                return null;
            }

            @Override
            public void registerNode(String nodeHost, int registrationTime) {

            }

            @Override
            public void unregisterNode(String nodeHost) {

            }

            @Override
            public Stream<ProtocolMapperModel> getProtocolMappersStream() {
                return null;
            }

            @Override
            public ProtocolMapperModel addProtocolMapper(ProtocolMapperModel model) {
                return null;
            }

            @Override
            public void removeProtocolMapper(ProtocolMapperModel mapping) {

            }

            @Override
            public void updateProtocolMapper(ProtocolMapperModel mapping) {

            }

            @Override
            public ProtocolMapperModel getProtocolMapperById(String id) {
                return null;
            }

            @Override
            public ProtocolMapperModel getProtocolMapperByName(String protocol, String name) {
                return null;
            }

            @Override
            public RoleModel getRole(String name) {
                return null;
            }

            @Override
            public RoleModel addRole(String name) {
                return null;
            }

            @Override
            public RoleModel addRole(String id, String name) {
                return null;
            }

            @Override
            public boolean removeRole(RoleModel role) {
                return false;
            }

            @Override
            public Stream<RoleModel> getRolesStream() {
                return null;
            }

            @Override
            public Stream<RoleModel> getRolesStream(Integer firstResult, Integer maxResults) {
                return null;
            }

            @Override
            public Stream<RoleModel> searchForRolesStream(String search, Integer first, Integer max) {
                return null;
            }

            @Override
            public Stream<String> getDefaultRolesStream() {
                return null;
            }

            @Override
            public void addDefaultRole(String name) {

            }

            @Override
            public void removeDefaultRoles(String... defaultRoles) {

            }

            @Override
            public Stream<RoleModel> getScopeMappingsStream() {
                return null;
            }

            @Override
            public Stream<RoleModel> getRealmScopeMappingsStream() {
                return null;
            }

            @Override
            public void addScopeMapping(RoleModel role) {

            }

            @Override
            public void deleteScopeMapping(RoleModel role) {

            }

            @Override
            public boolean hasScope(RoleModel role) {
                return false;
            }
        };
    }
}
