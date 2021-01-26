package org.keycloak.protocol.saml;

import org.keycloak.models.AuthenticatedClientSessionModel;
import org.keycloak.models.ClientModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.provider.Provider;

import java.util.stream.Stream;


/**
 * Provides a way to create artifacts and resolve them
 */
public interface ArtifactResolver extends Provider {

    ClientModel selectSourceClient(String artifact, Stream<ClientModel> clients) throws ArtifactResolverProcessingException;
    
    String buildArtifact(AuthenticatedClientSessionModel clientSessionModel, String entityId, String artifactResponse) throws ArtifactResolverProcessingException;

    String resolveArtifact(AuthenticatedClientSessionModel clientSessionModel, String artifact) throws ArtifactResolverProcessingException;
}
