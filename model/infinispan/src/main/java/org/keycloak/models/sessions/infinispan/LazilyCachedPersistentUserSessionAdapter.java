/*
 * Copyright 2024 Red Hat, Inc. and/or its affiliates
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

package org.keycloak.models.sessions.infinispan;

import org.keycloak.models.AuthenticatedClientSessionModel;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;
import org.keycloak.models.UserSessionModel;

import java.util.Collection;
import java.util.Map;
import java.util.function.Supplier;

public class LazilyCachedPersistentUserSessionAdapter implements UserSessionModel {


    private final UserSessionModel persistedUserSessions;
    private final Supplier<UserSessionModel> cachedUserSessionSupplier;
    private UserSessionModel cachedUserSession;

    public LazilyCachedPersistentUserSessionAdapter(UserSessionModel persistedUserSessions, Supplier<UserSessionModel> cachedUserSessionSupplier) {
        this.persistedUserSessions = persistedUserSessions;
        this.cachedUserSessionSupplier = cachedUserSessionSupplier;
    }

    private UserSessionModel forReading() {
        return cachedUserSession == null ? persistedUserSessions : cachedUserSession;
    }

    private UserSessionModel forWriting() {
        if (cachedUserSession == null) {
            cachedUserSession = cachedUserSessionSupplier.get();

            if (cachedUserSession == null) {
                throw new IllegalStateException("Cached user session is null");
            }
        }

        return cachedUserSession;
    }

    @Override
    public String getId() {
        return forReading().getId();
    }

    @Override
    public RealmModel getRealm() {
        return forReading().getRealm();
    }

    @Override
    public String getBrokerSessionId() {
        return forReading().getBrokerUserId();
    }

    @Override
    public String getBrokerUserId() {
        return forReading().getBrokerUserId();
    }

    @Override
    public UserModel getUser() {
        return forReading().getUser();
    }

    @Override
    public String getLoginUsername() {
        return forReading().getLoginUsername();
    }

    @Override
    public String getIpAddress() {
        return forReading().getIpAddress();
    }

    @Override
    public String getAuthMethod() {
        return forReading().getAuthMethod();
    }

    @Override
    public boolean isRememberMe() {
        return forReading().isRememberMe();
    }

    @Override
    public int getStarted() {
        return forReading().getStarted();
    }

    @Override
    public int getLastSessionRefresh() {
        return forReading().getLastSessionRefresh();
    }

    @Override
    public boolean isOffline() {
        return forReading().isOffline();
    }

    @Override
    public void setLastSessionRefresh(int seconds) {
        forWriting().setLastSessionRefresh(seconds);
    }

    @Override
    public Map<String, AuthenticatedClientSessionModel> getAuthenticatedClientSessions() {
        return forReading().getAuthenticatedClientSessions();
    }

    @Override
    public void removeAuthenticatedClientSessions(Collection<String> removedClientUUIDS) {
        forWriting().removeAuthenticatedClientSessions(removedClientUUIDS);
    }

    @Override
    public String getNote(String name) {
        return forReading().getNote(name);
    }

    @Override
    public void setNote(String name, String value) {
        forWriting().setNote(name, value);
    }

    @Override
    public void removeNote(String name) {
        forWriting().removeNote(name);
    }

    @Override
    public Map<String, String> getNotes() {
        return forReading().getNotes();
    }

    @Override
    public State getState() {
        return forReading().getState();
    }

    @Override
    public void setState(State state) {
        forWriting().setState(state);
    }

    @Override
    public void restartSession(RealmModel realm, UserModel user, String loginUsername, String ipAddress, String authMethod, boolean rememberMe, String brokerSessionId, String brokerUserId) {
        forWriting().restartSession(realm, user, loginUsername, ipAddress, authMethod, rememberMe, brokerSessionId, brokerUserId);
    }
}
