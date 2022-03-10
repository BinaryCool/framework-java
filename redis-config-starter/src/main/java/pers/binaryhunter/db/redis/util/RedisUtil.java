package pers.binaryhunter.db.redis.util;

import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.transaction.support.TransactionSynchronizationAdapter;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;

public class RedisUtil {
    private static final Logger log = LoggerFactory.getLogger(RedisUtil.class);

    /**
     * 分布式锁 (未设置等待时间和过期时间)
     * 有可能会锁死系统
     */
    public static RLock lock(RedissonClient redisson, String key) {
        RLock lock = redisson.getLock(key);
        boolean res = lock.tryLock();
        if (res) {
            return lock;
        }
        return null;
    }

    /**
     * 分布式锁
     * 加锁最大等待 15s
     * 获取锁 30s 后自动释放
     */
    public static RLock tryLock(RedissonClient redisson, String key) throws InterruptedException {
        return tryLock(redisson, key, 30);
    }

    /**
     * 分布式锁
     * 加锁最大等待14s
     * 获取锁 maxLockSeconds 后自动释放
     */
    public static RLock tryLock(RedissonClient redisson, String key, int maxLockSeconds) throws InterruptedException {
        return tryLock(redisson, key, maxLockSeconds, 15);
    }

    /**
     * 分布式锁
     * 加锁最大等待 maxWaitSeconds
     * 获取锁 maxLockSeconds 后自动释放
     */
    public static RLock tryLock(RedissonClient redisson, String key, int maxLockSeconds, int maxWaitSeconds) throws InterruptedException {
        RLock lock = redisson.getLock(key);
        boolean res = lock.tryLock(maxWaitSeconds, maxLockSeconds, TimeUnit.SECONDS);
        if (res) {
            return lock;
        }
        return null;
    }

    /**
     * 解开分布式锁
     * 如果有事务, 会等到当前事务提交才会释放锁
     * 如果无事务, 会立即是否
     */
    public static void unlock(RLock lock) {
        try {
            if (null != lock) {
                // 如果存在事务
                if (TransactionSynchronizationManager.isSynchronizationActive()) {
                    //事物完成后释放锁
                    TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronizationAdapter() {
                        @Override
                        public void afterCompletion(int status) {
                            super.afterCompletion(status);
                            lock.unlock();
                        }
                    });
                } else {
                    lock.unlock();
                }
            }
        } catch (Exception ex) {
            log.warn("", ex);
        }
    }

    /**
     * scan 实现 删除keys
     * @param keys     表达式，如：abc*，找出所有以abc开始的键
     */
    public static void deleteKeys(RedisTemplate redisTemplate, String keys) {
        try {
            Set<String> keySet = keysByScan(redisTemplate, keys);
            redisTemplate.delete(keySet);
        } catch (Exception ex) {
            log.error("", ex);
        }
    }

    /**
     * scan 实现 查找keys
     * @param pattern       表达式，如：abc*，找出所有以abc开始的键
     */
    public static Set<String> keysByScan(RedisTemplate<String, Object> redisTemplate, String pattern) {
        return redisTemplate.execute((RedisCallback<Set<String>>) connection -> {
            Set<String> keysTmp = new HashSet<>();
            try {
                Cursor<byte[]> cursor = connection.scan(new ScanOptions.ScanOptionsBuilder()
                        .match(pattern)
                        .count(10000).build());

                while (cursor.hasNext()) {
                    keysTmp.add(new String(cursor.next(), StandardCharsets.UTF_8));
                }
            } catch (Exception e) {
                log.error("", e);
            }
            return keysTmp;
        });
    }

    /**
     * 锁住 process 流程
     */
    public static void lock(RedissonClient redisson, String redisKeyLock, LockProcess process) {
        RLock lock = null;
        try {
            lock = tryLock(redisson, redisKeyLock);
            process.run();
        } catch (InterruptedException e) {
            log.error("", e);
        } finally {
            unlock(lock);
        }
    }

    public interface LockProcess {
        void run();
    }
}
