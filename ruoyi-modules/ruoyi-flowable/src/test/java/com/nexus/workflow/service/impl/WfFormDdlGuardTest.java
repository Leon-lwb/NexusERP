package com.nexus.workflow.service.impl;

import com.nexus.common.core.exception.ServiceException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 动态建表 DDL 注入防护单元测试。
 * <p>守护历史回归：表名/字段名/COMMENT/默认值原先未校验直接拼接 DDL，
 * 持有表单权限的用户可借 tableName 执行任意 SQL。此处对私有静态防护方法做反射调用，
 * 锁定白名单与转义行为。</p>
 */
class WfFormDdlGuardTest {

    private static Object invoke(String methodName, Class<?>[] paramTypes, Object... args) throws Exception {
        Method method = WfFormServiceImpl.class.getDeclaredMethod(methodName, paramTypes);
        method.setAccessible(true);
        return method.invoke(null, args);
    }

    // ==================== validateIdentifier ====================

    @Test
    @DisplayName("合法标识符：字母开头的字母数字下划线，长度不超 64")
    void acceptsLegalIdentifiers() throws Exception {
        assertEquals("form_data", invoke("validateIdentifier", new Class<?>[]{String.class}, "form_data"));
        assertEquals("BizField1", invoke("validateIdentifier", new Class<?>[]{String.class}, "BizField1"));
        assertEquals("a", invoke("validateIdentifier", new Class<?>[]{String.class}, "a"));
    }

    @Test
    @DisplayName("注入载荷被拒绝")
    void rejectsInjectionPayloads() {
        String[] payloads = {
            "x;DROP TABLE sys_user",
            "x; DROP TABLE sys_user",
            "x` UNION SELECT 1",
            "1abc",
            "",
            null,
            "a b",
            "a-b",
            "a'b",
            "中文字段"
        };
        for (String payload : payloads) {
            final String p = payload;
            assertThrows(Exception.class,
                () -> invoke("validateIdentifier", new Class<?>[]{String.class}, p),
                "应拒绝: " + p);
        }
    }

    @Test
    @DisplayName("超长标识符（MySQL 标识符上限 64）被拒绝")
    void rejectsOverlongIdentifier() {
        char[] chars = new char[65];
        Arrays.fill(chars, 'a');
        assertThrows(Exception.class,
            () -> invoke("validateIdentifier", new Class<?>[]{String.class}, new String(chars)));
    }

    // ==================== escapeLiteral ====================

    @Test
    @DisplayName("字面量转义：null 返回空串、单引号翻倍、反斜杠翻倍")
    void escapesLiterals() throws Exception {
        assertEquals("", invoke("escapeLiteral", new Class<?>[]{String.class}, (Object) null));
        assertEquals("o''brien", invoke("escapeLiteral", new Class<?>[]{String.class}, "o'brien"));
        assertEquals("a\\\\b", invoke("escapeLiteral", new Class<?>[]{String.class}, "a\\b"));
        assertEquals("normal", invoke("escapeLiteral", new Class<?>[]{String.class}, "normal"));
    }

    // ==================== buildColumnSql ====================

    @Test
    @DisplayName("字段名注入被拒绝而非拼进 DDL")
    void buildColumnRejectsInjectedFieldName() {
        assertThrows(Exception.class, () ->
            invoke("buildColumnSql",
                new Class<?>[]{String.class, String.class, Object.class, boolean.class, String.class},
                "x varchar(100)); DROP TABLE y;--", "varchar(100)", null, false, null));
    }

    @Test
    @DisplayName("label 单引号被转义后进 COMMENT")
    void buildColumnEscapesLabel() throws Exception {
        String sql = (String) invoke("buildColumnSql",
            new Class<?>[]{String.class, String.class, Object.class, boolean.class, String.class},
            "amount", "int", null, false, "金额'含引号");
        assertTrue(sql.startsWith("`amount` int"), "列定义应以反引号包裹的字段名开头: " + sql);
        assertFalse(sql.contains("'金额'含引号'".replace("''", "'")), "单引号必须已转义");
        assertTrue(sql.contains("金额''含引号"), "COMMENT 中单引号应翻倍: " + sql);
    }

    @Test
    @DisplayName("字符串默认值带转义、数值默认值仅接受纯数字")
    void buildColumnDefaultValue() throws Exception {
        String quoted = (String) invoke("buildColumnSql",
            new Class<?>[]{String.class, String.class, Object.class, boolean.class, String.class},
            "title", "varchar(100)", "it's ok", true, null);
        assertTrue(quoted.contains("DEFAULT 'it''s ok'"), quoted);

        String numeric = (String) invoke("buildColumnSql",
            new Class<?>[]{String.class, String.class, Object.class, boolean.class, String.class},
            "num", "int", 5, false, null);
        assertTrue(numeric.contains("DEFAULT 5"), numeric);

        // 数值型字段默认值非法时放弃 DEFAULT 子句（保留建表能力），而不是把载荷拼进去
        String bad = (String) invoke("buildColumnSql",
            new Class<?>[]{String.class, String.class, Object.class, boolean.class, String.class},
            "num2", "int", "5; DROP TABLE t", false, null);
        assertFalse(bad.contains("DEFAULT"), bad);
        assertFalse(bad.contains(";"), bad);
    }
}
