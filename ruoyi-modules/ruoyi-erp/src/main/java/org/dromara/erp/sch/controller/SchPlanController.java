package org.dromara.erp.sch.controller;

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
import org.dromara.erp.sch.domain.bo.SchPlanBo;
import org.dromara.erp.sch.domain.vo.SchPlanVo;
import org.dromara.erp.sch.service.ISchPlanService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 排班计划Controller
 *
 * @author nexus
 * @date 2025-12-25
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/erp/sch/plan")
public class SchPlanController extends BaseController {

    private final ISchPlanService schPlanService;

    /**
     * 查询排班计划列表
     *
     * @param bo 查询参数
     * @param pageQuery 分页参数
     * @return 排班计划分页列表
     */
    @SaCheckPermission("sch:plan:list")
    @GetMapping("/list")
    public TableDataInfo<SchPlanVo> list(SchPlanBo bo, PageQuery pageQuery) {
        return schPlanService.selectPageList(bo, pageQuery);
    }

    /**
     * 导出排班计划数据
     *
     * @param bo 查询参数
     * @param response HTTP响应对象
     */
    @SaCheckPermission("sch:plan:export")
    @Log(title = "排班计划", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(SchPlanBo bo, HttpServletResponse response) {
        List<SchPlanVo> list = schPlanService.selectList(bo);
        ExcelUtil.exportExcel(list, "排班计划数据", SchPlanVo.class, response);
    }

    /**
     * 获取排班计划详细信息
     *
     * @param planId 排班计划ID
     * @return 排班计划详细信息
     */
    @SaCheckPermission("sch:plan:query")
    @GetMapping("/{planId}")
    public R<SchPlanVo> getInfo(@PathVariable Long planId) {
        return R.ok(schPlanService.selectById(planId));
    }

    /**
     * 新增排班计划
     *
     * @param bo 排班计划信息
     * @return 操作结果
     */
    @SaCheckPermission("sch:plan:add")
    @Log(title = "排班计划", businessType = BusinessType.INSERT)
    @PostMapping
    public R<Void> add(@Validated @RequestBody SchPlanBo bo) {
        return toAjax(schPlanService.insertByBo(bo));
    }

    /**
     * 修改排班计划
     *
     * @param bo 排班计划信息
     * @return 操作结果
     */
    @SaCheckPermission("sch:plan:edit")
    @Log(title = "排班计划", businessType = BusinessType.UPDATE)
    @PutMapping
    public R<Void> edit(@Validated @RequestBody SchPlanBo bo) {
        return toAjax(schPlanService.updateByBo(bo));
    }

    /**
     * 删除排班计划
     *
     * @param planIds 排班计划ID数组
     * @return 操作结果
     */
    @SaCheckPermission("sch:plan:remove")
    @Log(title = "排班计划", businessType = BusinessType.DELETE)
    @DeleteMapping("/{planIds}")
    public R<Void> remove(@PathVariable Long[] planIds) {
        return toAjax(schPlanService.deleteByIds(planIds));
    }
}
