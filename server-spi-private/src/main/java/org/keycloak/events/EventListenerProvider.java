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

package org.keycloak.events;

import org.keycloak.events.admin.AdminEvent;
import org.keycloak.models.KeycloakTransaction;
import org.keycloak.provider.Provider;

/**
 * @author <a href="mailto:sthorger@redhat.com">Stian Thorgersen</a>
 *
 * Note that invocation of any of {@code onEvent} methods doesn't mean that the event happened at the moment but that it
 * will happen during the main transaction commit. This means the execution, corresponding to the triggered event,
 * may end up with a failure. Therefore, each implementation of this interface should consider using an internal
 * {@link KeycloakTransaction} for firing events only in case the main transaction ends up successfuly. This can be
 * achieved by enlisting the internal transaction to {@link org.keycloak.models.KeycloakTransactionManager} using the
 * {@link org.keycloak.models.KeycloakTransactionManager#enlistAfterCompletion(KeycloakTransaction)} method.
 *
 */
public interface EventListenerProvider extends Provider {

    /**
     *
     * Invocation of this method doesn't mean the execution described by the event will truly happen. It can be
     * rolled back by the {@link org.keycloak.models.KeycloakTransactionManager} in case of some failure. It is 
     * recommended to use this method just for saving events to be able to fire them only in case of the successful
     * transaction.
     * 
     * Note that this method should be always invoked in time when the main transaction is active, therefore it can be
     * used, for example, to make some changes in the database, ldap storage etc.
     * 
     * 
     * @param event to be triggered
     */
    void onEvent(Event event);

    /**
     *
     * Invocation of this method doesn't mean the execution described by the event will truly happen. It can be
     * rolled back by the {@link org.keycloak.models.KeycloakTransactionManager} in case of some failure. It is 
     * recommended to use this method just for saving events to some container and then fire them only in case of 
     * successful transaction. 
     *
     * Note that this method should be always invoked in time when main transaction is active, therefore it can be used,
     * for example, to make some changes in the database, ldap storage etc.
     *
     *
     * @param event to be triggered
     * @param includeRepresentation when false, event listener should NOT include representation field in the resulting
     *                              action
     */
    void onEvent(AdminEvent event, boolean includeRepresentation);

}
