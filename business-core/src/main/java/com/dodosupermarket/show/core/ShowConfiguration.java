package com.dodosupermarket.show.core;

import com.dodosupermarket.show.dao.ShowOrderDAO;
import com.dodosupermarket.show.service.ShowActivityLogService;
import com.dodosupermarket.show.service.ShowActivityRecordService;
import com.dodosupermarket.show.service.ShowSubmissionRecordService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.ibatis.session.SqlSessionFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;

/**
 * 晒单 CRUD 与优惠券复用框架现有 Mapper、数据源和事务管理器。
 */
@Configuration
public class ShowConfiguration {
    @Bean
    public ShowActivityService showActivityService(ShowActivityRecordService activities, ShowSubmissionRecordService submissions,
                                                   ShowActivityLogService logs, ShowOrderDAO orders, SqlSessionFactory sessions,
                                                   @Qualifier("transactionManager") DataSourceTransactionManager manager,
                                                   ObjectMapper mapper, ObjectProvider<ShowRewardIssuer> rewards,
                                                   ObjectProvider<ShowMediaStore> media, ApplicationEventPublisher events) {
        // 同一数据源对象才能让 MyBatis 加入审核事务，误配置时启动即失败。
        if (sessions.getConfiguration().getEnvironment().getDataSource() != manager.getDataSource()) {
            throw new IllegalStateException("晒单 Mapper 与审核事务必须共用框架数据源");
        }
        return new ShowActivityService(activities, submissions, logs, orders, manager, mapper, rewards, media, events);
    }
}
