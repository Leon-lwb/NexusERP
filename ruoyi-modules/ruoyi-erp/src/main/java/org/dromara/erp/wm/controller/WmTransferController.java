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

/**
 * 库存调拨Controller
 * <p>
 * 提供库存调拨单的 RESTful API 接口，包括 CRUD 操作、数据导出、执行调拨及审批流程等功能。
 *
 * @author ruoyi
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/erp/wm/transfer")
public class WmTransferController extends BaseController {

    private final IWmTransferService wmTransferService;

    /**
     * 分页查询库存调拨列表
     *
     * @param bo        查询参数
     * @param pageQuery 分页参数
     * @return 库存调拨分页列表
     */
    @SaCheckPermission("wm:transfer:list")
    @GetMapping("/list")
    public TableDataInfo<WmTransferVo> list(WmTransferBo bo, PageQuery pageQuery) {
        return wmTransferService.selectPageList(bo, pageQuery);
    }

    /**
     * 导出库存调拨数据到 Excel
     * <p>权限标识：{@code wm:transfer:export}</p>
     * <p>日志记录：title="库存调拨"，businessType=EXPORT</p>
     *
     * @param bo        查询参数
     * @param response  HTTP 响应对象
     */
    @SaCheckPermission("wm:transfer:export")
    @Log(title = "库存调拨", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(WmTransferBo bo, HttpServletResponse response) {
        List<WmTransferVo> list = wmTransferService.selectList(bo);
        ExcelUtil.exportExcel(list, "库存调拨数据", WmTransferVo.class, response);
    }

    /**
     * 获取库存调拨详细信息
     * <p>权限标识：{@code wm:transfer:query}</p>
     *
     * @param transferId 库存调拨ID
     * @return 库存调拨详细信息
     */
    @SaCheckPermission("wm:transfer:query")
    @GetMapping("/{transferId}")
    public R<WmTransferVo> getInfo(@PathVariable Long transferId) {
        return R.ok(wmTransferService.selectById(transferId));
    }

    /**
     * 新增库存调拨
     * <p>权限标识：{@code wm:transfer:add}</p>
     * <p>日志记录：title="库存调拨"，businessType=INSERT</p>
     *
     * @param bo 库存调拨业务对象
     * @return 操作结果
     */
    @SaCheckPermission("wm:transfer:add")
    @Log(title = "库存调拨", businessType = BusinessType.INSERT)
    @PostMapping
    public R<Void> add(@Validated @RequestBody WmTransferBo bo) {
        return toAjax(wmTransferService.insertByBo(bo));
    }

    /**
     * 修改库存调拨
     * <p>权限标识：{@code wm:transfer:edit}</p>
     * <p>日志记录：title="库存调拨"，businessType=UPDATE</p>
     *
     * @param bo 库存调拨业务对象
     * @return 操作结果
     */
    @SaCheckPermission("wm:transfer:edit")
    @Log(title = "库存调拨", businessType = BusinessType.UPDATE)
    @PutMapping
    public R<Void> edit(@Validated @RequestBody WmTransferBo bo) {
        return toAjax(wmTransferService.updateByBo(bo));
    }

    /**
     * 删除库存调拨
     * <p>权限标识：{@code wm:transfer:remove}</p>
     * <p>日志记录：title="库存调拨"，businessType=DELETE</p>
     *
     * @param transferIds 库存调拨ID数组
     * @return 操作结果
     */
    @SaCheckPermission("wm:transfer:remove")
    @Log(title = "库存调拨", businessType = BusinessType.DELETE)
    @DeleteMapping("/{transferIds}")
    public R<Void> remove(@PathVariable Long[] transferIds) {
        return toAjax(wmTransferService.deleteByIds(transferIds));
    }

    /**
     * 执行库存调拨（完成调拨出入库）
     * <p>权限标识：{@code wm:transfer:edit}</p>
     * <p>日志记录：title="库存调拨"，businessType=UPDATE</p>
     *
     * @param transferId 库存调拨ID
     * @return 操作结果
     */
    @SaCheckPermission("wm:transfer:edit")
    @Log(title = "库存调拨", businessType = BusinessType.UPDATE)
    @PutMapping("/execute/{transferId}")
    public R<Void> execute(@PathVariable Long transferId) {
        wmTransferService.execute(transferId);
        return R.ok();
    }

    // ==================== Flowable 工作流 ====================

    /**
     * 提交审批（启动 Flowable 审批流）
     * <p>权限标识：{@code wm:transfer:edit}</p>
     * <p>日志记录：title="库存调拨"，businessType=SUBMIT</p>
     *
     * @param transferId   库存调拨ID
     * @param selectFlowId 流程模板ID（可选）
     * @return 操作结果
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
     * <p>权限标识：{@code wm:transfer:query}</p>
     *
     * @param transferId 库存调拨ID
     * @return 流程实例ID
     */
    @SaCheckPermission("wm:transfer:query")
    @GetMapping("/{transferId}/proc")
    public R<String> getProcInsId(@PathVariable Long transferId) {
        return R.ok(wmTransferService.getProcInsId(String.valueOf(transferId)));
    }
}
