package pers.binaryhunter.db.mybatis.datasource;

import org.springframework.core.NamedThreadLocal;

public class DataSourceHolder {
    public static final String DEFAULT = "default";
	/**
	 * 当前数据组
	 */
	public final static ThreadLocal<String> CURRENT_DATASOURCE = new NamedThreadLocal<>("routingdatasource's key");
}
