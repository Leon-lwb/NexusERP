package com.nexus.erp.so;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.nexus.erp.so.controller.SoContractController;
import com.nexus.erp.so.controller.SoQuotationController;
import com.nexus.erp.so.controller.SoReturnController;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * so 独立权限点单元测试（ERP-SO-07）
 * <p>修复前：退货确认/执行、报价确认/转单、合同终止全部复用 {@code :edit}，无法分权。
 * 断言：这些端点的权限码必须是独立权限点（同时由增量脚本 §3 补 sys_menu 种子）。</p>
 *
 * @author Nexus
 */
@Tag("dev")
@Tag("local")
class SoPermissionCodeTest {

    private String permsOf(Class<?> controller, String methodName) throws Exception {
        Method method = null;
        for (Method m : controller.getDeclaredMethods()) {
            if (m.getName().equals(methodName)) {
                method = m;
                break;
            }
        }
        assertNotNull(method, controller.getSimpleName() + "." + methodName + " 不存在");
        SaCheckPermission permission = method.getAnnotation(SaCheckPermission.class);
        assertNotNull(permission, controller.getSimpleName() + "." + methodName + " 缺少 @SaCheckPermission");
        return permission.value()[0];
    }

    @Test
    void returnConfirmAndExecuteHaveIndependentPermissions() throws Exception {
        Map<String, String> expected = new HashMap<>();
        expected.put("confirm", "so:return:confirm");
        expected.put("execute", "so:return:execute");
        for (Map.Entry<String, String> e : expected.entrySet()) {
            assertEquals(e.getValue(), permsOf(SoReturnController.class, e.getKey()));
        }
    }

    @Test
    void quotationConfirmAndConvertHaveIndependentPermissions() throws Exception {
        assertEquals("so:quotation:confirm", permsOf(SoQuotationController.class, "confirm"));
        assertEquals("so:quotation:convert", permsOf(SoQuotationController.class, "convertToOrder"));
    }

    @Test
    void contractTerminateHasIndependentPermission() throws Exception {
        assertEquals("so:contract:terminate", permsOf(SoContractController.class, "terminate"));
    }
}
