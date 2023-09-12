package pers.binaryhunter.db.mybatis.datasource;

import org.apache.commons.lang3.StringUtils;
import org.springframework.core.NamedThreadLocal;

public class DataSourceHolder {
    public static String DEFAULT = "default";
	/**
	 * 当前数据组
	 */
	public final static ThreadLocal<String> CURRENT_DATASOURCE = new NamedThreadLocal<>("routingdatasource's key");

	public static String getCurrentDataSourceName() {
		return getCurrentDataSourceName(null);
	}

	public static String getCurrentDataSourceName(String defaultDataSourceName) {
		String currentDataSourceName = DataSourceHolder.CURRENT_DATASOURCE.get();
		if(StringUtils.isEmpty(currentDataSourceName)) {
			currentDataSourceName = defaultDataSourceName;
		}
		if(StringUtils.isEmpty(currentDataSourceName)) {
			currentDataSourceName = DEFAULT;
		}
		return currentDataSourceName;
	}
}
