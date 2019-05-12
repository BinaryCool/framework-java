package pers.binaryhunter.db.mybatis.datasource.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicInteger;

import javax.sql.DataSource;

/**
 * 简单实现读数据源负载均衡
 */
public class RoundRobinRWDataSourceRouter extends AbstractRWDataSourceRouter {
    private static final Logger logger = LoggerFactory.getLogger(RoundRobinRWDataSourceRouter.class);
    
	private AtomicInteger count = new AtomicInteger(0);

	@Override
	protected DataSource loadBalance() {
        int index = Math.abs(count.incrementAndGet()) % getReadDsSize();
		return getResolvedReadDataSources().get(index);
	}

}
