package org.keycloak.testsuite.saml;

import org.apache.http.util.EntityUtils;
import org.junit.Test;
import org.keycloak.dom.saml.v2.SAML2Object;
import org.keycloak.dom.saml.v2.assertion.AssertionType;
import org.keycloak.dom.saml.v2.assertion.AuthnStatementType;
import org.keycloak.dom.saml.v2.assertion.NameIDType;
import org.keycloak.dom.saml.v2.protocol.AuthnRequestType;
import org.keycloak.dom.saml.v2.protocol.ResponseType;
import org.keycloak.dom.saml.v2.protocol.StatusResponseType;
import org.keycloak.protocol.saml.SamlProtocol;
import org.keycloak.saml.SAML2LogoutRequestBuilder;
import org.keycloak.saml.common.constants.JBossSAMLURIConstants;
import org.keycloak.saml.common.exceptions.ConfigurationException;
import org.keycloak.saml.common.exceptions.ParsingException;
import org.keycloak.saml.common.exceptions.ProcessingException;
import org.keycloak.saml.processing.api.saml.v2.request.SAML2Request;
import org.keycloak.saml.processing.core.saml.v2.common.SAMLDocumentHolder;
import org.keycloak.testsuite.arquillian.annotation.AuthServerContainerExclude;
import org.keycloak.testsuite.updaters.ClientAttributeUpdater;
import org.keycloak.testsuite.util.ArtifactResolutionService;
import org.keycloak.testsuite.util.SamlClient;
import org.keycloak.testsuite.util.SamlClientBuilder;
import org.keycloak.testsuite.util.saml.CreateArtifactMessageStepBuilder;
import org.w3c.dom.Document;

import java.util.concurrent.atomic.AtomicReference;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.Assert.assertThat;
import static org.keycloak.testsuite.util.Matchers.isSamlResponse;
import static org.keycloak.testsuite.util.Matchers.isSamlStatusResponse;
import static org.keycloak.testsuite.util.SamlClient.Binding.POST;
import static org.keycloak.testsuite.util.SamlClient.Binding.REDIRECT;

@AuthServerContainerExclude(AuthServerContainerExclude.AuthServer.REMOTE) // Won't work with openshift, because openshift wouldn't see ArtifactResolutionService
public class ArtifactBindingWithResolutionServiceTest extends AbstractSamlTest {

    private final AtomicReference<NameIDType> nameIdRef = new AtomicReference<>();
    private final AtomicReference<String> sessionIndexRef = new AtomicReference<>();

    private SAML2Object extractNameIdAndSessionIndexAndTerminate(SAML2Object so) {
        assertThat(so, isSamlResponse(JBossSAMLURIConstants.STATUS_SUCCESS));
        ResponseType loginResp1 = (ResponseType) so;
        final AssertionType firstAssertion = loginResp1.getAssertions().get(0).getAssertion();
        assertThat(firstAssertion, org.hamcrest.Matchers.notNullValue());
        assertThat(firstAssertion.getSubject().getSubType().getBaseID(), instanceOf(NameIDType.class));

        NameIDType nameId = (NameIDType) firstAssertion.getSubject().getSubType().getBaseID();
        AuthnStatementType firstAssertionStatement = (AuthnStatementType) firstAssertion.getStatements().iterator().next();

        nameIdRef.set(nameId);
        sessionIndexRef.set(firstAssertionStatement.getSessionIndex());

        return null;
    }

    @Test
    public void testReceiveArtifactLoginFullWithPost() throws ParsingException, ConfigurationException, ProcessingException, InterruptedException {
        getCleanup()
            .addCleanup(ClientAttributeUpdater.forClient(adminClient, REALM_NAME, SAML_CLIENT_ID_SALES_POST)
                    .setAttribute(SamlProtocol.SAML_ARTIFACT_RESOLUTION_SERVICE_URL_ATTRIBUTE, "http://127.0.0.1:8082/")
                    .update()
            );

        AuthnRequestType loginRep = SamlClient.createLoginRequestDocument(SAML_CLIENT_ID_SALES_POST, AbstractSamlTest.SAML_ASSERTION_CONSUMER_URL_SALES_POST, null);
        Document doc = SAML2Request.convert(loginRep);

        SamlClientBuilder builder = new SamlClientBuilder();
        CreateArtifactMessageStepBuilder camb = new CreateArtifactMessageStepBuilder(getAuthServerSamlEndpoint(REALM_NAME), SAML_CLIENT_ID_SALES_POST,
                SamlClient.Binding.POST, builder);

        ArtifactResolutionService ars = new ArtifactResolutionService("http://127.0.0.1:8082/").setResponseDocument(doc);
        Thread arsThread = new Thread(ars);
        try {
            arsThread.start();
            synchronized (ars) {
                ars.wait();
                SAMLDocumentHolder response = builder.artifactMessage(camb).build().login().user(bburkeUser).build().getSamlResponse(SamlClient.Binding.POST);
                assertThat(response.getSamlObject(), instanceOf(ResponseType.class));
                ResponseType rt = (ResponseType)response.getSamlObject();
                assertThat(rt.getAssertions(),not(empty()));
                assertThat(ars.getLastArtifactResolve(), notNullValue());
                assertThat(camb.getLastArtifact(), is(ars.getLastArtifactResolve().getArtifact()));
            }
        } finally {
            ars.stop();
            arsThread.join();
        }
    }

    @Test
    public void testReceiveArtifactLoginFullWithRedirect() throws ParsingException, ConfigurationException, ProcessingException, InterruptedException {
        getCleanup()
            .addCleanup(ClientAttributeUpdater.forClient(adminClient, REALM_NAME, SAML_CLIENT_ID_SALES_POST)
                    .setAttribute(SamlProtocol.SAML_ARTIFACT_RESOLUTION_SERVICE_URL_ATTRIBUTE, "http://127.0.0.1:8082/")
                    .update()
            );

        AuthnRequestType loginReq = SamlClient.createLoginRequestDocument(SAML_CLIENT_ID_SALES_POST, AbstractSamlTest.SAML_ASSERTION_CONSUMER_URL_SALES_POST, null);
        loginReq.setProtocolBinding(JBossSAMLURIConstants.SAML_HTTP_REDIRECT_BINDING.getUri());
        Document doc = SAML2Request.convert(loginReq);

        SamlClientBuilder builder = new SamlClientBuilder();
        CreateArtifactMessageStepBuilder camb = new CreateArtifactMessageStepBuilder(getAuthServerSamlEndpoint(REALM_NAME), SAML_CLIENT_ID_SALES_POST,
                SamlClient.Binding.REDIRECT, builder);

        ArtifactResolutionService ars = new ArtifactResolutionService("http://127.0.0.1:8082/").setResponseDocument(doc);
        Thread arsThread = new Thread(ars);
        try {
            arsThread.start();
            synchronized (ars) {
                ars.wait();
                SAMLDocumentHolder response = builder.artifactMessage(camb).build().login().user(bburkeUser).build().getSamlResponse(REDIRECT);
                assertThat(response.getSamlObject(), instanceOf(ResponseType.class));
                ResponseType rt = (ResponseType)response.getSamlObject();
                assertThat(rt.getAssertions(),not(empty()));
                assertThat(ars.getLastArtifactResolve(), notNullValue());
                assertThat(camb.getLastArtifact(), is(ars.getLastArtifactResolve().getArtifact()));
            }
        } finally {
            ars.stop();
            arsThread.join();
        }
    }

    @Test
    public void testReceiveArtifactNonExistingClient() throws ParsingException, ConfigurationException, ProcessingException, InterruptedException {
        getCleanup()
            .addCleanup(ClientAttributeUpdater.forClient(adminClient, REALM_NAME, SAML_CLIENT_ID_SALES_POST)
                    .setAttribute(SamlProtocol.SAML_ARTIFACT_RESOLUTION_SERVICE_URL_ATTRIBUTE, "http://127.0.0.1:8082/")
                    .update()
            );

        AuthnRequestType loginRep = SamlClient.createLoginRequestDocument("blabla", AbstractSamlTest.SAML_ASSERTION_CONSUMER_URL_SALES_POST, null);
        Document doc = SAML2Request.convert(loginRep);

        SamlClientBuilder builder = new SamlClientBuilder();
        CreateArtifactMessageStepBuilder camb = new CreateArtifactMessageStepBuilder(getAuthServerSamlEndpoint(REALM_NAME), "blabla",
                SamlClient.Binding.POST, builder);

        ArtifactResolutionService ars = new ArtifactResolutionService("http://127.0.0.1:8082/").setResponseDocument(doc);
        Thread arsThread = new Thread(ars);
        try {
            arsThread.start();
            synchronized (ars) {
                ars.wait();
                String response = builder.artifactMessage(camb).build().executeAndTransform(resp -> EntityUtils.toString(resp.getEntity()));
                assertThat(response, containsString("Invalid Request"));
            }
        } finally {
            ars.stop();
            arsThread.join();
        }
    }

    @Test
    public void testReceiveArtifactLogoutFullWithPost() throws InterruptedException {
        getCleanup()
            .addCleanup(ClientAttributeUpdater.forClient(adminClient, REALM_NAME, SAML_CLIENT_ID_SALES_POST)
                    .setAttribute(SamlProtocol.SAML_ARTIFACT_RESOLUTION_SERVICE_URL_ATTRIBUTE, "http://127.0.0.1:8082/")
                    .update()
            );

        SamlClientBuilder builder = new SamlClientBuilder();
        CreateArtifactMessageStepBuilder camb = new CreateArtifactMessageStepBuilder(getAuthServerSamlEndpoint(REALM_NAME), SAML_CLIENT_ID_SALES_POST,
                POST, builder);

        ArtifactResolutionService ars = new ArtifactResolutionService("http://127.0.0.1:8082/");
        Thread arsThread = new Thread(ars);
        try {
            arsThread.start();
            synchronized (ars) {
                ars.wait();
                SAMLDocumentHolder samlResponse = builder.authnRequest(getAuthServerSamlEndpoint(REALM_NAME), SAML_CLIENT_ID_SALES_POST, SAML_ASSERTION_CONSUMER_URL_SALES_POST, POST).build()
                        .login().user(bburkeUser).build()
                        .processSamlResponse(POST)
                        .transformObject(x -> {
                            SAML2Object samlObj =  extractNameIdAndSessionIndexAndTerminate(x);
                            setArtifactResolutionServiceLogoutRequest(ars);
                            return samlObj;
                        })
                        .build().artifactMessage(camb).build().getSamlResponse(POST);
               assertThat(samlResponse.getSamlObject(), instanceOf(StatusResponseType.class));
               StatusResponseType srt = (StatusResponseType) samlResponse.getSamlObject();
               assertThat(srt, isSamlStatusResponse(JBossSAMLURIConstants.STATUS_SUCCESS));
               assertThat(camb.getLastArtifact(), is(ars.getLastArtifactResolve().getArtifact()));
            }
        } finally {
            ars.stop();
            arsThread.join();
        }
    }

    @Test
    public void testReceiveArtifactLogoutFullWithRedirect() throws InterruptedException {
        getCleanup()
            .addCleanup(ClientAttributeUpdater.forClient(adminClient, REALM_NAME, SAML_CLIENT_ID_SALES_POST)
                    .setAttribute(SamlProtocol.SAML_ARTIFACT_RESOLUTION_SERVICE_URL_ATTRIBUTE, "http://127.0.0.1:8082/")
                    .setAttribute(SamlProtocol.SAML_SINGLE_LOGOUT_SERVICE_URL_REDIRECT_ATTRIBUTE, "http://url")
                    .setFrontchannelLogout(true)
                    .update()
            );

        SamlClientBuilder builder = new SamlClientBuilder();
        CreateArtifactMessageStepBuilder camb = new CreateArtifactMessageStepBuilder(getAuthServerSamlEndpoint(REALM_NAME), SAML_CLIENT_ID_SALES_POST,
                REDIRECT, builder);

        ArtifactResolutionService ars = new ArtifactResolutionService("http://127.0.0.1:8082/");
        Thread arsThread = new Thread(ars);
        try {
            arsThread.start();
            synchronized (ars) {
                ars.wait();
                SAMLDocumentHolder samlResponse = builder
                        .authnRequest(getAuthServerSamlEndpoint(REALM_NAME), SAML_CLIENT_ID_SALES_POST,
                                SAML_ASSERTION_CONSUMER_URL_SALES_POST, REDIRECT)
                            .setProtocolBinding(JBossSAMLURIConstants.SAML_HTTP_REDIRECT_BINDING.getUri())
                            .build()
                        .login().user(bburkeUser).build()
                        .processSamlResponse(REDIRECT)
                        .transformObject(x -> {
                            SAML2Object samlObj =  extractNameIdAndSessionIndexAndTerminate(x);
                            setArtifactResolutionServiceLogoutRequest(ars);
                            return samlObj;
                        })
                        .build().artifactMessage(camb).build().getSamlResponse(REDIRECT);
                assertThat(samlResponse.getSamlObject(), instanceOf(StatusResponseType.class));
                StatusResponseType srt = (StatusResponseType) samlResponse.getSamlObject();
                assertThat(srt, isSamlStatusResponse(JBossSAMLURIConstants.STATUS_SUCCESS));
                assertThat(camb.getLastArtifact(), is(ars.getLastArtifactResolve().getArtifact()));
            }
        } finally {
            ars.stop();
            arsThread.join();
        }
    }

    private void setArtifactResolutionServiceLogoutRequest(ArtifactResolutionService ars) throws ParsingException, ConfigurationException, ProcessingException {
        SAML2LogoutRequestBuilder builder = new SAML2LogoutRequestBuilder()
                .destination(getAuthServerSamlEndpoint(REALM_NAME).toString())
                .issuer(SAML_CLIENT_ID_SALES_POST)
                .sessionIndex(sessionIndexRef.get());

        final NameIDType nameIdValue = nameIdRef.get();

        if (nameIdValue != null) {
            builder = builder.userPrincipal(nameIdValue.getValue(), nameIdValue.getFormat() == null ? null : nameIdValue.getFormat().toString());
        }
        ars.setResponseDocument(builder.buildDocument());
    }
}
