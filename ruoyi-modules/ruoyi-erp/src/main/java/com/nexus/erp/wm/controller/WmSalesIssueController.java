package com.nexus.erp.wm.controller;

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
import com.nexus.erp.wm.domain.bo.WmSalesIssueBo;
import com.nexus.erp.wm.domain.vo.WmSalesIssueVo;
import com.nexus.erp.wm.service.IWmSalesIssueService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 销售出库 Controller
 * <p>
 * 提供销售出库单的 RESTful API 接口，包括：
 * <ul>
 *   <li>CRUD 操作：分页列表查询、详情查询、新增、编辑、删除</li>
 *   <li>数据导出（Excel）</li>
 *   <li>状态流转操作：确认、完成出库</li>
 *   <li>流程审批集成</li>
 * </ul>
 *
 * @author nexus
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/erp/wm/salesIssue")
public class WmSalesIssueController extends BaseController {

    private final IWmSalesIssueService wmSalesIssueService;

    /**
     * 分页查询销售出库列表
     * <p>
     * 权限标识：{@code wm:salesIssue:list}</p>
     *
     * @param bo        查询参数，包含出库单号、客户、日期范围等筛选条件
     * @param pageQuery 分页参数，包含页码、每页条数及排序信息
     * @return 销售出库分页列表
     */
    @SaCheckPermission("wm:salesIssue:list")
    @GetMapping("/list")
    public TableDataInfo<WmSalesIssueVo> list(WmSalesIssueBo bo, PageQuery pageQuery) {
        return wmSalesIssueService.selectPageList(bo, pageQuery);
    }

    /**
     * 导出销售出库数据到 Excel
     * <p>权限标识：{@code wm:salesIssue:export}</p>
     * <p>日志记录：title="销售出库"，businessType=EXPORT</p>
     *
     * @param bo        查询参数
     * @param response  HTTP 响应对象，用于输出 Excel 文件流
     */
    @SaCheckPermission("wm:salesIssue:export")
    @Log(title = "销售出库", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(WmSalesIssueBo bo, HttpServletResponse response) {
        List<WmSalesIssueVo> list = wmSalesIssueService.selectList(bo);
        ExcelUtil.exportExcel(list, "销售出库数据", WmSalesIssueVo.class, response);
    }

    /**
     * 根据主键获取销售出库详细信息
     * <p>
     * 权限标识：{@code wm:salesIssue:query}</p>
     *
     * @param issueId 销售出库 ID
     * @return 销售出库详细信息
     */
    @SaCheckPermission("wm:salesIssue:query")
    @GetMapping("/{issueId}")
    public R<WmSalesIssueVo> getInfo(@PathVariable Long issueId) {
        return R.ok(wmSalesIssueService.selectById(issueId));
    }

    /**
     * 新增销售出库
     * <p>
     * 权限标识：{@code wm:salesIssue:add}</p>
     * <p>日志记录：title="销售出库"，businessType=INSERT</p>
     *
     * @param bo 销售出库业务对象，包含客户、出库物料等信息
     * @return 操作结果，成功返回 {@code R.success()}，失败返回 {@code R.fail()}
     */
    @SaCheckPermission("wm:salesIssue:add")
    @Log(title = "销售出库", businessType = BusinessType.INSERT)
    @PostMapping
    public R<Void> add(@Validated @RequestBody WmSalesIssueBo bo) {
        return toAjax(wmSalesIssueService.insertByBo(bo));
    }

    /**
     * 修改销售出库
     * <p>
     * 权限标识：{@code wm:salesIssue:edit}</p>
     * <p>日志记录：title="销售出库"，businessType=UPDATE</p>
     *
     * @param bo 销售出库业务对象，包含需要更新的字段信息
     * @return 操作结果，成功返回 {@code R.success()}，失败返回 {@code R.fail()}
     */
    @SaCheckPermission("wm:salesIssue:edit")
    @Log(title = "销售出库", businessType = BusinessType.UPDATE)
    @PutMapping
    public R<Void> edit(@Validated @RequestBody WmSalesIssueBo bo) {
        return toAjax(wmSalesIssueService.updateByBo(bo));
    }

    /**
     * 批量删除销售出库
     * <p>
     * 权限标识：{@code wm:salesIssue:remove}</p>
     * <p>日志记录：title="销售出库"，businessType=DELETE</p>
     *
     * @param issueIds 要删除的销售出库 ID 数组（支持批量删除）
     * @return 操作结果，成功返回 {@code R.success()}，失败返回 {@code R.fail()}
     */
    @SaCheckPermission("wm:salesIssue:remove")
    @Log(title = "销售出库", businessType = BusinessType.DELETE)
    @DeleteMapping("/{issueIds}")
    public R<Void> remove(@PathVariable Long[] issueIds) {
        return toAjax(wmSalesIssueService.deleteByIds(issueIds));
    }

    /**
     * 确认销售出库
     * <p>权限标识：{@code wm:salesIssue:confirm}</p>
     * <p>日志记录：title="销售出库"，businessType=UPDATE</p>
     *
     * @param issueId 销售出库 ID
     * @return 操作结果
     */
    @SaCheckPermission("wm:salesIssue:confirm")
    @Log(title = "销售出库", businessType = BusinessType.UPDATE)
    @PutMapping("/confirm/{issueId}")
    public R<Void> confirm(@PathVariable Long issueId) {
        return toAjax(wmSalesIssueService.confirmSalesIssue(issueId));
    }

    /**
     * 完成销售出库
     * <p>权限标识：{@code wm:salesIssue:complete}</p>
     * <p>日志记录：title="销售出库"，businessType=UPDATE</p>
     *
     * @param issueId 销售出库 ID
     * @return 操作结果
     */
    @SaCheckPermission("wm:salesIssue:complete")
    @Log(title = "销售出库", businessType = BusinessType.UPDATE)
    @PutMapping("/complete/{issueId}")
    public R<Void> complete(@PathVariable Long issueId) {
        return toAjax(wmSalesIssueService.completeSalesIssue(issueId));
    }

    // ==================== Flowable 工作流 ====================

    /**
     * 提交销售出库审批
     * <p>权限标识：{@code wm:salesIssue:edit}</p>
     * <p>日志记录：title="销售出库"，businessType=SUBMIT</p>
     *
     * @param issueId     销售出库 ID
     * @param selectFlowId 选择的流程模板 ID
     * @return 操作结果
     */
    @SaCheckPermission("wm:salesIssue:edit")
    @Log(title = "销售出库", businessType = BusinessType.SUBMIT)
    @PostMapping("/{issueId}/submit")
    public R<Void> submit(@PathVariable Long issueId, @RequestParam(required = false) String selectFlowId) {
        wmSalesIssueService.submit(String.valueOf(issueId), selectFlowId);
        return R.ok();
    }

    /**
     * 获取销售出库流程实例 ID
     * <p>
     * 权限标识：{@code wm:salesIssue:query}</p>
     *
     * @param issueId 销售出库 ID
     * @return 流程实例 ID
     */
    @SaCheckPermission("wm:salesIssue:query")
    @GetMapping("/{issueId}/proc")
    public R<String> getProcInsId(@PathVariable Long issueId) {
        // 使用 R.ok(String msg, T data) 重载，避免 R.ok(String) 把 ID 误塞进 msg
        return R.ok("查询成功", wmSalesIssueService.getProcInsId(String.valueOf(issueId)));
    }
}
