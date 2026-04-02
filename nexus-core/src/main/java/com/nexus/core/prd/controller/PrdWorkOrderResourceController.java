package com.nexus.core.prd.controller;

import java.util.List;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.nexus.common.annotation.Log;
import com.nexus.common.core.controller.BaseController;
import com.nexus.common.core.domain.AjaxResult;
import com.nexus.common.enums.BusinessType;
import com.nexus.core.prd.domain.PrdWorkOrderResource;
import com.nexus.core.prd.service.IPrdWorkOrderResourceService;
import com.nexus.common.utils.poi.ExcelUtil;
import com.nexus.common.core.page.TableDataInfo;

/**
 * 生产工单资源分配Controller
 *
 * @author nexus
 * @date 2025-12-16
 */
@RestController
@RequestMapping("/prd/workOrderResource")
public class PrdWorkOrderResourceController extends BaseController {

    @Autowired
    private IPrdWorkOrderResourceService prdWorkOrderResourceService;

    /**
     * 查询生产工单资源分配列表
     */
    @PreAuthorize("@ss.hasPermi('prd:workOrderResource:list')")
    @GetMapping("/list")
    public TableDataInfo list(PrdWorkOrderResource prdWorkOrderResource) {
        startPage();
        List<PrdWorkOrderResource> list = prdWorkOrderResourceService.selectPrdWorkOrderResourceList(prdWorkOrderResource);
        return getDataTable(list);
    }

    /**
     * 导出生产工单资源分配列表
     */
    @PreAuthorize("@ss.hasPermi('prd:workOrderResource:export')")
    @Log(title = "生产工单资源分配", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(HttpServletResponse response, PrdWorkOrderResource prdWorkOrderResource) {
        List<PrdWorkOrderResource> list = prdWorkOrderResourceService.selectPrdWorkOrderResourceList(prdWorkOrderResource);
        ExcelUtil<PrdWorkOrderResource> util = new ExcelUtil<>(PrdWorkOrderResource.class);
        util.exportExcel(response, list, "生产工单资源分配数据");
    }

    /**
     * 获取生产工单资源分配详细信息
     */
    @PreAuthorize("@ss.hasPermi('prd:workOrderResource:query')")
    @GetMapping(value = "/{id}")
    public AjaxResult getInfo(@PathVariable("id") Long id) {
        return success(prdWorkOrderResourceService.getById(id));
    }

    /**
     * 新增生产工单资源分配
     */
    @PreAuthorize("@ss.hasPermi('prd:workOrderResource:add')")
    @Log(title = "生产工单资源分配", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@RequestBody PrdWorkOrderResource prdWorkOrderResource) {
        return toAjax(prdWorkOrderResourceService.save(prdWorkOrderResource));
    }

    /**
     * 修改生产工单资源分配
     */
    @PreAuthorize("@ss.hasPermi('prd:workOrderResource:edit')")
    @Log(title = "生产工单资源分配", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@RequestBody PrdWorkOrderResource prdWorkOrderResource) {
        return toAjax(prdWorkOrderResourceService.updateById(prdWorkOrderResource));
    }

    /**
     * 删除生产工单资源分配
     */
    @PreAuthorize("@ss.hasPermi('prd:workOrderResource:remove')")
    @Log(title = "生产工单资源分配", businessType = BusinessType.DELETE)
    @DeleteMapping("/{ids}")
    public AjaxResult remove(@PathVariable Long[] ids) {
        return toAjax(prdWorkOrderResourceService.removeByIds(java.util.Arrays.asList(ids)));
    }

    /**
     * 根据工单ID查询资源分配列表
     */
    @PreAuthorize("@ss.hasPermi('prd:workOrderResource:query')")
    @GetMapping("/wo/{woId}")
    public AjaxResult getByWoId(@PathVariable("woId") Long woId) {
        List<PrdWorkOrderResource> list = prdWorkOrderResourceService.selectByWoId(woId);
        return success(list);
    }

    /**
     * 根据工单ID和资源类型查询资源分配列表
     */
    @PreAuthorize("@ss.hasPermi('prd:workOrderResource:query')")
    @GetMapping("/wo/{woId}/type/{resourceType}")
    public AjaxResult getByWoIdAndType(@PathVariable("woId") Long woId, @PathVariable("resourceType") String resourceType) {
        List<PrdWorkOrderResource> list = prdWorkOrderResourceService.selectByWoIdAndType(woId, resourceType);
        return success(list);
    }

    /**
     * 批量保存工单资源分配
     */
    @PreAuthorize("@ss.hasPermi('prd:workOrderResource:add')")
    @Log(title = "生产工单资源分配", businessType = BusinessType.INSERT)
    @PostMapping("/batch/{woId}")
    public AjaxResult saveBatch(@PathVariable("woId") Long woId, @RequestBody List<PrdWorkOrderResource> resourceList) {
        return toAjax(prdWorkOrderResourceService.saveBatch(woId, resourceList));
    }
}
