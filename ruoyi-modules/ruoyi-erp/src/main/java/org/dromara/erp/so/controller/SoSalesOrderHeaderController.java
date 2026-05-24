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
import org.dromara.erp.so.domain.bo.SoSalesOrderHeaderBo;
import org.dromara.erp.so.domain.vo.SoSalesOrderHeaderVo;
import org.dromara.erp.so.domain.vo.SoSalesOrderLineVo;
import org.dromara.erp.so.service.ISoSalesOrderHeaderService;
import org.dromara.erp.so.service.ISoSalesOrderLineService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 销售订单Controller
 *
 * @author ruoyi
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/erp/so/orderheader")
public class SoSalesOrderHeaderController extends BaseController {

    private final ISoSalesOrderHeaderService soSalesOrderHeaderService;
    private final ISoSalesOrderLineService soSalesOrderLineService;

    /**
     * 查询销售订单列表
     */
    @SaCheckPermission("so:orderheader:list")
    @GetMapping("/list")
    public TableDataInfo<SoSalesOrderHeaderVo> list(SoSalesOrderHeaderBo bo, PageQuery pageQuery) {
        return soSalesOrderHeaderService.selectPageList(bo, pageQuery);
    }

    /**
     * 导出销售订单列表
     */
    @SaCheckPermission("so:orderheader:export")
    @Log(title = "销售订单", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(SoSalesOrderHeaderBo bo, HttpServletResponse response) {
        List<SoSalesOrderHeaderVo> list = soSalesOrderHeaderService.selectList(bo);
        ExcelUtil.exportExcel(list, "销售订单数据", SoSalesOrderHeaderVo.class, response);
    }

    /**
     * 获取销售订单详细信息（通过ID）
     */
    @SaCheckPermission("so:orderheader:query")
    @GetMapping("/{id}")
    public R<SoSalesOrderHeaderVo> getInfo(@PathVariable Long id) {
        return R.ok(soSalesOrderHeaderService.selectById(id));
    }

    /**
     * 提交审批
     */
    @SaCheckPermission("so:orderheader:edit")
    @Log(title = "销售订单", businessType = BusinessType.UPDATE)
    @PostMapping("/{soId}/submit")
    public R<Void> submit(@PathVariable Long soId, @RequestParam("selectFlowId") String selectFlowId) {
        soSalesOrderHeaderService.submit(String.valueOf(soId), selectFlowId);
        return R.ok();
    }

    /**
     * 获取流程实例ID
     */
    @SaCheckPermission("so:orderheader:query")
    @GetMapping("/{soId}/proc")
    public R<String> getProcInsId(@PathVariable Long soId) {
        String procInsId = soSalesOrderHeaderService.getProcInsId(String.valueOf(soId));
        return R.ok(procInsId);
    }

    /**
     * 新增销售订单
     */
    @SaCheckPermission("so:orderheader:add")
    @Log(title = "销售订单", businessType = BusinessType.INSERT)
    @PostMapping
    public R<Void> add(@Validated @RequestBody SoSalesOrderHeaderBo bo) {
        return toAjax(soSalesOrderHeaderService.insertByBo(bo));
    }

    /**
     * 修改销售订单
     */
    @SaCheckPermission("so:orderheader:edit")
    @Log(title = "销售订单", businessType = BusinessType.UPDATE)
    @PutMapping
    public R<Void> edit(@Validated @RequestBody SoSalesOrderHeaderBo bo) {
        return toAjax(soSalesOrderHeaderService.updateByBo(bo));
    }

    /**
     * 删除销售订单
     */
    @SaCheckPermission("so:orderheader:remove")
    @Log(title = "销售订单", businessType = BusinessType.DELETE)
    @DeleteMapping("/{ids}")
    public R<Void> remove(@PathVariable Long[] ids) {
        return toAjax(soSalesOrderHeaderService.deleteByIds(ids));
    }

    /**
     * 通过销售订单编号查询销售订单行信息
     */
    @SaCheckPermission("so:orderheader:list")
    @GetMapping("/listLine/{soNo}")
    public R<List<SoSalesOrderLineVo>> listLine(@PathVariable String soNo) {
        List<SoSalesOrderLineVo> lineList = soSalesOrderHeaderService.selectLineListBySoNo(soNo);
        lineList.forEach(line -> line.setSoNo(soNo));
        return R.ok(lineList);
    }

    /**
     * 确认销售订单
     *
     * @param soId 销售订单ID
     * @return 操作结果
     */
    @SaCheckPermission("so:orderheader:edit")
    @Log(title = "销售订单", businessType = BusinessType.UPDATE)
    @PutMapping("/confirm/{soId}")
    public R<Void> confirm(@PathVariable Long soId) {
        soSalesOrderHeaderService.confirm(soId);
        return R.ok();
    }

    /**
     * 履行销售订单
     *
     * @param soId 销售订单ID
     * @return 操作结果
     */
    @SaCheckPermission("so:orderheader:edit")
    @Log(title = "销售订单", businessType = BusinessType.UPDATE)
    @PutMapping("/fulfill/{soId}")
    public R<Void> fulfill(@PathVariable Long soId) {
        soSalesOrderHeaderService.fulfill(soId);
        return R.ok();
    }

    /**
     * 关闭销售订单
     *
     * @param soId 销售订单ID
     * @return 操作结果
     */
    @SaCheckPermission("so:orderheader:edit")
    @Log(title = "销售订单", businessType = BusinessType.UPDATE)
    @PutMapping("/close/{soId}")
    public R<Void> close(@PathVariable Long soId) {
        soSalesOrderHeaderService.close(soId);
        return R.ok();
    }

    /**
     * 取消销售订单
     *
     * @param soId 销售订单ID
     * @param reason 取消原因
     * @return 操作结果
     */
    @SaCheckPermission("so:orderheader:edit")
    @Log(title = "销售订单", businessType = BusinessType.UPDATE)
    @PutMapping("/cancel/{soId}")
    public R<Void> cancel(@PathVariable Long soId, String reason) {
        soSalesOrderHeaderService.cancel(soId, reason);
        return R.ok();
    }

    /**
     * 通过销售订单子表id获取销售订单行详细信息
     */
    @SaCheckPermission("so:orderheader:list")
    @GetMapping("/listLineById/{soLineId}")
    public R<SoSalesOrderLineVo> listLineById(@PathVariable Long soLineId) {
        return R.ok(soSalesOrderLineService.selectById(soLineId));
    }
}
