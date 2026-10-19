package com.nexus.erp.wm.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.nexus.common.core.domain.R;
import com.nexus.erp.wm.domain.bo.WmSerialSelectionBo;
import com.nexus.erp.wm.service.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 使用各原单据权限；租户只能来自认证上下文。
 */
@RestController
@RequestMapping("/erp/wm/serial-selections")
@RequiredArgsConstructor
public class WmSerialSelectionController {
    private final WmSerialSelectionService selections;

    static void access(String type, boolean write) {
        String permission =
                switch (type) {
                    case "WM_SALES_ISSUE" -> "wm:salesIssue";
                    case "WM_PRODUCTION_ISSUE" -> "wm:productionissue";
                    case "WM_MATERIAL_RECEIVE" -> "wm:receive";
                    case "WM_MISC_ISSUE" -> "wm:miscissue";
                    case "WM_MISC_RECPT" -> "wm:miscrecpt";
                    case "WM_TRANSFER" -> "wm:transfer";
                    case "WM_STOCK_TAKING" -> "wm:stocktaking";
                    case "WM_MATERIAL_RETURN" -> "wm:materialReturn";
                    case "SO_RETURN" -> "so:return";
                    case "PU_PURCHASE_RETURN" -> "pu:return";
                    case "PRD_REWORK_ORDER", "PRD_REWORK_ORDER_RECEIVE" -> "prd:rework";
                    case "WM_SUBCONTRACT_ISSUE" -> "wm:subcontractIssue";
                    case "WM_SUBCONTRACT_RECEIVE" -> "wm:subcontractReceive";
                    default -> throw new IllegalArgumentException("来源类型未授权");
                };
        StpUtil.checkPermission(permission + (write ? ":edit" : ":query"));
    }

    @PostMapping
    public R<Long> freeze(@Valid @RequestBody WmSerialSelectionBo bo) {
        access(bo.getSourceDocType(), true);
        return R.ok(selections.freeze(bo));
    }

    @GetMapping("/version")
    public R<WmSerialInventoryRepository.Snapshot> version(
            @RequestParam String sourceDocType,
            @RequestParam Long sourceDocId,
            @RequestParam Long lineId) {
        access(sourceDocType, false);
        return R.ok(selections.snapshot(sourceDocType, sourceDocId, lineId));
    }
}
