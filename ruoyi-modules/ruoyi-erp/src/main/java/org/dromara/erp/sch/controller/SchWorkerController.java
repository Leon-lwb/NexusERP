package org.dromara.erp.sch.controller;

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
import org.dromara.erp.sch.domain.bo.SchWorkerBo;
import org.dromara.erp.sch.domain.vo.SchWorkerVo;
import org.dromara.erp.sch.service.ISchWorkerService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 生产工人档案 Controller
 * <p>
 * 管理生产工人的基本信息档案，包括工号、姓名、技能等级、所属班组等。
 *
 * @author nexus
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/erp/sch/worker")
public class SchWorkerController extends BaseController {

    private final ISchWorkerService schWorkerService;

    /**
     * 分页查询生产工人档案列表
     * <p>
     * 权限标识：{@code sch:worker:list}</p>
     *
     * @param bo        查询参数，包含工号、姓名、班组等筛选条件
     * @param pageQuery 分页参数，包含页码、每页条数及排序信息
     * @return 工人档案分页列表
     */
    @SaCheckPermission("sch:worker:list")
    @GetMapping("/list")
    public TableDataInfo<SchWorkerVo> list(SchWorkerBo bo, PageQuery pageQuery) {
        return schWorkerService.selectPageList(bo, pageQuery);
    }

    /**
     * 导出生产工人档案数据到 Excel
     * <p>权限标识：{@code sch:worker:export}</p>
     * <p>日志记录：title="生产工人档案"，businessType=EXPORT</p>
     *
     * @param bo        查询参数
     * @param response  HTTP 响应对象，用于输出 Excel 文件流
     */
    @SaCheckPermission("sch:worker:export")
    @Log(title = "生产工人档案", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(SchWorkerBo bo, HttpServletResponse response) {
        List<SchWorkerVo> list = schWorkerService.selectList(bo);
        ExcelUtil.exportExcel(list, "生产工人档案数据", SchWorkerVo.class, response);
    }

    /**
     * 根据主键获取工人详细信息
     * <p>
     * 权限标识：{@code sch:worker:query}</p>
     *
     * @param workerId 工人 ID
     * @return 工人详细信息
     */
    @SaCheckPermission("sch:worker:query")
    @GetMapping("/{workerId}")
    public R<SchWorkerVo> getInfo(@PathVariable Long workerId) {
        return R.ok(schWorkerService.selectById(workerId));
    }

    /**
     * 新增生产工人档案
     * <p>
     * 权限标识：{@code sch:worker:add}</p>
     * <p>日志记录：title="生产工人档案"，businessType=INSERT</p>
     *
     * @param bo 工人档案业务对象，包含工号、姓名、所属班组等信息
     * @return 操作结果，成功返回 {@code R.success()}，失败返回 {@code R.fail()}
     */
    @SaCheckPermission("sch:worker:add")
    @Log(title = "生产工人档案", businessType = BusinessType.INSERT)
    @PostMapping
    public R<Void> add(@Validated @RequestBody SchWorkerBo bo) {
        return toAjax(schWorkerService.insertByBo(bo));
    }

    /**
     * 修改生产工人档案
     * <p>
     * 权限标识：{@code sch:worker:edit}</p>
     * <p>日志记录：title="生产工人档案"，businessType=UPDATE</p>
     *
     * @param bo 工人档案业务对象，包含需要更新的字段信息
     * @return 操作结果，成功返回 {@code R.success()}，失败返回 {@code R.fail()}
     */
    @SaCheckPermission("sch:worker:edit")
    @Log(title = "生产工人档案", businessType = BusinessType.UPDATE)
    @PutMapping
    public R<Void> edit(@Validated @RequestBody SchWorkerBo bo) {
        return toAjax(schWorkerService.updateByBo(bo));
    }

    /**
     * 批量删除生产工人档案
     * <p>
     * 权限标识：{@code sch:worker:remove}</p>
     * <p>日志记录：title="生产工人档案"，businessType=DELETE</p>
     *
     * @param workerIds 要删除的工人 ID 数组（支持批量删除）
     * @return 操作结果，成功返回 {@code R.success()}，失败返回 {@code R.fail()}
     */
    @SaCheckPermission("sch:worker:remove")
    @Log(title = "生产工人档案", businessType = BusinessType.DELETE)
    @DeleteMapping("/{workerIds}")
    public R<Void> remove(@PathVariable Long[] workerIds) {
        return toAjax(schWorkerService.deleteByIds(workerIds));
    }
}
