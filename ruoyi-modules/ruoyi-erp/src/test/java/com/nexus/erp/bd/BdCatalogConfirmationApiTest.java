package com.nexus.erp.bd;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.nexus.common.log.annotation.Log;
import com.nexus.erp.bd.controller.BdMaterialVendorController;
import com.nexus.erp.bd.controller.BdUnitOfMeasureController;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.PostMapping;

import java.lang.reflect.Method;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

/** 确认接口必须复用已有编辑权限并保存证据请求，不以普通编辑替代核验。 */
class BdCatalogConfirmationApiTest {
    @Test
    void quantityEndpointRequiresPermissionAndAudit() {
        assertEndpoint(BdUnitOfMeasureController.class, "/{id}/confirm-quantity", "bd:measure:edit");
    }

    @Test
    void termsEndpointRequiresPermissionAndAudit() {
        assertEndpoint(BdMaterialVendorController.class, "/{id}/confirm-terms", "bd:materialVendor:edit");
    }

    private static void assertEndpoint(Class<?> controller, String path, String permission) {
        Method endpoint = Arrays.stream(controller.getDeclaredMethods())
            .filter(method -> method.isAnnotationPresent(PostMapping.class))
            .filter(method -> Arrays.asList(method.getAnnotation(PostMapping.class).value()).contains(path))
            .findFirst().orElse(null);
        assertNotNull(endpoint, "缺少受保护的确认接口：" + path);
        assertArrayEquals(new String[]{permission}, endpoint.getAnnotation(SaCheckPermission.class).value());
        assertTrue(endpoint.getAnnotation(Log.class).isSaveRequestData(), "确认资料与证据须留操作审计");
    }
}
