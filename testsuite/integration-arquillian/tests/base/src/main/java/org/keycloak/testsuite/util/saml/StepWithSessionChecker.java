package org.keycloak.testsuite.util.saml;

public interface StepWithSessionChecker {
    default SessionStateChecker getBeforeStepChecker() {
        return null;
    }
    default SessionStateChecker getAfterStepChecker() {
        return null;
    }
}
