package pers.binaryhunter.db.mybatis.datasource.impl;

import javax.sql.DataSource;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 简单实现读数据源负载均衡
 */
public class RoundRobinRWDataSourceRouter extends AbstractRWDataSourceRouter {
	private AtomicInteger count = new AtomicInteger(0);

	@Override
	protected DataSource loadBalance() {
        int index = Math.abs(count.incrementAndGet()) % getReadDsSize();
		return getResolvedReadDataSources().get(index);
	}

}
