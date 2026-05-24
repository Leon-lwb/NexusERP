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
import org.dromara.erp.prd.domain.PrdMaterialRequirement;
import org.dromara.erp.prd.domain.PrdMaterialRequirementDetail;
import org.dromara.erp.prd.domain.bo.PrdMaterialRequirementBo;
import org.dromara.erp.prd.domain.vo.PrdMaterialRequirementVo;
import org.dromara.erp.prd.service.IPrdMaterialRequirementService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

/**
 * 物料清单（物料需求） Controller
 * <p>
 * 提供物料需求清单的 RESTful API 接口，包括：
 * <ul>
 *   <li>CRUD 操作：分页列表查询、详情查询、新增、编辑、删除</li>
 *   <li>数据导出（Excel）</li>
 *   <li>按生产计划查询物料需求与树形展示</li>
 *   <li>从 BOM 导入物料需求明细</li>
 * </ul>
 *
 * @author ruoyi
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/erp/prd/requirement")
public class PrdMaterialRequirementController extends BaseController {

    private final IPrdMaterialRequirementService prdMaterialRequirementService;

    /**
     * 分页查询物料需求清单列表
     * <p>
     * 权限标识：{@code prd:requirement:list}</p>
     *
     * @param bo        查询参数，包含物料编码、生产计划编号等筛选条件
     * @param pageQuery 分页参数，包含页码、每页条数及排序信息
     * @return 物料需求清单分页列表
     */
    @SaCheckPermission("prd:requirement:list")
    @GetMapping("/list")
    public TableDataInfo<PrdMaterialRequirementVo> list(PrdMaterialRequirementBo bo, PageQuery pageQuery) {
        return prdMaterialRequirementService.selectPageList(bo, pageQuery);
    }

    /**
     * 导出物料需求清单数据到 Excel
     * <p>权限标识：{@code prd:requirement:export}</p>
     * <p>日志记录：title="物料清单"，businessType=EXPORT</p>
     *
     * @param bo        查询参数
     * @param response  HTTP 响应对象，用于输出 Excel 文件流
     */
    @SaCheckPermission("prd:requirement:export")
    @Log(title = "物料清单", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(PrdMaterialRequirementBo bo, HttpServletResponse response) {
        List<PrdMaterialRequirementVo> list = prdMaterialRequirementService.selectList(bo);
        ExcelUtil.exportExcel(list, "物料清单数据", PrdMaterialRequirementVo.class, response);
    }

    /**
     * 根据主键获取物料需求清单详细信息
     * <p>
     * 权限标识：{@code prd:requirement:query}</p>
     *
     * @param requirementId 物料需求 ID
     * @return 物料需求清单详细信息
     */
    @SaCheckPermission("prd:requirement:query")
    @GetMapping("/{requirementId}")
    public R<PrdMaterialRequirementVo> getInfo(@PathVariable Long requirementId) {
        return R.ok(prdMaterialRequirementService.selectById(requirementId));
    }

    /**
     * 新增物料需求清单
     * <p>
     * 权限标识：{@code prd:requirement:add}</p>
     * <p>日志记录：title="物料清单"，businessType=INSERT</p>
     *
     * @param bo 物料需求业务对象，包含物料、数量、生产计划等信息
     * @return 操作结果，成功返回 {@code R.success()}，失败返回 {@code R.fail()}
     */
    @SaCheckPermission("prd:requirement:add")
    @Log(title = "物料清单", businessType = BusinessType.INSERT)
    @PostMapping
    public R<Void> add(@Validated @RequestBody PrdMaterialRequirementBo bo) {
        return toAjax(prdMaterialRequirementService.insertByBo(bo));
    }

    /**
     * 修改物料需求清单
     * <p>
     * 权限标识：{@code prd:requirement:edit}</p>
     * <p>日志记录：title="物料清单"，businessType=UPDATE</p>
     *
     * @param bo 物料需求业务对象，包含需要更新的字段信息
     * @return 操作结果，成功返回 {@code R.success()}，失败返回 {@code R.fail()}
     */
    @SaCheckPermission("prd:requirement:edit")
    @Log(title = "物料清单", businessType = BusinessType.UPDATE)
    @PutMapping
    public R<Void> edit(@Validated @RequestBody PrdMaterialRequirementBo bo) {
        return toAjax(prdMaterialRequirementService.updateByBo(bo));
    }

    /**
     * 批量删除物料需求清单
     * <p>
     * 权限标识：{@code prd:requirement:remove}</p>
     * <p>日志记录：title="物料清单"，businessType=DELETE</p>
     *
     * @param requirementIds 要删除的物料需求 ID 数组（支持批量删除）
     * @return 操作结果，成功返回 {@code R.success()}，失败返回 {@code R.fail()}
     */
    @SaCheckPermission("prd:requirement:remove")
    @Log(title = "物料清单", businessType = BusinessType.DELETE)
    @DeleteMapping("/{requirementIds}")
    public R<Void> remove(@PathVariable Long[] requirementIds) {
        return toAjax(prdMaterialRequirementService.deleteByIds(requirementIds));
    }

    /**
     * 根据生产计划编号查询物料需求清单（含明细，无分页）
     * <p>
     * 权限标识：{@code prd:requirement:list}</p>
     *
     * @param planNo 生产计划编号
     * @return 物料需求清单列表（含明细）
     */
    @SaCheckPermission("prd:requirement:list")
    @GetMapping("/listDetailsByPlanNo/{planNo}")
    public R<List<PrdMaterialRequirement>> listDetailsByPlanNo(@PathVariable String planNo) {
        PrdMaterialRequirement queryParam = new PrdMaterialRequirement();
        queryParam.setPlanNo(planNo);
        List<PrdMaterialRequirement> mainList = prdMaterialRequirementService.selectPrdMaterialRequirementListWithDetails(queryParam);
        return R.ok(mainList);
    }

    /**
     * 根据计划编号查询物料需求树（含父子嵌套，用于树形展示）
     * <p>
     * 权限标识：{@code prd:requirement:list}</p>
     *
     * @param planNo 生产计划编号
     * @return 物料需求树形结构
     */
    @SaCheckPermission("prd:requirement:list")
    @GetMapping("/treeByPlanNo/{planNo}")
    public R<List<PrdMaterialRequirementVo>> treeByPlanNo(@PathVariable String planNo) {
        List<PrdMaterialRequirementVo> tree = prdMaterialRequirementService.selectPrdMaterialRequirementTreeByPlanNo(planNo);
        return R.ok(tree);
    }

    /**
     * 从 BOM 导入物料需求明细
     * <p>
     * 权限标识：{@code prd:requirement:query}</p>
     *
     * @param bomId                BOM 配置 ID
     * @param totalRequiredQuantity 总需求数量
     * @return BOM 展开的物料需求明细列表
     */
    @SaCheckPermission("prd:requirement:query")
    @GetMapping("/importBomDetails")
    public R<List<PrdMaterialRequirementDetail>> importBomDetails(
            @RequestParam Long bomId,
            @RequestParam BigDecimal totalRequiredQuantity) {
        List<PrdMaterialRequirementDetail> detailList =
            prdMaterialRequirementService.importBomDetails(bomId, totalRequiredQuantity);
        return R.ok(detailList);
    }
}
