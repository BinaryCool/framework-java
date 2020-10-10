package pers.binaryhunter.db.redis.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;

import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class RedisUtil {
    private static final Logger log = LoggerFactory.getLogger(RedisUtil.class);
    public static final long TIMEOUT_CACHE_IN_SECOND = 10L;
    private static RedisScript<Long> scriptDel;
    static {
        String scriptStr = "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end";
        scriptDel = new DefaultRedisScript<>(scriptStr, Long.class);
    }

    public static void tryLock(RedisTemplate redisTemplate, String key, String requestId) {
        try {
            Boolean result = redisTemplate.opsForValue().setIfAbsent(key, requestId);
            redisTemplate.expire(key, TIMEOUT_CACHE_IN_SECOND, TimeUnit.SECONDS);

            if (null == result || !result) {
                log.info("Waiting " + key);
                Thread.sleep(200L);
                tryLock(redisTemplate, key, requestId);
            }
        } catch (Exception ex) {
            log.info("", ex);
        }
    }

    public static void unlock(RedisTemplate redisTemplate, String key, String requestId) {
        try {
            redisTemplate.execute(scriptDel, Stream.of(key).collect(Collectors.toList()), requestId);
        } catch (Exception ex) {
            log.info("", ex);
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
