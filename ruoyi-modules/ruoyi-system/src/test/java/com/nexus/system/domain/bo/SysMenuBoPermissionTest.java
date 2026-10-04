package com.nexus.system.domain.bo;

import com.nexus.common.core.constant.RegexConstants;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import jakarta.validation.constraints.Pattern;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 离线验证菜单 API 实际使用的权限约束，不启动 Spring、服务或数据库。
 */
class SysMenuBoPermissionTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void closeValidator() {
        factory.close();
    }

    @ParameterizedTest(name = "允许权限 [{0}]")
    @NullAndEmptySource
    @ValueSource(strings = {
        "system:user:list", "tool:build:list", "pu:order:terms:add", "pu:order:terms:confirm",
        "pu:order:terms:review:confirm", "A1_b:C2_d:E3_f", "1:2:3",
        "system:*:*", "system:user:*", "system:u*er:li*t", "pu:order:terms:*", "pu:*:terms:*"
    })
    void acceptsOptionalAndHierarchicalPermissions(String permission) {
        assertTrue(validator.validateValue(SysMenuBo.class, "perms", permission).isEmpty(),
            "实际菜单 BO 应允许可选值及至少三段权限");
        assertTrue(permission == null || java.util.regex.Pattern.matches(RegexConstants.PERMISSION_STRING, permission),
            "共享规则应保留相同边界");
    }

    @ParameterizedTest(name = "拒绝权限 [{0}]")
    @ValueSource(strings = {
        "system", "system:user", "*", "*:*:*", "sys*tem:user:list",
        ":user:list", "system::list", "system:user:", "system:user:list:",
        "system:user::list", "system:user:terms::add", "system:user:terms:add:",
        " system:user:list", "system:user:list ", "system:us er:list", " ",
        "\t", "\n", "\r", "\r\n", "system:user:list\n", "system:user:list\r\n",
        "\nsystem:user:list", "system:user:li\nst", "system:user:li\tst",
        "system:user:list\u2028", "system:user:list\u2029", "system:user:list\u00a0",
        "system:user:list\u200b", "system:user:li-st", "system:user:li.st", "system:user:list/add",
        "system:user:list,edit", "system:user:list;edit", "system:user:权限", "system：user：list"
    })
    void rejectsMalformedPermissions(String permission) {
        assertFalse(java.util.regex.Pattern.matches(RegexConstants.PERMISSION_STRING, permission),
            "共享规则不应接受少于三段、空段或非法字符");
        assertFalse(validator.validateValue(SysMenuBo.class, "perms", permission).isEmpty(),
            "实际菜单 BO 必须拒绝非法权限");
    }

    @Test
    void menuAnnotationUsesSharedPermissionRule() throws NoSuchFieldException {
        assertEquals(RegexConstants.PERMISSION_STRING,
            SysMenuBo.class.getDeclaredField("perms").getAnnotation(Pattern.class).regexp());
    }

    @Test
    void preservesExistingMenuPermissionLengthLimit() {
        String permission = "system:user:" + "a".repeat(88);
        assertEquals(100, permission.length());
        assertTrue(validator.validateValue(SysMenuBo.class, "perms", permission).isEmpty());
        assertFalse(validator.validateValue(SysMenuBo.class, "perms", permission + "a").isEmpty());
    }
}
