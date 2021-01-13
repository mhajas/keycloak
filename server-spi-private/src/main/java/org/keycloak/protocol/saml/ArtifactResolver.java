package org.keycloak.protocol.saml;

import org.keycloak.models.AuthenticatedClientSessionModel;
import org.keycloak.models.ClientModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.SamlArtifactSessionMappingModel;
import org.keycloak.models.UserSessionModel;
import org.keycloak.provider.Provider;
import org.w3c.dom.Document;

import java.util.stream.Stream;


/**
 * Provides a way to create artifacts and resolve them
 */
public interface ArtifactResolver extends Provider {

    ClientModel selectSourceClient(String artifact, Stream<ClientModel> clients) throws ArtifactResolverProcessingException;
    
    String buildArtifact(String entityId, AuthenticatedClientSessionModel clientSessionModel, String artifactResponse) throws ArtifactResolverProcessingException;

    // TODO: remove
    String buildAuthnArtifact(String entityId, String samlDocument, AuthenticatedClientSessionModel clientSession) throws ArtifactResolverProcessingException, ArtifactResolverConfigException;

    // TODO: remove
    String buildLogoutArtifact(String entityId, String samlDocument, UserSessionModel userSession) throws ArtifactResolverProcessingException, ArtifactResolverConfigException;

    String resolveArtifactResponseString(RealmModel realm, String artifact) throws ArtifactResolverProcessingException;

    void initialize(KeycloakSession session);

}
