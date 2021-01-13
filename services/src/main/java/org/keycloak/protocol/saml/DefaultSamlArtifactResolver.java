package org.keycloak.protocol.saml;

import com.google.common.base.Charsets;
import com.google.common.base.Strings;
import org.jboss.logging.Logger;
import org.keycloak.broker.saml.SAMLDataMarshaller;
import org.keycloak.dom.saml.v2.protocol.ArtifactResponseType;
import org.keycloak.events.Errors;
import org.keycloak.models.AuthenticatedClientSessionModel;
import org.keycloak.models.ClientModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.SamlArtifactSessionMappingModel;
import org.keycloak.models.UserSessionModel;
import org.keycloak.saml.common.constants.GeneralConstants;
import org.keycloak.saml.common.exceptions.ConfigurationException;
import org.keycloak.saml.common.exceptions.ParsingException;
import org.keycloak.saml.common.exceptions.ProcessingException;
import org.w3c.dom.Document;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.stream.Stream;

/**
 * ArtifactResolver for artifact-04 format.
 * Other kind of format for artifact are allowed by standard but not specified.
 * Artifact 04 is the only one specified in SAML2.0 specification.
 */
public class DefaultSamlArtifactResolver implements ArtifactResolver {

    /** SAML 2 artifact type code (0x0004). */
    private static final byte[] TYPE_CODE = {0, 4};

    protected static final Logger logger = Logger.getLogger(SamlService.class);

    private KeycloakSession session;

    @Override
    public String buildLogoutArtifact(String entityId, String samlDocument, UserSessionModel userSession) throws ArtifactResolverProcessingException, ArtifactResolverConfigException {
        String realmId = userSession.getRealm().getId();
        return buildArtifact(realmId, entityId, samlDocument);
    }

    @Override
    public String buildAuthnArtifact(String entityId, String samlDocument, AuthenticatedClientSessionModel clientSession) throws ArtifactResolverProcessingException, ArtifactResolverConfigException {
        String realmId = clientSession.getRealm().getId();
        return buildArtifact(realmId, entityId, samlDocument);
    }

    private String buildArtifact(String realmId, String entityId, String samlDocument) throws ArtifactResolverProcessingException, ArtifactResolverConfigException {
        String artifact = createArtifact(entityId);
        session.sessions().addArtifactSessionsMapping(realmId, artifact, samlDocument, null);
        return artifact;
    }

    @Override
    public String resolveArtifactResponseString(RealmModel realm, String artifact) throws ArtifactResolverProcessingException {
        SamlArtifactSessionMappingModel sessionsMapping = session.sessions().getArtifactSessionsMapping(artifact);

        if (sessionsMapping == null) {
            throw new ArtifactResolverProcessingException("Cannot find artifact " + artifact + " in cache");
        }
        session.sessions().removeArtifactResponse(artifact);

        UserSessionModel userSessionModel = session.sessions().getUserSession(realm, sessionsMapping.getUserSessionId());
        if (userSessionModel == null) {
            logger.errorf("UserSession with id: %s, that corresponds to artifact: %s does not exist.", sessionsMapping.getUserSessionId(), artifact);
            throw new ArtifactResolverProcessingException("Unable to get UserSession.");
        }

        AuthenticatedClientSessionModel clientSessionModel = userSessionModel.getAuthenticatedClientSessions().get(sessionsMapping.getClientSessionId());
        if (clientSessionModel == null) {
            logger.errorf("ClientSession with id: %s, that corresponds to artifact: %s and UserSession: %s does not exist.", sessionsMapping.getClientSessionId(), artifact, sessionsMapping.getUserSessionId());
            throw new ArtifactResolverProcessingException("Unable to get ClientSession.");
        }

        String artifactResponse = clientSessionModel.getNote(GeneralConstants.SAML_ARTIFACT_KEY + "=" + artifact);
        clientSessionModel.removeNote(GeneralConstants.SAML_ARTIFACT_KEY + "=" + artifact);
        logger.tracef("ArtifactResponse obtained from clientSession is: %s", artifactResponse);
        if (Strings.isNullOrEmpty(artifactResponse)) {
            throw new ArtifactResolverProcessingException("Artifact not present in ClientSession.");
        }
        
        return artifactResponse;
    }

    @Override
    public void initialize(KeycloakSession session) {
        this.session = session;
    }


    @Override
    public ClientModel selectSourceClient(String artifact, Stream<ClientModel> clients) throws ArtifactResolverProcessingException {
        try {
            byte[] source = extractSourceFromArtifact(artifact);

            MessageDigest sha1Digester = MessageDigest.getInstance("SHA-1");
            return clients.filter(clientModel -> Arrays.equals(source,
                    sha1Digester.digest(clientModel.getClientId().getBytes(Charsets.UTF_8))))
                    .findFirst()
                    .orElseThrow(() -> new ArtifactResolverProcessingException("No client matching the artifact source found"));
        } catch (NoSuchAlgorithmException e) {
            throw new ArtifactResolverProcessingException(e);
        }
    }

    @Override
    public String buildArtifact(String entityId, AuthenticatedClientSessionModel clientSessionModel, String artifactResponse) throws ArtifactResolverProcessingException {
        String artifact = createArtifact(entityId);
        UserSessionModel userSessionModel = clientSessionModel.getUserSession();
        session.sessions().addArtifactSessionsMapping(userSessionModel.getRealm().getId(), artifact, userSessionModel.getId(), clientSessionModel.getClient().getId());

        clientSessionModel.setNote(GeneralConstants.SAML_ARTIFACT_KEY + "=" + artifact, artifactResponse);

        return artifact;
    }

    private void assertSupportedArtifactFormat(String artifactString) throws ArtifactResolverProcessingException {
        byte[] artifact = Base64.getDecoder().decode(artifactString);

        if (artifact.length != 44) {
            throw new ArtifactResolverProcessingException("Artifact " + artifactString + " has a length of " + artifact.length + ". It should be 44");
        }
        if (artifact[0] != TYPE_CODE[0] || artifact[1] != TYPE_CODE[1]) {
            throw new ArtifactResolverProcessingException("Artifact " + artifactString + " doesn't start with 0x0004");
        }
    }

    private byte[] extractSourceFromArtifact(String artifactString) throws ArtifactResolverProcessingException {
        assertSupportedArtifactFormat(artifactString);

        byte[] artifact = Base64.getDecoder().decode(artifactString);

        byte[] source = new byte[20];
        System.arraycopy(artifact, 4, source, 0, source.length);

        return source;
    }

    /**
     * Creates an artifact. Format is:
     * <p>
     * SAML_artifact := B64(TypeCode EndpointIndex RemainingArtifact)
     * <p>
     * TypeCode := 0x0004
     * EndpointIndex := Byte1Byte2
     * RemainingArtifact := SourceID MessageHandle
     * <p>
     * SourceID := 20-byte_sequence, used by the artifact receiver to determine artifact issuer
     * MessageHandle := 20-byte_sequence
     *
     * @param entityId the entity id to encode in the sourceId
     * @return an artifact
     * @throws ArtifactResolverProcessingException
     */
    public String createArtifact(String entityId) throws ArtifactResolverProcessingException {
        try {
            SecureRandom handleGenerator = SecureRandom.getInstance("SHA1PRNG");
            byte[] trimmedIndex = new byte[2];

            MessageDigest sha1Digester = MessageDigest.getInstance("SHA-1");
            byte[] source = sha1Digester.digest(entityId.getBytes(Charsets.UTF_8));

            byte[] assertionHandle = new byte[20];
            handleGenerator.nextBytes(assertionHandle);

            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            bos.write(TYPE_CODE);
            bos.write(trimmedIndex);
            bos.write(source);
            bos.write(assertionHandle);

            byte[] artifact = bos.toByteArray();

            return Base64.getEncoder().encodeToString(artifact);
        } catch (NoSuchAlgorithmException e) {
            throw new ArtifactResolverProcessingException("JVM does not support required cryptography algorithms: SHA-1/SHA1PRNG.", e);
        } catch (IOException e) {
            throw new ArtifactResolverProcessingException(e);
        }

    }

    @Override
    public void close() {

    }

}
