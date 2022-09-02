/*
 * Copyright 2022 Red Hat, Inc. and/or its affiliates
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

package org.keycloak.testsuite.model.hotrod;

import org.infinispan.client.hotrod.RemoteCache;
import org.infinispan.client.hotrod.exceptions.HotRodClientException;
import org.jboss.logging.Logger;
import org.junit.Test;
import org.keycloak.models.map.storage.hotRod.HotRodLocks;
import org.keycloak.models.map.storage.hotRod.connections.HotRodConnectionProvider;
import org.keycloak.testsuite.model.KeycloakModelParameters;
import org.keycloak.testsuite.model.KeycloakModelTest;
import org.keycloak.testsuite.model.RequireProvider;

import java.util.LinkedList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;

@RequireProvider(HotRodConnectionProvider.class)
public class HotRodLockingTest extends KeycloakModelTest {

    private static final Logger LOG = Logger.getLogger(HotRodLockingTest.class);

    private static final String LOCK_NAME = "testLock";

    @Override
    protected boolean isUseSameKeycloakSessionFactoryForAllThreads() {
        return true;
    }

    @Test
    public void simpleLockTest() {
        AtomicInteger counter = new AtomicInteger();
        int numIterations = 50;
        Random rand = new Random();
        List<Integer> resultingList = new LinkedList<>();

        IntStream.range(0, numIterations).parallel().forEach(index -> inComittedTransaction(s -> {
            HotRodConnectionProvider hotRodConnectionProvider = s.getProvider(HotRodConnectionProvider.class);
            RemoteCache<String, Object> realms = hotRodConnectionProvider.getRemoteCache("realms");
            LOG.infof("%s - %s", Thread.currentThread().getName(), HotRodLocks.acquireLock(realms, LOCK_NAME, 55));
            // Locked block
            int c = counter.getAndIncrement();

            try {
                Thread.sleep(rand.nextInt(500));
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }

            resultingList.add(c);
            LOG.infof("%s - adding: %d", Thread.currentThread().getName(), c);

            // End of locked block
            LOG.infof("%s - %s", Thread.currentThread().getName(), HotRodLocks.releaseLock(realms, LOCK_NAME));
        }));

        assertThat(resultingList, hasSize(numIterations));
        assertThat(resultingList, equalTo(IntStream.range(0, 50).boxed().collect(Collectors.toList())));
    }

    @Test
    public void lockTimeoutExceptionTest() {
        AtomicInteger counter = new AtomicInteger();

        IntStream.range(0, 2).parallel().forEach(index -> inComittedTransaction(s -> {
            HotRodConnectionProvider hotRodConnectionProvider = s.getProvider(HotRodConnectionProvider.class);
            RemoteCache<String, Object> realms = hotRodConnectionProvider.getRemoteCache("realms");
            try {
                LOG.infof("%s - %s", Thread.currentThread().getName(), HotRodLocks.acquireLock(realms, LOCK_NAME, 5));
                int c = counter.incrementAndGet();
                if (c != 1) {
                    LOG.infof("Lock acquired by thread %s with counter: %d", Thread.currentThread().getName(), c);
                    throw new RuntimeException("Lock acquired by more than one thread.");
                }
            } catch (HotRodClientException e) {
                int c = counter.incrementAndGet();
                LOG.infof("Exception when acquiring lock by thread %s with counter: %d", Thread.currentThread().getName(), c);
                if (c != 2) {
                    throw new RuntimeException("Acquiring lock failed by different thread than second.");
                }

                assertThat(e.getMessage(), containsString("org.infinispan.util.concurrent.TimeoutException"));
            }
        }));

        inComittedTransaction(s -> {
            HotRodConnectionProvider hotRodConnectionProvider = s.getProvider(HotRodConnectionProvider.class);
            RemoteCache<String, Object> realms = hotRodConnectionProvider.getRemoteCache("realms");

            LOG.infof("%s - %s", Thread.currentThread().getName(), HotRodLocks.releaseLock(realms, LOCK_NAME));
        });
    }

}
