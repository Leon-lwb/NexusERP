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
import com.nexus.erp.prd.domain.bo.PrdWorkOrderBo;
import com.nexus.erp.prd.domain.vo.PrdWorkOrderVo;
import com.nexus.erp.prd.service.IPrdWorkOrderService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 生产工单 Controller
 * <p>
 * 提供生产工单的 RESTful API 接口，包括：
 * <ul>
 *   <li>CRUD 操作：分页列表查询、详情查询、新增、编辑、删除</li>
 *   <li>数据导出（Excel）</li>
 *   <li>状态流转操作：下达、开始生产、完工、关闭</li>
 *   <li>流程审批集成</li>
 * </ul>
 *
 * @author nexus
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/erp/prd/work")
public class PrdWorkOrderController extends BaseController {

    private final IPrdWorkOrderService prdWorkOrderService;

    /**
     * 分页查询生产工单列表
     * <p>
     * 权限标识：{@code prd:work:list}</p>
     *
     * @param bo        查询参数，包含工单编号、产品、状态等筛选条件
     * @param pageQuery 分页参数，包含页码、每页条数及排序信息
     * @return 生产工单分页列表
     */
    @SaCheckPermission("prd:work:list")
    @GetMapping("/list")
    public TableDataInfo<PrdWorkOrderVo> list(PrdWorkOrderBo bo, PageQuery pageQuery) {
        return prdWorkOrderService.selectPageList(bo, pageQuery);
    }

    /**
     * 导出生产工单数据到 Excel
     * <p>权限标识：{@code prd:work:export}</p>
     * <p>日志记录：title="生产工单"，businessType=EXPORT</p>
     *
     * @param bo        查询参数
     * @param response  HTTP 响应对象，用于输出 Excel 文件流
     */
    @SaCheckPermission("prd:work:export")
    @Log(title = "生产工单", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(PrdWorkOrderBo bo, HttpServletResponse response) {
        List<PrdWorkOrderVo> list = prdWorkOrderService.selectList(bo);
        ExcelUtil.exportExcel(list, "生产工单数据", PrdWorkOrderVo.class, response);
    }

    /**
     * 根据主键获取生产工单详细信息
     * <p>
     * 权限标识：{@code prd:work:query}</p>
     *
     * @param woId 生产工单 ID
     * @return 生产工单详细信息
     */
    @SaCheckPermission("prd:work:query")
    @GetMapping("/{woId}")
    public R<PrdWorkOrderVo> getInfo(@PathVariable Long woId) {
        return R.ok(prdWorkOrderService.selectById(woId));
    }

    /**
     * 新增生产工单
     * <p>
     * 权限标识：{@code prd:work:add}</p>
     * <p>日志记录：title="生产工单"，businessType=INSERT</p>
     *
     * @param bo 生产工单业务对象，包含产品、数量、计划时间等信息
     * @return 新增后的工单详细信息
     */
    @SaCheckPermission("prd:work:add")
    @Log(title = "生产工单", businessType = BusinessType.INSERT)
    @PostMapping
    public R<PrdWorkOrderVo> add(@Validated @RequestBody PrdWorkOrderBo bo) {
        prdWorkOrderService.insertByBo(bo);
        return R.ok(prdWorkOrderService.selectById(bo.getWoId()));
    }

    /**
     * 修改生产工单
     * <p>
     * 权限标识：{@code prd:work:edit}</p>
     * <p>日志记录：title="生产工单"，businessType=UPDATE</p>
     *
     * @param bo 生产工单业务对象，包含需要更新的字段信息
     * @return 操作结果，成功返回 {@code R.success()}，失败返回 {@code R.fail()}
     */
    @SaCheckPermission("prd:work:edit")
    @Log(title = "生产工单", businessType = BusinessType.UPDATE)
    @PutMapping
    public R<Void> edit(@Validated @RequestBody PrdWorkOrderBo bo) {
        return toAjax(prdWorkOrderService.updateByBo(bo));
    }

    /**
     * 批量删除生产工单
     * <p>
     * 权限标识：{@code prd:work:remove}</p>
     * <p>日志记录：title="生产工单"，businessType=DELETE</p>
     *
     * @param woIds 要删除的生产工单 ID 数组（支持批量删除）
     * @return 操作结果，成功返回 {@code R.success()}，失败返回 {@code R.fail()}
     */
    @SaCheckPermission("prd:work:remove")
    @Log(title = "生产工单", businessType = BusinessType.DELETE)
    @DeleteMapping("/{woIds}")
    public R<Void> remove(@PathVariable Long[] woIds) {
        return toAjax(prdWorkOrderService.deleteByIds(woIds));
    }

    /**
     * 下达工单
     * <p>权限标识：{@code prd:work:edit}</p>
     * <p>日志记录：title="生产工单"，businessType=UPDATE</p>
     *
     * @param woId 生产工单 ID
     * @return 操作结果
     */
    @SaCheckPermission("prd:work:edit")
    @Log(title = "生产工单", businessType = BusinessType.UPDATE)
    @PutMapping("/release/{woId}")
    public R<Void> release(@PathVariable Long woId) {
        prdWorkOrderService.release(woId);
        return R.ok();
    }

    /**
     * 开始生产
     * <p>权限标识：{@code prd:work:edit}</p>
     * <p>日志记录：title="生产工单"，businessType=UPDATE</p>
     *
     * @param woId 生产工单 ID
     * @return 操作结果
     */
    @SaCheckPermission("prd:work:edit")
    @Log(title = "生产工单", businessType = BusinessType.UPDATE)
    @PutMapping("/start/{woId}")
    public R<Void> startProduction(@PathVariable Long woId) {
        prdWorkOrderService.startProduction(woId);
        return R.ok();
    }

    /**
     * 完工
     * <p>权限标识：{@code prd:work:edit}</p>
     * <p>日志记录：title="生产工单"，businessType=UPDATE</p>
     *
     * @param woId 生产工单 ID
     * @return 操作结果
     */
    @SaCheckPermission("prd:work:edit")
    @Log(title = "生产工单", businessType = BusinessType.UPDATE)
    @PutMapping("/complete/{woId}")
    public R<Void> complete(@PathVariable Long woId) {
        prdWorkOrderService.complete(woId);
        return R.ok();
    }

    /**
     * 关闭工单
     * <p>权限标识：{@code prd:work:edit}</p>
     * <p>日志记录：title="生产工单"，businessType=UPDATE</p>
     *
     * @param woId 生产工单 ID
     * @return 操作结果
     */
    @SaCheckPermission("prd:work:edit")
    @Log(title = "生产工单", businessType = BusinessType.UPDATE)
    @PutMapping("/close/{woId}")
    public R<Void> close(@PathVariable Long woId) {
        prdWorkOrderService.close(woId);
        return R.ok();
    }

    /**
     * 提交生产工单审批
     * <p>权限标识：{@code prd:work:edit}</p>
     * <p>日志记录：title="生产工单"，businessType=UPDATE</p>
     *
     * @param woId         生产工单 ID
     * @param selectFlowId 选择的流程模板 ID
     * @return 操作结果
     */
    @SaCheckPermission("prd:work:edit")
    @Log(title = "生产工单", businessType = BusinessType.UPDATE)
    @PostMapping("/{woId}/submit")
    public R<Void> submit(@PathVariable Long woId, @RequestParam(required = false) String selectFlowId) {
        prdWorkOrderService.submit(String.valueOf(woId), selectFlowId);
        return R.ok();
    }

    /**
     * 获取生产工单流程实例 ID
     * <p>
     * 权限标识：{@code prd:work:query}</p>
     *
     * @param woId 生产工单 ID
     * @return 流程实例 ID
     */
    @SaCheckPermission("prd:work:query")
    @GetMapping("/{woId}/proc")
    public R<String> getProcInsId(@PathVariable Long woId) {
        return R.ok(prdWorkOrderService.getProcInsId(String.valueOf(woId)));
    }
}
