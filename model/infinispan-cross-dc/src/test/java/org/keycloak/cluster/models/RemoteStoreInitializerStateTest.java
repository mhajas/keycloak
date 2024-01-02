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

package org.keycloak.cluster.models;

import org.junit.Assert;
import org.junit.Test;
import org.keycloak.models.sessions.infinispan.remotestore.RemoteCacheSessionsLoaderContext;

/**
 * @author <a href="mailto:mposolda@redhat.com">Marek Posolda</a>
 */
public class RemoteStoreInitializerStateTest {

    @Test
    public void testRemoteLoaderContext() {
        assertSegmentsForRemoteLoader(0, 64, -1, 1);
        assertSegmentsForRemoteLoader(0, 64, 256, 1);
        assertSegmentsForRemoteLoader(5, 64, 256, 1);
        assertSegmentsForRemoteLoader(63, 64, 256, 1);
        assertSegmentsForRemoteLoader(64, 64, 256, 1);
        assertSegmentsForRemoteLoader(65, 64, 256, 2);
        assertSegmentsForRemoteLoader(127, 64, 256, 2);
        assertSegmentsForRemoteLoader(1000, 64, 256, 16);

        assertSegmentsForRemoteLoader(2047, 64, 256, 32);
        assertSegmentsForRemoteLoader(2048, 64, 256, 32);
        assertSegmentsForRemoteLoader(2049, 64, 256, 64);

        assertSegmentsForRemoteLoader(1000, 64, 256, 16);
        assertSegmentsForRemoteLoader(10000, 64, 256, 256);
        assertSegmentsForRemoteLoader(1000000, 64, 256, 256);
        assertSegmentsForRemoteLoader(10000000, 64, 256, 256);
    }

    private void assertSegmentsForRemoteLoader(int sessionsTotal, int sessionsPerSegment, int ispnSegmentsCount, int expectedSegments) {
        RemoteCacheSessionsLoaderContext ctx = new RemoteCacheSessionsLoaderContext(ispnSegmentsCount, sessionsPerSegment, sessionsTotal);
        Assert.assertEquals(expectedSegments, ctx.getSegmentsCount());
    }
}
