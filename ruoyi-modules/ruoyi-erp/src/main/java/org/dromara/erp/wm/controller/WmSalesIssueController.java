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
import org.dromara.erp.wm.domain.bo.WmSalesIssueBo;
import org.dromara.erp.wm.domain.vo.WmSalesIssueVo;
import org.dromara.erp.wm.service.IWmSalesIssueService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 销售出库Controller
 *
 * @author ruoyi
 * @date 2026-04-03
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/erp/wm/salesIssue")
public class WmSalesIssueController extends BaseController {

    private final IWmSalesIssueService wmSalesIssueService;

    /**
     * 查询销售出库列表
     */
    @SaCheckPermission("wm:salesIssue:list")
    @GetMapping("/list")
    public TableDataInfo<WmSalesIssueVo> list(WmSalesIssueBo bo, PageQuery pageQuery) {
        return wmSalesIssueService.selectPageList(bo, pageQuery);
    }

    /**
     * 导出销售出库列表
     */
    @SaCheckPermission("wm:salesIssue:export")
    @Log(title = "销售出库", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(WmSalesIssueBo bo, HttpServletResponse response) {
        List<WmSalesIssueVo> list = wmSalesIssueService.selectList(bo);
        ExcelUtil.exportExcel(list, "销售出库数据", WmSalesIssueVo.class, response);
    }

    /**
     * 获取销售出库详细信息
     */
    @SaCheckPermission("wm:salesIssue:query")
    @GetMapping("/{issueId}")
    public R<WmSalesIssueVo> getInfo(@PathVariable Long issueId) {
        return R.ok(wmSalesIssueService.selectById(issueId));
    }

    /**
     * 新增销售出库
     */
    @SaCheckPermission("wm:salesIssue:add")
    @Log(title = "销售出库", businessType = BusinessType.INSERT)
    @PostMapping
    public R<Void> add(@Validated @RequestBody WmSalesIssueBo bo) {
        return toAjax(wmSalesIssueService.insertByBo(bo));
    }

    /**
     * 修改销售出库
     */
    @SaCheckPermission("wm:salesIssue:edit")
    @Log(title = "销售出库", businessType = BusinessType.UPDATE)
    @PutMapping
    public R<Void> edit(@Validated @RequestBody WmSalesIssueBo bo) {
        return toAjax(wmSalesIssueService.updateByBo(bo));
    }

    /**
     * 删除销售出库
     */
    @SaCheckPermission("wm:salesIssue:remove")
    @Log(title = "销售出库", businessType = BusinessType.DELETE)
    @DeleteMapping("/{issueIds}")
    public R<Void> remove(@PathVariable Long[] issueIds) {
        return toAjax(wmSalesIssueService.deleteByIds(issueIds));
    }

    /**
     * 确认销售出库
     */
    @SaCheckPermission("wm:salesIssue:confirm")
    @Log(title = "销售出库", businessType = BusinessType.UPDATE)
    @PutMapping("/confirm/{issueId}")
    public R<Void> confirm(@PathVariable Long issueId) {
        return toAjax(wmSalesIssueService.confirmSalesIssue(issueId));
    }

    /**
     * 完成销售出库
     */
    @SaCheckPermission("wm:salesIssue:complete")
    @Log(title = "销售出库", businessType = BusinessType.UPDATE)
    @PutMapping("/complete/{issueId}")
    public R<Void> complete(@PathVariable Long issueId) {
        return toAjax(wmSalesIssueService.completeSalesIssue(issueId));
    }

    // ==================== Flowable 工作流 ====================

    /**
     * 提交审批
     */
    @SaCheckPermission("wm:salesIssue:edit")
    @Log(title = "销售出库", businessType = BusinessType.SUBMIT)
    @PostMapping("/{issueId}/submit")
    public R<Void> submit(@PathVariable Long issueId, @RequestParam(required = false) String selectFlowId) {
        wmSalesIssueService.submit(String.valueOf(issueId), selectFlowId);
        return R.ok();
    }

    /**
     * 获取流程实例ID
     */
    @SaCheckPermission("wm:salesIssue:query")
    @GetMapping("/{issueId}/proc")
    public R<String> getProcInsId(@PathVariable Long issueId) {
        return R.ok(wmSalesIssueService.getProcInsId(String.valueOf(issueId)));
    }
}
