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

package org.keycloak.rotation;

import org.apache.xml.security.encryption.EncryptedData;

import java.security.KeyManagementException;
import java.security.PrivateKey;
import java.util.List;

public interface DecryptionKeyLocator {
    
    /**
     * Provides a list of private keys that are suitable for decrypting
     * the given {@code encryptedData}.
     *
     * @param encryptedData data that need to be decrypted
     * @return a list of private keys
     */
    List<PrivateKey> getKeys(EncryptedData encryptedData);
}
