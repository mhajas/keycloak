/*
 * Copyright 2023 Red Hat, Inc. and/or its affiliates
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

package org.keycloak.protocol.saml.rotation;

import org.apache.xml.security.encryption.XMLCipher;
import org.keycloak.crypto.Algorithm;

import java.util.Arrays;
import java.util.Objects;

import static org.apache.xml.security.utils.EncryptionConstants.MGF1_SHA256;

/**
 * This enum provides mapping between Keycloak provided encryption algorithms and algorithms from xmlsec.
 * It is used to make sure we are using keys generated for given algorithm only with that algorithm.
 */
public enum SAMLEncryptionAlgorithms {
    RSA_OAEP(XMLCipher.RSA_OAEP, Algorithm.RSA_OAEP),
    RSA1_5(XMLCipher.RSA_v1dot5, Algorithm.RSA1_5);

    private String xmlEncIdentifier;
    private String keycloakIdentifier;

    SAMLEncryptionAlgorithms(String xmlEncIdentifier, String keycloakIdentifier) {
        this.xmlEncIdentifier = xmlEncIdentifier;
        this.keycloakIdentifier = keycloakIdentifier;
    }

    public String getXmlEncIdentifier() {
        return xmlEncIdentifier;
    }

    public String getKeycloakIdentifier() {
        return keycloakIdentifier;
    }

    public static SAMLEncryptionAlgorithms forXMLEncIdentifier(String xmlEncIdentifier) {
        return Arrays.stream(values())
                .filter(en -> Objects.equals(en.xmlEncIdentifier, xmlEncIdentifier))
                .findFirst().orElse(null);
    }

    public static SAMLEncryptionAlgorithms forKeycloakIdentifier(String keycloakIdentifier) {
        return Arrays.stream(values())
                .filter(en -> Objects.equals(en.keycloakIdentifier, keycloakIdentifier))
                .findFirst().orElse(null);
    }
}
