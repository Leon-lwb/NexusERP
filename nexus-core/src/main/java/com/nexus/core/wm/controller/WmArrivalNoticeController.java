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
import com.nexus.core.wm.domain.WmArrivalNotice;
import com.nexus.core.wm.service.IWmArrivalNoticeService;
import com.nexus.common.utils.poi.ExcelUtil;
import com.nexus.common.core.page.TableDataInfo;

/**
 * 到货通知Controller
 *
 * @author nexus
 * @date 2025-12-04
 */
@Api(tags = "到货通知Controller")
@RestController
@RequestMapping("/wm/arrivalnotice")
public class WmArrivalNoticeController extends BaseController
{
    @Autowired
    private IWmArrivalNoticeService wmArrivalNoticeService;

    /**
     * 查询到货通知列表
     */
    @ApiOperation("查询到货通知列表")
    @PreAuthorize("@ss.hasPermi('wm:arrivalnotice:list')")
    @GetMapping("/list")
    public TableDataInfo list(WmArrivalNotice wmArrivalNotice)
    {
        startPage();
        List<WmArrivalNotice> list = wmArrivalNoticeService.selectWmArrivalNoticeList(wmArrivalNotice);
        return getDataTable(list);
    }

    /**
     * 导出到货通知列表
     */
    @ApiOperation("导出到货通知列表")
    @PreAuthorize("@ss.hasPermi('wm:arrivalnotice:export')")
    @Log(title = "到货通知", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(HttpServletResponse response, WmArrivalNotice wmArrivalNotice)
    {
        List<WmArrivalNotice> list = wmArrivalNoticeService.selectWmArrivalNoticeList(wmArrivalNotice);
        ExcelUtil<WmArrivalNotice> util = new ExcelUtil<WmArrivalNotice>(WmArrivalNotice.class);
        util.exportExcel(response, list, "到货通知数据");
    }

    /**
     * 获取到货通知详细信息
     */
    @ApiOperation("获取到货通知列表")
    @PreAuthorize("@ss.hasPermi('wm:arrivalnotice:query')")
    @GetMapping(value = "/{noticeId}")
    public AjaxResult getInfo(@PathVariable("noticeId") Long noticeId)
    {
        return success(wmArrivalNoticeService.selectWmArrivalNoticeByNoticeId(noticeId));
    }

    /**
     * 新增到货通知
     */
    @ApiOperation("新增到货通知详细信息")
    @PreAuthorize("@ss.hasPermi('wm:arrivalnotice:add')")
    @Log(title = "到货通知", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@RequestBody WmArrivalNotice wmArrivalNotice)
    {
        return toAjax(wmArrivalNoticeService.insertWmArrivalNotice(wmArrivalNotice));
    }

    /**
     * 修改到货通知
     */
    @ApiOperation("修改到货通知详细信息")
    @PreAuthorize("@ss.hasPermi('wm:arrivalnotice:edit')")
    @Log(title = "到货通知", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@RequestBody WmArrivalNotice wmArrivalNotice)
    {
        return toAjax(wmArrivalNoticeService.updateWmArrivalNotice(wmArrivalNotice));
    }

    /**
     * 删除到货通知
     */
    @ApiOperation("删除到货通知信息")
    @PreAuthorize("@ss.hasPermi('wm:arrivalnotice:remove')")
    @Log(title = "到货通知", businessType = BusinessType.DELETE)
    @DeleteMapping("/{noticeIds}")
    public AjaxResult remove(@PathVariable Long[] noticeIds)
    {
        return toAjax(wmArrivalNoticeService.deleteWmArrivalNoticeByNoticeIds(noticeIds));
    }
}
