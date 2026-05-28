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
import com.nexus.erp.sch.domain.bo.SchHolidayBo;
import com.nexus.erp.sch.domain.vo.SchHolidayVo;
import com.nexus.erp.sch.service.ISchHolidayService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 节假日设置 Controller
 * <p>
 * 提供国家法定节假日与企业自定义假期的增删改查功能，
 * 用于排班、考勤等业务模块的日历规则计算。
 *
 * @author nexus
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/erp/sch/holiday")
public class SchHolidayController extends BaseController {

    private final ISchHolidayService schHolidayService;

    /**
     * 分页查询节假日设置列表
     * <p>
     * 权限标识：{@code sch:holiday:list}</p>
     *
     * @param bo        查询参数，包含日期范围、节日名称等筛选条件
     * @param pageQuery 分页参数，包含页码、每页条数及排序信息
     * @return 节假日分页列表
     */
    @SaCheckPermission("sch:holiday:list")
    @GetMapping("/list")
    public TableDataInfo<SchHolidayVo> list(SchHolidayBo bo, PageQuery pageQuery) {
        return schHolidayService.selectPageList(bo, pageQuery);
    }

    /**
     * 导出节假日设置数据到 Excel
     * <p>权限标识：{@code sch:holiday:export}</p>
     * <p>日志记录：title="节假日设置"，businessType=EXPORT</p>
     *
     * @param bo        查询参数
     * @param response  HTTP 响应对象，用于输出 Excel 文件流
     */
    @SaCheckPermission("sch:holiday:export")
    @Log(title = "节假日设置", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(SchHolidayBo bo, HttpServletResponse response) {
        List<SchHolidayVo> list = schHolidayService.selectList(bo);
        ExcelUtil.exportExcel(list, "节假日设置数据", SchHolidayVo.class, response);
    }

    /**
     * 根据主键获取节假日详细信息
     * <p>
     * 权限标识：{@code sch:holiday:query}</p>
     *
     * @param holidayId 节假日 ID
     * @return 节假日详细信息
     */
    @SaCheckPermission("sch:holiday:query")
    @GetMapping("/{holidayId}")
    public R<SchHolidayVo> getInfo(@PathVariable Long holidayId) {
        return R.ok(schHolidayService.selectById(holidayId));
    }

    /**
     * 新增节假日设置
     * <p>
     * 权限标识：{@code sch:holiday:add}</p>
     * <p>日志记录：title="节假日设置"，businessType=INSERT</p>
     *
     * @param bo 节假日业务对象，包含节日日期、名称、类型等信息
     * @return 操作结果，成功返回 {@code R.success()}，失败返回 {@code R.fail()}
     */
    @SaCheckPermission("sch:holiday:add")
    @Log(title = "节假日设置", businessType = BusinessType.INSERT)
    @PostMapping
    public R<Void> add(@Validated @RequestBody SchHolidayBo bo) {
        return toAjax(schHolidayService.insertByBo(bo));
    }

    /**
     * 修改节假日设置
     * <p>
     * 权限标识：{@code sch:holiday:edit}</p>
     * <p>日志记录：title="节假日设置"，businessType=UPDATE</p>
     *
     * @param bo 节假日业务对象，包含需要更新的字段信息
     * @return 操作结果，成功返回 {@code R.success()}，失败返回 {@code R.fail()}
     */
    @SaCheckPermission("sch:holiday:edit")
    @Log(title = "节假日设置", businessType = BusinessType.UPDATE)
    @PutMapping
    public R<Void> edit(@Validated @RequestBody SchHolidayBo bo) {
        return toAjax(schHolidayService.updateByBo(bo));
    }

    /**
     * 批量删除节假日设置
     * <p>
     * 权限标识：{@code sch:holiday:remove}</p>
     * <p>日志记录：title="节假日设置"，businessType=DELETE</p>
     *
     * @param holidayIds 要删除的节假日 ID 数组（支持批量删除）
     * @return 操作结果，成功返回 {@code R.success()}，失败返回 {@code R.fail()}
     */
    @SaCheckPermission("sch:holiday:remove")
    @Log(title = "节假日设置", businessType = BusinessType.DELETE)
    @DeleteMapping("/{holidayIds}")
    public R<Void> remove(@PathVariable Long[] holidayIds) {
        return toAjax(schHolidayService.deleteByIds(holidayIds));
    }
}
