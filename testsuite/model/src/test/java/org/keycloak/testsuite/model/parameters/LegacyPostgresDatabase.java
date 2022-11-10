/*
 * Copyright 2020 Red Hat, Inc. and/or its affiliates
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
package org.keycloak.testsuite.model.parameters;

import org.keycloak.testsuite.model.Config;
import org.keycloak.testsuite.model.KeycloakModelParameters;

import java.util.Collections;

/**
 *
 * @author hmlnarik
 */
public class LegacyPostgresDatabase extends KeycloakModelParameters {
    

    public LegacyPostgresDatabase() {
        super(Collections.emptySet(), Collections.emptySet());
    }


    @Override
    public void updateConfig(Config cf) {
        updateConfigForJpa(cf);
    }

    public static void updateConfigForJpa(Config cf) {
        cf.spi("connectionsJpa").provider("default")
                .config("url", "jdbc:postgresql://localhost:5432/keycloak")
                .config("driver", "org.postgresql.Driver")
                .config("driverDialect", "org.hibernate.dialect.PostgreSQLDialect")
                .config("user", "keycloak")
                .config("password", "pass")
                .config("showSql", "${keycloak.connectionsJpa.showSql:}")
                .config("formatSql", "${keycloak.connectionsJpa.formatSql:}")
                .config("globalStatsInterval", "${keycloak.connectionsJpa.globalStatsInterval:}")
        ;
    }
}
