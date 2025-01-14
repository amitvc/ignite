/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.ignite.internal.processors.cache;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.cache.Cache;
import javax.cache.configuration.Factory;
import javax.cache.integration.CacheLoader;
import javax.cache.integration.CacheWriter;
import org.apache.ignite.IgniteCache;
import org.apache.ignite.cache.CacheAtomicityMode;
import org.apache.ignite.cache.CacheMode;
import org.apache.ignite.cache.CacheWriteSynchronizationMode;
import org.apache.ignite.cache.store.CacheStore;
import org.apache.ignite.cache.store.CacheStoreAdapter;
import org.apache.ignite.configuration.CacheConfiguration;
import org.apache.ignite.configuration.IgniteConfiguration;
import org.apache.ignite.configuration.NearCacheConfiguration;
import org.apache.ignite.lang.IgniteBiInClosure;
import org.apache.ignite.spi.discovery.tcp.TcpDiscoverySpi;
import org.apache.ignite.spi.eventstorage.memory.MemoryEventStorageSpi;
import org.apache.ignite.testframework.junits.common.GridCommonAbstractTest;

import static org.apache.ignite.cache.CacheMode.PARTITIONED;
import static org.apache.ignite.cache.CacheWriteSynchronizationMode.FULL_SYNC;

/**
 * Abstract class for cache tests.
 */
public abstract class IgniteCacheAbstractTest extends GridCommonAbstractTest {
    /** */
    public static final Map<Object, Object> storeMap = new ConcurrentHashMap<>();

    /**
     * @return Grids count to start.
     */
    protected abstract int gridCount();

    /** {@inheritDoc} */
    @Override protected void beforeTestsStarted() throws Exception {
        startGrids();
    }

    /**
     * @throws Exception If failed.
     */
    protected void startGrids() throws Exception {
        int cnt = gridCount();

        assert cnt >= 1 : "At least one grid must be started";

        startGridsMultiThreaded(cnt);

        awaitPartitionMapExchange();
    }

    /** {@inheritDoc} */
    @Override protected void afterTestsStopped() throws Exception {
        storeMap.clear();
    }

    /** {@inheritDoc} */
    @Override protected IgniteConfiguration getConfiguration(String igniteInstanceName) throws Exception {
        IgniteConfiguration cfg = super.getConfiguration(igniteInstanceName);

        TcpDiscoverySpi disco = (TcpDiscoverySpi)cfg.getDiscoverySpi();

        if (isDebug())
            disco.setAckTimeout(Integer.MAX_VALUE);

        MemoryEventStorageSpi evtSpi = new MemoryEventStorageSpi();
        evtSpi.setExpireCount(100);

        cfg.setEventStorageSpi(evtSpi);

        cfg.setDiscoverySpi(disco);

        cfg.setCacheConfiguration(cacheConfiguration(igniteInstanceName));

        return cfg;
    }

    /**
     * @param igniteInstanceName Ignite instance name.
     * @return Cache configuration.
     * @throws Exception In case of error.
     */
    @SuppressWarnings("unchecked")
    protected CacheConfiguration cacheConfiguration(String igniteInstanceName) throws Exception {
        CacheConfiguration cfg = defaultCacheConfiguration();

        cfg.setCacheMode(cacheMode());
        cfg.setAtomicityMode(atomicityMode());

        cfg.setWriteSynchronizationMode(writeSynchronization());
        cfg.setNearConfiguration(nearConfiguration());

        cfg.setCacheLoaderFactory(loaderFactory());

        if (cfg.getCacheLoaderFactory() != null)
            cfg.setReadThrough(true);

        cfg.setCacheWriterFactory(writerFactory());

        if (cfg.getCacheWriterFactory() != null)
            cfg.setWriteThrough(true);

        Factory<CacheStore> storeFactory = cacheStoreFactory();

        if (storeFactory != null) {
            cfg.setCacheStoreFactory(storeFactory);
            cfg.setReadThrough(true);
            cfg.setWriteThrough(true);
            cfg.setLoadPreviousValue(true);

            cfg.setWriteBehindEnabled(writeBehindEnabled());
            cfg.setWriteBehindCoalescing(writeBehindCoalescing());
        }

        if (cacheMode() == PARTITIONED)
            cfg.setBackups(1);

        cfg.setOnheapCacheEnabled(onheapCacheEnabled());

        return cfg;
    }

    /**
     * @return Cache store.
     */
    protected Factory<CacheStore> cacheStoreFactory() {
        return null;
    }

    /**
     * @return write behind enabled flag.
     */
    protected boolean writeBehindEnabled() {
        return false;
    }

    /**
     * @return write behind coalescing flag.
     */
    protected boolean writeBehindCoalescing() {
        return true;
    }

    /**
     * @return Cache loader factory.
     */
    protected Factory<? extends CacheLoader> loaderFactory() {
        return null;
    }

    /**
     * @return Cache writer factory.
     */
    protected Factory<? extends CacheWriter> writerFactory() {
        return null;
    }

    /**
     * @return Default cache mode.
     */
    protected abstract CacheMode cacheMode();

    /**
     * @return Cache atomicity mode.
     */
    protected abstract CacheAtomicityMode atomicityMode();

    /**
     * @return Partitioned mode.
     */
    protected abstract NearCacheConfiguration nearConfiguration();

    /**
     * @return Write synchronization.
     */
    protected CacheWriteSynchronizationMode writeSynchronization() {
        return FULL_SYNC;
    }

    /**
     * @return {@code True} if on-heap cache is enabled.
     */
    protected boolean onheapCacheEnabled() {
        return false;
    }

    /**
     * @return Cache.
     */
    @Override protected <K, V> IgniteCache<K, V> jcache() {
        return jcache(0);
    }

    /**
     * @param idx Grid index.
     * @return Cache.
     */
    @Override protected <K, V> IgniteCache<K, V> jcache(int idx) {
        return grid(idx).cache(DEFAULT_CACHE_NAME);
    }

    /**
     *
     */
    public static class TestStoreFactory implements Factory<CacheStore> {
        /** {@inheritDoc} */
        @Override public CacheStore create() {
            return new TestStore();
        }
    }

    /**
     *
     */
    public static class TestStore extends CacheStoreAdapter<Object, Object> {
        /** {@inheritDoc} */
        @Override public void loadCache(IgniteBiInClosure<Object, Object> clo, Object... args) {
            for (Map.Entry<Object, Object> e : storeMap.entrySet())
                clo.apply(e.getKey(), e.getValue());
        }

        /** {@inheritDoc} */
        @Override public Object load(Object key) {
            return storeMap.get(key);
        }

        /** {@inheritDoc} */
        @Override public void write(Cache.Entry<? extends Object, ? extends Object> entry) {
            storeMap.put(entry.getKey(), entry.getValue());
        }

        /** {@inheritDoc} */
        @Override public void delete(Object key) {
            storeMap.remove(key);
        }
    }
}
