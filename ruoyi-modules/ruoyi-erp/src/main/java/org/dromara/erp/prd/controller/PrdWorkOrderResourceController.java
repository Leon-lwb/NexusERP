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
import org.dromara.erp.prd.domain.PrdWorkOrderResource;
import org.dromara.erp.prd.domain.bo.PrdWorkOrderResourceBo;
import org.dromara.erp.prd.domain.vo.PrdWorkOrderResourceVo;
import org.dromara.erp.prd.service.IPrdWorkOrderResourceService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 生产工单资源分配Controller
 *
 * @author ruoyi
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/erp/prd/workOrderResource")
public class PrdWorkOrderResourceController extends BaseController {

    private final IPrdWorkOrderResourceService prdWorkOrderResourceService;

    /**
     * 查询生产工单资源分配列表
     */
    @SaCheckPermission("prd:workOrderResource:list")
    @GetMapping("/list")
    public TableDataInfo<PrdWorkOrderResourceVo> list(PrdWorkOrderResourceBo bo, PageQuery pageQuery) {
        return prdWorkOrderResourceService.selectPageList(bo, pageQuery);
    }

    /**
     * 导出生产工单资源分配列表
     */
    @SaCheckPermission("prd:workOrderResource:export")
    @Log(title = "生产工单资源分配", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(PrdWorkOrderResourceBo bo, HttpServletResponse response) {
        List<PrdWorkOrderResourceVo> list = prdWorkOrderResourceService.selectList(bo);
        ExcelUtil.exportExcel(list, "生产工单资源分配数据", PrdWorkOrderResourceVo.class, response);
    }

    /**
     * 获取生产工单资源分配详细信息
     */
    @SaCheckPermission("prd:workOrderResource:query")
    @GetMapping("/{id}")
    public R<PrdWorkOrderResourceVo> getInfo(@PathVariable Long id) {
        return R.ok(prdWorkOrderResourceService.selectById(id));
    }

    /**
     * 新增生产工单资源分配
     */
    @SaCheckPermission("prd:workOrderResource:add")
    @Log(title = "生产工单资源分配", businessType = BusinessType.INSERT)
    @PostMapping
    public R<Void> add(@Validated @RequestBody PrdWorkOrderResourceBo bo) {
        return toAjax(prdWorkOrderResourceService.insertByBo(bo));
    }

    /**
     * 修改生产工单资源分配
     */
    @SaCheckPermission("prd:workOrderResource:edit")
    @Log(title = "生产工单资源分配", businessType = BusinessType.UPDATE)
    @PutMapping
    public R<Void> edit(@Validated @RequestBody PrdWorkOrderResourceBo bo) {
        return toAjax(prdWorkOrderResourceService.updateByBo(bo));
    }

    /**
     * 删除生产工单资源分配
     */
    @SaCheckPermission("prd:workOrderResource:remove")
    @Log(title = "生产工单资源分配", businessType = BusinessType.DELETE)
    @DeleteMapping("/{ids}")
    public R<Void> remove(@PathVariable Long[] ids) {
        return toAjax(prdWorkOrderResourceService.deleteByIds(ids));
    }

    /**
     * 根据工单ID查询资源分配列表
     */
    @SaCheckPermission("prd:workOrderResource:query")
    @GetMapping("/wo/{woId}")
    public R<List<PrdWorkOrderResource>> getByWoId(@PathVariable Long woId) {
        return R.ok(prdWorkOrderResourceService.selectByWoId(woId));
    }

    /**
     * 根据工单ID和资源类型查询资源分配列表
     */
    @SaCheckPermission("prd:workOrderResource:query")
    @GetMapping("/wo/{woId}/type/{resourceType}")
    public R<List<PrdWorkOrderResource>> getByWoIdAndType(@PathVariable Long woId,
                                                          @PathVariable String resourceType) {
        return R.ok(prdWorkOrderResourceService.selectByWoIdAndType(woId, resourceType));
    }

    /**
     * 批量保存工单资源分配
     */
    @SaCheckPermission("prd:workOrderResource:add")
    @Log(title = "生产工单资源分配", businessType = BusinessType.INSERT)
    @PostMapping("/batch/{woId}")
    public R<Void> saveBatch(@PathVariable Long woId, @RequestBody List<PrdWorkOrderResource> resourceList) {
        return toAjax(prdWorkOrderResourceService.saveBatch(woId, resourceList));
    }
}
