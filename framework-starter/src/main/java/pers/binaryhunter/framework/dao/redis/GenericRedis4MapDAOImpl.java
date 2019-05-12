package pers.binaryhunter.framework.dao.redis;

import org.springframework.data.redis.core.RedisTemplate;
import pers.binaryhunter.framework.dao.GenericNosqlDAO;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Created by BinaryHunter on 2019/3/1.
 */
public class GenericRedis4MapDAOImpl<B, K> implements GenericNosqlDAO<B, K> {
    protected static final Long TIME_OUT_IN_MIN = -1L;

    protected String beanName;

    @Resource
    protected RedisTemplate redisTemplate;

    @PostConstruct
    public void init() throws Exception {
        Class<B> beanClass = (Class<B>) ((ParameterizedType) getClass().getGenericSuperclass()).getActualTypeArguments()[0];
        this.beanName = beanClass.getName();
    }

    @Override
    public B get(K id) {
        return (B) this.redisTemplate.opsForHash().get(beanName, id.toString());
    }

    @Override
    public List<B> query() {
        return this.redisTemplate.opsForHash().values(beanName);
    }

    @Override
    public List<B> query(List<K> ids) throws Exception {
        List<String> hashKeys = new ArrayList<>();
        for (K id : ids) {
            hashKeys.add(id.toString());
        }
        return this.redisTemplate.opsForHash().multiGet(beanName, hashKeys);
    }

    @Override
    public void create(B bean) throws Exception {
        this.redisTemplate.opsForHash().put(beanName, getId(bean), bean);
        if (0 < getTimeoutInMin()) {
            this.redisTemplate.expire(beanName, getTimeoutInMin(), TimeUnit.MINUTES);
        }
    }

    @Override
    public void createBatch(List<B> beans) throws Exception {
        Map<Object, Object> map = new HashMap<>();

        for (B bean : beans) {
            map.put(getId(bean), bean);
        }

        this.redisTemplate.opsForHash().putAll(beanName, map);
        if (0 < getTimeoutInMin()) {
            this.redisTemplate.expire(beanName, getTimeoutInMin(), TimeUnit.MINUTES);
        }
    }

    @Override
    public void delete(K id) throws Exception {
        this.redisTemplate.opsForHash().delete(beanName, id.toString());
    }

    @Override
    public void deleteBatch(List<K> ids) throws Exception {
        Object[] array = new String[ids.size()];
        K id = null;
        for (int i = 0; i < ids.size(); i++) {
            id = ids.get(i);
            array[i] = id.toString();
        }
        this.redisTemplate.opsForHash().delete(beanName, array);
    }

    @Override
    public void deleteAll() throws Exception {
        this.redisTemplate.delete(beanName);
    }

    protected Object getId(B bean) throws Exception {
        Field idField = bean.getClass().getDeclaredField("id");

        idField.setAccessible(true);
        Object obj = idField.get(bean);
        return obj.toString();
    }

    /**
     * 子类可以覆盖此函数来设置默认超时时间
     *
     * @return 超时时间(分钟) 默认-1分钟
     */
    protected long getTimeoutInMin() {
        return TIME_OUT_IN_MIN;
    }
}
