package com.nexus.erp.so.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import com.nexus.common.core.domain.R;
import com.nexus.common.core.utils.StringUtils;
import com.nexus.common.excel.utils.ExcelUtil;
import com.nexus.common.log.annotation.Log;
import com.nexus.common.log.enums.BusinessType;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.common.web.core.BaseController;
import com.nexus.erp.so.domain.bo.SoSalesOrderHeaderBo;
import com.nexus.erp.so.domain.vo.SoSalesOrderHeaderVo;
import com.nexus.erp.so.domain.vo.SoSalesOrderLineVo;
import com.nexus.erp.so.service.IPricingEngineService;
import com.nexus.erp.so.service.ISoSalesOrderHeaderService;
import com.nexus.erp.so.service.ISoSalesOrderLineService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 销售订单 Controller
 * <p>
 * 提供销售订单的 RESTful API 接口，包括：
 * <ul>
 *   <li>CRUD 操作：分页列表查询、详情查询、新增、编辑、删除</li>
 *   <li>数据导出（Excel）</li>
 *   <li>状态流转操作：提交、确认、履行、关闭、取消</li>
 *   <li>销售订单行明细查询</li>
 * </ul>
 * 所有接口均通过 Sa-Token 注解 {@link SaCheckPermission} 进行权限校验。
 *
 * @author nexus
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/erp/so/orderheader")
public class SoSalesOrderHeaderController extends BaseController {

    private final ISoSalesOrderHeaderService soSalesOrderHeaderService;
    private final ISoSalesOrderLineService soSalesOrderLineService;
    private final IPricingEngineService pricingEngineService;

    /**
     * 分页查询销售订单列表
     * <p>
     * 权限标识：{@code so:orderheader:list}</p>
     *
     * @param bo        查询参数，包含订单编号、客户名称、日期范围等筛选条件
     * @param pageQuery 分页参数，包含页码、每页条数及排序信息
     * @return 销售订单分页列表
     */
    @SaCheckPermission("so:orderheader:list")
    @GetMapping("/list")
    public TableDataInfo<SoSalesOrderHeaderVo> list(SoSalesOrderHeaderBo bo, PageQuery pageQuery) {
        return soSalesOrderHeaderService.selectPageList(bo, pageQuery);
    }

    /**
     * 导出销售订单数据到 Excel
     * <p>权限标识：{@code so:orderheader:export}</p>
     * <p>日志记录：title="销售订单"，businessType=EXPORT</p>
     *
     * @param bo        查询参数
     * @param response  HTTP 响应对象，用于输出 Excel 文件流
     */
    @SaCheckPermission("so:orderheader:export")
    @Log(title = "销售订单", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(SoSalesOrderHeaderBo bo, HttpServletResponse response) {
        List<SoSalesOrderHeaderVo> list = soSalesOrderHeaderService.selectList(bo);
        ExcelUtil.exportExcel(list, "销售订单数据", SoSalesOrderHeaderVo.class, response);
    }

    /**
     * 根据主键获取销售订单详细信息
     * <p>
     * 权限标识：{@code so:orderheader:query}</p>
     *
     * @param id 销售订单 ID
     * @return 销售订单详细信息
     */
    @SaCheckPermission("so:orderheader:query")
    @GetMapping("/{id}")
    public R<SoSalesOrderHeaderVo> getInfo(@PathVariable Long id) {
        return R.ok(soSalesOrderHeaderService.selectById(id));
    }

    /**
     * 提交销售订单审批（启动 Flowable 审批流）
     * <p>权限标识：{@code so:orderheader:edit}</p>
     * <p>日志记录：title="销售订单"，businessType=UPDATE</p>
     *
     * @param soId         销售订单 ID
     * @param selectFlowId 选择的流程模板 ID
     * @return 操作结果
     */
    @SaCheckPermission("so:orderheader:edit")
    @Log(title = "销售订单", businessType = BusinessType.UPDATE)
    @PostMapping("/{soId}/submit")
    public R<Void> submit(@PathVariable Long soId, @RequestParam("selectFlowId") String selectFlowId) {
        soSalesOrderHeaderService.submit(String.valueOf(soId), selectFlowId);
        return R.ok();
    }

    /**
     * 获取销售订单流程实例 ID
     * <p>
     * 权限标识：{@code so:orderheader:query}</p>
     *
     * @param soId 销售订单 ID
     * @return 流程实例 ID
     */
    @SaCheckPermission("so:orderheader:query")
    @GetMapping("/{soId}/proc")
    public R<String> getProcInsId(@PathVariable Long soId) {
        String procInsId = soSalesOrderHeaderService.getProcInsId(String.valueOf(soId));
        // 注意：不能使用 R.ok(procInsId)。procInsId 是 String，会命中重载 R.ok(String msg)，
        // 导致流程实例 ID 被塞进 msg、data 为 null，前端读取 res.data 时拿不到值（显示“暂无流程信息”）。
        // 这里显式使用 R.ok(String msg, T data) 重载，把 ID 放入 data。
        return R.ok("查询成功", procInsId);
    }

    /**
     * 新增销售订单
     * <p>
     * 权限标识：{@code so:orderheader:add}</p>
     * <p>日志记录：title="销售订单"，businessType=INSERT</p>
     *
     * @param bo 销售订单业务对象，包含客户信息、产品明细等
     * @return 操作结果，成功返回 {@code R.success()}，失败返回 {@code R.fail()}
     */
    @SaCheckPermission("so:orderheader:add")
    @Log(title = "销售订单", businessType = BusinessType.INSERT)
    @PostMapping
    public R<Void> add(@Validated @RequestBody SoSalesOrderHeaderBo bo) {
        return toAjax(soSalesOrderHeaderService.insertByBo(bo));
    }

    /**
     * 修改销售订单
     * <p>
     * 权限标识：{@code so:orderheader:edit}</p>
     * <p>日志记录：title="销售订单"，businessType=UPDATE</p>
     *
     * @param bo 销售订单业务对象，包含需要更新的字段信息
     * @return 操作结果，成功返回 {@code R.success()}，失败返回 {@code R.fail()}
     */
    @SaCheckPermission("so:orderheader:edit")
    @Log(title = "销售订单", businessType = BusinessType.UPDATE)
    @PutMapping
    public R<Void> edit(@Validated @RequestBody SoSalesOrderHeaderBo bo) {
        return toAjax(soSalesOrderHeaderService.updateByBo(bo));
    }

    /**
     * 批量删除销售订单
     * <p>
     * 权限标识：{@code so:orderheader:remove}</p>
     * <p>日志记录：title="销售订单"，businessType=DELETE</p>
     *
     * @param ids 要删除的销售订单 ID 数组（支持批量删除）
     * @return 操作结果，成功返回 {@code R.success()}，失败返回 {@code R.fail()}
     */
    @SaCheckPermission("so:orderheader:remove")
    @Log(title = "销售订单", businessType = BusinessType.DELETE)
    @DeleteMapping("/{ids}")
    public R<Void> remove(@PathVariable Long[] ids) {
        return toAjax(soSalesOrderHeaderService.deleteByIds(ids));
    }

    /**
     * 通过销售订单编号查询销售订单行信息
     * <p>
     * 权限标识：{@code so:orderheader:list}</p>
     *
     * @param soNo 销售订单编号
     * @return 销售订单行列表
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
     * <p>权限标识：{@code so:orderheader:edit}</p>
     * <p>日志记录：title="销售订单"，businessType=UPDATE</p>
     *
     * @param soId 销售订单 ID
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
     * <p>权限标识：{@code so:orderheader:edit}</p>
     * <p>日志记录：title="销售订单"，businessType=UPDATE</p>
     *
     * @param soId  销售订单 ID
     * @param force 是否强制履行（跳过发货数量校验，用于尾差/客诉场景）
     * @return 操作结果
     */
    @SaCheckPermission("so:orderheader:edit")
    @Log(title = "销售订单", businessType = BusinessType.UPDATE)
    @PutMapping("/fulfill/{soId}")
    public R<Void> fulfill(@PathVariable Long soId,
                           @RequestParam(value = "force", defaultValue = "false") boolean force) {
        soSalesOrderHeaderService.fulfill(soId, force);
        return R.ok();
    }

    /**
     * 关闭销售订单
     * <p>权限标识：{@code so:orderheader:edit}</p>
     * <p>日志记录：title="销售订单"，businessType=UPDATE</p>
     *
     * @param soId 销售订单 ID
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
     * <p>权限标识：{@code so:orderheader:edit}</p>
     * <p>日志记录：title="销售订单"，businessType=UPDATE</p>
     *
     * @param soId   销售订单 ID
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
     * 解析销售单价（客户专属价 &gt; 期间促销价 &gt; 标准基价 &gt; 物料参考价）。
     * <p>供订单录入时按客户与数量自动带出单价，销售代表仍可在表单中覆盖。</p>
     * <p>权限标识：{@code so:orderheader:list}</p>
     *
     * @param clientId   客户ID
     * @param materialId 物料ID
     * @param qty        数量（可空，为空时不参与数量区间匹配）
     * @param orderDate  订单日期 yyyy-MM-dd（可空，默认取当天）
     * @return 解析出的单价
     */
    @SaCheckPermission("so:orderheader:list")
    @GetMapping("/resolvePrice")
    public R<BigDecimal> resolvePrice(@RequestParam Long clientId,
                                      @RequestParam Long materialId,
                                      @RequestParam(required = false) BigDecimal qty,
                                      @RequestParam(required = false) String orderDate) {
        LocalDate date = StringUtils.isNotBlank(orderDate) ? LocalDate.parse(orderDate) : LocalDate.now();
        return R.ok(pricingEngineService.resolvePrice(clientId, materialId, qty, date));
    }

    /**
     * 通过销售订单行 ID 获取行详细信息
     * <p>
     * 权限标识：{@code so:orderheader:list}</p>
     *
     * @param soLineId 销售订单行 ID
     * @return 销售订单行详细信息
     */
    @SaCheckPermission("so:orderheader:list")
    @GetMapping("/listLineById/{soLineId}")
    public R<SoSalesOrderLineVo> listLineById(@PathVariable Long soLineId) {
        return R.ok(soSalesOrderLineService.selectById(soLineId));
    }
}
