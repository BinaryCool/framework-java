package com.dodosupermarket.support;

import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 测试直接从统一业务 SQL 提取所属业务的 DDL，避免再维护一份旧建表文件。 */
public final class BusinessSqlFixture {
    private BusinessSqlFixture() {
    }

    public static String createTables(String tablePrefix) {
        StringBuilder ddl = new StringBuilder();
        Matcher tables = Pattern.compile("(?s)CREATE TABLE IF NOT EXISTS (\\w+) \\(.*?;+")
                .matcher(source());
        while (tables.find()) {
            if (tables.group(1).startsWith(tablePrefix)) {
                ddl.append(tables.group()).append('\n');
            }
        }
        if (ddl.length() == 0) {
            throw new IllegalArgumentException("统一业务 SQL 中不存在指定业务表：" + tablePrefix);
        }
        return ddl.toString();
    }

    public static Resource restockSchema() {
        // H2 不执行 MySQL 的 PREPARE 包装；提取同一条原始扩容语句，保证测试仍覆盖日志字段变更。
        Matcher alteration = Pattern.compile("'((?:ALTER TABLE dodo_operate_log)(?:''|[^'])*)'")
                .matcher(source());
        if (!alteration.find()) {
            throw new IllegalStateException("统一业务 SQL 缺少操作日志扩容语句");
        }
        String ddl = alteration.group(1).replace("''", "'") + ";\n" + createTables("dodo_restock_");
        return new ByteArrayResource(ddl.getBytes(StandardCharsets.UTF_8));
    }

    private static String source() {
        try (InputStream input = BusinessSqlFixture.class.getResourceAsStream("/db/test/01-business.sql")) {
            return new String(Objects.requireNonNull(input, "统一业务 SQL 未打包").readAllBytes(),
                    StandardCharsets.UTF_8);
        } catch (IOException error) {
            throw new IllegalStateException("统一业务 SQL 读取失败", error);
        }
    }
}
