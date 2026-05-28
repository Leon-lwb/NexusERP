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
import com.nexus.erp.sch.domain.bo.SchTeamScheduleBo;
import com.nexus.erp.sch.domain.vo.SchTeamScheduleVo;
import com.nexus.erp.sch.service.ISchTeamScheduleService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 班组排班（模板） Controller
 * <p>
 * 管理班组周期排班模板，定义班组每周/每月的固定排班计划，
 * 用于快速生成实际的排班日历。
 *
 * @author nexus
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/erp/sch/teamSchedule")
public class SchTeamScheduleController extends BaseController {

    private final ISchTeamScheduleService schTeamScheduleService;

    /**
     * 分页查询班组排班模板列表
     * <p>
     * 权限标识：{@code sch:teamSchedule:list}</p>
     *
     * @param bo        查询参数，包含班组、班次等筛选条件
     * @param pageQuery 分页参数，包含页码、每页条数及排序信息
     * @return 班组排班模板分页列表
     */
    @SaCheckPermission("sch:teamSchedule:list")
    @GetMapping("/list")
    public TableDataInfo<SchTeamScheduleVo> list(SchTeamScheduleBo bo, PageQuery pageQuery) {
        return schTeamScheduleService.selectPageList(bo, pageQuery);
    }

    /**
     * 导出班组排班模板数据到 Excel
     * <p>权限标识：{@code sch:teamSchedule:export}</p>
     * <p>日志记录：title="班组排班（模板）"，businessType=EXPORT</p>
     *
     * @param bo        查询参数
     * @param response  HTTP 响应对象，用于输出 Excel 文件流
     */
    @SaCheckPermission("sch:teamSchedule:export")
    @Log(title = "班组排班（模板）", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(SchTeamScheduleBo bo, HttpServletResponse response) {
        List<SchTeamScheduleVo> list = schTeamScheduleService.selectList(bo);
        ExcelUtil.exportExcel(list, "班组排班（模板）数据", SchTeamScheduleVo.class, response);
    }

    /**
     * 根据主键获取班组排班模板详细信息
     * <p>
     * 权限标识：{@code sch:teamSchedule:query}</p>
     *
     * @param teamScheduleId 排班模板记录 ID
     * @return 班组排班模板详细信息
     */
    @SaCheckPermission("sch:teamSchedule:query")
    @GetMapping("/{teamScheduleId}")
    public R<SchTeamScheduleVo> getInfo(@PathVariable Long teamScheduleId) {
        return R.ok(schTeamScheduleService.selectById(teamScheduleId));
    }

    /**
     * 新增班组排班模板
     * <p>
     * 权限标识：{@code sch:teamSchedule:add}</p>
     * <p>日志记录：title="班组排班（模板）"，businessType=INSERT</p>
     *
     * @param bo 班组排班模板业务对象，包含班组、班次、日期范围等信息
     * @return 操作结果，成功返回 {@code R.success()}，失败返回 {@code R.fail()}
     */
    @SaCheckPermission("sch:teamSchedule:add")
    @Log(title = "班组排班（模板）", businessType = BusinessType.INSERT)
    @PostMapping
    public R<Void> add(@Validated @RequestBody SchTeamScheduleBo bo) {
        return toAjax(schTeamScheduleService.insertByBo(bo));
    }

    /**
     * 修改班组排班模板
     * <p>
     * 权限标识：{@code sch:teamSchedule:edit}</p>
     * <p>日志记录：title="班组排班（模板）"，businessType=UPDATE</p>
     *
     * @param bo 班组排班模板业务对象，包含需要更新的字段信息
     * @return 操作结果，成功返回 {@code R.success()}，失败返回 {@code R.fail()}
     */
    @SaCheckPermission("sch:teamSchedule:edit")
    @Log(title = "班组排班（模板）", businessType = BusinessType.UPDATE)
    @PutMapping
    public R<Void> edit(@Validated @RequestBody SchTeamScheduleBo bo) {
        return toAjax(schTeamScheduleService.updateByBo(bo));
    }

    /**
     * 批量删除班组排班模板
     * <p>
     * 权限标识：{@code sch:teamSchedule:remove}</p>
     * <p>日志记录：title="班组排班（模板）"，businessType=DELETE</p>
     *
     * @param teamScheduleIds 要删除的排班模板记录 ID 数组（支持批量删除）
     * @return 操作结果，成功返回 {@code R.success()}，失败返回 {@code R.fail()}
     */
    @SaCheckPermission("sch:teamSchedule:remove")
    @Log(title = "班组排班（模板）", businessType = BusinessType.DELETE)
    @DeleteMapping("/{teamScheduleIds}")
    public R<Void> remove(@PathVariable Long[] teamScheduleIds) {
        return toAjax(schTeamScheduleService.deleteByIds(teamScheduleIds));
    }
}
