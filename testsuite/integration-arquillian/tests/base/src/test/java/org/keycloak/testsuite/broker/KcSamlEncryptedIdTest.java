package org.keycloak.testsuite.broker;

import org.apache.xml.security.encryption.XMLCipher;
import org.hamcrest.CoreMatchers;
import org.hamcrest.Matchers;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.broker.saml.SAMLIdentityProviderConfig;
import org.keycloak.common.util.PemUtils;
import org.keycloak.crypto.Algorithm;
import org.keycloak.crypto.KeyUse;
import org.keycloak.dom.saml.v2.protocol.AuthnRequestType;
import org.keycloak.protocol.saml.rotation.SAMLEncryptionAlgorithms;
import org.keycloak.representations.idm.KeysMetadataRepresentation;
import org.keycloak.representations.idm.KeysMetadataRepresentation.KeyMetadataRepresentation;
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
import org.keycloak.testsuite.updaters.IdentityProviderAttributeUpdater;
import org.keycloak.testsuite.util.SamlClient;
import org.keycloak.testsuite.util.SamlClientBuilder;
import org.keycloak.testsuite.util.saml.SamlDocumentStepBuilder;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import javax.ws.rs.core.Response;
import javax.xml.namespace.QName;
import java.io.Closeable;
import java.io.IOException;
import java.security.PublicKey;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.keycloak.broker.saml.SAMLEndpoint.ENCRYPTION_DEPRECATED_MODE_PROPERTY;
import static org.keycloak.saml.common.constants.JBossSAMLURIConstants.ASSERTION_NSURI;
import static org.keycloak.testsuite.broker.BrokerTestTools.getConsumerRoot;
import static org.keycloak.testsuite.saml.AbstractSamlTest.SAML_CLIENT_ID_SALES_POST;
import static org.keycloak.testsuite.util.Matchers.bodyHC;
import static org.keycloak.testsuite.util.Matchers.isSamlResponse;
import static org.keycloak.testsuite.util.Matchers.statusCodeIsHC;

public class KcSamlEncryptedIdTest extends AbstractKcSamlEncryptedElementsTest {

    @Override
    protected SamlDocumentStepBuilder.Saml2DocumentTransformer encryptDocument(PublicKey publicKey, String keyEncryptionAlgorithm) {
        return document -> { // Replace Subject -> NameID with EncryptedId
            Node assertionElement = document.getDocumentElement()
                    .getElementsByTagNameNS(ASSERTION_NSURI.get(), JBossSAMLConstants.ASSERTION.get()).item(0);

            if (assertionElement == null) {
                throw new IllegalStateException("Unable to find assertion in saml response document");
            }

            String samlNSPrefix = assertionElement.getPrefix();
            String username;
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
                username = nameIdElement.getTextContent();

                byte[] secret = RandomSecret.createRandomSecret(128 / 8);
                SecretKey secretKey = new SecretKeySpec(secret, "AES");

                // encrypt the Assertion element and replace it with a EncryptedAssertion element.
                XMLEncryptionUtil.encryptElement(nameIdQName, document, publicKey,
                        secretKey, 128, encryptedIdElementQName, true, keyEncryptionAlgorithm);
            } catch (Exception e) {
                throw new ProcessingException("failed to encrypt", e);
            }

            String doc = DocumentUtil.asString(document);
            assertThat(doc, not(containsString(username)));
            assertThat(doc, CoreMatchers.containsString(keyEncryptionAlgorithm));
            return document;
        };
    }
}
