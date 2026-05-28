package com.nexus.erp.prd.controller;

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
import com.nexus.erp.prd.domain.bo.PrdReworkOrderBo;
import com.nexus.erp.prd.domain.vo.PrdReworkOrderVo;
import com.nexus.erp.prd.service.IPrdReworkOrderService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 返工订单Controller
 *
 * @author nexus
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/erp/prd/rework")
public class PrdReworkOrderController extends BaseController {

    private final IPrdReworkOrderService reworkOrderService;

    @SaCheckPermission("prd:rework:list")
    @GetMapping("/list")
    public TableDataInfo<PrdReworkOrderVo> list(PrdReworkOrderBo bo, PageQuery pageQuery) {
        return reworkOrderService.selectPageList(bo, pageQuery);
    }

    @SaCheckPermission("prd:rework:export")
    @Log(title = "返工订单", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(PrdReworkOrderBo bo, HttpServletResponse response) {
        List<PrdReworkOrderVo> list = reworkOrderService.selectList(bo);
        ExcelUtil.exportExcel(list, "返工订单数据", PrdReworkOrderVo.class, response);
    }

    @SaCheckPermission("prd:rework:query")
    @GetMapping("/{reworkId}")
    public R<PrdReworkOrderVo> getInfo(@PathVariable Long reworkId) {
        return R.ok(reworkOrderService.selectById(reworkId));
    }

    @SaCheckPermission("prd:rework:add")
    @Log(title = "返工订单", businessType = BusinessType.INSERT)
    @PostMapping
    public R<PrdReworkOrderVo> add(@Validated @RequestBody PrdReworkOrderBo bo) {
        reworkOrderService.insertByBo(bo);
        return R.ok(reworkOrderService.selectById(bo.getReworkId()));
    }

    @SaCheckPermission("prd:rework:edit")
    @Log(title = "返工订单", businessType = BusinessType.UPDATE)
    @PutMapping
    public R<Void> edit(@Validated @RequestBody PrdReworkOrderBo bo) {
        return toAjax(reworkOrderService.updateByBo(bo));
    }

    @SaCheckPermission("prd:rework:remove")
    @Log(title = "返工订单", businessType = BusinessType.DELETE)
    @DeleteMapping("/{reworkIds}")
    public R<Void> remove(@PathVariable Long[] reworkIds) {
        return toAjax(reworkOrderService.deleteByIds(reworkIds));
    }

    @SaCheckPermission("prd:rework:edit")
    @Log(title = "返工订单-开始", businessType = BusinessType.UPDATE)
    @PutMapping("/start/{reworkId}")
    public R<Void> start(@PathVariable Long reworkId) {
        reworkOrderService.start(reworkId);
        return R.ok();
    }

    @SaCheckPermission("prd:rework:edit")
    @Log(title = "返工订单-完工", businessType = BusinessType.UPDATE)
    @PutMapping("/complete/{reworkId}")
    public R<Void> complete(@PathVariable Long reworkId) {
        reworkOrderService.complete(reworkId);
        return R.ok();
    }
}
