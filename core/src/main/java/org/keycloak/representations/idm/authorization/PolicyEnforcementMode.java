/*
 * Copyright 2016 Red Hat, Inc. and/or its affiliates
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
package org.keycloak.representations.idm.authorization;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.keycloak.util.EnumWithUnchangableIndex;

/**
 * The policy enforcement mode dictates how authorization requests are handled by the server.
 *
 * @author <a href="mailto:psilva@redhat.com">Pedro Igor</a>
 */
public enum PolicyEnforcementMode implements EnumWithUnchangableIndex {

    /**
     * Requests are denied by default even when there is no policy associated with a given resource.
     */
    ENFORCING(0),

    /**
     * Requests are allowed even when there is no policy associated with a given resource.
     */
    PERMISSIVE(1),

    /**
     * Completely disables the evaluation of policies and allow access to any resource.
     */
    DISABLED(2);

    private final Integer unchangebleIndex;
    private static final Map<Integer, PolicyEnforcementMode> BY_ID = new HashMap<>();

    static {
        for (PolicyEnforcementMode policyEnforcementMode : values()) {
            BY_ID.put(policyEnforcementMode.getUnchangebleIndex(), policyEnforcementMode);
        }
    }

    private PolicyEnforcementMode(Integer unchangebleIndex) {
        Objects.requireNonNull(unchangebleIndex);
        this.unchangebleIndex = unchangebleIndex;
    }

    @Override
    public Integer getUnchangebleIndex() {
        return unchangebleIndex;
    }

    public static PolicyEnforcementMode valueOfInteger(Integer id) {
        return id == null ? null : BY_ID.get(id);
    }
}
