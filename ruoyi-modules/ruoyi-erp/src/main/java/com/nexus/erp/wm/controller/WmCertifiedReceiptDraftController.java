package com.nexus.erp.wm.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.nexus.common.core.domain.R;
import com.nexus.erp.wm.service.WmCertifiedReceiptDraftService;
import org.springframework.web.bind.annotation.*;

/** 批准订单的明确收货行创建入口，结果仅为草稿，不授予上架或过账资格。 */
@RestController
@RequestMapping("/erp/wm/receive/certified")
public class WmCertifiedReceiptDraftController {
    private final WmCertifiedReceiptDraftService service;
    public WmCertifiedReceiptDraftController(WmCertifiedReceiptDraftService service){this.service=service;}
    @PostMapping("/draft")
    @SaCheckPermission("wm:receive:add")
    public R<WmCertifiedReceiptDraftService.Result> create(@RequestBody WmCertifiedReceiptDraftService.Request request){return R.ok(service.create(request));}
}
