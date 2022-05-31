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

package org.keycloak.events.admin;

import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.keycloak.util.EnumWithUnchangableIndex;

/**
 * @author <a href="mailto:sthorger@redhat.com">Stian Thorgersen</a>
 */
public enum OperationType implements EnumWithUnchangableIndex {

    CREATE(0),
    UPDATE(1),
    DELETE(2),
    ACTION(3);

    private final Integer unchangebleIndex;
    private static final Map<Integer, OperationType> BY_ID = Arrays.stream(values()).collect(Collectors.toMap(
            OperationType::getUnchangebleIndex, 
            Function.identity()));

    private OperationType(Integer unchangebleIndex) {
        Objects.requireNonNull(unchangebleIndex);
        this.unchangebleIndex = unchangebleIndex;
    }

    @Override
    public Integer getUnchangebleIndex() {
        return unchangebleIndex;
    }

    public static OperationType valueOfInteger(Integer id) {
        return id == null ? null : BY_ID.get(id);
    }
}
