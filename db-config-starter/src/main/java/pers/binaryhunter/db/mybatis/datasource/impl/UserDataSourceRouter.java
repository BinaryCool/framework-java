package pers.binaryhunter.db.mybatis.datasource.impl;

import pers.binaryhunter.db.mybatis.datasource.DataSourceHolder;
import pers.binaryhunter.db.mybatis.datasource.DataSourceRouter;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;

import java.util.HashMap;
import java.util.Map;

import javax.sql.DataSource;
/**
 * 数据库路由
 */
public class UserDataSourceRouter implements DataSourceRouter, InitializingBean {
    private static final Logger logger = LoggerFactory.getLogger(UserDataSourceRouter.class);
	/**
	 * 应用启动时配置一组读写分离数据组，根据不同的用户路由到不同的数据组上。后期不在修改，属于不变模式，不考虑线程安全问题
	 */
	private Map<String, AbstractRWDataSourceRouter> userDataSource;
	
	public UserDataSourceRouter(Map<String, AbstractRWDataSourceRouter> userDataSource) {
		this.userDataSource = userDataSource;
	}

	@Override
	public DataSource getTargetDataSource() {
        String currentDataSourceName = DataSourceHolder.CURRENT_DATASOURCE.get();
        if(StringUtils.isEmpty(currentDataSourceName)) {
            currentDataSourceName = DataSourceHolder.DEFAULT;
        }
        logger.debug("Current data source name " + currentDataSourceName);
		AbstractRWDataSourceRouter currentDataSource = userDataSource.get(currentDataSourceName);
		return currentDataSource.getTargetDataSource();
	}

    @Override
    public void afterPropertiesSet() {
        for(Map.Entry<String, AbstractRWDataSourceRouter> entry : userDataSource.entrySet()) {
            entry.getValue().afterPropertiesSet();
        }
    }
}
