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
import com.nexus.common.utils.poi.ExcelUtil;
import com.nexus.common.core.page.TableDataInfo;

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
        // 检查入库单编号是否已经存在
        LambdaQueryWrapper<WmMaterialReceive> queryWrapper = new LambdaQueryWrapper<>();
        if (wmMaterialReceive.getReceiveCode() != null){
            queryWrapper.eq(WmMaterialReceive::getReceiveCode, wmMaterialReceive.getReceiveCode());
            Long receiveId = wmMaterialReceive.getReceiveId() == null ? -1L : wmMaterialReceive.getReceiveId();

            List<WmMaterialReceive> existingReceives = wmMaterialReceiveService.list(queryWrapper);
            if (!existingReceives.isEmpty()) {
                WmMaterialReceive existingReceive = existingReceives.get(0);
                if (!existingReceive.getReceiveId().equals(receiveId)) {
                    return AjaxResult.error("编号已存在！");
                }
            }
        } else {
            return AjaxResult.error("编号不能为空！");
        }

        // 如果审核到待上架状态，则检查是否有对应的入库单行
        if (NexusConstants.ORDER_STATUS_APPROVING.equals(wmMaterialReceive.getStatus())) {
            // 检查是否存在入库单行表
            if (wmMaterialReceive.getWmMaterialReceiveLineList() == null || wmMaterialReceive.getWmMaterialReceiveLineList().isEmpty()) {
                return AjaxResult.error("入库单行不能为空！");
            }
        }

        // 如果是提交到待入库状态，则检查各个行表是否都已分配了对应的明细行，数量必须一致。
        if (NexusConstants.ORDER_STATUS_APPROVED.equals(wmMaterialReceive.getStatus())) {
            boolean isValid = true;
            StringBuilder errorMsg = new StringBuilder();
            
            if (wmMaterialReceive.getWmMaterialReceiveLineList() == null || 
                wmMaterialReceive.getWmMaterialReceiveLineList().isEmpty()) {
                isValid = false;
                errorMsg.append("入库单行不能为空！");
            } else {
                for (WmMaterialReceiveLine line : wmMaterialReceive.getWmMaterialReceiveLineList()) {
                    // 检查行表是否已分配对应的明细行
                    if (line.getWmMaterialReceiveLineDetailList() == null || 
                        line.getWmMaterialReceiveLineDetailList().isEmpty()) {
                        isValid = false;
                        errorMsg.append("物料[").append(line.getMaterialName())
                               .append("]未分配对应的明细行！");
                    } else {
                        // 检查行表数量必须一致
                        for (WmMaterialReceiveDetail detail : line.getWmMaterialReceiveLineDetailList()) {
                            if (detail.getQuantity().compareTo(line.getQuantityReceived()) != 0) {
                                isValid = false;
                                errorMsg.append("物料[").append(line.getMaterialName())
                                       .append("]明细数量与入库数量不一致！");
                            }
                        }
                    }
                }
            }
            
            if (!isValid) {
                return AjaxResult.error(errorMsg.toString());
            }
        }


        return toAjax(wmMaterialReceiveService.updateWmMaterialReceive(wmMaterialReceive));
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
}