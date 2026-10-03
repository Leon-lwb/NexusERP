package com.nexus.erp.wm.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.nexus.common.core.domain.R;
import com.nexus.erp.wm.service.WmCertifiedReceiptPostingService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/erp/wm/receive/certified")
public class WmCertifiedReceiptPostingController {
    private final WmCertifiedReceiptPostingService posting;
    public WmCertifiedReceiptPostingController(WmCertifiedReceiptPostingService posting) { this.posting=posting; }
    @SaCheckPermission("wm:receive:shelf")
    @PostMapping("/shelf/{receiveId}")
    public R<Void> shelf(@PathVariable Long receiveId) { return posting.shelf(receiveId) ? R.ok() : R.fail("上架失败"); }
    @SaCheckPermission("wm:receive:execute")
    @PostMapping("/execute/{receiveId}")
    public R<WmCertifiedReceiptPostingService.Result> execute(@PathVariable Long receiveId) {
        return R.ok(posting.post(receiveId));
    }
}
