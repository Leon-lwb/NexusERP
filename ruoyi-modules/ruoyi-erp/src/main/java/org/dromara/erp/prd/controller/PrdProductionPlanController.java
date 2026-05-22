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
import org.dromara.common.core.utils.MapstructUtils;
import org.dromara.common.web.core.BaseController;
import org.dromara.erp.bd.domain.vo.BdBomDetailVo;
import org.dromara.erp.prd.domain.PrdMaterialRequirement;
import org.dromara.erp.prd.domain.PrdProductionPlan;
import org.dromara.erp.prd.domain.bo.PrdProductionPlanBo;
import org.dromara.erp.prd.domain.vo.PrdProductionPlanVo;
import org.dromara.erp.prd.service.IPrdProductionPlanService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 生产计划Controller
 *
 * @author ruoyi
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/erp/prd/plan")
public class PrdProductionPlanController extends BaseController {

    private final IPrdProductionPlanService prdProductionPlanService;

    /**
     * 查询生产计划列表
     */
    @SaCheckPermission("prd:plan:list")
    @GetMapping("/list")
    public TableDataInfo<PrdProductionPlanVo> list(PrdProductionPlanBo bo, PageQuery pageQuery) {
        return prdProductionPlanService.selectPageList(bo, pageQuery);
    }

    /**
     * 导出生产计划列表
     */
    @SaCheckPermission("prd:plan:export")
    @Log(title = "生产计划", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(PrdProductionPlanBo bo, HttpServletResponse response) {
        List<PrdProductionPlanVo> list = prdProductionPlanService.selectList(bo);
        ExcelUtil.exportExcel(list, "生产计划数据", PrdProductionPlanVo.class, response);
    }

    /**
     * 获取生产计划详细信息
     */
    @SaCheckPermission("prd:plan:query")
    @GetMapping("/{planId}")
    public R<PrdProductionPlanVo> getInfo(@PathVariable Long planId) {
        return R.ok(prdProductionPlanService.selectById(planId));
    }

    /**
     * 新增生产计划
     */
    @SaCheckPermission("prd:plan:add")
    @Log(title = "生产计划", businessType = BusinessType.INSERT)
    @PostMapping
    public R<Void> add(@Validated @RequestBody PrdProductionPlanBo bo) {
        return toAjax(prdProductionPlanService.insertByBo(bo));
    }

    /**
     * 修改生产计划
     */
    @SaCheckPermission("prd:plan:edit")
    @Log(title = "生产计划", businessType = BusinessType.UPDATE)
    @PutMapping
    public R<Void> edit(@Validated @RequestBody PrdProductionPlanBo bo) {
        return toAjax(prdProductionPlanService.updateByBo(bo));
    }

    /**
     * 删除生产计划
     */
    @SaCheckPermission("prd:plan:remove")
    @Log(title = "生产计划", businessType = BusinessType.DELETE)
    @DeleteMapping("/{planIds}")
    public R<Void> remove(@PathVariable Long[] planIds) {
        return toAjax(prdProductionPlanService.deleteByIds(planIds));
    }

    /**
     * 生产计划版本更新
     */
    @SaCheckPermission("prd:plan:edit")
    @Log(title = "生产计划", businessType = BusinessType.UPDATE)
    @PutMapping("/updateVersion")
    public R<Void> updateVersion(@RequestBody PrdProductionPlanBo bo) {
        PrdProductionPlan plan = MapstructUtils.convert(bo, PrdProductionPlan.class);
        return toAjax(prdProductionPlanService.updatePrdProductionPlanVersion(plan));
    }

    /**
     * BOM树计算子节点
     */
    @SaCheckPermission("prd:plan:edit")
    @Log(title = "生产计划", businessType = BusinessType.UPDATE)
    @GetMapping("/calculateBom/{planId}")
    public R<List<BdBomDetailVo>> calculateBom(@PathVariable Long planId) {
        return R.ok(prdProductionPlanService.calculateBomTree(planId));
    }

    /**
     * 根据BOM详情生成物料需求清单及明细
     */
    @SaCheckPermission("prd:plan:edit")
    @Log(title = "生产计划", businessType = BusinessType.UPDATE)
    @PostMapping("/generateMaterialRequirementsWithDetails/{planId}")
    public R<String> generateMaterialRequirementsWithDetails(@PathVariable Long planId) {
        try {
            List<PrdMaterialRequirement> requirements = prdProductionPlanService.generateMaterialRequirementsWithDetails(planId);
            return R.ok("物料需求清单及明细生成成功，共生成" + requirements.size() + "条记录");
        } catch (Exception e) {
            return R.fail("物料需求清单及明细生成失败：" + e.getMessage());
        }
    }

    /**
     * 从物料需求单生成生产工单
     */
    @SaCheckPermission("prd:plan:edit")
    @Log(title = "生产计划", businessType = BusinessType.UPDATE)
    @PostMapping("/generateWorkOrders/{planId}")
    public R<String> generateWorkOrders(@PathVariable Long planId, @RequestBody List<Long> requirementIds) {
        try {
            Integer count = prdProductionPlanService.generateWorkOrders(planId, requirementIds);
            return R.ok("成功生成 " + count + " 个工单");
        } catch (Exception e) {
            return R.fail("生成工单失败：" + e.getMessage());
        }
    }
}
