package com.nexus.core.wm.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.nexus.common.constant.NexusConstants;
import com.nexus.common.utils.StringUtils;
import com.nexus.core.wm.domain.WmMaterialReceiveDetail;
import com.nexus.core.wm.domain.WmMaterialReceiveLine;
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
import com.nexus.core.wm.domain.WmMaterialReceive;
import com.nexus.core.wm.service.IWmMaterialReceiveService;
import com.nexus.core.wm.service.IWmInventoryCoreService;
import com.nexus.common.utils.poi.ExcelUtil;
import com.nexus.common.core.page.TableDataInfo;
import com.nexus.common.exception.ServiceException;

/**
 * 物料入库单Controller
 *
 * @author nexus
 * @date 2025-12-08
 */
@Api(tags = "物料入库单Controller")
@RestController
@RequestMapping("/wm/receive")
public class WmMaterialReceiveController extends BaseController
{
    @Autowired
    private IWmMaterialReceiveService wmMaterialReceiveService;
    
    @Autowired
    private IWmInventoryCoreService wmInventoryCoreService;

    /**
     * 查询物料入库单列表
     */
    @ApiOperation("查询物料入库单列表")
    @PreAuthorize("@ss.hasPermi('wm:receive:list')")
    @GetMapping("/list")
    public TableDataInfo list(WmMaterialReceive wmMaterialReceive)
    {
        startPage();
        List<WmMaterialReceive> list = wmMaterialReceiveService.selectWmMaterialReceiveList(wmMaterialReceive);
        return getDataTable(list);
    }

    /**
     * 导出物料入库单列表
     */
    @ApiOperation("导出物料入库单列表")
    @PreAuthorize("@ss.hasPermi('wm:receive:export')")
    @Log(title = "物料入库单", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(HttpServletResponse response, WmMaterialReceive wmMaterialReceive)
    {
        List<WmMaterialReceive> list = wmMaterialReceiveService.selectWmMaterialReceiveList(wmMaterialReceive);
        ExcelUtil<WmMaterialReceive> util = new ExcelUtil<WmMaterialReceive>(WmMaterialReceive.class);
        util.exportExcel(response, list, "物料入库单数据");
    }

    /**
     * 获取物料入库单详细信息
     */
    @ApiOperation("获取物料入库单列表")
    @PreAuthorize("@ss.hasPermi('wm:receive:query')")
    @GetMapping(value = "/{receiveId}")
    public AjaxResult getInfo(@PathVariable("receiveId") Long receiveId)
    {
        return success(wmMaterialReceiveService.selectWmMaterialReceiveByReceiveId(receiveId));
    }

    /**
     * 新增物料入库单
     */
    @ApiOperation("新增物料入库单详细信息")
    @PreAuthorize("@ss.hasPermi('wm:receive:add')")
    @Log(title = "物料入库单", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@RequestBody WmMaterialReceive wmMaterialReceive)
    {
        return toAjax(wmMaterialReceiveService.insertWmMaterialReceive(wmMaterialReceive));
    }

    /**
     * 修改物料入库单
     */
    @ApiOperation("修改物料入库单详细信息")
    @PreAuthorize("@ss.hasPermi('wm:receive:edit')")
    @Log(title = "物料入库单", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@RequestBody WmMaterialReceive wmMaterialReceive)
    {
        try {
            return toAjax(wmMaterialReceiveService.updateWithValidation(wmMaterialReceive));
        } catch (ServiceException e) {
            return AjaxResult.error(e.getMessage());
        }
    }

    /**
     * 删除物料入库单
     */
    @ApiOperation("删除物料入库单信息")
    @PreAuthorize("@ss.hasPermi('wm:receive:remove')")
    @Log(title = "物料入库单", businessType = BusinessType.DELETE)
    @DeleteMapping("/{receiveIds}")
    public AjaxResult remove(@PathVariable Long[] receiveIds)
    {
        return toAjax(wmMaterialReceiveService.deleteWmMaterialReceiveByReceiveIds(receiveIds));
    }
    
    /**
     * 执行物料入库
     * @param receiveId 入库单ID
     * @return 结果
     */
    @ApiOperation("执行物料入库")
    @PreAuthorize("@ss.hasPermi('wm:receive:execute')")
    @Log(title = "物料入库单", businessType = BusinessType.UPDATE)
    @PostMapping("/execute/{receiveId}")
    public AjaxResult executeReceive(@PathVariable Long receiveId) {
        return wmInventoryCoreService.executeReceive(receiveId);
    }
}