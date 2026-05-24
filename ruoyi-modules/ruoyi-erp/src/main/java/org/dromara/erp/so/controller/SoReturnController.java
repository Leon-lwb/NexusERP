package org.dromara.erp.so.controller;

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
import org.dromara.erp.so.domain.bo.SoReturnBo;
import org.dromara.erp.so.domain.vo.SoReturnVo;
import org.dromara.erp.so.service.ISoReturnService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 销售退货Controller
 * <p>
 * 提供销售退货单的 RESTful API 接口，包括 CRUD 操作、数据导出、退货确认、退货执行及审批流程等功能。
 *
 * @author ruoyi
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/erp/so/return")
public class SoReturnController extends BaseController {

    private final ISoReturnService soReturnService;

    /**
     * 分页查询销售退货列表
     *
     * @param bo        查询参数
     * @param pageQuery 分页参数
     * @return 销售退货分页列表
     */
    @SaCheckPermission("so:return:list")
    @GetMapping("/list")
    public TableDataInfo<SoReturnVo> list(SoReturnBo bo, PageQuery pageQuery) {
        return soReturnService.selectPageList(bo, pageQuery);
    }

    /**
     * 导出销售退货数据到 Excel
     * <p>权限标识：{@code so:return:export}</p>
     * <p>日志记录：title="销售退货"，businessType=EXPORT</p>
     *
     * @param bo        查询参数
     * @param response  HTTP 响应对象
     */
    @SaCheckPermission("so:return:export")
    @Log(title = "销售退货", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(SoReturnBo bo, HttpServletResponse response) {
        List<SoReturnVo> list = soReturnService.selectList(bo);
        ExcelUtil.exportExcel(list, "销售退货数据", SoReturnVo.class, response);
    }

    /**
     * 获取销售退货详细信息
     * <p>权限标识：{@code so:return:query}</p>
     *
     * @param returnId 销售退货ID
     * @return 销售退货详细信息
     */
    @SaCheckPermission("so:return:query")
    @GetMapping("/{returnId}")
    public R<SoReturnVo> getInfo(@PathVariable Long returnId) {
        return R.ok(soReturnService.selectById(returnId));
    }

    /**
     * 新增销售退货
     * <p>权限标识：{@code so:return:add}</p>
     * <p>日志记录：title="销售退货"，businessType=INSERT</p>
     *
     * @param bo 销售退货业务对象
     * @return 操作结果
     */
    @SaCheckPermission("so:return:add")
    @Log(title = "销售退货", businessType = BusinessType.INSERT)
    @PostMapping
    public R<Void> add(@Validated @RequestBody SoReturnBo bo) {
        return toAjax(soReturnService.insertByBo(bo));
    }

    /**
     * 修改销售退货
     * <p>权限标识：{@code so:return:edit}</p>
     * <p>日志记录：title="销售退货"，businessType=UPDATE</p>
     *
     * @param bo 销售退货业务对象
     * @return 操作结果
     */
    @SaCheckPermission("so:return:edit")
    @Log(title = "销售退货", businessType = BusinessType.UPDATE)
    @PutMapping
    public R<Void> edit(@Validated @RequestBody SoReturnBo bo) {
        return toAjax(soReturnService.updateByBo(bo));
    }

    /**
     * 删除销售退货
     * <p>权限标识：{@code so:return:remove}</p>
     * <p>日志记录：title="销售退货"，businessType=DELETE</p>
     *
     * @param returnIds 销售退货ID数组
     * @return 操作结果
     */
    @SaCheckPermission("so:return:remove")
    @Log(title = "销售退货", businessType = BusinessType.DELETE)
    @DeleteMapping("/{returnIds}")
    public R<Void> remove(@PathVariable Long[] returnIds) {
        return toAjax(soReturnService.deleteByIds(returnIds));
    }

    /**
     * 确认销售退货单
     * <p>权限标识：{@code so:return:edit}</p>
     * <p>日志记录：title="销售退货"，businessType=UPDATE</p>
     *
     * @param returnId 销售退货ID
     * @return 操作结果
     */
    @SaCheckPermission("so:return:edit")
    @Log(title = "销售退货", businessType = BusinessType.UPDATE)
    @PutMapping("/confirm/{returnId}")
    public R<Void> confirm(@PathVariable Long returnId) {
        soReturnService.confirm(returnId);
        return R.ok();
    }

    /**
     * 执行退货出库（扣减库存并生成库存交易记录）
     * <p>权限标识：{@code so:return:edit}</p>
     * <p>日志记录：title="销售退货"，businessType=UPDATE</p>
     *
     * @param returnId 销售退货ID
     * @return 操作结果
     */
    @SaCheckPermission("so:return:edit")
    @Log(title = "销售退货", businessType = BusinessType.UPDATE)
    @PutMapping("/execute/{returnId}")
    public R<Void> execute(@PathVariable Long returnId) {
        soReturnService.execute(returnId);
        return R.ok();
    }

    /**
     * 提交审批（启动 Flowable 审批流）
     * <p>权限标识：{@code so:return:edit}</p>
     * <p>日志记录：title="销售退货"，businessType=UPDATE</p>
     *
     * @param returnId     销售退货ID
     * @param selectFlowId 流程模板ID（可选）
     * @return 操作结果
     */
    @SaCheckPermission("so:return:edit")
    @Log(title = "销售退货", businessType = BusinessType.UPDATE)
    @PostMapping("/{returnId}/submit")
    public R<Void> submit(@PathVariable Long returnId, @RequestParam(required = false) String selectFlowId) {
        soReturnService.submit(String.valueOf(returnId), selectFlowId);
        return R.ok();
    }

    /**
     * 获取流程实例ID
     * <p>权限标识：{@code so:return:query}</p>
     *
     * @param returnId 销售退货ID
     * @return 流程实例ID
     */
    @SaCheckPermission("so:return:query")
    @GetMapping("/{returnId}/proc")
    public R<String> getProcInsId(@PathVariable Long returnId) {
        return R.ok(soReturnService.getProcInsId(String.valueOf(returnId)));
    }
}
