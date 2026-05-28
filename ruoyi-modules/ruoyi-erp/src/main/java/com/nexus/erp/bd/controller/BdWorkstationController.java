package com.nexus.erp.bd.controller;

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
import com.nexus.erp.bd.domain.bo.BdWorkstationBo;
import com.nexus.erp.bd.domain.vo.BdWorkstationVo;
import com.nexus.erp.bd.service.IBdWorkstationService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 工作中心 Controller
 * <p>
 * 管理工作中心基本信息，包含工作中心编码、名称、所属车间及产能参数等。
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/erp/bd/workstation")
public class BdWorkstationController extends BaseController {

    private final IBdWorkstationService bdWorkstationService;

    /**
     * 分页查询工作中心列表
     * <p>权限标识：{@code bd:workstation:list}</p>
     *
     * @param bo        查询参数，包含工作中心名称、编码等筛选条件
     * @param pageQuery 分页参数，包含页码、每页条数及排序信息
     * @return 工作中心分页列表
     */
    @SaCheckPermission("bd:workstation:list")
    @GetMapping("/list")
    public TableDataInfo<BdWorkstationVo> list(BdWorkstationBo bo, PageQuery pageQuery) {
        return bdWorkstationService.selectPageList(bo, pageQuery);
    }

    /**
     * 导出工作中心数据到 Excel
     * <p>权限标识：{@code bd:workstation:export}</p>
     * <p>日志记录：title="工作站"，businessType=EXPORT</p>
     *
     * @param bo        查询参数
     * @param response  HTTP 响应对象，用于输出 Excel 文件流
     */
    @SaCheckPermission("bd:workstation:export")
    @Log(title = "工作站", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(BdWorkstationBo bo, HttpServletResponse response) {
        List<BdWorkstationVo> list = bdWorkstationService.selectList(bo);
        ExcelUtil.exportExcel(list, "工作站数据", BdWorkstationVo.class, response);
    }

    /**
     * 根据主键获取工作中心详细信息
     * <p>权限标识：{@code bd:workstation:query}</p>
     *
     * @param id 工作中心 ID
     * @return 工作中心详细信息
     */
    @SaCheckPermission("bd:workstation:query")
    @GetMapping("/{id}")
    public R<BdWorkstationVo> getInfo(@PathVariable Long id) {
        return R.ok(bdWorkstationService.selectById(id));
    }

    /**
     * 新增工作中心
     * <p>权限标识：{@code bd:workstation:add}</p>
     * <p>日志记录：title="工作站"，businessType=INSERT</p>
     *
     * @param bo 工作中心业务对象，包含工作中心基本信息字段
     * @return 操作结果，成功返回 {@code R.success()}，失败返回 {@code R.fail()}
     */
    @SaCheckPermission("bd:workstation:add")
    @Log(title = "工作站", businessType = BusinessType.INSERT)
    @PostMapping
    public R<Void> add(@Validated @RequestBody BdWorkstationBo bo) {
        return toAjax(bdWorkstationService.insertByBo(bo));
    }

    /**
     * 修改工作中心
     * <p>权限标识：{@code bd:workstation:edit}</p>
     * <p>日志记录：title="工作站"，businessType=UPDATE</p>
     *
     * @param bo 工作中心业务对象，包含需要更新的字段信息
     * @return 操作结果，成功返回 {@code R.success()}，失败返回 {@code R.fail()}
     */
    @SaCheckPermission("bd:workstation:edit")
    @Log(title = "工作站", businessType = BusinessType.UPDATE)
    @PutMapping
    public R<Void> edit(@Validated @RequestBody BdWorkstationBo bo) {
        return toAjax(bdWorkstationService.updateByBo(bo));
    }

    /**
     * 批量删除工作中心
     * <p>权限标识：{@code bd:workstation:remove}</p>
     * <p>日志记录：title="工作站"，businessType=DELETE</p>
     *
     * @param ids 工作中心 ID 数组（支持批量删除）
     * @return 操作结果，成功返回 {@code R.success()}，失败返回 {@code R.fail()}
     */
    @SaCheckPermission("bd:workstation:remove")
    @Log(title = "工作站", businessType = BusinessType.DELETE)
    @DeleteMapping("/{ids}")
    public R<Void> remove(@PathVariable Long[] ids) {
        return toAjax(bdWorkstationService.deleteByIds(ids));
    }
}
