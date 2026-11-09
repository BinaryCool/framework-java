package com.dodosupermarket.restock;

import com.dodosupermarket.support.BusinessSqlFixture;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** 每个用例只操作本机随机新建的隔离库，继承全部并发与恢复用例，不接触开发业务数据。 */
public class RestockMysqlTest extends RestockServiceTest {
    private JdbcTemplate admin;
    private String schema;

    @BeforeEach
    @Override
    public void setup() {
        String url = System.getenv("DUDU_RESTOCK_TEST_URL");
        assumeTrue(url != null, "Local MySQL tests require explicit environment");
        if (!url.matches("^jdbc:mysql://(?:127\\.0\\.0\\.1|localhost):3306/\\?.*$")) {
            throw new IllegalArgumentException("Only loopback MySQL without a database is allowed");
        }
        String username = System.getenv("DUDU_RESTOCK_TEST_USER");
        String password = System.getenv("DUDU_RESTOCK_TEST_PASSWORD");
        admin = new JdbcTemplate(new DriverManagerDataSource(url, username, password));
        schema = "dodo_restock_test_" + UUID.randomUUID().toString().replace("-", "");
        admin.execute("CREATE DATABASE " + schema + " CHARACTER SET utf8mb4");
        DriverManagerDataSource source = new DriverManagerDataSource(url.replace("/?", "/" + schema + "?"), username, password);
        jdbc = new JdbcTemplate(source);
        service = newService(source);
        fixture(jdbc);
        new ResourceDatabasePopulator(BusinessSqlFixture.restockSchema()).execute(source);
        jdbc.execute("ALTER TABLE dodo_users MODIFY is_subscribe_msg TINYINT(1), MODIFY is_open_push TINYINT(1)");
        jdbc.execute("ALTER TABLE dodo_goods MODIFY is_on_sale TINYINT(1), MODIFY is_delete TINYINT(1), MODIFY is_virtual TINYINT(1)");
    }

    @AfterEach
    public void cleanup() {
        if (admin != null && schema != null && schema.matches("dodo_restock_test_[a-f0-9]{32}")) {
            admin.execute("DROP DATABASE " + schema);
        }
    }

    @Test
    public void jdbcBooleanFlagsAreSupported() {
        assertEquals(1, RestockAvailability.number(Boolean.TRUE), 0);
        assertEquals(0, RestockAvailability.number(Boolean.FALSE), 0);
    }

    @Test
    public void migrationCreatesOnlyThreeCommentedTablesAndCanBeReapplied() {
        new ResourceDatabasePopulator(BusinessSqlFixture.restockSchema()).execute(jdbc.getDataSource());
        assertEquals(3, jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() "
                + "AND TABLE_NAME LIKE 'dodo_restock_%'", Integer.class).intValue());
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() "
                + "AND TABLE_NAME LIKE 'dodo_restock_%' AND (TABLE_COMMENT='' OR ENGINE<>'InnoDB')", Integer.class).intValue());
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() "
                + "AND TABLE_NAME LIKE 'dodo_restock_%' AND COLUMN_COMMENT=''", Integer.class).intValue());
        assertEquals("text", jdbc.queryForObject("SELECT DATA_TYPE FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() "
                + "AND TABLE_NAME='dodo_operate_log' AND COLUMN_NAME='operate_content'", String.class));
    }
}
