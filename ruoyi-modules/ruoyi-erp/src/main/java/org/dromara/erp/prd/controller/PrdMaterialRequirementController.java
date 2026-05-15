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
 * 物料清单Controller
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
     * 查询物料清单列表
     */
    @SaCheckPermission("prd:requirement:list")
    @GetMapping("/list")
    public TableDataInfo<PrdMaterialRequirementVo> list(PrdMaterialRequirementBo bo, PageQuery pageQuery) {
        return prdMaterialRequirementService.selectPageList(bo, pageQuery);
    }

    /**
     * 导出物料清单列表
     */
    @SaCheckPermission("prd:requirement:export")
    @Log(title = "物料清单", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(PrdMaterialRequirementBo bo, HttpServletResponse response) {
        List<PrdMaterialRequirementVo> list = prdMaterialRequirementService.selectList(bo);
        ExcelUtil.exportExcel(list, "物料清单数据", PrdMaterialRequirementVo.class, response);
    }

    /**
     * 获取物料清单详细信息
     */
    @SaCheckPermission("prd:requirement:query")
    @GetMapping("/{requirementId}")
    public R<PrdMaterialRequirementVo> getInfo(@PathVariable Long requirementId) {
        return R.ok(prdMaterialRequirementService.selectById(requirementId));
    }

    /**
     * 新增物料清单
     */
    @SaCheckPermission("prd:requirement:add")
    @Log(title = "物料清单", businessType = BusinessType.INSERT)
    @PostMapping
    public R<Void> add(@Validated @RequestBody PrdMaterialRequirementBo bo) {
        return toAjax(prdMaterialRequirementService.insertByBo(bo));
    }

    /**
     * 修改物料清单
     */
    @SaCheckPermission("prd:requirement:edit")
    @Log(title = "物料清单", businessType = BusinessType.UPDATE)
    @PutMapping
    public R<Void> edit(@Validated @RequestBody PrdMaterialRequirementBo bo) {
        return toAjax(prdMaterialRequirementService.updateByBo(bo));
    }

    /**
     * 删除物料清单
     */
    @SaCheckPermission("prd:requirement:remove")
    @Log(title = "物料清单", businessType = BusinessType.DELETE)
    @DeleteMapping("/{requirementIds}")
    public R<Void> remove(@PathVariable Long[] requirementIds) {
        return toAjax(prdMaterialRequirementService.deleteByIds(requirementIds));
    }

    /**
     * 根据生产计划编号查询物料清单（含明细，无分页）
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
     * 从BOM导入物料需求明细
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
