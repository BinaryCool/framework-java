/**
 * GenericServiceImpl.java
 * Create by Liyw -- 2014-5-22
 */
package pers.binaryhunter.framework.dao.redis;

import org.springframework.data.redis.core.RedisTemplate;
import pers.binaryhunter.framework.dao.GenericNosqlDAO;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;


/**
 * DAO泛型实现类
 * @author Liyw -- 2019-2-18
 */
public class GenericRedisDAOImpl<B,K> implements GenericNosqlDAO<B,K> {
    protected static final Long TIME_OUT_IN_MIN = 30L;
    
    protected String beanName;
    
    @Resource
    protected RedisTemplate redisTemplate;
    
    @PostConstruct
    public void init() throws Exception {
        Class<B> beanClass = (Class<B>) ((ParameterizedType) getClass().getGenericSuperclass()).getActualTypeArguments()[0];
        this.beanName = beanClass.getName() + ":";
    }

    @Override
    public B get(K id) {
        return (B) this.redisTemplate.opsForValue().get(beanName + id);
    }

    @Override
    public List<B> query() {
        Set<String> keys = this.redisTemplate.keys(this.beanName + "*");
        return this.redisTemplate.opsForValue().multiGet(keys);
    }

    @Override
    public List<B> query(List<K> ids) throws Exception {
        if(null == ids && 0 >= ids.size()) {
            return null;
        }
        Set<String> keys = ids.stream().map(key -> this.beanName + key).collect(Collectors.toSet());
        return this.redisTemplate.opsForValue().multiGet(keys);
    }

    @Override
    public void create(B bean) throws Exception {
        String key = beanName + getId(bean);
        this.redisTemplate.opsForValue().set(key,  bean);
        if (0 < getTimeoutInMin()) {
            this.redisTemplate.expire(key, getTimeoutInMin(), TimeUnit.MINUTES);
        }
    }
    @Override
    public void createBatch(List<B> beans) throws Exception  {
        if(null == beans && 0 >= beans.size()) {
            return;
        }
        
        Map<String, B> map = new HashMap<>(); 
        for(B bean : beans) {
            map.put(this.beanName + getId(bean), bean);    
        }
        this.redisTemplate.opsForValue().multiSet(map);
        if (0 < getTimeoutInMin()) {
            for (String key : map.keySet()) {
                this.redisTemplate.expire(key, getTimeoutInMin(), TimeUnit.MINUTES);
            }
        }
    }

    @Override
    public void delete(K id) throws Exception {
        this.redisTemplate.delete(this.beanName + id);
    }

    @Override
    public void deleteBatch(List<K> ids) throws Exception {
        if(null == ids && 0 >= ids.size()) {
            return;
        }
        Set<String> keys = ids.stream().map(key -> this.beanName + key).collect(Collectors.toSet());
        this.redisTemplate.delete(keys);
    }
    
    @Override
    public void deleteAll() throws Exception {
        Set<String> keys = this.redisTemplate.keys(this.beanName + "*");
        this.redisTemplate.delete(keys);
    }

    protected Object getId(B bean) throws Exception {
        Field idField = bean.getClass().getDeclaredField("id");
        
        idField.setAccessible(true);
        Object obj = idField.get(bean);
        return obj.toString();
    }

    /**
     * 子类可以覆盖此函数来设置默认超时时间
     * @return 超时时间(分钟) 默认30分钟
     */
    protected long getTimeoutInMin() {
        return TIME_OUT_IN_MIN;
    }
}
