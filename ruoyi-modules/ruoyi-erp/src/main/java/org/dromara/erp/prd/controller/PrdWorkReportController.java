package org.dromara.erp.prd.controller;

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
import org.dromara.erp.prd.domain.bo.PrdWorkReportBo;
import org.dromara.erp.prd.domain.vo.PrdWorkReportVo;
import org.dromara.erp.prd.service.IPrdWorkReportService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 生产报工Controller
 *
 * @author ruoyi
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/erp/prd/report")
public class PrdWorkReportController extends BaseController {

    private final IPrdWorkReportService prdWorkReportService;

    /**
     * 查询生产报工列表
     */
    @SaCheckPermission("prd:report:list")
    @GetMapping("/list")
    public TableDataInfo<PrdWorkReportVo> list(PrdWorkReportBo bo, PageQuery pageQuery) {
        return prdWorkReportService.selectPageList(bo, pageQuery);
    }

    /**
     * 导出生产报工列表
     */
    @SaCheckPermission("prd:report:export")
    @Log(title = "生产报工", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(PrdWorkReportBo bo, HttpServletResponse response) {
        List<PrdWorkReportVo> list = prdWorkReportService.selectList(bo);
        ExcelUtil.exportExcel(list, "生产报工数据", PrdWorkReportVo.class, response);
    }

    /**
     * 获取生产报工详细信息
     */
    @SaCheckPermission("prd:report:query")
    @GetMapping("/{reportId}")
    public R<PrdWorkReportVo> getInfo(@PathVariable Long reportId) {
        return R.ok(prdWorkReportService.selectById(reportId));
    }

    /**
     * 新增生产报工
     */
    @SaCheckPermission("prd:report:add")
    @Log(title = "生产报工", businessType = BusinessType.INSERT)
    @PostMapping
    public R<Void> add(@Validated @RequestBody PrdWorkReportBo bo) {
        return toAjax(prdWorkReportService.insertByBo(bo));
    }

    /**
     * 修改生产报工
     */
    @SaCheckPermission("prd:report:edit")
    @Log(title = "生产报工", businessType = BusinessType.UPDATE)
    @PutMapping
    public R<Void> edit(@Validated @RequestBody PrdWorkReportBo bo) {
        return toAjax(prdWorkReportService.updateByBo(bo));
    }

    /**
     * 删除生产报工
     */
    @SaCheckPermission("prd:report:remove")
    @Log(title = "生产报工", businessType = BusinessType.DELETE)
    @DeleteMapping("/{reportIds}")
    public R<Void> remove(@PathVariable Long[] reportIds) {
        return toAjax(prdWorkReportService.deleteByIds(reportIds));
    }
}
