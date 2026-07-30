package com.nexus.erp.fin.gl.controller;

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
import com.nexus.erp.fin.gl.domain.bo.GlVoucherBo;
import com.nexus.erp.fin.gl.domain.vo.GlVoucherVo;
import com.nexus.erp.fin.gl.service.IGlVoucherService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 记账凭证 Controller
 *
 * @author nexus
 * @date 2026-07-29
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/erp/fin/gl/voucher")
public class GlVoucherController extends BaseController {

    private final IGlVoucherService glVoucherService;

    /** 分页查询记账凭证 */
    @SaCheckPermission("fin:gl:voucher:list")
    @GetMapping("/list")
    public TableDataInfo<GlVoucherVo> list(GlVoucherBo bo, PageQuery pageQuery) {
        return glVoucherService.queryPageList(bo, pageQuery);
    }

    /** 导出记账凭证 */
    @SaCheckPermission("fin:gl:voucher:export")
    @Log(title = "记账凭证", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(GlVoucherBo bo, HttpServletResponse response) {
        List<GlVoucherVo> list = glVoucherService.queryList(bo);
        ExcelUtil.exportExcel(list, "记账凭证数据", GlVoucherVo.class, response);
    }

    /** 查询记账凭证详情 */
    @SaCheckPermission("fin:gl:voucher:query")
    @GetMapping("/{voucherId}")
    public R<GlVoucherVo> getInfo(@PathVariable Long voucherId) {
        return R.ok(glVoucherService.queryById(voucherId));
    }

    /** 新增记账凭证(草稿) */
    @SaCheckPermission("fin:gl:voucher:add")
    @PostMapping
    public R<Void> add(@Validated @RequestBody GlVoucherBo bo) {
        return toAjax(glVoucherService.insertByBo(bo));
    }

    /** 修改记账凭证(草稿) */
    @SaCheckPermission("fin:gl:voucher:edit")
    @PutMapping
    public R<Void> edit(@Validated @RequestBody GlVoucherBo bo) {
        return toAjax(glVoucherService.updateByBo(bo));
    }

    /** 删除记账凭证 */
    @SaCheckPermission("fin:gl:voucher:remove")
    @DeleteMapping("/{voucherIds}")
    public R<Void> remove(@PathVariable Long[] voucherIds) {
        return toAjax(glVoucherService.deleteByIds(voucherIds));
    }

    /** 过账 */
    @SaCheckPermission("fin:gl:voucher:post")
    @PutMapping("/post/{voucherId}")
    public R<Void> post(@PathVariable Long voucherId) {
        glVoucherService.post(voucherId);
        return toAjax(1);
    }

    /** 反过账 */
    @SaCheckPermission("fin:gl:voucher:unpost")
    @PutMapping("/unpost/{voucherId}")
    public R<Void> unpost(@PathVariable Long voucherId) {
        glVoucherService.unpost(voucherId);
        return toAjax(1);
    }

    /** 作废记账凭证 */
    @SaCheckPermission("fin:gl:voucher:edit")
    @PutMapping("/cancel/{voucherId}")
    public R<Void> cancel(@PathVariable Long voucherId) {
        glVoucherService.cancel(voucherId);
        return toAjax(1);
    }
}
