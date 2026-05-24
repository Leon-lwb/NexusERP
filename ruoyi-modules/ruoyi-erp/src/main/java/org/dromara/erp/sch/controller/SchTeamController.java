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
import org.dromara.erp.sch.domain.bo.SchTeamBo;
import org.dromara.erp.sch.domain.vo.SchTeamVo;
import org.dromara.erp.sch.service.ISchTeamService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 班组设置 Controller
 * <p>
 * 管理生产班组的基础信息配置，用于排班计划与生产任务的班组分配。
 *
 * @author nexus
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/erp/sch/team")
public class SchTeamController extends BaseController {

    private final ISchTeamService schTeamService;

    /**
     * 分页查询班组设置列表
     * <p>
     * 权限标识：{@code sch:team:list}</p>
     *
     * @param bo        查询参数，包含班组名称、编码等筛选条件
     * @param pageQuery 分页参数，包含页码、每页条数及排序信息
     * @return 班组分页列表
     */
    @SaCheckPermission("sch:team:list")
    @GetMapping("/list")
    public TableDataInfo<SchTeamVo> list(SchTeamBo bo, PageQuery pageQuery) {
        return schTeamService.selectPageList(bo, pageQuery);
    }

    /**
     * 导出班组设置数据到 Excel
     * <p>权限标识：{@code sch:team:export}</p>
     * <p>日志记录：title="班组设置"，businessType=EXPORT</p>
     *
     * @param bo        查询参数
     * @param response  HTTP 响应对象，用于输出 Excel 文件流
     */
    @SaCheckPermission("sch:team:export")
    @Log(title = "班组设置", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(SchTeamBo bo, HttpServletResponse response) {
        List<SchTeamVo> list = schTeamService.selectList(bo);
        ExcelUtil.exportExcel(list, "班组设置数据", SchTeamVo.class, response);
    }

    /**
     * 根据主键获取班组详细信息
     * <p>
     * 权限标识：{@code sch:team:query}</p>
     *
     * @param teamId 班组 ID
     * @return 班组详细信息
     */
    @SaCheckPermission("sch:team:query")
    @GetMapping("/{teamId}")
    public R<SchTeamVo> getInfo(@PathVariable Long teamId) {
        return R.ok(schTeamService.selectById(teamId));
    }

    /**
     * 新增班组设置
     * <p>
     * 权限标识：{@code sch:team:add}</p>
     * <p>日志记录：title="班组设置"，businessType=INSERT</p>
     *
     * @param bo 班组业务对象，包含班组名称、编码、所属部门等信息
     * @return 操作结果，成功返回 {@code R.success()}，失败返回 {@code R.fail()}
     */
    @SaCheckPermission("sch:team:add")
    @Log(title = "班组设置", businessType = BusinessType.INSERT)
    @PostMapping
    public R<Void> add(@Validated @RequestBody SchTeamBo bo) {
        return toAjax(schTeamService.insertByBo(bo));
    }

    /**
     * 修改班组设置
     * <p>
     * 权限标识：{@code sch:team:edit}</p>
     * <p>日志记录：title="班组设置"，businessType=UPDATE</p>
     *
     * @param bo 班组业务对象，包含需要更新的字段信息
     * @return 操作结果，成功返回 {@code R.success()}，失败返回 {@code R.fail()}
     */
    @SaCheckPermission("sch:team:edit")
    @Log(title = "班组设置", businessType = BusinessType.UPDATE)
    @PutMapping
    public R<Void> edit(@Validated @RequestBody SchTeamBo bo) {
        return toAjax(schTeamService.updateByBo(bo));
    }

    /**
     * 批量删除班组设置
     * <p>
     * 权限标识：{@code sch:team:remove}</p>
     * <p>日志记录：title="班组设置"，businessType=DELETE</p>
     *
     * @param teamIds 要删除的班组 ID 数组（支持批量删除）
     * @return 操作结果，成功返回 {@code R.success()}，失败返回 {@code R.fail()}
     */
    @SaCheckPermission("sch:team:remove")
    @Log(title = "班组设置", businessType = BusinessType.DELETE)
    @DeleteMapping("/{teamIds}")
    public R<Void> remove(@PathVariable Long[] teamIds) {
        return toAjax(schTeamService.deleteByIds(teamIds));
    }
}
