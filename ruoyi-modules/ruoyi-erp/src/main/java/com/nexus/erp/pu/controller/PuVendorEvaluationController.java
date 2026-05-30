package com.nexus.erp.pu.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import com.nexus.common.core.domain.R;
import com.nexus.common.excel.utils.ExcelUtil;
import com.nexus.common.log.annotation.Log;
import com.nexus.common.log.enums.BusinessType;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.common.web.core.BaseController;
import com.nexus.erp.pu.domain.bo.PuVendorEvaluationBo;
import com.nexus.erp.pu.domain.vo.PuVendorEvaluationVo;
import com.nexus.erp.pu.service.IPuVendorEvaluationService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 供应商评估控制器
 * <p>
 * 提供供应商绩效评估的 RESTful API 接口，包括：
 * <ul>
 *   <li>CRUD 操作：分页列表查询、详情查询、新增、编辑、删除</li>
 *   <li>数据导出（Excel）</li>
 *   <li>评估计算：按供应商单条计算、批量全量计算</li>
 * </ul>
 * 所有接口均通过 Sa-Token 注解 {@link SaCheckPermission} 进行权限校验。
 *
 * @author ruoyi
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/erp/pu/vendor-evaluation")
public class PuVendorEvaluationController extends BaseController {

    private final IPuVendorEvaluationService vendorEvaluationService;

    /**
     * 分页查询供应商评估列表
     * <p>
     * 权限标识：{@code pu:vendorEvaluation:list}</p>
     *
     * @param bo        查询参数，包含供应商、评估周期等筛选条件
     * @param pageQuery 分页参数，包含页码、每页条数及排序信息
     * @return 供应商评估分页列表
     */
    @SaCheckPermission("pu:vendorEvaluation:list")
    @GetMapping("/list")
    public TableDataInfo<PuVendorEvaluationVo> list(PuVendorEvaluationBo bo, PageQuery pageQuery) {
        return vendorEvaluationService.selectPageList(bo, pageQuery);
    }

    /**
     * 导出供应商评估数据到 Excel
     * <p>权限标识：{@code pu:vendorEvaluation:export}</p>
     * <p>日志记录：title="供应商评估"，businessType=EXPORT</p>
     *
     * @param bo        查询参数
     * @param response  HTTP 响应对象，用于输出 Excel 文件流
     */
    @SaCheckPermission("pu:vendorEvaluation:export")
    @Log(title = "供应商评估", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(PuVendorEvaluationBo bo, HttpServletResponse response) {
        List<PuVendorEvaluationVo> list = vendorEvaluationService.selectList(bo);
        ExcelUtil.exportExcel(list, "供应商评估数据", PuVendorEvaluationVo.class, response);
    }

    /**
     * 根据主键获取供应商评估详情
     * <p>
     * 权限标识：{@code pu:vendorEvaluation:query}</p>
     *
     * @param evaluationId 评估记录 ID
     * @return 供应商评估详细信息
     */
    @SaCheckPermission("pu:vendorEvaluation:query")
    @GetMapping("/{evaluationId}")
    public R<PuVendorEvaluationVo> getInfo(@PathVariable Long evaluationId) {
        return R.ok(vendorEvaluationService.selectById(evaluationId));
    }

    /**
     * 新增供应商评估记录
     * <p>
     * 权限标识：{@code pu:vendorEvaluation:add}</p>
     * <p>日志记录：title="供应商评估"，businessType=INSERT</p>
     *
     * @param bo 供应商评估业务对象
     * @return 操作结果，成功返回 {@code R.success()}，失败返回 {@code R.fail()}
     */
    @SaCheckPermission("pu:vendorEvaluation:add")
    @Log(title = "供应商评估", businessType = BusinessType.INSERT)
    @PostMapping
    public R<Void> add(@Validated @RequestBody PuVendorEvaluationBo bo) {
        return toAjax(vendorEvaluationService.insertByBo(bo));
    }

    /**
     * 修改供应商评估记录
     * <p>
     * 权限标识：{@code pu:vendorEvaluation:edit}</p>
     * <p>日志记录：title="供应商评估"，businessType=UPDATE</p>
     *
     * @param bo 供应商评估业务对象，包含需要更新的字段信息
     * @return 操作结果，成功返回 {@code R.success()}，失败返回 {@code R.fail()}
     */
    @SaCheckPermission("pu:vendorEvaluation:edit")
    @Log(title = "供应商评估", businessType = BusinessType.UPDATE)
    @PutMapping
    public R<Void> edit(@Validated @RequestBody PuVendorEvaluationBo bo) {
        return toAjax(vendorEvaluationService.updateByBo(bo));
    }

    /**
     * 批量删除供应商评估记录
     * <p>
     * 权限标识：{@code pu:vendorEvaluation:remove}</p>
     * <p>日志记录：title="供应商评估"，businessType=DELETE</p>
     *
     * @param evaluationIds 要删除的评估记录 ID 数组（支持批量删除）
     * @return 操作结果，成功返回 {@code R.success()}，失败返回 {@code R.fail()}
     */
    @SaCheckPermission("pu:vendorEvaluation:remove")
    @Log(title = "供应商评估", businessType = BusinessType.DELETE)
    @DeleteMapping("/{evaluationIds}")
    public R<Void> remove(@PathVariable Long[] evaluationIds) {
        return toAjax(vendorEvaluationService.deleteByIds(evaluationIds));
    }

    /**
     * 按供应商计算绩效评估
     * <p>
     * 根据供应商ID触发单条供应商的绩效评估计算，统计该供应商在评估周期内的
     * 交货及时率、质量合格率、价格竞争力等指标并生成评估记录。
     * <p>权限标识：{@code pu:vendorEvaluation:edit}</p>
     * <p>日志记录：title="供应商评估-计算"，businessType=UPDATE</p>
     *
     * @param vendorId 供应商 ID
     * @return 操作结果
     */
    @SaCheckPermission("pu:vendorEvaluation:edit")
    @Log(title = "供应商评估-计算", businessType = BusinessType.UPDATE)
    @PostMapping("/calculate/{vendorId}")
    public R<Void> calculate(@PathVariable Long vendorId) {
        vendorEvaluationService.calculateByVendor(vendorId);
        return R.ok();
    }

    /**
     * 批量计算所有供应商绩效评估
     * <p>
     * 扫描所有状态为 ACTIVE 的供应商，逐条触发绩效评估计算。
     * 通常由定时任务调用，支持手动触发。
     * <p>权限标识：{@code pu:vendorEvaluation:edit}</p>
     * <p>日志记录：title="供应商评估-批量计算"，businessType=UPDATE</p>
     *
     * @return 操作结果
     */
    @SaCheckPermission("pu:vendorEvaluation:edit")
    @Log(title = "供应商评估-批量计算", businessType = BusinessType.UPDATE)
    @PostMapping("/calculate-all")
    public R<Void> calculateAll() {
        vendorEvaluationService.batchCalculateAll();
        return R.ok();
    }

    /**
     * 查询供应商评估趋势数据
     * <p>
     * 根据供应商ID查询其历史评估记录，按评估周期升序返回，用于前端趋势折线图展示。</p>
     * <p>权限标识：{@code pu:vendorEvaluation:query}</p>
     *
     * @param vendorId 供应商 ID
     * @return 供应商评估趋势列表
     */
    @SaCheckPermission("pu:vendorEvaluation:query")
    @GetMapping("/trend/{vendorId}")
    public R<List<PuVendorEvaluationVo>> trend(@PathVariable Long vendorId) {
        return R.ok(vendorEvaluationService.selectTrendByVendorId(vendorId));
    }

    /**
     * 查询供应商评估等级分布
     * <p>
     * 统计所有评估记录中 A/B/C/D 四个等级的供应商数量，用于前端饼图展示。</p>
     * <p>权限标识：{@code pu:vendorEvaluation:list}</p>
     *
     * @return 等级分布统计，key 为等级(A/B/C/D)，value 为供应商数量
     */
    @SaCheckPermission("pu:vendorEvaluation:list")
    @GetMapping("/distribution")
    public R<Map<String, Integer>> distribution() {
        return R.ok(vendorEvaluationService.selectLevelDistribution());
    }
}
