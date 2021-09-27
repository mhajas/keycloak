package org.keycloak.models.map.client;

import org.infinispan.protostream.annotations.ProtoDoc;
import org.infinispan.protostream.annotations.ProtoField;
import org.keycloak.models.ProtocolMapperModel;
import org.keycloak.models.map.common.Versioned;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class HotRodClientEntity implements MapClientEntity, Versioned {

    @ProtoField(number = 1, required = true)
    @ProtoDoc("@Field(index = Index.YES, store = Store.YES)")
    public int entityVersion = 1;

    @ProtoField(number = 2, required = true)
    @ProtoDoc("@Field(index = Index.YES, store = Store.YES)")
    public String id;

    @ProtoField(number = 3, required = true)
    @ProtoDoc("@Field(index = Index.YES, store = Store.YES)")
    public String realmId;



    @ProtoField(number = 4, required = true)
    @ProtoDoc("@Field(index = Index.YES, store = Store.YES)")
    public String clientId;

    @ProtoField(number = 5)
    public String name;

    @ProtoField(number = 6)
    public String description;

    @ProtoField(number = 7)
    public Set<String> redirectUris = new HashSet<>();

    @ProtoField(number = 8, defaultValue = "false")
    public boolean enabled;

    @ProtoField(number = 9, defaultValue = "false")
    public boolean alwaysDisplayInConsole;

    @ProtoField(number = 10)
    public String clientAuthenticatorType;

    @ProtoField(number = 11)
    public String secret;

    public String registrationToken;
    @ProtoField(number = 13)
    public String protocol;

    @ProtoField(number = 14)
    public Set<HotRodAttributeEntity> attributes = new HashSet<>();

    @ProtoField(number = 15)
    public Set<HotRodPair<String, String>> authFlowBindings = new HashSet<>();

    @ProtoField(number = 16, defaultValue = "false")
    public boolean publicClient;

    @ProtoField(number = 17, defaultValue = "false")
    public boolean fullScopeAllowed;

    @ProtoField(number = 18, defaultValue = "false")
    public boolean frontchannelLogout;

    @ProtoField(number = 19, defaultValue = "0")
    public int notBefore;

    @ProtoField(number = 20)
    public Set<String> scope = new HashSet<>();

    @ProtoField(number = 21)
    public Set<String> webOrigins = new HashSet<>();

    @ProtoField(number = 22)
    public Set<HotRodProtocolMapperEntity> protocolMappers = new HashSet<>();

    @ProtoField(number = 23)
    public Set<HotRodPair<String, Boolean>> clientScopes = new HashSet<>();

    @ProtoField(number = 24)
    public Set<String> scopeMappings = new HashSet<>();

    @ProtoField(number = 25, defaultValue = "false")
    public boolean surrogateAuthRequired;

    @ProtoField(number = 26)
    public String managementUrl;

    @ProtoField(number = 27)
    public String baseUrl;

    @ProtoField(number = 28, defaultValue = "false")
    public boolean bearerOnly;

    @ProtoField(number = 29, defaultValue = "false")
    public boolean consentRequired;

    @ProtoField(number = 30)
    public String rootUrl;

    @ProtoField(number = 31, defaultValue = "false")
    public boolean standardFlowEnabled;

    @ProtoField(number = 32, defaultValue = "false")
    public boolean implicitFlowEnabled;

    @ProtoField(number = 33, defaultValue = "false")
    public boolean directAccessGrantsEnabled;

    @ProtoField(number = 34, defaultValue = "false")
    public boolean serviceAccountsEnabled;

    @ProtoField(number = 35, defaultValue = "0")
    public int nodeReRegistrationTimeout;

    private boolean updated = false;

    public HotRodClientEntity() {}

    public HotRodClientEntity(String id) {
        this.id = id;
    }

    @Override
    public int getEntityVersion() {
        return entityVersion;
    }

    @Override
    public List<String> getAttribute(String name) {
        return attributes.stream()
                .filter(attributeEntity -> Objects.equals(attributeEntity.getName(), name))
                .findFirst()
                .map(HotRodAttributeEntity::getValues)
                .orElse(Collections.emptyList());
    }

    @Override
    public Map<String, List<String>> getAttributes() {
        return attributes.stream().collect(Collectors.toMap(HotRodAttributeEntity::getName, HotRodAttributeEntity::getValues));
    }

    @Override
    public void setAttribute(String name, List<String> values) {
        boolean valueUndefined = values == null || values.isEmpty();

        Optional<HotRodAttributeEntity> first = attributes.stream()
                .filter(attributeEntity -> Objects.equals(attributeEntity.getName(), name))
                .findFirst();

        if (first.isPresent()) {
            HotRodAttributeEntity attributeEntity = first.get();
            if (valueUndefined) {
                this.updated = true;
                removeAttribute(name);
            } else {
                this.updated |= !Objects.equals(attributeEntity.getValues(), values);
                attributeEntity.setValues(values);
            }

            return;
        }

        // do not create attributes if empty or null
        if (valueUndefined) {
            return;
        }

        HotRodAttributeEntity newAttributeEntity = new HotRodAttributeEntity(name, values);
        updated |= attributes.add(newAttributeEntity);
    }

    @Override
    public void removeAttribute(String name) {
        attributes.stream()
                .filter(attributeEntity -> Objects.equals(attributeEntity.getName(), name))
                .findFirst()
                .ifPresent(attr -> {
                    this.updated |= attributes.remove(attr);
                });
    }

    @Override
    public String getClientId() {
        return clientId;
    }

    @Override
    public void setClientId(String clientId) {
        this.updated |= ! Objects.equals(this.clientId, clientId);
        this.clientId = clientId;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public void setName(String name) {
        this.updated |= ! Objects.equals(this.name, name);
        this.name = name;
    }

    @Override
    public String getDescription() {
        return description;
    }

    @Override
    public void setDescription(String description) {
        this.updated |= ! Objects.equals(this.description, description);
        this.description = description;
    }

    @Override
    public Set<String> getRedirectUris() {
        return redirectUris;
    }

    @Override
    public void setRedirectUris(Set<String> redirectUris) {
        this.updated |= ! Objects.equals(this.redirectUris, redirectUris);
        this.redirectUris = redirectUris;
    }

    @Override
    public Boolean isEnabled() {
        return enabled;
    }

    @Override
    public void setEnabled(Boolean enabled) {
        this.updated |= ! Objects.equals(this.enabled, enabled);
        this.enabled = enabled;
    }

    @Override
    public Boolean isAlwaysDisplayInConsole() {
        return alwaysDisplayInConsole;
    }

    @Override
    public void setAlwaysDisplayInConsole(Boolean alwaysDisplayInConsole) {
        this.updated |= ! Objects.equals(this.alwaysDisplayInConsole, alwaysDisplayInConsole);
        this.alwaysDisplayInConsole = alwaysDisplayInConsole;
    }

    @Override
    public String getClientAuthenticatorType() {
        return clientAuthenticatorType;
    }

    @Override
    public void setClientAuthenticatorType(String clientAuthenticatorType) {
        this.updated |= ! Objects.equals(this.clientAuthenticatorType, clientAuthenticatorType);
        this.clientAuthenticatorType = clientAuthenticatorType;
    }

    @Override
    public String getSecret() {
        return secret;
    }

    @Override
    public void setSecret(String secret) {
        this.updated |= ! Objects.equals(this.secret, secret);
        this.secret = secret;
    }

    @Override
    public String getRegistrationToken() {
        return registrationToken;
    }

    @Override
    public void setRegistrationToken(String registrationToken) {
        this.updated |= ! Objects.equals(this.registrationToken, registrationToken);
        this.registrationToken = registrationToken;
    }

    @Override
    public String getProtocol() {
        return protocol;
    }

    @Override
    public void setProtocol(String protocol) {
        this.updated |= ! Objects.equals(this.protocol, protocol);
        this.protocol = protocol;
    }

    @Override
    public Map<String, String> getAuthFlowBindings() {
        return authFlowBindings.stream().collect(Collectors.toMap(HotRodPair::getFirst, HotRodPair::getSecond));
    }

    @Override
    public void setAuthFlowBindings(Map<String, String> authFlowBindings) {
        this.updated = true;

        this.authFlowBindings.clear();
        this.authFlowBindings.addAll(authFlowBindings.entrySet().stream().map(e -> new HotRodPair<>(e.getKey(), e.getValue())).collect(Collectors.toSet()));
    }

    @Override
    public Boolean isPublicClient() {
        return publicClient;
    }

    @Override
    public void setPublicClient(Boolean publicClient) {
        this.updated |= ! Objects.equals(this.publicClient, publicClient);
        this.publicClient = publicClient;
    }

    @Override
    public void setRealmId(String realmId) {
        this.realmId = realmId;
    }

    @Override
    public Boolean isFullScopeAllowed() {
        return fullScopeAllowed;
    }

    @Override
    public void setFullScopeAllowed(Boolean fullScopeAllowed) {
        this.updated |= ! Objects.equals(this.fullScopeAllowed, fullScopeAllowed);
        this.fullScopeAllowed = fullScopeAllowed;
    }

    @Override
    public Boolean isFrontchannelLogout() {
        return frontchannelLogout;
    }

    @Override
    public void setFrontchannelLogout(Boolean frontchannelLogout) {
        this.updated |= ! Objects.equals(this.frontchannelLogout, frontchannelLogout);
        this.frontchannelLogout = frontchannelLogout;
    }

    @Override
    public int getNotBefore() {
        return notBefore;
    }

    @Override
    public void setNotBefore(int notBefore) {
        this.updated |= ! Objects.equals(this.notBefore, notBefore);
        this.notBefore = notBefore;
    }

    @Override
    public Set<String> getScope() {
        return scope;
    }

    @Override
    public void setScope(Set<String> scope) {
        this.updated |= ! Objects.equals(this.scope, scope);
        this.scope.clear();
        this.scope.addAll(scope);
    }

    @Override
    public Set<String> getWebOrigins() {
        return webOrigins;
    }

    @Override
    public void setWebOrigins(Set<String> webOrigins) {
        this.updated |= ! Objects.equals(this.webOrigins, webOrigins);
        this.webOrigins.clear();
        this.webOrigins.addAll(webOrigins);
    }

    @Override
    public ProtocolMapperModel addProtocolMapper(ProtocolMapperModel model) {
        Objects.requireNonNull(model.getId(), "protocolMapper.id");
        updated = true;
        
        removeProtocolMapper(model.getId());

        this.protocolMappers.add(HotRodProtocolMapperEntity.fromModel(model));
        return model;
    }

    @Override
    public Collection<ProtocolMapperModel> getProtocolMappers() {
        return protocolMappers.stream().map(HotRodProtocolMapperEntity::toModel).collect(Collectors.toSet());
    }

    @Override
    public void updateProtocolMapper(String id, ProtocolMapperModel mapping) {
        protocolMappers.stream().filter(entity -> Objects.equals(id, entity.id))
                .findFirst()
                .ifPresent(entity -> {
                    protocolMappers.remove(entity);
                    protocolMappers.add(HotRodProtocolMapperEntity.fromModel(mapping));
                    updated = true;
                });
    }

    @Override
    public void removeProtocolMapper(String id) {
        protocolMappers.stream().filter(entity -> Objects.equals(id, entity.id))
                .findFirst()
                .ifPresent(entity -> {
                    protocolMappers.remove(entity);
                    updated = true;
                });
    }

    @Override
    public void setProtocolMappers(Collection<ProtocolMapperModel> protocolMappers) {
        this.updated = true;

        this.protocolMappers.clear();
        this.protocolMappers.addAll(protocolMappers.stream().map(HotRodProtocolMapperEntity::fromModel).collect(Collectors.toSet()));
    }

    @Override
    public ProtocolMapperModel getProtocolMapperById(String id) {
        return protocolMappers.stream().filter(entity -> Objects.equals(id, entity.id)).findFirst()
                .map(HotRodProtocolMapperEntity::toModel)
                .orElse(null);
    }

    @Override
    public Boolean isSurrogateAuthRequired() {
        return surrogateAuthRequired;
    }

    @Override
    public void setSurrogateAuthRequired(Boolean surrogateAuthRequired) {
        this.updated |= ! Objects.equals(this.surrogateAuthRequired, surrogateAuthRequired);
        this.surrogateAuthRequired = surrogateAuthRequired;
    }

    @Override
    public String getManagementUrl() {
        return managementUrl;
    }

    @Override
    public void setManagementUrl(String managementUrl) {
        this.updated |= ! Objects.equals(this.managementUrl, managementUrl);
        this.managementUrl = managementUrl;
    }

    @Override
    public String getRootUrl() {
        return rootUrl;
    }

    @Override
    public void setRootUrl(String rootUrl) {
        this.updated |= ! Objects.equals(this.rootUrl, rootUrl);
        this.rootUrl = rootUrl;
    }

    @Override
    public String getBaseUrl() {
        return baseUrl;
    }

    @Override
    public void setBaseUrl(String baseUrl) {
        this.updated |= ! Objects.equals(this.baseUrl, baseUrl);
        this.baseUrl = baseUrl;
    }

    @Override
    public Boolean isBearerOnly() {
        return bearerOnly;
    }

    @Override
    public void setBearerOnly(Boolean bearerOnly) {
        this.updated |= ! Objects.equals(this.bearerOnly, bearerOnly);
        this.bearerOnly = bearerOnly;
    }

    @Override
    public Boolean isConsentRequired() {
        return consentRequired;
    }

    @Override
    public void setConsentRequired(Boolean consentRequired) {
        this.updated |= ! Objects.equals(this.consentRequired, consentRequired);
        this.consentRequired = consentRequired;
    }

    @Override
    public Boolean isStandardFlowEnabled() {
        return standardFlowEnabled;
    }

    @Override
    public void setStandardFlowEnabled(Boolean standardFlowEnabled) {
        this.updated |= ! Objects.equals(this.standardFlowEnabled, standardFlowEnabled);
        this.standardFlowEnabled = standardFlowEnabled;
    }

    @Override
    public Boolean isImplicitFlowEnabled() {
        return implicitFlowEnabled;
    }

    @Override
    public void setImplicitFlowEnabled(Boolean implicitFlowEnabled) {
        this.updated |= ! Objects.equals(this.implicitFlowEnabled, implicitFlowEnabled);
        this.implicitFlowEnabled = implicitFlowEnabled;
    }

    @Override
    public Boolean isDirectAccessGrantsEnabled() {
        return directAccessGrantsEnabled;
    }

    @Override
    public void setDirectAccessGrantsEnabled(Boolean directAccessGrantsEnabled) {
        this.updated |= ! Objects.equals(this.directAccessGrantsEnabled, directAccessGrantsEnabled);
        this.directAccessGrantsEnabled = directAccessGrantsEnabled;
    }

    @Override
    public Boolean isServiceAccountsEnabled() {
        return serviceAccountsEnabled;
    }

    @Override
    public void setServiceAccountsEnabled(Boolean serviceAccountsEnabled) {
        this.updated |= ! Objects.equals(this.serviceAccountsEnabled, serviceAccountsEnabled);
        this.serviceAccountsEnabled = serviceAccountsEnabled;
    }

    @Override
    public int getNodeReRegistrationTimeout() {
        return nodeReRegistrationTimeout;
    }

    @Override
    public void setNodeReRegistrationTimeout(int nodeReRegistrationTimeout) {
        this.updated |= ! Objects.equals(this.nodeReRegistrationTimeout, nodeReRegistrationTimeout);
        this.nodeReRegistrationTimeout = nodeReRegistrationTimeout;
    }

    @Override
    public void addWebOrigin(String webOrigin) {
        updated = true;
        this.webOrigins.add(webOrigin);
    }

    @Override
    public void removeWebOrigin(String webOrigin) {
        updated |= this.webOrigins.remove(webOrigin);
    }

    @Override
    public void addRedirectUri(String redirectUri) {
        this.updated |= ! this.redirectUris.contains(redirectUri);
        this.redirectUris.add(redirectUri);
    }

    @Override
    public void removeRedirectUri(String redirectUri) {
        updated |= this.redirectUris.remove(redirectUri);
    }

    @Override
    public String getAuthenticationFlowBindingOverride(String binding) {
        return authFlowBindings.stream().filter(pair -> Objects.equals(pair.getFirst(), binding)).findFirst()
                .map(HotRodPair::getSecond)
                .orElse(null);
    }

    @Override
    public Map<String, String> getAuthenticationFlowBindingOverrides() {
        return this.authFlowBindings.stream().collect(Collectors.toMap(HotRodPair::getFirst, HotRodPair::getSecond));
    }

    @Override
    public void removeAuthenticationFlowBindingOverride(String binding) {
        this.authFlowBindings.stream().filter(pair -> Objects.equals(pair.getFirst(), binding)).findFirst()
                .ifPresent(pair -> {
                    updated = true;
                    authFlowBindings.remove(pair);
                });
    }

    @Override
    public void setAuthenticationFlowBindingOverride(String binding, String flowId) {
        this.updated = true;
        
        removeAuthenticationFlowBindingOverride(binding);
        
        this.authFlowBindings.add(new HotRodPair<>(binding, flowId));
    }

    @Override
    public Collection<String> getScopeMappings() {
        return scopeMappings;
    }

    @Override
    public void addScopeMapping(String id) {
        if (id != null) {
            updated = true;
            scopeMappings.add(id);
        }
    }

    @Override
    public void deleteScopeMapping(String id) {
        updated |= scopeMappings.remove(id);
    }

    @Override
    public void addClientScope(String id, Boolean defaultScope) {
        if (id != null) {
            updated = true;
            removeClientScope(id);

            this.clientScopes.add(new HotRodPair<>(id, defaultScope));
        }
    }

    @Override
    public void removeClientScope(String id) {
        this.clientScopes.stream().filter(pair -> Objects.equals(pair.getFirst(), id)).findFirst()
                .ifPresent(pair -> {
                    updated = true;
                    clientScopes.remove(pair);
                });
    }

    @Override
    public Stream<String> getClientScopes(boolean defaultScope) {
        return this.clientScopes.stream()
                .filter(pair -> Objects.equals(pair.getSecond(), defaultScope))
                .map(HotRodPair::getFirst);
    }

    @Override
    public String getRealmId() {
        return this.realmId;
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public boolean isUpdated() {
        return updated;
    }
}
