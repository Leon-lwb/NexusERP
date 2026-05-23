package org.dromara.erp.pu.controller;

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
import org.dromara.erp.pu.domain.bo.PuPurchaseReturnBo;
import org.dromara.erp.pu.domain.vo.PuPurchaseReturnVo;
import org.dromara.erp.pu.service.IPuPurchaseReturnService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 采购退货控制器
 * <p>
 * 提供采购退货单的 RESTful API 接口，包括：
 * <ul>
 *   <li>CRUD 操作：分页列表查询、详情查询、新增、编辑、删除</li>
 *   <li>数据导出（Excel）</li>
 *   <li>状态流转操作：确认、执行退货出库</li>
 * </ul>
 * 所有接口均通过 Sa-Token 注解 {@link SaCheckPermission} 进行权限校验。
 *
 * @author ruoyi
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/erp/pu/return")
public class PuPurchaseReturnController extends BaseController {

    private final IPuPurchaseReturnService purchaseReturnService;

    /**
     * 分页查询采购退货单列表
     */
    @SaCheckPermission("pu:return:list")
    @GetMapping("/list")
    public TableDataInfo<PuPurchaseReturnVo> list(PuPurchaseReturnBo bo, PageQuery pageQuery) {
        return purchaseReturnService.selectPageList(bo, pageQuery);
    }

    /**
     * 导出采购退货数据到 Excel
     */
    @SaCheckPermission("pu:return:export")
    @Log(title = "采购退货", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(PuPurchaseReturnBo bo, HttpServletResponse response) {
        List<PuPurchaseReturnVo> list = purchaseReturnService.selectList(bo);
        ExcelUtil.exportExcel(list, "采购退货数据", PuPurchaseReturnVo.class, response);
    }

    /**
     * 根据主键获取采购退货单详情（含明细行）
     */
    @SaCheckPermission("pu:return:query")
    @GetMapping("/{returnId}")
    public R<PuPurchaseReturnVo> getInfo(@PathVariable Long returnId) {
        return R.ok(purchaseReturnService.selectById(returnId));
    }

    /**
     * 新增采购退货单（含明细行）
     */
    @SaCheckPermission("pu:return:add")
    @Log(title = "采购退货", businessType = BusinessType.INSERT)
    @PostMapping
    public R<Void> add(@Validated @RequestBody PuPurchaseReturnBo bo) {
        return toAjax(purchaseReturnService.insertByBo(bo));
    }

    /**
     * 修改采购退货单（含明细行）
     */
    @SaCheckPermission("pu:return:edit")
    @Log(title = "采购退货", businessType = BusinessType.UPDATE)
    @PutMapping
    public R<Void> edit(@Validated @RequestBody PuPurchaseReturnBo bo) {
        return toAjax(purchaseReturnService.updateByBo(bo));
    }

    /**
     * 确认退货单（草稿 → 已确认）
     */
    @SaCheckPermission("pu:return:confirm")
    @Log(title = "采购退货", businessType = BusinessType.UPDATE)
    @PutMapping("/confirm/{returnId}")
    public R<Void> confirm(@PathVariable Long returnId) {
        purchaseReturnService.confirm(returnId);
        return R.ok();
    }

    /**
     * 执行退货出库（扣减库存并生成库存交易记录）
     */
    @SaCheckPermission("pu:return:execute")
    @Log(title = "采购退货", businessType = BusinessType.UPDATE)
    @PutMapping("/execute/{returnId}")
    public R<Void> execute(@PathVariable Long returnId) {
        purchaseReturnService.execute(returnId);
        return R.ok();
    }

    /**
     * 批量删除采购退货单
     */
    @SaCheckPermission("pu:return:remove")
    @Log(title = "采购退货", businessType = BusinessType.DELETE)
    @DeleteMapping("/{returnIds}")
    public R<Void> remove(@PathVariable Long[] returnIds) {
        return toAjax(purchaseReturnService.deleteByIds(returnIds));
    }
}
