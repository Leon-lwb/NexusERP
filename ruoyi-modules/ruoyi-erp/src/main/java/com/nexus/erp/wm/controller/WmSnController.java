package com.nexus.erp.wm.controller;

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
import com.nexus.erp.wm.domain.bo.WmSnBo;
import com.nexus.erp.wm.domain.vo.WmSnVo;
import com.nexus.erp.wm.service.IWmSnService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 序列号管理Controller
 *
 * @author nexus
 * @date 2026-06-02
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/erp/wm/sn")
public class WmSnController extends BaseController {

    private final IWmSnService wmSnService;

    /**
     * 分页查询序列号列表
     */
    @SaCheckPermission("wm:sn:list")
    @GetMapping("/list")
    public TableDataInfo<WmSnVo> list(WmSnBo bo, PageQuery pageQuery) {
        return wmSnService.selectPageList(bo, pageQuery);
    }

    /**
     * 导出序列号数据到Excel
     */
    @SaCheckPermission("wm:sn:export")
    @Log(title = "序列号管理", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(WmSnBo bo, HttpServletResponse response) {
        List<WmSnVo> list = wmSnService.selectList(bo);
        ExcelUtil.exportExcel(list, "序列号数据", WmSnVo.class, response);
    }

    /**
     * 根据主键获取序列号详细信息
     */
    @SaCheckPermission("wm:sn:query")
    @GetMapping("/{snId}")
    public R<WmSnVo> getInfo(@PathVariable Long snId) {
        return R.ok(wmSnService.selectById(snId));
    }

    /**
     * 新增序列号
     */
    @SaCheckPermission("wm:sn:add")
    @Log(title = "序列号管理", businessType = BusinessType.INSERT)
    @PostMapping
    public R<Void> add(@Validated @RequestBody WmSnBo bo) {
        return toAjax(wmSnService.insertByBo(bo));
    }

    /**
     * 修改序列号
     */
    @SaCheckPermission("wm:sn:edit")
    @Log(title = "序列号管理", businessType = BusinessType.UPDATE)
    @PutMapping
    public R<Void> edit(@Validated @RequestBody WmSnBo bo) {
        return toAjax(wmSnService.updateByBo(bo));
    }

    /**
     * 批量删除序列号
     */
    @SaCheckPermission("wm:sn:remove")
    @Log(title = "序列号管理", businessType = BusinessType.DELETE)
    @DeleteMapping("/{snIds}")
    public R<Void> remove(@PathVariable Long[] snIds) {
        return toAjax(wmSnService.deleteByIds(snIds));
    }

    /**
     * 批量生成序列号
     */
    @SaCheckPermission("wm:sn:add")
    @Log(title = "序列号管理-批量生成", businessType = BusinessType.INSERT)
    @PostMapping("/batchGenerate")
    public R<Void> batchGenerate(@RequestBody WmSnBo bo) {
        wmSnService.batchGenerate(bo.getMaterialId(), bo.getQuantity(),
            bo.getRuleId(), bo.getSourceDocId(), bo.getSourceDocType(), bo.getSourceDocNo());
        return R.ok();
    }

    /**
     * 绑定序列号到库存记录
     */
    @SaCheckPermission("wm:sn:edit")
    @Log(title = "序列号管理-绑定库存", businessType = BusinessType.UPDATE)
    @PutMapping("/bindStock")
    public R<Void> bindStock(@RequestBody WmSnBo bo) {
        wmSnService.bindStock(bo.getSnId(), bo.getStockId(), bo.getWarehouseId(), bo.getLocationId());
        return R.ok();
    }

    /**
     * 解绑序列号与库存记录
     */
    @SaCheckPermission("wm:sn:edit")
    @Log(title = "序列号管理-解绑库存", businessType = BusinessType.UPDATE)
    @PutMapping("/unbindStock/{snId}")
    public R<Void> unbindStock(@PathVariable Long snId) {
        wmSnService.unbindStock(snId);
        return R.ok();
    }

    /**
     * 变更序列号状态
     */
    @SaCheckPermission("wm:sn:edit")
    @Log(title = "序列号管理-变更状态", businessType = BusinessType.UPDATE)
    @PutMapping("/changeStatus")
    public R<Void> changeStatus(@RequestBody WmSnBo bo) {
        wmSnService.changeStatus(bo.getSnId(), bo.getStatus());
        return R.ok();
    }
}
