package com.dodosupermarket.rank;

import org.apache.ibatis.session.SqlSessionFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;

/** 启动时确认榜单 Mapper 与事务共用业务数据源，避免回滚只覆盖部分写入。 */
@Configuration
public class RankConfiguration {
    public RankConfiguration(SqlSessionFactory sessions,
                             @Qualifier("transactionManager") DataSourceTransactionManager manager) {
        if (sessions.getConfiguration().getEnvironment().getDataSource() != manager.getDataSource()) {
            throw new IllegalStateException("榜单 Mapper 与业务事务必须共用框架数据源");
        }
    }
}
