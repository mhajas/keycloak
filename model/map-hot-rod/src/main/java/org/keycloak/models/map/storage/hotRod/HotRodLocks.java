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

package org.keycloak.models.map.storage.hotRod;

import org.infinispan.client.hotrod.RemoteCache;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class HotRodLocks {

    /**
     * Executes a script that acquires a lock in the Infinispan server
     *
     * @param cache Cache that will be used for locking
     * @param lockName Name of the lock
     * @param timeout number of seconds to wait for the lock to be acquired
     *
     * @return result of the locking script (Currently "locked" if the lock was successful)
     * @throws org.infinispan.client.hotrod.exceptions.HotRodClientException if lock was unsuccessful
     */
    public static Object acquireLock(RemoteCache<String, ?> cache, String lockName, int timeout) {
        return cache.execute("locking.js", mapParams("lock", lockName, TimeUnit.SECONDS.toMillis(timeout)));
    }

    /**
     * Executes the script
     *
     * NOTE: this will unlock only if the same thread locked the lock as Infinispan is checking lockOwner, currently it is the Thread name but can be changed to something else
     *
     * @param cache Cache that will be used for unlocking
     * @param lockName Name of the lock that will be unlocked
     * @return result of locking script (Currently "unlocked")
     */
    public static Object releaseLock(RemoteCache<String, ?> cache, String lockName) {
        return cache.execute("locking.js", mapParams("unlock", lockName, null));
    }

    private static Map<String, Object> mapParams(String op, String lockName, Long timeout) {
        HashMap<String, Object> ret = new HashMap<>();
        ret.put("op", op);
        ret.put("lockName", lockName);
        ret.put("lockOwner", Thread.currentThread().getName());
        ret.put("timeout", timeout);
        return  ret;
    }
}
