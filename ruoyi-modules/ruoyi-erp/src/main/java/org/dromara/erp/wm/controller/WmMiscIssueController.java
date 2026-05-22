package org.dromara.erp.wm.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.R;
import org.dromara.common.excel.utils.ExcelUtil;
import org.dromara.common.log.annotation.Log;
import org.dromara.common.log.enums.BusinessType;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.common.web.core.BaseController;
import org.dromara.erp.wm.domain.bo.WmMiscIssueBo;
import org.dromara.erp.wm.domain.vo.WmMiscIssueVo;
import org.dromara.erp.wm.service.IWmMiscIssueService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/erp/wm/miscissue")
public class WmMiscIssueController extends BaseController {

    private final IWmMiscIssueService wmMiscIssueService;

    @SaCheckPermission("wm:miscissue:list")
    @GetMapping("/list")
    public TableDataInfo<WmMiscIssueVo> list(WmMiscIssueBo bo, PageQuery pageQuery) {
        return wmMiscIssueService.selectPageList(bo, pageQuery);
    }

    @SaCheckPermission("wm:miscissue:export")
    @Log(title = "杂项出库", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(WmMiscIssueBo bo, HttpServletResponse response) {
        List<WmMiscIssueVo> list = wmMiscIssueService.selectList(bo);
        ExcelUtil.exportExcel(list, "杂项出库数据", WmMiscIssueVo.class, response);
    }

    @SaCheckPermission("wm:miscissue:query")
    @GetMapping("/{issueId}")
    public R<WmMiscIssueVo> getInfo(@PathVariable Long issueId) {
        return R.ok(wmMiscIssueService.selectById(issueId));
    }

    @SaCheckPermission("wm:miscissue:add")
    @Log(title = "杂项出库", businessType = BusinessType.INSERT)
    @PostMapping
    public R<Void> add(@Validated @RequestBody WmMiscIssueBo bo) {
        return toAjax(wmMiscIssueService.insertByBo(bo));
    }

    @SaCheckPermission("wm:miscissue:edit")
    @Log(title = "杂项出库", businessType = BusinessType.UPDATE)
    @PutMapping
    public R<Void> edit(@Validated @RequestBody WmMiscIssueBo bo) {
        return toAjax(wmMiscIssueService.updateByBo(bo));
    }

    @SaCheckPermission("wm:miscissue:remove")
    @Log(title = "杂项出库", businessType = BusinessType.DELETE)
    @DeleteMapping("/{issueIds}")
    public R<Void> remove(@PathVariable Long[] issueIds) {
        return toAjax(wmMiscIssueService.deleteByIds(issueIds));
    }

    @SaCheckPermission("wm:miscissue:edit")
    @Log(title = "杂项出库", businessType = BusinessType.UPDATE)
    @PutMapping("/execute/{issueId}")
    public R<Void> execute(@PathVariable Long issueId) {
        wmMiscIssueService.execute(issueId);
        return R.ok();
    }
}