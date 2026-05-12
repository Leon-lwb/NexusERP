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
import org.dromara.erp.wm.domain.bo.WmMaterialReceiveBo;
import org.dromara.erp.wm.domain.vo.WmMaterialReceiveVo;
import org.dromara.erp.wm.service.IWmMaterialReceiveService;
import org.dromara.erp.wm.service.IWmStockOperationService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 物料入库单Controller
 *
 * @author ruoyi
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/erp/wm/receive")
public class WmMaterialReceiveController extends BaseController {

    private final IWmMaterialReceiveService wmMaterialReceiveService;
    private final IWmStockOperationService stockOperationService;

    /**
     * 查询物料入库单列表
     */
    @SaCheckPermission("wm:receive:list")
    @GetMapping("/list")
    public TableDataInfo<WmMaterialReceiveVo> list(WmMaterialReceiveBo bo, PageQuery pageQuery) {
        return wmMaterialReceiveService.selectPageList(bo, pageQuery);
    }

    /**
     * 导出物料入库单列表
     */
    @SaCheckPermission("wm:receive:export")
    @Log(title = "物料入库单", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(WmMaterialReceiveBo bo, HttpServletResponse response) {
        List<WmMaterialReceiveVo> list = wmMaterialReceiveService.selectList(bo);
        ExcelUtil.exportExcel(list, "物料入库单数据", WmMaterialReceiveVo.class, response);
    }

    /**
     * 获取物料入库单详细信息
     */
    @SaCheckPermission("wm:receive:query")
    @GetMapping("/{id}")
    public R<WmMaterialReceiveVo> getInfo(@PathVariable Long id) {
        return R.ok(wmMaterialReceiveService.selectById(id));
    }

    /**
     * 新增物料入库单
     */
    @SaCheckPermission("wm:receive:add")
    @Log(title = "物料入库单", businessType = BusinessType.INSERT)
    @PostMapping
    public R<Void> add(@Validated @RequestBody WmMaterialReceiveBo bo) {
        return toAjax(wmMaterialReceiveService.insertByBo(bo));
    }

    /**
     * 修改物料入库单
     */
    @SaCheckPermission("wm:receive:edit")
    @Log(title = "物料入库单", businessType = BusinessType.UPDATE)
    @PutMapping
    public R<Void> edit(@Validated @RequestBody WmMaterialReceiveBo bo) {
        try {
            return toAjax(wmMaterialReceiveService.updateWithValidation(bo));
        } catch (RuntimeException e) {
            return R.fail(e.getMessage());
        }
    }

    /**
     * 删除物料入库单
     */
    @SaCheckPermission("wm:receive:remove")
    @Log(title = "物料入库单", businessType = BusinessType.DELETE)
    @DeleteMapping("/{ids}")
    public R<Void> remove(@PathVariable Long[] ids) {
        return toAjax(wmMaterialReceiveService.deleteByIds(ids));
    }

    /**
     * 执行物料入库
     *
     * @param receiveId 入库单ID
     * @return 结果
     */
    @SaCheckPermission("wm:receive:execute")
    @Log(title = "物料入库单", businessType = BusinessType.UPDATE)
    @PostMapping("/execute/{receiveId}")
    public R<Void> executeReceive(@PathVariable Long receiveId) {
        try {
            stockOperationService.postMaterialReceive(receiveId);
            return R.ok();
        } catch (RuntimeException e) {
            return R.fail(e.getMessage());
        }
    }

    /**
     * 提交物料入库单并启动流程
     *
     * @param receiveId    入库单ID
     * @param selectFlowId 选择的流程模板ID
     * @return 结果
     */
    @SaCheckPermission("wm:receive:submit")
    @Log(title = "物料入库单", businessType = BusinessType.UPDATE)
    @PostMapping("/{receiveId}/submit")
    public R<Void> submit(@PathVariable("receiveId") String receiveId,
                          @RequestParam(value = "selectFlowId", required = false) String selectFlowId) {
        try {
            return toAjax(wmMaterialReceiveService.submit(receiveId, selectFlowId));
        } catch (RuntimeException e) {
            return R.fail(e.getMessage());
        }
    }

    /**
     * 获取流程实例ID
     *
     * @param receiveId 入库单ID
     * @return 结果
     */
    @SaCheckPermission("wm:receive:query")
    @GetMapping("/{receiveId}/proc")
    public R<String> getProcInsId(@PathVariable("receiveId") String receiveId) {
        return R.ok(wmMaterialReceiveService.getProcInsId(receiveId));
    }
}
