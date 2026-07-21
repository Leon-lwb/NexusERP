package com.nexus.erp.wm.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import com.nexus.common.core.domain.R;
import com.nexus.common.excel.utils.ExcelUtil;
import com.nexus.common.log.annotation.Log;
import com.nexus.common.log.enums.BusinessType;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.common.web.core.BaseController;
import com.nexus.erp.wm.domain.bo.WmBatchBo;
import com.nexus.erp.wm.domain.vo.WmBatchVo;
import com.nexus.erp.wm.service.IWmBatchService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

/**
 * 批次管理 Controller
 *
 * @author nexus
 * @date 2026-06-02
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/erp/wm/batch")
public class WmBatchController extends BaseController {

    /** 批次业务服务，提供批次相关能力 */
    private final IWmBatchService wmBatchService;

    /**
     * 分页查询批次管理列表
     */
    @SaCheckPermission("wm:batch:list")
    @GetMapping("/list")
    public TableDataInfo<WmBatchVo> list(WmBatchBo bo, PageQuery pageQuery) {
        return wmBatchService.selectPageList(bo, pageQuery);
    }

    /**
     * 导出批次管理数据到 Excel
     */
    @SaCheckPermission("wm:batch:export")
    @Log(title = "批次管理", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(WmBatchBo bo, HttpServletResponse response) {
        List<WmBatchVo> list = wmBatchService.selectList(bo);
        ExcelUtil.exportExcel(list, "批次管理数据", WmBatchVo.class, response);
    }

    /**
     * 根据主键获取批次详细信息
     */
    @SaCheckPermission("wm:batch:query")
    @GetMapping("/{id}")
    public R<WmBatchVo> getInfo(@PathVariable Long id) {
        return R.ok(wmBatchService.selectById(id));
    }

    /**
     * 新增批次管理
     */
    @SaCheckPermission("wm:batch:add")
    @Log(title = "批次管理", businessType = BusinessType.INSERT)
    @PostMapping
    public R<Void> add(@Validated @RequestBody WmBatchBo bo) {
        return toAjax(wmBatchService.insertByBo(bo));
    }

    /**
     * 修改批次管理
     */
    @SaCheckPermission("wm:batch:edit")
    @Log(title = "批次管理", businessType = BusinessType.UPDATE)
    @PutMapping
    public R<Void> edit(@Validated @RequestBody WmBatchBo bo) {
        return toAjax(wmBatchService.updateByBo(bo));
    }

    /**
     * 批量删除批次管理
     */
    @SaCheckPermission("wm:batch:remove")
    @Log(title = "批次管理", businessType = BusinessType.DELETE)
    @DeleteMapping("/{ids}")
    public R<Void> remove(@PathVariable Long[] ids) {
        return toAjax(wmBatchService.deleteByIds(ids));
    }

    /**
     * 根据批次编码查询批次
     */
    @SaCheckPermission("wm:batch:query")
    @GetMapping("/code/{batchCode}")
    public R<WmBatchVo> getByCode(@PathVariable String batchCode) {
        return R.ok(wmBatchService.selectByCode(batchCode));
    }

    /**
     * 创建批次（自动生成批次编码）
     */
    @SaCheckPermission("wm:batch:add")
    @Log(title = "批次管理", businessType = BusinessType.INSERT)
    @PostMapping("/createBatch")
    public R<WmBatchVo> createBatch(@RequestBody WmBatchBo bo) {
        return R.ok(wmBatchService.createBatch(bo));
    }

    /**
     * 更新批次数量
     *
     * @param batchId  批次ID
     * @param deltaQty 变动数量
     * @param isReceive true为收货，false为发料
     */
    @SaCheckPermission("wm:batch:edit")
    @Log(title = "批次管理", businessType = BusinessType.UPDATE)
    @PutMapping("/updateQuantity")
    public R<Void> updateQuantity(@RequestParam Long batchId,
                                  @RequestParam BigDecimal deltaQty,
                                  @RequestParam boolean isReceive) {
        wmBatchService.updateQuantity(batchId, deltaQty, isReceive);
        return R.ok();
    }

    /**
     * 变更批次状态
     *
     * @param batchId 批次ID
     * @param status  目标状态
     */
    @SaCheckPermission("wm:batch:edit")
    @Log(title = "批次管理", businessType = BusinessType.UPDATE)
    @PutMapping("/changeStatus")
    public R<Void> changeStatus(@RequestParam Long batchId,
                                @RequestParam String status) {
        wmBatchService.changeStatus(batchId, status);
        return R.ok();
    }

    /**
     * 检查过期批次
     */
    @SaCheckPermission("wm:batch:edit")
    @Log(title = "批次管理", businessType = BusinessType.UPDATE)
    @PostMapping("/checkExpiry")
    public R<Void> checkExpiry() {
        wmBatchService.checkExpiry();
        return R.ok();
    }
}
