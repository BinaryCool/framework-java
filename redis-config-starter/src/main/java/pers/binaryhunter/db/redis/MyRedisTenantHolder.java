package pers.binaryhunter.db.redis;

import org.springframework.core.NamedThreadLocal;

public class MyRedisTenantHolder {
    /**
     * 强制写数据源
     */
    public final static ThreadLocal<String> TENANT_ID = new NamedThreadLocal<>("MY_REDIS_TENANT_HOLDER");
}
