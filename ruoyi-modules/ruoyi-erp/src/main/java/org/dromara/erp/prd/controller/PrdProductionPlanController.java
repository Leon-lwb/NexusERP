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
 * 生产计划 Controller
 * <p>
 * 提供生产计划的 RESTful API 接口，包括：
 * <ul>
 *   <li>CRUD 操作：分页列表查询、详情查询、新增、编辑、删除</li>
 *   <li>数据导出（Excel）</li>
 *   <li>版本管理：生产计划版本更新</li>
 *   <li>BOM 相关：BOM 树计算、物料需求生成、工单生成</li>
 * </ul>
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
     * 分页查询生产计划列表
     * <p>
     * 权限标识：{@code prd:plan:list}</p>
     *
     * @param bo        查询参数，包含计划编号、产品、日期范围等筛选条件
     * @param pageQuery 分页参数，包含页码、每页条数及排序信息
     * @return 生产计划分页列表
     */
    @SaCheckPermission("prd:plan:list")
    @GetMapping("/list")
    public TableDataInfo<PrdProductionPlanVo> list(PrdProductionPlanBo bo, PageQuery pageQuery) {
        return prdProductionPlanService.selectPageList(bo, pageQuery);
    }

    /**
     * 导出生产计划数据到 Excel
     * <p>权限标识：{@code prd:plan:export}</p>
     * <p>日志记录：title="生产计划"，businessType=EXPORT</p>
     *
     * @param bo        查询参数
     * @param response  HTTP 响应对象，用于输出 Excel 文件流
     */
    @SaCheckPermission("prd:plan:export")
    @Log(title = "生产计划", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(PrdProductionPlanBo bo, HttpServletResponse response) {
        List<PrdProductionPlanVo> list = prdProductionPlanService.selectList(bo);
        ExcelUtil.exportExcel(list, "生产计划数据", PrdProductionPlanVo.class, response);
    }

    /**
     * 根据主键获取生产计划详细信息
     * <p>
     * 权限标识：{@code prd:plan:query}</p>
     *
     * @param planId 生产计划 ID
     * @return 生产计划详细信息
     */
    @SaCheckPermission("prd:plan:query")
    @GetMapping("/{planId}")
    public R<PrdProductionPlanVo> getInfo(@PathVariable Long planId) {
        return R.ok(prdProductionPlanService.selectById(planId));
    }

    /**
     * 新增生产计划
     * <p>
     * 权限标识：{@code prd:plan:add}</p>
     * <p>日志记录：title="生产计划"，businessType=INSERT</p>
     *
     * @param bo 生产计划业务对象，包含产品、数量、计划日期等信息
     * @return 操作结果，成功返回 {@code R.success()}，失败返回 {@code R.fail()}
     */
    @SaCheckPermission("prd:plan:add")
    @Log(title = "生产计划", businessType = BusinessType.INSERT)
    @PostMapping
    public R<Void> add(@Validated @RequestBody PrdProductionPlanBo bo) {
        return toAjax(prdProductionPlanService.insertByBo(bo));
    }

    /**
     * 修改生产计划
     * <p>
     * 权限标识：{@code prd:plan:edit}</p>
     * <p>日志记录：title="生产计划"，businessType=UPDATE</p>
     *
     * @param bo 生产计划业务对象，包含需要更新的字段信息
     * @return 操作结果，成功返回 {@code R.success()}，失败返回 {@code R.fail()}
     */
    @SaCheckPermission("prd:plan:edit")
    @Log(title = "生产计划", businessType = BusinessType.UPDATE)
    @PutMapping
    public R<Void> edit(@Validated @RequestBody PrdProductionPlanBo bo) {
        return toAjax(prdProductionPlanService.updateByBo(bo));
    }

    /**
     * 批量删除生产计划
     * <p>
     * 权限标识：{@code prd:plan:remove}</p>
     * <p>日志记录：title="生产计划"，businessType=DELETE</p>
     *
     * @param planIds 要删除的生产计划 ID 数组（支持批量删除）
     * @return 操作结果，成功返回 {@code R.success()}，失败返回 {@code R.fail()}
     */
    @SaCheckPermission("prd:plan:remove")
    @Log(title = "生产计划", businessType = BusinessType.DELETE)
    @DeleteMapping("/{planIds}")
    public R<Void> remove(@PathVariable Long[] planIds) {
        return toAjax(prdProductionPlanService.deleteByIds(planIds));
    }

    /**
     * 生产计划版本更新
     * <p>权限标识：{@code prd:plan:edit}</p>
     * <p>日志记录：title="生产计划"，businessType=UPDATE</p>
     *
     * @param bo 包含版本更新信息的业务对象
     * @return 操作结果
     */
    @SaCheckPermission("prd:plan:edit")
    @Log(title = "生产计划", businessType = BusinessType.UPDATE)
    @PutMapping("/updateVersion")
    public R<Void> updateVersion(@RequestBody PrdProductionPlanBo bo) {
        PrdProductionPlan plan = MapstructUtils.convert(bo, PrdProductionPlan.class);
        return toAjax(prdProductionPlanService.updatePrdProductionPlanVersion(plan));
    }

    /**
     * BOM 树计算子节点
     * <p>权限标识：{@code prd:plan:edit}</p>
     * <p>日志记录：title="生产计划"，businessType=UPDATE</p>
     *
     * @param planId 生产计划 ID
     * @return BOM 详情树节点列表
     */
    @SaCheckPermission("prd:plan:edit")
    @Log(title = "生产计划", businessType = BusinessType.UPDATE)
    @GetMapping("/calculateBom/{planId}")
    public R<List<BdBomDetailVo>> calculateBom(@PathVariable Long planId) {
        return R.ok(prdProductionPlanService.calculateBomTree(planId));
    }

    /**
     * 根据 BOM 详情生成物料需求清单及明细
     * <p>权限标识：{@code prd:plan:edit}</p>
     * <p>日志记录：title="生产计划"，businessType=UPDATE</p>
     *
     * @param planId 生产计划 ID
     * @return 操作结果信息
     * @throws Exception 生成失败时抛出异常
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
     * <p>权限标识：{@code prd:plan:edit}</p>
     * <p>日志记录：title="生产计划"，businessType=UPDATE</p>
     *
     * @param planId         生产计划 ID
     * @param requirementIds 选中的物料需求 ID 列表
     * @return 操作结果信息
     * @throws Exception 生成失败时抛出异常
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
