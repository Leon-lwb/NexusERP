package com.nexus.erp.bd.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.nexus.common.core.domain.R;
import com.nexus.erp.bd.service.BdQuantityConfirmationContextService;
import com.nexus.erp.bd.service.BdQuantityConfirmationContextService.Context;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 数量核验独立只读入口；沿用原生单位前缀，不接管普通 CRUD 或既有数量写接口。
 * <p>只返回登录主体与权限提示，既不认证单位资料，也不取得业务数据库连接。</p>
 */
@RestController
@RequestMapping("/erp/bd/measure")
public class BdQuantityConfirmationContextController {
    private final BdQuantityConfirmationContextService service;

    public BdQuantityConfirmationContextController(BdQuantityConfirmationContextService service) {
        this.service = service;
    }

    /**
     * query-only 用户也可打开数量核验详情；实际 POST 仍由既有编辑权限及资料合同守卫。
     *
     * @return 当前实际登录租户、用户及数量确认按钮提示（不是认证）
     */
    @SaCheckPermission("bd:measure:query")
    @GetMapping("/quantity-context")
    public R<Context> quantityContext() {
        return R.ok(service.currentContext());
    }
}
