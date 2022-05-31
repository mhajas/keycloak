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

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import org.keycloak.util.EnumWithUnchangableIndex;

/**
 * The decision strategy dictates how the policies associated with a given policy are evaluated and how a final decision
 * is obtained.
 *
 * @author <a href="mailto:psilva@redhat.com">Pedro Igor</a>
 */
public enum DecisionStrategy implements EnumWithUnchangableIndex {

    /**
     * Defines that at least one policy must evaluate to a positive decision in order to the overall decision be also positive.
     */
    AFFIRMATIVE(0),

    /**
     * Defines that all policies must evaluate to a positive decision in order to the overall decision be also positive.
     */
    UNANIMOUS(1),

    /**
     * Defines that the number of positive decisions must be greater than the number of negative decisions. If the number of positive and negative is the same,
     * the final decision will be negative.
     */
    CONSENSUS(2);

    private final Integer unchangebleIndex;
    private static final Map<Integer, DecisionStrategy> BY_ID = new HashMap<>();

    static {
        for (DecisionStrategy decisionStrategy : values()) {
            BY_ID.put(decisionStrategy.getUnchangebleIndex(), decisionStrategy);
        }
    }

    private DecisionStrategy(Integer unchangebleIndex) {
        Objects.requireNonNull(unchangebleIndex);
        this.unchangebleIndex = unchangebleIndex;
    }

    @Override
    public Integer getUnchangebleIndex() {
        return unchangebleIndex;
    }

    public static DecisionStrategy valueOfInteger(Integer id) {
        return id == null ? null : BY_ID.get(id);
    }
}
