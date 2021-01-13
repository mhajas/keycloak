package org.keycloak.models;

public interface SamlArtifactSessionMappingModel {
    String getUserSessionId();
    String getClientSessionId();
}
