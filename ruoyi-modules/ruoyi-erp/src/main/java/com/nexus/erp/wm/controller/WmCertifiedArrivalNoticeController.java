package com.nexus.erp.wm.controller;
import cn.dev33.satoken.annotation.SaCheckPermission;
import com.nexus.common.core.domain.R;
import com.nexus.erp.wm.service.WmCertifiedArrivalNoticeService;
import org.springframework.web.bind.annotation.*;
/** 认证独立到货入口：现有权限/正常审批，不接受客户端批准标志。 */
@RestController
@RequestMapping("/erp/wm/arrivalnotice/certified")
public class WmCertifiedArrivalNoticeController {
    private final WmCertifiedArrivalNoticeService service;
    public WmCertifiedArrivalNoticeController(WmCertifiedArrivalNoticeService service){this.service=service;}
    @SaCheckPermission("wm:arrivalnotice:add") @PostMapping
    public R<WmCertifiedArrivalNoticeService.Result> create(@RequestBody WmCertifiedArrivalNoticeService.Request body){return R.ok(service.create(body));}
    @SaCheckPermission("wm:arrivalnotice:submit") @PostMapping("/{id}/submit")
    public R<Void> submit(@PathVariable Long id,@RequestParam String selectFlowId){return service.submit(id,selectFlowId)?R.ok():R.fail("提交失败");}
    @SaCheckPermission("wm:arrivalnotice:submit") @PostMapping("/{id}/synchronize")
    public R<Void> synchronize(@PathVariable Long id){return service.synchronize(id)?R.ok():R.fail("流程状态同步失败");}
}
