package com.nexus.test;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 断言单元测试案例：同时验证成功条件和故意失败时的断言异常，不让示例破坏全量回归。
 *
 * @author Lion Li
 */
@DisplayName("断言单元测试案例")
public class AssertUnitTest {

    @DisplayName("测试 assertEquals 方法")
    @Test
    public void testAssertEquals() {
        Assertions.assertEquals("666", new String("666"));
        Assertions.assertThrows(AssertionError.class, () -> Assertions.assertNotEquals("666", new String("666")));
    }

    @DisplayName("测试 assertSame 方法")
    @Test
    public void testAssertSame() {
        Object obj = new Object();
        Object obj1 = obj;
        Assertions.assertSame(obj, obj1);
        Assertions.assertThrows(AssertionError.class, () -> Assertions.assertNotSame(obj, obj1));
    }

    @DisplayName("测试 assertTrue 方法")
    @Test
    public void testAssertTrue() {
        Assertions.assertTrue(true);
        Assertions.assertThrows(AssertionError.class, () -> Assertions.assertFalse(true));
    }

    @DisplayName("测试 assertNull 方法")
    @Test
    public void testAssertNull() {
        Assertions.assertNull(null);
        Assertions.assertThrows(AssertionError.class, () -> Assertions.assertNotNull(null));
    }

}
