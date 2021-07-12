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

    public static RLock tryLock(RedissonClient redisson, String key) throws InterruptedException {
        return tryLock(redisson, key, 30);
    }

    public static RLock tryLock(RedissonClient redisson, String key, int maxLockSeconds) throws InterruptedException {
        return tryLock(redisson, key, maxLockSeconds, 15);
    }

    public static RLock tryLock(RedissonClient redisson, String key, int maxLockSeconds, int maxWaitSeconds) throws InterruptedException {
        log.info("Try lock {} ...", key);
        RLock lock = redisson.getFairLock(key);
        boolean res = lock.tryLock(maxWaitSeconds, maxLockSeconds, TimeUnit.SECONDS);
        if (res) {
            log.info("Lock {} success", key);
            return lock;
        }
        return null;
    }

    public static void unlock(RLock lock) {
        try {
            log.info("Try unlock {} ...", lock.getName());
            if (null != lock) {
                // 如果存在事务
                if (TransactionSynchronizationManager.isSynchronizationActive()) {
                    //事物完成后释放锁
                    TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronizationAdapter() {
                        @Override
                        public void afterCompletion(int status) {
                            super.afterCompletion(status);
                            lock.unlock();
                            log.info("Unlock {} success", lock.getName());
                        }
                    });
                } else {
                    log.info("Unlock {} success", lock.getName());
                    lock.unlock();
                }
            }
        } catch (Exception ex) {
            log.warn("", ex);
        }
    }

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
     * @param redisTemplate redisTemplate
     * @param pattern       表达式，如：abc*，找出所有以abc开始的键
     */
    private static Set<String> keysByScan(RedisTemplate<String, Object> redisTemplate, String pattern) {
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
}
