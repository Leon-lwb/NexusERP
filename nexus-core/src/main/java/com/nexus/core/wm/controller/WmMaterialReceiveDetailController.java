package com.nexus.core.wm.controller;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import java.util.List;
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
import com.nexus.core.wm.domain.WmMaterialReceiveDetail;
import com.nexus.core.wm.service.IWmMaterialReceiveDetailService;
import com.nexus.common.utils.poi.ExcelUtil;
import com.nexus.common.core.page.TableDataInfo;

/**
 * 物料入库单明细Controller
 *
 * @author nexus
 * @date 2025-12-09
 */
@Api(tags = "物料入库单明细Controller")
@RestController
@RequestMapping("/wm/detail")
public class WmMaterialReceiveDetailController extends BaseController
{
    @Autowired
    private IWmMaterialReceiveDetailService wmMaterialReceiveDetailService;

    /**
     * 查询物料入库单明细列表
     */
    @ApiOperation("查询物料入库单明细列表")
    @PreAuthorize("@ss.hasPermi('wm:detail:list')")
    @GetMapping("/list")
    public TableDataInfo list(WmMaterialReceiveDetail wmMaterialReceiveDetail)
    {
        startPage();
        List<WmMaterialReceiveDetail> list = wmMaterialReceiveDetailService.selectWmMaterialReceiveDetailList(wmMaterialReceiveDetail);
        return getDataTable(list);
    }

    /**
     * 导出物料入库单明细列表
     */
    @ApiOperation("导出物料入库单明细列表")
    @PreAuthorize("@ss.hasPermi('wm:detail:export')")
    @Log(title = "物料入库单明细", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(HttpServletResponse response, WmMaterialReceiveDetail wmMaterialReceiveDetail)
    {
        List<WmMaterialReceiveDetail> list = wmMaterialReceiveDetailService.selectWmMaterialReceiveDetailList(wmMaterialReceiveDetail);
        ExcelUtil<WmMaterialReceiveDetail> util = new ExcelUtil<WmMaterialReceiveDetail>(WmMaterialReceiveDetail.class);
        util.exportExcel(response, list, "物料入库单明细数据");
    }

    /**
     * 获取物料入库单明细详细信息
     */
    @ApiOperation("获取物料入库单明细列表")
    @PreAuthorize("@ss.hasPermi('wm:detail:query')")
    @GetMapping(value = "/{detailId}")
    public AjaxResult getInfo(@PathVariable("detailId") Long detailId)
    {
        return success(wmMaterialReceiveDetailService.selectWmMaterialReceiveDetailByDetailId(detailId));
    }

    /**
     * 新增物料入库单明细
     */
    @ApiOperation("新增物料入库单明细详细信息")
    @PreAuthorize("@ss.hasPermi('wm:detail:add')")
    @Log(title = "物料入库单明细", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@RequestBody WmMaterialReceiveDetail wmMaterialReceiveDetail)
    {
        return toAjax(wmMaterialReceiveDetailService.insertWmMaterialReceiveDetail(wmMaterialReceiveDetail));
    }

    /**
     * 修改物料入库单明细
     */
    @ApiOperation("修改物料入库单明细详细信息")
    @PreAuthorize("@ss.hasPermi('wm:detail:edit')")
    @Log(title = "物料入库单明细", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@RequestBody WmMaterialReceiveDetail wmMaterialReceiveDetail)
    {
        return toAjax(wmMaterialReceiveDetailService.updateWmMaterialReceiveDetail(wmMaterialReceiveDetail));
    }

    /**
     * 删除物料入库单明细
     */
    @ApiOperation("删除物料入库单明细信息")
    @PreAuthorize("@ss.hasPermi('wm:detail:remove')")
    @Log(title = "物料入库单明细", businessType = BusinessType.DELETE)
    @DeleteMapping("/{detailIds}")
    public AjaxResult remove(@PathVariable Long[] detailIds)
    {
        return toAjax(wmMaterialReceiveDetailService.deleteWmMaterialReceiveDetailByDetailIds(detailIds));
    }
}
