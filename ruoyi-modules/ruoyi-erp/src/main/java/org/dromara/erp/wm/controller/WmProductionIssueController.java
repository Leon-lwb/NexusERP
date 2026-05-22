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
import org.dromara.erp.wm.domain.bo.WmProductionIssueBo;
import org.dromara.erp.wm.domain.vo.WmProductionIssueVo;
import org.dromara.erp.wm.service.IWmProductionIssueService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/erp/wm/productionissue")
public class WmProductionIssueController extends BaseController {

    private final IWmProductionIssueService wmProductionIssueService;

    @SaCheckPermission("wm:productionissue:list")
    @GetMapping("/list")
    public TableDataInfo<WmProductionIssueVo> list(WmProductionIssueBo bo, PageQuery pageQuery) {
        return wmProductionIssueService.selectPageList(bo, pageQuery);
    }

    @SaCheckPermission("wm:productionissue:export")
    @Log(title = "生产领料", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(WmProductionIssueBo bo, HttpServletResponse response) {
        List<WmProductionIssueVo> list = wmProductionIssueService.selectList(bo);
        ExcelUtil.exportExcel(list, "生产领料数据", WmProductionIssueVo.class, response);
    }

    @SaCheckPermission("wm:productionissue:query")
    @GetMapping("/{issueId}")
    public R<WmProductionIssueVo> getInfo(@PathVariable Long issueId) {
        return R.ok(wmProductionIssueService.selectById(issueId));
    }

    @SaCheckPermission("wm:productionissue:add")
    @Log(title = "生产领料", businessType = BusinessType.INSERT)
    @PostMapping
    public R<Void> add(@Validated @RequestBody WmProductionIssueBo bo) {
        return toAjax(wmProductionIssueService.insertByBo(bo));
    }

    @SaCheckPermission("wm:productionissue:edit")
    @Log(title = "生产领料", businessType = BusinessType.UPDATE)
    @PutMapping
    public R<Void> edit(@Validated @RequestBody WmProductionIssueBo bo) {
        return toAjax(wmProductionIssueService.updateByBo(bo));
    }

    @SaCheckPermission("wm:productionissue:remove")
    @Log(title = "生产领料", businessType = BusinessType.DELETE)
    @DeleteMapping("/{issueIds}")
    public R<Void> remove(@PathVariable Long[] issueIds) {
        return toAjax(wmProductionIssueService.deleteByIds(issueIds));
    }

    @SaCheckPermission("wm:productionissue:edit")
    @Log(title = "生产领料", businessType = BusinessType.UPDATE)
    @PutMapping("/execute/{issueId}")
    public R<Void> execute(@PathVariable Long issueId) {
        wmProductionIssueService.execute(issueId);
        return R.ok();
    }
}
