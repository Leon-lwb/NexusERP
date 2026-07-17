package com.nexus.erp.pu.invoice.controller;

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
import com.nexus.erp.pu.invoice.domain.bo.PuInvoiceBo;
import com.nexus.erp.pu.invoice.domain.vo.MatchResultVo;
import com.nexus.erp.pu.invoice.domain.vo.PuInvoiceVo;
import com.nexus.erp.pu.invoice.service.IPuInvoiceService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

/**
 * 采购进项发票 Controller
 * <p>
 * 管理采购进项发票:登记/收票确认/三单匹配/认证/抵扣/核销/红冲/作废全生命周期。
 * 权限拆分:核销红冲 pu:invoice:verify,认证抵扣 pu:invoice:certify,三单匹配 pu:invoice:match,
 * 收票确认/作废/CRUD pu:invoice:edit,从订单生成草稿 pu:invoice:add。
 * </p>
 *
 * @author nexus
 * @date 2026-07-16
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/erp/pu/invoice")
public class PuInvoiceController extends BaseController {

    private final IPuInvoiceService puInvoiceService;

    /**
     * 分页查询采购进项发票列表
     * <p>权限标识:{@code pu:invoice:list}</p>
     */
    @SaCheckPermission("pu:invoice:list")
    @GetMapping("/list")
    public TableDataInfo<PuInvoiceVo> list(PuInvoiceBo bo, PageQuery pageQuery) {
        return puInvoiceService.selectPageList(bo, pageQuery);
    }

    /**
     * 导出采购进项发票数据到 Excel
     * <p>权限标识:{@code pu:invoice:export}</p>
     */
    @SaCheckPermission("pu:invoice:export")
    @Log(title = "采购进项发票", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(PuInvoiceBo bo, HttpServletResponse response) {
        List<PuInvoiceVo> list = puInvoiceService.selectList(bo);
        ExcelUtil.exportExcel(list, "采购进项发票数据", PuInvoiceVo.class, response);
    }

    /**
     * 根据主键获取采购进项发票详细信息
     * <p>权限标识:{@code pu:invoice:query}</p>
     */
    @SaCheckPermission("pu:invoice:query")
    @GetMapping("/{id}")
    public R<PuInvoiceVo> getInfo(@PathVariable Long id) {
        return R.ok(puInvoiceService.selectById(id));
    }

    /**
     * 新增采购进项发票
     * <p>权限标识:{@code pu:invoice:add}</p>
     */
    @SaCheckPermission("pu:invoice:add")
    @Log(title = "采购进项发票", businessType = BusinessType.INSERT)
    @PostMapping
    public R<Void> add(@Validated @RequestBody PuInvoiceBo bo) {
        return toAjax(puInvoiceService.insertByBo(bo));
    }

    /**
     * 从采购订单生成发票草稿(按"已收货未开票"自动汇总)
     * <p>权限标识:{@code pu:invoice:add}</p>
     *
     * @param poId 采购订单ID
     * @return 新建的发票ID(无可开票行时为 null)
     */
    @SaCheckPermission("pu:invoice:add")
    @Log(title = "采购进项发票", businessType = BusinessType.INSERT)
    @PostMapping("/generateFromPo/{poId}")
    public R<Long> generateFromPo(@PathVariable Long poId) {
        return R.ok(puInvoiceService.generateDraftFromPo(poId));
    }

    /**
     * 修改采购进项发票(差量更新明细行 + 头乐观锁)
     * <p>权限标识:{@code pu:invoice:edit}</p>
     */
    @SaCheckPermission("pu:invoice:edit")
    @Log(title = "采购进项发票", businessType = BusinessType.UPDATE)
    @PutMapping
    public R<Void> edit(@Validated @RequestBody PuInvoiceBo bo) {
        return toAjax(puInvoiceService.updateByBo(bo));
    }

    /**
     * 收票确认(草稿 -> 已收)
     * <p>权限标识:{@code pu:invoice:edit}</p>
     *
     * @param id 发票ID
     */
    @SaCheckPermission("pu:invoice:edit")
    @Log(title = "采购进项发票", businessType = BusinessType.UPDATE)
    @PutMapping("/receive/{id}")
    public R<Void> receive(@PathVariable Long id) {
        puInvoiceService.receiveInvoice(id);
        return R.ok();
    }

    /**
     * 三单匹配校验(PO + 收货 + 发票)
     * <p>权限标识:{@code pu:invoice:match}</p>
     *
     * @param id 发票ID
     * @return 匹配结果(含差异明细)
     */
    @SaCheckPermission("pu:invoice:match")
    @GetMapping("/match/{id}")
    public R<MatchResultVo> match(@PathVariable Long id) {
        return R.ok(puInvoiceService.matchThreeWay(id));
    }

    /**
     * 核销发票(累加已核销金额,达价税合计则转已核销;前置三单匹配)
     * <p>权限标识:{@code pu:invoice:verify}</p>
     *
     * @param id           发票ID
     * @param verifyAmount 本次核销金额
     */
    @SaCheckPermission("pu:invoice:verify")
    @Log(title = "采购进项发票", businessType = BusinessType.UPDATE)
    @PutMapping("/verify/{id}")
    public R<Void> verify(@PathVariable Long id, @RequestParam BigDecimal verifyAmount) {
        puInvoiceService.verifyInvoice(id, verifyAmount, null);
        return R.ok();
    }

    /**
     * 进项认证(未认证 -> 已认证,记录可抵扣税额)
     * <p>权限标识:{@code pu:invoice:certify}</p>
     *
     * @param id 发票ID
     */
    @SaCheckPermission("pu:invoice:certify")
    @Log(title = "采购进项发票", businessType = BusinessType.UPDATE)
    @PutMapping("/certify/{id}")
    public R<Void> certify(@PathVariable Long id) {
        puInvoiceService.certifyInvoice(id);
        return R.ok();
    }

    /**
     * 进项抵扣(已认证 -> 已抵扣)
     * <p>权限标识:{@code pu:invoice:certify}</p>
     *
     * @param id 发票ID
     */
    @SaCheckPermission("pu:invoice:certify")
    @Log(title = "采购进项发票", businessType = BusinessType.UPDATE)
    @PutMapping("/deduct/{id}")
    public R<Void> deduct(@PathVariable Long id) {
        puInvoiceService.deductInvoice(id);
        return R.ok();
    }

    /**
     * 作废发票(回退采购订单行已开票数量)
     * <p>权限标识:{@code pu:invoice:edit}</p>
     *
     * @param id     发票ID
     * @param reason 作废原因
     */
    @SaCheckPermission("pu:invoice:edit")
    @Log(title = "采购进项发票", businessType = BusinessType.UPDATE)
    @PutMapping("/cancel/{id}")
    public R<Void> cancel(@PathVariable Long id, @RequestParam(required = false) String reason) {
        puInvoiceService.cancelInvoice(id, reason);
        return R.ok();
    }

    /**
     * 红冲发票(生成红字负数发票,冲减原发票已核销金额;原已抵扣则进项转出)
     * <p>权限标识:{@code pu:invoice:verify}</p>
     *
     * @param id     原蓝字发票ID
     * @param reason 红冲原因
     * @return 红字发票ID
     */
    @SaCheckPermission("pu:invoice:verify")
    @Log(title = "采购进项发票", businessType = BusinessType.INSERT)
    @PutMapping("/redFlush/{id}")
    public R<Long> redFlush(@PathVariable Long id, @RequestParam(required = false) String reason) {
        return R.ok(puInvoiceService.redFlush(id, reason));
    }

    /**
     * 批量删除采购进项发票
     * <p>权限标识:{@code pu:invoice:remove}</p>
     */
    @SaCheckPermission("pu:invoice:remove")
    @Log(title = "采购进项发票", businessType = BusinessType.DELETE)
    @DeleteMapping("/{ids}")
    public R<Void> remove(@PathVariable Long[] ids) {
        return toAjax(puInvoiceService.deleteByIds(ids));
    }
}
