package com.nexus.erp.wm.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.nexus.common.core.domain.R;
import com.nexus.erp.wm.service.WmCertifiedReceiptRecoveryService;
import org.springframework.web.bind.annotation.*;

/** 只读永久恢复入口；不把查询变为post重放或消息补发。 */
@RestController
@RequestMapping("/erp/wm/receive/certified")
public class WmCertifiedReceiptRecoveryController {
    private final WmCertifiedReceiptRecoveryService recovery;
    public WmCertifiedReceiptRecoveryController(WmCertifiedReceiptRecoveryService recovery){this.recovery=recovery;}
    @SaCheckPermission("wm:receive:query")
    @GetMapping("/recovery/{receiveId}")
    public R<WmCertifiedReceiptRecoveryService.RecoveryView> recovery(@PathVariable Long receiveId){
        return R.ok(recovery.read(receiveId));
    }
}
