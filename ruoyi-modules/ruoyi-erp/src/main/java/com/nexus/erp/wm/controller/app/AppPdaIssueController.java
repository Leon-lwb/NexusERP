package com.nexus.erp.wm.controller.app;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.nexus.common.core.domain.R;
import com.nexus.common.log.annotation.Log;
import com.nexus.common.log.enums.BusinessType;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.common.satoken.pda.PdaIdempotent;
import com.nexus.erp.wm.domain.bo.pda.PdaIssueFinishBo;
import com.nexus.erp.wm.domain.bo.pda.PdaPickBo;
import com.nexus.erp.wm.domain.vo.pda.PdaActionResultVo;
import com.nexus.erp.wm.domain.vo.pda.PdaIssueDetailVo;
import com.nexus.erp.wm.domain.vo.pda.PdaTodoItemVo;
import com.nexus.erp.wm.service.IPdaIssueService;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * PDA 备料发料接口 (摘果式拣料)
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/app/pda/wm/issue")
public class AppPdaIssueController {

    private final IPdaIssueService pdaIssueService;

    @SaCheckPermission("wm:productionissue:list")
    @GetMapping("/todo")
    public TableDataInfo<PdaTodoItemVo> todo(@RequestParam(required = false) String keyword, PageQuery pageQuery) {
        return pdaIssueService.todo(keyword, pageQuery);
    }

    @SaCheckPermission("wm:productionissue:list")
    @GetMapping("/{issueId}")
    public R<PdaIssueDetailVo> detail(@PathVariable Long issueId) {
        return R.ok(pdaIssueService.detail(issueId));
    }

    /** 行级拣货核销 (幂等键: 单号:行号:pick) */
    @SaCheckPermission("wm:productionissue:edit")
    @PdaIdempotent
    @Log(title = "PDA发料拣货", businessType = BusinessType.UPDATE)
    @PostMapping("/{issueId}/line/pick")
    public R<PdaActionResultVo> pick(@PathVariable Long issueId, @Validated @RequestBody @NotNull PdaPickBo bo) {
        return R.ok(pdaIssueService.pick(issueId, bo));
    }

    /** 完成发料 (幂等键: 单号:0:finish); body 可携带缺货声明行走缺货逃生通道 */
    @SaCheckPermission("wm:productionissue:edit")
    @PdaIdempotent
    @Log(title = "PDA发料完成", businessType = BusinessType.UPDATE)
    @PostMapping("/{issueId}/finish")
    public R<PdaActionResultVo> finish(
        @PathVariable Long issueId,
        @RequestBody(required = false) PdaIssueFinishBo bo) {
        return R.ok(pdaIssueService.finish(issueId, bo));
    }
}
