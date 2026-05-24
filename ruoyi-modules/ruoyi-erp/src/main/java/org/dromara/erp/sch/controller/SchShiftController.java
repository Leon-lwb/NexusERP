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
import org.dromara.erp.sch.domain.bo.SchShiftBo;
import org.dromara.erp.sch.domain.vo.SchShiftVo;
import org.dromara.erp.sch.service.ISchShiftService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 班次定义 Controller
 * <p>
 * 管理企业生产/工作班次（如早班、中班、晚班）的起止时间、跨日规则等配置。
 *
 * @author nexus
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/erp/sch/shift")
public class SchShiftController extends BaseController {

    private final ISchShiftService schShiftService;

    /**
     * 分页查询班次定义列表
     * <p>
     * 权限标识：{@code sch:shift:list}</p>
     *
     * @param bo        查询参数，包含班次名称、类型等筛选条件
     * @param pageQuery 分页参数，包含页码、每页条数及排序信息
     * @return 班次分页列表
     */
    @SaCheckPermission("sch:shift:list")
    @GetMapping("/list")
    public TableDataInfo<SchShiftVo> list(SchShiftBo bo, PageQuery pageQuery) {
        return schShiftService.selectPageList(bo, pageQuery);
    }

    /**
     * 导出班次定义数据到 Excel
     * <p>权限标识：{@code sch:shift:export}</p>
     * <p>日志记录：title="班次定义"，businessType=EXPORT</p>
     *
     * @param bo        查询参数
     * @param response  HTTP 响应对象，用于输出 Excel 文件流
     */
    @SaCheckPermission("sch:shift:export")
    @Log(title = "班次定义", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(SchShiftBo bo, HttpServletResponse response) {
        List<SchShiftVo> list = schShiftService.selectList(bo);
        ExcelUtil.exportExcel(list, "班次定义数据", SchShiftVo.class, response);
    }

    /**
     * 根据主键获取班次详细信息
     * <p>
     * 权限标识：{@code sch:shift:query}</p>
     *
     * @param shiftId 班次 ID
     * @return 班次详细信息
     */
    @SaCheckPermission("sch:shift:query")
    @GetMapping("/{shiftId}")
    public R<SchShiftVo> getInfo(@PathVariable Long shiftId) {
        return R.ok(schShiftService.selectById(shiftId));
    }

    /**
     * 新增班次定义
     * <p>
     * 权限标识：{@code sch:shift:add}</p>
     * <p>日志记录：title="班次定义"，businessType=INSERT</p>
     *
     * @param bo 班次业务对象，包含班次名称、起止时间等配置信息
     * @return 操作结果，成功返回 {@code R.success()}，失败返回 {@code R.fail()}
     */
    @SaCheckPermission("sch:shift:add")
    @Log(title = "班次定义", businessType = BusinessType.INSERT)
    @PostMapping
    public R<Void> add(@Validated @RequestBody SchShiftBo bo) {
        return toAjax(schShiftService.insertByBo(bo));
    }

    /**
     * 修改班次定义
     * <p>
     * 权限标识：{@code sch:shift:edit}</p>
     * <p>日志记录：title="班次定义"，businessType=UPDATE</p>
     *
     * @param bo 班次业务对象，包含需要更新的字段信息
     * @return 操作结果，成功返回 {@code R.success()}，失败返回 {@code R.fail()}
     */
    @SaCheckPermission("sch:shift:edit")
    @Log(title = "班次定义", businessType = BusinessType.UPDATE)
    @PutMapping
    public R<Void> edit(@Validated @RequestBody SchShiftBo bo) {
        return toAjax(schShiftService.updateByBo(bo));
    }

    /**
     * 批量删除班次定义
     * <p>
     * 权限标识：{@code sch:shift:remove}</p>
     * <p>日志记录：title="班次定义"，businessType=DELETE</p>
     *
     * @param shiftIds 要删除的班次 ID 数组（支持批量删除）
     * @return 操作结果，成功返回 {@code R.success()}，失败返回 {@code R.fail()}
     */
    @SaCheckPermission("sch:shift:remove")
    @Log(title = "班次定义", businessType = BusinessType.DELETE)
    @DeleteMapping("/{shiftIds}")
    public R<Void> remove(@PathVariable Long[] shiftIds) {
        return toAjax(schShiftService.deleteByIds(shiftIds));
    }
}
