package com.example.cloudpicture.ai;

import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

/**
 * 结构守卫：AI 服务不得引入数据库、MyBatis、Flyway 或 COS 依赖，
 * 避免后续有人误加依赖后悄悄写库或上传对象（对应计划书 §8.1-6）
 */
class AiServiceIsolationTest {

    @Test
    void hasNoDatabaseStorageOrOrmDependency() {
        assertAbsent("com.qcloud.cos.COSClient");
        assertAbsent("org.mybatis.spring.SqlSessionFactoryBean");
        assertAbsent("org.flywaydb.core.Flyway");
        assertAbsent("org.springframework.jdbc.core.JdbcTemplate");
    }

    private static void assertAbsent(String className) {
        assertThrows(ClassNotFoundException.class, () -> Class.forName(className),
                className + " 不应出现在 AI 服务的类路径上");
    }
}