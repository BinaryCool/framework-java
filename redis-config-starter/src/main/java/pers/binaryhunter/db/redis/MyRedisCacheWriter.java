package pers.binaryhunter.db.redis;

import com.alibaba.fastjson.JSON;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.support.NullValue;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheWriter;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStringCommands.SetOption;
import org.springframework.data.redis.core.types.Expiration;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.ObjectUtils;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Collections;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * {@link RedisCacheWriter} implementation capable of reading/writing binary data from/to Redis in {@literal standalone}
 * and {@literal cluster} environments. Works upon a given {@link RedisConnectionFactory} to obtain the actual
 * {@link RedisConnection}. <br />
 * {@link org.springframework.data.redis.cache.DefaultRedisCacheWriter} can be used in
 * {@link RedisCacheWriter#lockingRedisCacheWriter(RedisConnectionFactory) locking} or
 * {@link RedisCacheWriter#nonLockingRedisCacheWriter(RedisConnectionFactory) non-locking} mode. While
 * {@literal non-locking} aims for maximum performance it may result in overlapping, non atomic, command execution for
 * operations spanning multiple Redis interactions like {@code putIfAbsent}. The {@literal locking} counterpart prevents
 * command overlap by setting an explicit lock key and checking against presence of this key which leads to additional
 * requests and potential command wait times.
 *
 * @author Christoph Strobl
 * @author Mark Paluch
 * @since 2.0
 */

public class MyRedisCacheWriter implements RedisCacheWriter {
    private static final byte[] BINARY_NULL_VALUE = RedisSerializer.java().serialize(NullValue.INSTANCE);
    private static final Logger log = LoggerFactory.getLogger(MyRedisCacheWriter.class);

    private final RedisCacheConfiguration cacheConfig;
    private final RedisConnectionFactory connectionFactory;
    private final Duration sleepTime;

    /**
     * @param connectionFactory must not be {@literal null}.
     */
    MyRedisCacheWriter(RedisConnectionFactory connectionFactory, RedisCacheConfiguration cacheConfig) {
        this(connectionFactory, Duration.ZERO, cacheConfig);
    }

    /**
     * @param connectionFactory must not be {@literal null}.
     * @param sleepTime sleep time between lock request attempts. Must not be {@literal null}. Use {@link Duration#ZERO}
     *          to disable locking.
     */
    MyRedisCacheWriter(RedisConnectionFactory connectionFactory, Duration sleepTime, RedisCacheConfiguration cacheConfig) {

        Assert.notNull(connectionFactory, "ConnectionFactory must not be null!");
        Assert.notNull(sleepTime, "SleepTime must not be null!");

        this.connectionFactory = connectionFactory;
        this.sleepTime = sleepTime;
        this.cacheConfig = cacheConfig;
    }

    /*
     * (non-Javadoc)
     * @see org.springframework.data.redis.cache.RedisCacheWriter#put(java.lang.String, byte[], byte[], java.time.Duration)
     */
    @Override
    public void put(String name, byte[] key, byte[] value, @Nullable Duration ttl) {

        Assert.notNull(name, "Name must not be null!");
        Assert.notNull(key, "Key must not be null!");
        Assert.notNull(value, "Value must not be null!");
        
        // 如果是空值, 不进行缓存
        if (isNullCacheValue(value)) {
            return;
        }

        execute(name, connection -> {
            //判断name里面是否设置了过期时间，如果设置了则对key进行缓存，并设置过期时间
            //name 以 ttl + 过期时间 + # 开头
            Long ttlManual = null;
            try {
                if (name.contains("#ttl")) {
                    ttlManual = Long.parseLong(name.substring(name.indexOf("#ttl") + 4));
                }
            } catch (Exception ex) {
                
            }
            
            if(null != ttlManual) {
                connection.set(key, value, Expiration.from(ttlManual, TimeUnit.SECONDS), SetOption.upsert());
            } else if (shouldExpireWithin(ttl)) {
                long ttlIinMillis = ttl.toMillis();
                ttlIinMillis += ThreadLocalRandom.current().nextLong(1800000);
                connection.set(key, value, Expiration.from(ttlIinMillis, TimeUnit.MILLISECONDS), SetOption.upsert());
            } else {
                connection.set(key, value);
            }

            return "OK";
        });
    }

    /*
     * (non-Javadoc)
     * @see org.springframework.data.redis.cache.RedisCacheWriter#get(java.lang.String, byte[])
     */
    @Override
    public byte[] get(String name, byte[] key) {

        Assert.notNull(name, "Name must not be null!");
        Assert.notNull(key, "Key must not be null!");

        return execute(name, connection -> connection.get(key));
    }

    /*
     * (non-Javadoc)
     * @see org.springframework.data.redis.cache.RedisCacheWriter#putIfAbsent(java.lang.String, byte[], byte[], java.time.Duration)
     */
    @Override
    public byte[] putIfAbsent(String name, byte[] key, byte[] value, @Nullable Duration ttl) {

        Assert.notNull(name, "Name must not be null!");
        Assert.notNull(key, "Key must not be null!");
        Assert.notNull(value, "Value must not be null!");

        // 如果是控制, 不进行缓存
        if (isNullCacheValue(value)) {
            return null;
        }

        return execute(name, connection -> {

            if (isLockingCacheWriter()) {
                doLock(name, connection);
            }

            try {
                if (connection.setNX(key, value)) {

                    if (shouldExpireWithin(ttl)) {
                        connection.pExpire(key, ttl.toMillis());
                    }
                    return null;
                }

                return connection.get(key);
            } finally {

                if (isLockingCacheWriter()) {
                    doUnlock(name, connection);
                }
            }
        });
    }

    /*
     * (non-Javadoc)
     * @see org.springframework.data.redis.cache.RedisCacheWriter#remove(java.lang.String, byte[])
     */
    @Override
    public void remove(String name, byte[] key) {

        Assert.notNull(name, "Name must not be null!");
        Assert.notNull(key, "Key must not be null!");

        execute(name, connection -> connection.del(key));
    }

    /*
     * (non-Javadoc)
     * @see org.springframework.data.redis.cache.RedisCacheWriter#clean(java.lang.String, byte[])
     */
    @Override
    public void clean(String name, byte[] pattern) {

        Assert.notNull(name, "Name must not be null!");
        Assert.notNull(pattern, "Pattern must not be null!");

        execute(name, connection -> {

            boolean wasLocked = false;

            try {

                if (isLockingCacheWriter()) {
                    doLock(name, connection);
                    wasLocked = true;
                }

                byte[][] keys = Optional.ofNullable(connection.keys(pattern)).orElse(Collections.emptySet())
                        .toArray(new byte[0][]);

                if (keys.length > 0) {
                    connection.del(keys);
                }
            } finally {

                if (wasLocked && isLockingCacheWriter()) {
                    doUnlock(name, connection);
                }
            }

            return "OK";
        });
    }

    /**
     * Explicitly set a write lock on a cache.
     *
     * @param name the name of the cache to lock.
     */
    void lock(String name) {
        execute(name, connection -> doLock(name, connection));
    }

    /**
     * Explicitly remove a write lock from a cache.
     *
     * @param name the name of the cache to unlock.
     */
    void unlock(String name) {
        executeLockFree(connection -> doUnlock(name, connection));
    }

    private Boolean doLock(String name, RedisConnection connection) {
        return connection.setNX(createCacheLockKey(name), new byte[0]);
    }

    private Long doUnlock(String name, RedisConnection connection) {
        return connection.del(createCacheLockKey(name));
    }

    boolean doCheckLock(String name, RedisConnection connection) {
        return connection.exists(createCacheLockKey(name));
    }

    /**
     * @return {@literal true} if {@link RedisCacheWriter} uses locks.
     */
    private boolean isLockingCacheWriter() {
        return !sleepTime.isZero() && !sleepTime.isNegative();
    }

    private <T> T execute(String name, Function<RedisConnection, T> callback) {
        RedisConnection connection = null;
        try {
            connection = connectionFactory.getConnection();
            checkAndPotentiallyWaitUntilUnlocked(name, connection);
            return callback.apply(connection);
        } catch (Exception ex) {
            log.warn("", ex);
        } finally {
            if (null != connection) {
                connection.close();
            }
        }
        return null;
    }

    private void executeLockFree(Consumer<RedisConnection> callback) {
        RedisConnection connection = null;
        try {
            connection = connectionFactory.getConnection();
            callback.accept(connection);
        } catch (Exception ex) {
            log.warn("", ex);
        } finally {
            if (null != connection) {
                connection.close();
            }
        }
    }

    private void checkAndPotentiallyWaitUntilUnlocked(String name, RedisConnection connection) {

        if (!isLockingCacheWriter()) {
            return;
        }

        try {

            while (doCheckLock(name, connection)) {
                Thread.sleep(sleepTime.toMillis());
            }
        } catch (InterruptedException ex) {

            // Re-interrupt current thread, to allow other participants to react.
            Thread.currentThread().interrupt();

            throw new PessimisticLockingFailureException(String.format("Interrupted while waiting to unlock cache %s", name),
                    ex);
        }
    }

    private static boolean shouldExpireWithin(@Nullable Duration ttl) {
        return ttl != null && !ttl.isZero() && !ttl.isNegative();
    }

    private static byte[] createCacheLockKey(String name) {
        return (name + "~lock").getBytes(StandardCharsets.UTF_8);
    }

    protected boolean isNullCacheValue(byte[] value) {

        if (ObjectUtils.nullSafeEquals(value, BINARY_NULL_VALUE)) {
            return true;
        }

        Object obj = cacheConfig.getValueSerializationPair().read(ByteBuffer.wrap(value));
        if (null == obj) {
            return true;
        }
        String json = JSON.toJSONString(obj);
        return null == json || "".equals(json) || "\"\"".equals(json) || "[]".equals(json) || "{}".equals(json);
    }
}