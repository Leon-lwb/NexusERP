package org.dromara.erp.wm.controller;

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
import org.dromara.erp.wm.domain.bo.WmTransferBo;
import org.dromara.erp.wm.domain.vo.WmTransferVo;
import org.dromara.erp.wm.service.IWmTransferService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/erp/wm/transfer")
public class WmTransferController extends BaseController {

    private final IWmTransferService wmTransferService;

    @SaCheckPermission("wm:transfer:list")
    @GetMapping("/list")
    public TableDataInfo<WmTransferVo> list(WmTransferBo bo, PageQuery pageQuery) {
        return wmTransferService.selectPageList(bo, pageQuery);
    }

    @SaCheckPermission("wm:transfer:export")
    @Log(title = "库存调拨", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(WmTransferBo bo, HttpServletResponse response) {
        List<WmTransferVo> list = wmTransferService.selectList(bo);
        ExcelUtil.exportExcel(list, "库存调拨数据", WmTransferVo.class, response);
    }

    @SaCheckPermission("wm:transfer:query")
    @GetMapping("/{transferId}")
    public R<WmTransferVo> getInfo(@PathVariable Long transferId) {
        return R.ok(wmTransferService.selectById(transferId));
    }

    @SaCheckPermission("wm:transfer:add")
    @Log(title = "库存调拨", businessType = BusinessType.INSERT)
    @PostMapping
    public R<Void> add(@Validated @RequestBody WmTransferBo bo) {
        return toAjax(wmTransferService.insertByBo(bo));
    }

    @SaCheckPermission("wm:transfer:edit")
    @Log(title = "库存调拨", businessType = BusinessType.UPDATE)
    @PutMapping
    public R<Void> edit(@Validated @RequestBody WmTransferBo bo) {
        return toAjax(wmTransferService.updateByBo(bo));
    }

    @SaCheckPermission("wm:transfer:remove")
    @Log(title = "库存调拨", businessType = BusinessType.DELETE)
    @DeleteMapping("/{transferIds}")
    public R<Void> remove(@PathVariable Long[] transferIds) {
        return toAjax(wmTransferService.deleteByIds(transferIds));
    }

    @SaCheckPermission("wm:transfer:edit")
    @Log(title = "库存调拨", businessType = BusinessType.UPDATE)
    @PutMapping("/execute/{transferId}")
    public R<Void> execute(@PathVariable Long transferId) {
        wmTransferService.execute(transferId);
        return R.ok();
    }

    // ==================== Flowable 工作流 ====================

    /**
     * 提交审批
     */
    @SaCheckPermission("wm:transfer:edit")
    @Log(title = "库存调拨", businessType = BusinessType.SUBMIT)
    @PostMapping("/{transferId}/submit")
    public R<Void> submit(@PathVariable Long transferId, @RequestParam(required = false) String selectFlowId) {
        wmTransferService.submit(String.valueOf(transferId), selectFlowId);
        return R.ok();
    }

    /**
     * 获取流程实例ID
     */
    @SaCheckPermission("wm:transfer:query")
    @GetMapping("/{transferId}/proc")
    public R<String> getProcInsId(@PathVariable Long transferId) {
        return R.ok(wmTransferService.getProcInsId(String.valueOf(transferId)));
    }
}
