package com.nexus.erp.wm.controller.app;

import com.nexus.common.core.domain.R;
import com.nexus.common.log.annotation.Log;
import com.nexus.common.log.enums.BusinessType;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.erp.wm.annotation.PdaIdempotent;
import com.nexus.erp.wm.domain.vo.pda.PdaActionResultVo;
import com.nexus.erp.wm.domain.vo.pda.PdaTodoItemVo;
import com.nexus.erp.wm.domain.vo.pda.PdaTransferDetailVo;
import com.nexus.erp.wm.service.IPdaTransferService;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * PDA 移库接口 (M1 整单执行; 两段状态机为 M2)
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/app/pda/wm/transfer")
public class AppPdaTransferController {

    private final IPdaTransferService pdaTransferService;

    @GetMapping("/todo")
    public TableDataInfo<PdaTodoItemVo> todo(@RequestParam(required = false) String keyword, PageQuery pageQuery) {
        return pdaTransferService.todo(keyword, pageQuery);
    }

    @GetMapping("/{transferId}")
    public R<PdaTransferDetailVo> detail(@PathVariable Long transferId) {
        return R.ok(pdaTransferService.detail(transferId));
    }

    /** 整单执行移库 (幂等键: 单号:0:execute) */
    @PdaIdempotent
    @Log(title = "PDA移库执行", businessType = BusinessType.UPDATE)
    @PostMapping("/{transferId}/execute")
    public R<PdaActionResultVo> execute(@PathVariable Long transferId) {
        return R.ok(pdaTransferService.execute(transferId));
    }
}
