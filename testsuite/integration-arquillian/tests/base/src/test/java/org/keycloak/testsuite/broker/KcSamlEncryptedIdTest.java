package org.keycloak.testsuite.broker;

import org.hamcrest.Matchers;
import org.junit.Assert;
import org.junit.Test;
import org.keycloak.common.util.PemUtils;
import org.keycloak.dom.saml.v2.protocol.AuthnRequestType;
import org.keycloak.saml.RandomSecret;
import org.keycloak.saml.common.constants.JBossSAMLConstants;
import org.keycloak.saml.common.constants.JBossSAMLURIConstants;
import org.keycloak.saml.common.exceptions.ConfigurationException;
import org.keycloak.saml.common.exceptions.ParsingException;
import org.keycloak.saml.common.exceptions.ProcessingException;
import org.keycloak.saml.common.util.DocumentUtil;
import org.keycloak.saml.processing.api.saml.v2.request.SAML2Request;
import org.keycloak.saml.processing.core.saml.v2.common.SAMLDocumentHolder;
import org.keycloak.saml.processing.core.util.XMLEncryptionUtil;
import org.keycloak.testsuite.admin.ApiUtil;
import org.keycloak.testsuite.util.SamlClient;
import org.keycloak.testsuite.util.SamlClientBuilder;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import javax.xml.namespace.QName;

import static org.keycloak.saml.common.constants.JBossSAMLURIConstants.ASSERTION_NSURI;
import static org.keycloak.testsuite.saml.AbstractSamlTest.SAML_ASSERTION_CONSUMER_URL_SALES_POST;
import static org.keycloak.testsuite.saml.AbstractSamlTest.SAML_CLIENT_ID_SALES_POST;
import static org.keycloak.testsuite.util.Matchers.isSamlResponse;

public class KcSamlEncryptedIdTest extends AbstractBrokerTest {
    @Override
    protected BrokerConfiguration getBrokerConfiguration() {
        return KcSamlBrokerConfiguration.INSTANCE;
    }

    @Test
    public void testEncryptedIdIsReadable() throws ConfigurationException, ParsingException, ProcessingException {
        createRolesForRealm(bc.consumerRealmName());

        AuthnRequestType loginRep = SamlClient.createLoginRequestDocument(SAML_CLIENT_ID_SALES_POST, SAML_ASSERTION_CONSUMER_URL_SALES_POST, null);

        Document doc = SAML2Request.convert(loginRep);

        SAMLDocumentHolder samlResponse = new SamlClientBuilder()
                .authnRequest(getConsumerSamlEndpoint(bc.consumerRealmName()), doc, SamlClient.Binding.POST).build()   // Request to consumer IdP
                .login().idp(bc.getIDPAlias()).build()

                .processSamlResponse(SamlClient.Binding.POST)    // AuthnRequest to producer IdP
                .targetAttributeSamlRequest()
                .build()

                .login().user(bc.getUserLogin(), bc.getUserPassword()).build()

                .processSamlResponse(SamlClient.Binding.POST)    // Response from producer IdP
                .transformDocument(document -> { // Replace Subject -> NameID with EncryptedId
                    Node assertionElement = document.getDocumentElement()
                            .getElementsByTagNameNS(ASSERTION_NSURI.get(), JBossSAMLConstants.ASSERTION.get()).item(0);

                    if (assertionElement == null) {
                        throw new IllegalStateException("Unable to find assertion in saml response document");
                    }

                    String samlNSPrefix = assertionElement.getPrefix();

                    try {
                        QName encryptedIdElementQName = new QName(ASSERTION_NSURI.get(), JBossSAMLConstants.ENCRYPTED_ID.get(), samlNSPrefix);
                        QName nameIdQName = new QName(ASSERTION_NSURI.get(),
                                JBossSAMLConstants.NAMEID.get(), samlNSPrefix);

                        // Add xmlns:saml attribute to NameId element,
                        // this is necessary as it is decrypted as a separate doc and saml namespace is not know
                        // unless added to NameId element
                        Element nameIdElement = DocumentUtil.getElement(document, nameIdQName);
                        if (nameIdElement == null) {
                            throw new RuntimeException("Assertion doesn't contain NameId " + DocumentUtil.asString(document));
                        }
                        nameIdElement.setAttribute("xmlns:" + samlNSPrefix, ASSERTION_NSURI.get());

                        byte[] secret = RandomSecret.createRandomSecret(128 / 8);
                        SecretKey secretKey = new SecretKeySpec(secret, "AES");

                        // encrypt the Assertion element and replace it with a EncryptedAssertion element.
                        XMLEncryptionUtil.encryptElement(nameIdQName, document, PemUtils.decodePublicKey(ApiUtil.findActiveSigningKey(adminClient.realm(bc.consumerRealmName())).getPublicKey()),
                                secretKey, 128, encryptedIdElementQName, true);
                    } catch (Exception e) {
                        throw new ProcessingException("failed to encrypt", e);
                    }

                    return document;
                })
                .build()

                // first-broker flow
                .updateProfile().firstName("a").lastName("b").email(bc.getUserEmail()).username(bc.getUserLogin()).build()
                .followOneRedirect()

                .getSamlResponse(SamlClient.Binding.POST);       // Response from consumer IdP

        Assert.assertThat(samlResponse, Matchers.notNullValue());
        Assert.assertThat(samlResponse.getSamlObject(), isSamlResponse(JBossSAMLURIConstants.STATUS_SUCCESS));
    }
}
