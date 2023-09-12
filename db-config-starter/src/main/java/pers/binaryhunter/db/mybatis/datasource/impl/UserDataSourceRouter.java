package pers.binaryhunter.db.mybatis.datasource.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import pers.binaryhunter.db.mybatis.datasource.DataSourceHolder;
import pers.binaryhunter.db.mybatis.datasource.DataSourceRouter;

import javax.sql.DataSource;
import java.util.Map;
/**
 * 数据库路由
 */
public class UserDataSourceRouter implements DataSourceRouter, InitializingBean {
    private static final Logger log = LoggerFactory.getLogger(UserDataSourceRouter.class);
	/**
	 * 应用启动时配置一组读写分离数据组，根据不同的用户路由到不同的数据组上。后期不在修改，属于不变模式，不考虑线程安全问题
	 */
	private Map<String, AbstractRWDataSourceRouter> userDataSource;
    /**
     * 主数据源名称, 一般是配置文件第一个数据源
     */
    private final String mainDataSourceName;

	public UserDataSourceRouter(Map<String, AbstractRWDataSourceRouter> userDataSource, String mainDataSourceName) {
		this.userDataSource = userDataSource;
        this.mainDataSourceName = mainDataSourceName;
	}

	@Override
	public DataSource getTargetDataSource() {
        String currentDataSourceName = DataSourceHolder.getCurrentDataSourceName(this.mainDataSourceName);
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
