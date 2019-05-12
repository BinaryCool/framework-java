package pers.binaryhunter.db.mybatis.datasource;

import org.springframework.core.NamedThreadLocal;

public class DataSourceHolder {
    public static final String FOODSAFETY = "foodsafety";
    public static final String B2B = "b2b";
    
	/**
	 * 当前数据组
	 */
	public final static ThreadLocal<String> CURRENT_DATASOURCE = new NamedThreadLocal<String>("routingdatasource's key");
}
