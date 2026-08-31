package com.nexus.system.controller.monitor;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.baomidou.lock.annotation.Lock4j;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import com.nexus.common.core.domain.R;
import com.nexus.common.excel.utils.ExcelUtil;
import com.nexus.common.log.annotation.Log;
import com.nexus.common.log.enums.BusinessType;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.common.web.core.BaseController;
import com.nexus.system.domain.bo.SysOperLogBo;
import com.nexus.system.domain.vo.SysOperLogVo;
import com.nexus.system.service.ISysOperLogService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 操作日志记录Controller
 * <p>提供操作日志的分页查询、Excel 导出、批量删除与清空等接口。
 * 属于系统管理（System）模块的监控（monitor）分组。</p>
 *
 * @author wanghuan
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/monitor/operlog")
public class SysOperlogController extends BaseController {

    /** 操作日志业务服务，提供分页、导出、删除与清理等能力。 */
    private final ISysOperLogService operLogService;

    /**
     * 分页查询操作日志记录列表。
     *
     * @param operLog  操作日志查询条件（操作人、模块、状态、时间等）
     * @param pageQuery 分页查询参数，包含页码、每页大小及排序条件
     * @return 分页结果，包含操作日志列表及分页元信息
     */
    @SaCheckPermission("monitor:operlog:list")
    @GetMapping("/list")
    public TableDataInfo<SysOperLogVo> list(SysOperLogBo operLog, PageQuery pageQuery) {
        return operLogService.selectPageOperLogList(operLog, pageQuery);
    }

    /**
     * 导出操作日志记录列表为 Excel 文件。
     *
     * @param operLog  操作日志查询条件
     * @param response HTTP 响应，用于写入导出的 Excel 流
     */
    @Log(title = "操作日志", businessType = BusinessType.EXPORT)
    @SaCheckPermission("monitor:operlog:export")
    @PostMapping("/export")
    public void export(SysOperLogBo operLog, HttpServletResponse response) {
        List<SysOperLogVo> list = operLogService.selectOperLogList(operLog);
        ExcelUtil.exportExcel(list, "操作日志", SysOperLogVo.class, response);
    }

    /**
     * 批量删除操作日志记录
     * @param operIds 日志ids
     */
    @Log(title = "操作日志", businessType = BusinessType.DELETE)
    @SaCheckPermission("monitor:operlog:remove")
    @DeleteMapping("/{operIds}")
    public R<Void> remove(@PathVariable Long[] operIds) {
        return toAjax(operLogService.deleteOperLogByIds(operIds));
    }

    /**
     * 清理操作日志记录
     */
    @Log(title = "操作日志", businessType = BusinessType.CLEAN)
    @SaCheckPermission("monitor:operlog:remove")
    @Lock4j
    @DeleteMapping("/clean")
    public R<Void> clean() {
        operLogService.cleanOperLog();
        return R.ok();
    }
}
