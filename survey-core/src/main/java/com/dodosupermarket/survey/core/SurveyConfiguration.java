package com.dodosupermarket.survey.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

/**
 * 问卷模块 Spring 配置。
 * 复用主库写库凭证（可用 dudu.survey.jdbc-* 显式覆盖），
 * 单独建立一个小连接池，使事务行锁不受读写分离路由影响。
 */
@Configuration
public class SurveyConfiguration {
    // Reuse the existing primary database credentials, with optional explicit overrides.
    // A small dedicated pool makes transaction locks independent of replica routing.

    /**
     * 创建问卷服务 Bean：独立 Hikari 池（最多 5 连接），随 Spring 容器关闭时销毁
     */
    @Bean(destroyMethod = "close")
    public SurveyService surveyService(Environment env, ObjectMapper mapper) {
        String prefix = "spring.datasource.dynamic.default.writeDataSource.";
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(env.getProperty("dudu.survey.jdbc-url", env.getProperty(prefix + "url")));
        config.setUsername(env.getProperty("dudu.survey.jdbc-username", env.getProperty(prefix + "username")));
        config.setPassword(env.getProperty("dudu.survey.jdbc-password", env.getProperty(prefix + "password")));
        config.setMaximumPoolSize(5);
        config.setMinimumIdle(0);
        config.setPoolName("dudu-survey");
        config.setInitializationFailTimeout(-1);
        config.setConnectionTimeout(10000);
        return new SurveyService(new HikariDataSource(config), mapper);
    }
}
