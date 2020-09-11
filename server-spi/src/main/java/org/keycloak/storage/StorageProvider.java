package org.keycloak.storage;

import org.keycloak.provider.Provider;

public interface StorageProvider extends Provider {
    interface Capability <T extends StorageProvider> extends Provider.Capability<T> {}
}
