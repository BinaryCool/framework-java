package pers.binaryhunter.db.mybatis.datasource;

import javax.sql.DataSource;
/**
 * 数据库路由
 */
public interface DataSourceRouter {
	/**
	 * 根据自己的需要，实现数据库路由，可以是读写分离的数据源，或者是分表后的数据源
	 * @return
	 */
    DataSource getTargetDataSource();

}
