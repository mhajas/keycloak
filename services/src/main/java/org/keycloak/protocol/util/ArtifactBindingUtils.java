package org.keycloak.protocol.util;

import org.keycloak.protocol.saml.DefaultSamlArtifactResolverFactory;

import java.util.Base64;

public class ArtifactBindingUtils {
    public static String artifactToResolverProviderId(String artifact) {
        return byteArrayToResolverProviderId(Base64.getDecoder().decode(artifact));
    }
    
    public static String byteArrayToResolverProviderId(byte[] ar) {
        int firstNumber = ar[0];
        int secondNumber = ar[1];
        
        String s = firstNumber + "" + secondNumber;

        if (s.equals(DefaultSamlArtifactResolverFactory.TYPE_CODE_STRING)) {
            return "default";
        }

        return s;
    }
}
