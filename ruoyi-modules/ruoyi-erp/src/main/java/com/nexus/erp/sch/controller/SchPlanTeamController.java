package com.nexus.erp.sch.controller;

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
import com.nexus.erp.sch.domain.bo.SchPlanTeamBo;
import com.nexus.erp.sch.domain.vo.SchPlanTeamVo;
import com.nexus.erp.sch.service.ISchPlanTeamService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 计划班组关联 Controller
 * <p>
 * 管理排班计划与班组之间的关联关系，用于将排班计划分配给指定班组执行。
 *
 * @author nexus
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/erp/sch/planteam")
public class SchPlanTeamController extends BaseController {

    private final ISchPlanTeamService schPlanTeamService;

    /**
     * 分页查询计划班组关联列表
     * <p>
     * 权限标识：{@code sch:planteam:list}</p>
     *
     * @param bo        查询参数
     * @param pageQuery 分页参数，包含页码、每页条数及排序信息
     * @return 计划班组关联分页列表
     */
    @SaCheckPermission("sch:planteam:list")
    @GetMapping("/list")
    public TableDataInfo<SchPlanTeamVo> list(SchPlanTeamBo bo, PageQuery pageQuery) {
        return schPlanTeamService.selectPageList(bo, pageQuery);
    }

    /**
     * 导出计划班组关联数据到 Excel
     * <p>权限标识：{@code sch:planteam:export}</p>
     * <p>日志记录：title="计划班组关联"，businessType=EXPORT</p>
     *
     * @param bo        查询参数
     * @param response  HTTP 响应对象，用于输出 Excel 文件流
     */
    @SaCheckPermission("sch:planteam:export")
    @Log(title = "计划班组关联", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(SchPlanTeamBo bo, HttpServletResponse response) {
        List<SchPlanTeamVo> list = schPlanTeamService.selectList(bo);
        ExcelUtil.exportExcel(list, "计划班组关联数据", SchPlanTeamVo.class, response);
    }

    /**
     * 根据主键获取计划班组关联详细信息
     * <p>
     * 权限标识：{@code sch:planteam:query}</p>
     *
     * @param recordId 关联记录 ID
     * @return 计划班组关联详细信息
     */
    @SaCheckPermission("sch:planteam:query")
    @GetMapping("/{recordId}")
    public R<SchPlanTeamVo> getInfo(@PathVariable Long recordId) {
        return R.ok(schPlanTeamService.selectById(recordId));
    }

    /**
     * 新增计划班组关联
     * <p>
     * 权限标识：{@code sch:planteam:add}</p>
     * <p>日志记录：title="计划班组关联"，businessType=INSERT</p>
     *
     * @param bo 计划班组关联业务对象
     * @return 操作结果，成功返回 {@code R.success()}，失败返回 {@code R.fail()}
     */
    @SaCheckPermission("sch:planteam:add")
    @Log(title = "计划班组关联", businessType = BusinessType.INSERT)
    @PostMapping
    public R<Void> add(@Validated @RequestBody SchPlanTeamBo bo) {
        return toAjax(schPlanTeamService.insertByBo(bo));
    }

    /**
     * 修改计划班组关联
     * <p>
     * 权限标识：{@code sch:planteam:edit}</p>
     * <p>日志记录：title="计划班组关联"，businessType=UPDATE</p>
     *
     * @param bo 计划班组关联业务对象，包含需要更新的字段信息
     * @return 操作结果，成功返回 {@code R.success()}，失败返回 {@code R.fail()}
     */
    @SaCheckPermission("sch:planteam:edit")
    @Log(title = "计划班组关联", businessType = BusinessType.UPDATE)
    @PutMapping
    public R<Void> edit(@Validated @RequestBody SchPlanTeamBo bo) {
        return toAjax(schPlanTeamService.updateByBo(bo));
    }

    /**
     * 批量删除计划班组关联
     * <p>
     * 权限标识：{@code sch:planteam:remove}</p>
     * <p>日志记录：title="计划班组关联"，businessType=DELETE</p>
     *
     * @param recordIds 要删除的关联记录 ID 数组（支持批量删除）
     * @return 操作结果，成功返回 {@code R.success()}，失败返回 {@code R.fail()}
     */
    @SaCheckPermission("sch:planteam:remove")
    @Log(title = "计划班组关联", businessType = BusinessType.DELETE)
    @DeleteMapping("/{recordIds}")
    public R<Void> remove(@PathVariable Long[] recordIds) {
        return toAjax(schPlanTeamService.deleteByIds(recordIds));
    }
}
