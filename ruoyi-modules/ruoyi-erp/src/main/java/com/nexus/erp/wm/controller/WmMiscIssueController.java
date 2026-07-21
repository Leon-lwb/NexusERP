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
import com.nexus.erp.wm.domain.bo.WmMiscIssueBo;
import com.nexus.erp.wm.domain.vo.WmMiscIssueVo;
import com.nexus.erp.wm.service.IWmMiscIssueService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 杂项出库Controller
 * <p>
 * 提供杂项出库单的 RESTful API 接口，包括 CRUD 操作、数据导出及执行出库等功能。
 *
 * @author nexus
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/erp/wm/miscissue")
public class WmMiscIssueController extends BaseController {

    /** 杂项发料业务服务，提供杂项发料相关能力 */
    private final IWmMiscIssueService wmMiscIssueService;

    /**
     * 分页查询杂项出库列表
     *
     * @param bo        查询参数
     * @param pageQuery 分页参数
     * @return 杂项出库分页列表
     */
    @SaCheckPermission("wm:miscissue:list")
    @GetMapping("/list")
    public TableDataInfo<WmMiscIssueVo> list(WmMiscIssueBo bo, PageQuery pageQuery) {
        return wmMiscIssueService.selectPageList(bo, pageQuery);
    }

    /**
     * 导出杂项出库数据到 Excel
     * <p>权限标识：{@code wm:miscissue:export}</p>
     * <p>日志记录：title="杂项出库"，businessType=EXPORT</p>
     *
     * @param bo        查询参数
     * @param response  HTTP 响应对象
     */
    @SaCheckPermission("wm:miscissue:export")
    @Log(title = "杂项出库", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(WmMiscIssueBo bo, HttpServletResponse response) {
        List<WmMiscIssueVo> list = wmMiscIssueService.selectList(bo);
        ExcelUtil.exportExcel(list, "杂项出库数据", WmMiscIssueVo.class, response);
    }

    /**
     * 获取杂项出库详细信息
     * <p>权限标识：{@code wm:miscissue:query}</p>
     *
     * @param issueId 杂项出库ID
     * @return 杂项出库详细信息
     */
    @SaCheckPermission("wm:miscissue:query")
    @GetMapping("/{issueId}")
    public R<WmMiscIssueVo> getInfo(@PathVariable Long issueId) {
        return R.ok(wmMiscIssueService.selectById(issueId));
    }

    /**
     * 新增杂项出库
     * <p>权限标识：{@code wm:miscissue:add}</p>
     * <p>日志记录：title="杂项出库"，businessType=INSERT</p>
     *
     * @param bo 杂项出库业务对象
     * @return 操作结果
     */
    @SaCheckPermission("wm:miscissue:add")
    @Log(title = "杂项出库", businessType = BusinessType.INSERT)
    @PostMapping
    public R<Void> add(@Validated @RequestBody WmMiscIssueBo bo) {
        return toAjax(wmMiscIssueService.insertByBo(bo));
    }

    /**
     * 修改杂项出库
     * <p>权限标识：{@code wm:miscissue:edit}</p>
     * <p>日志记录：title="杂项出库"，businessType=UPDATE</p>
     *
     * @param bo 杂项出库业务对象
     * @return 操作结果
     */
    @SaCheckPermission("wm:miscissue:edit")
    @Log(title = "杂项出库", businessType = BusinessType.UPDATE)
    @PutMapping
    public R<Void> edit(@Validated @RequestBody WmMiscIssueBo bo) {
        return toAjax(wmMiscIssueService.updateByBo(bo));
    }

    /**
     * 删除杂项出库
     * <p>权限标识：{@code wm:miscissue:remove}</p>
     * <p>日志记录：title="杂项出库"，businessType=DELETE</p>
     *
     * @param issueIds 杂项出库ID数组
     * @return 操作结果
     */
    @SaCheckPermission("wm:miscissue:remove")
    @Log(title = "杂项出库", businessType = BusinessType.DELETE)
    @DeleteMapping("/{issueIds}")
    public R<Void> remove(@PathVariable Long[] issueIds) {
        return toAjax(wmMiscIssueService.deleteByIds(issueIds));
    }

    /**
     * 执行杂项出库（扣减库存）
     * <p>权限标识：{@code wm:miscissue:edit}</p>
     * <p>日志记录：title="杂项出库"，businessType=UPDATE</p>
     *
     * @param issueId 杂项出库ID
     * @return 操作结果
     */
    @SaCheckPermission("wm:miscissue:edit")
    @Log(title = "杂项出库", businessType = BusinessType.UPDATE)
    @PutMapping("/execute/{issueId}")
    public R<Void> execute(@PathVariable Long issueId) {
        wmMiscIssueService.execute(issueId);
        return R.ok();
    }
}
