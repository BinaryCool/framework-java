package com.dodosupermarket.restock;

import com.dodosupermarket.restock.dao.RestockContextDAO;
import com.dodosupermarket.restock.service.RestockNoticeService;
import com.dodosupermarket.restock.service.RestockSubscriptionService;
import com.dodosupermarket.restock.service.RestockSupplyService;
import org.apache.ibatis.session.SqlSessionFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;

@Configuration
public class RestockConfiguration {
    @Bean
    public RestockService restockService(RestockSubscriptionService subscriptions, RestockNoticeService notices,
                                         RestockSupplyService supplies, RestockContextDAO context, SqlSessionFactory sessions,
                                         @Qualifier("transactionManager") DataSourceTransactionManager manager) {
        // 与晒单一致，Mapper 必须加入同一业务主库事务，配置不一致时启动即失败。
        if (sessions.getConfiguration().getEnvironment().getDataSource() != manager.getDataSource()) {
            throw new IllegalStateException("补货提醒 Mapper 与业务事务必须共用框架数据源");
        }
        return new RestockService(subscriptions, notices, supplies, context, manager);
    }
}
