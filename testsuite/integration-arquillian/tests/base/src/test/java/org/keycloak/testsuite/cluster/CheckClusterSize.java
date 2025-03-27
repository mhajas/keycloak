package org.keycloak.testsuite.cluster;

import org.infinispan.Cache;
import org.infinispan.remoting.transport.Address;
import org.keycloak.connections.infinispan.InfinispanConnectionProvider;
import org.keycloak.models.KeycloakSession;

import java.util.Arrays;
import java.util.Set;

import static org.junit.Assert.assertEquals;

public class CheckClusterSize {

    public static void clusterSize(KeycloakSession session) {
        InfinispanConnectionProvider provider = session.getProvider(InfinispanConnectionProvider.class);
        Cache<Object, Object> authenticationSessions = provider.getCache("authenticationSessions");
        Set<Address> membersSet = authenticationSessions.getAdvancedCache().getDistributionManager().getCacheTopology().getMembersSet();
        System.out.printf("Member set size: %d [%s]%n", membersSet.size(), Arrays.toString(membersSet.toArray()));
        assertEquals(2, membersSet.size());
    }
}
