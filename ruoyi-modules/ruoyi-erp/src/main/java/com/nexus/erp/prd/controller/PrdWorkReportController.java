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
import com.nexus.erp.prd.domain.bo.PrdWorkReportBo;
import com.nexus.erp.prd.domain.vo.PrdWorkReportVo;
import com.nexus.erp.prd.service.IPrdWorkReportService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 生产报工 Controller
 * <p>
 * 提供生产报工记录的 RESTful API 接口，用于管理工人生产工时的上报与统计。
 *
 * @author nexus
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/erp/prd/report")
public class PrdWorkReportController extends BaseController {

    private final IPrdWorkReportService prdWorkReportService;

    /**
     * 分页查询生产报工列表
     * <p>
     * 权限标识：{@code prd:report:list}</p>
     *
     * @param bo        查询参数，包含工单编号、工人、日期范围等筛选条件
     * @param pageQuery 分页参数，包含页码、每页条数及排序信息
     * @return 生产报工分页列表
     */
    @SaCheckPermission("prd:report:list")
    @GetMapping("/list")
    public TableDataInfo<PrdWorkReportVo> list(PrdWorkReportBo bo, PageQuery pageQuery) {
        return prdWorkReportService.selectPageList(bo, pageQuery);
    }

    /**
     * 导出生产报工数据到 Excel
     * <p>权限标识：{@code prd:report:export}</p>
     * <p>日志记录：title="生产报工"，businessType=EXPORT</p>
     *
     * @param bo        查询参数
     * @param response  HTTP 响应对象，用于输出 Excel 文件流
     */
    @SaCheckPermission("prd:report:export")
    @Log(title = "生产报工", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(PrdWorkReportBo bo, HttpServletResponse response) {
        List<PrdWorkReportVo> list = prdWorkReportService.selectList(bo);
        ExcelUtil.exportExcel(list, "生产报工数据", PrdWorkReportVo.class, response);
    }

    /**
     * 根据主键获取生产报工详细信息
     * <p>
     * 权限标识：{@code prd:report:query}</p>
     *
     * @param reportId 生产报工 ID
     * @return 生产报工详细信息
     */
    @SaCheckPermission("prd:report:query")
    @GetMapping("/{reportId}")
    public R<PrdWorkReportVo> getInfo(@PathVariable Long reportId) {
        return R.ok(prdWorkReportService.selectById(reportId));
    }

    /**
     * 新增生产报工记录
     * <p>
     * 权限标识：{@code prd:report:add}</p>
     * <p>日志记录：title="生产报工"，businessType=INSERT</p>
     *
     * @param bo 生产报工业务对象，包含工单、工人、工时、数量等信息
     * @return 操作结果，成功返回 {@code R.success()}，失败返回 {@code R.fail()}
     */
    @SaCheckPermission("prd:report:add")
    @Log(title = "生产报工", businessType = BusinessType.INSERT)
    @PostMapping
    public R<Void> add(@Validated @RequestBody PrdWorkReportBo bo) {
        return toAjax(prdWorkReportService.insertByBo(bo));
    }

    /**
     * 修改生产报工记录
     * <p>
     * 权限标识：{@code prd:report:edit}</p>
     * <p>日志记录：title="生产报工"，businessType=UPDATE</p>
     *
     * @param bo 生产报工业务对象，包含需要更新的字段信息
     * @return 操作结果，成功返回 {@code R.success()}，失败返回 {@code R.fail()}
     */
    @SaCheckPermission("prd:report:edit")
    @Log(title = "生产报工", businessType = BusinessType.UPDATE)
    @PutMapping
    public R<Void> edit(@Validated @RequestBody PrdWorkReportBo bo) {
        return toAjax(prdWorkReportService.updateByBo(bo));
    }

    /**
     * 批量删除生产报工记录
     * <p>
     * 权限标识：{@code prd:report:remove}</p>
     * <p>日志记录：title="生产报工"，businessType=DELETE</p>
     *
     * @param reportIds 要删除的生产报工 ID 数组（支持批量删除）
     * @return 操作结果，成功返回 {@code R.success()}，失败返回 {@code R.fail()}
     */
    @SaCheckPermission("prd:report:remove")
    @Log(title = "生产报工", businessType = BusinessType.DELETE)
    @DeleteMapping("/{reportIds}")
    public R<Void> remove(@PathVariable Long[] reportIds) {
        return toAjax(prdWorkReportService.deleteByIds(reportIds));
    }
}
