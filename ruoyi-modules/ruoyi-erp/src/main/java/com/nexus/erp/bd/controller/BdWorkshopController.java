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
import com.nexus.erp.bd.domain.bo.BdWorkshopBo;
import com.nexus.erp.bd.domain.vo.BdWorkshopVo;
import com.nexus.erp.bd.service.IBdWorkshopService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 车间信息 Controller
 * <p>
 * 管理生产车间基本信息，包含车间编码、名称及所属工厂等。
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/erp/bd/workshop")
public class BdWorkshopController extends BaseController {

    private final IBdWorkshopService bdWorkshopService;

    /**
     * 分页查询车间列表
     * <p>权限标识：{@code bd:workshop:list}</p>
     *
     * @param bo        查询参数，包含车间名称、编码等筛选条件
     * @param pageQuery 分页参数，包含页码、每页条数及排序信息
     * @return 车间分页列表
     */
    @SaCheckPermission("bd:workshop:list")
    @GetMapping("/list")
    public TableDataInfo<BdWorkshopVo> list(BdWorkshopBo bo, PageQuery pageQuery) {
        return bdWorkshopService.selectPageList(bo, pageQuery);
    }

    /**
     * 导出车间数据到 Excel
     * <p>权限标识：{@code bd:workshop:export}</p>
     * <p>日志记录：title="车间"，businessType=EXPORT</p>
     *
     * @param bo        查询参数
     * @param response  HTTP 响应对象，用于输出 Excel 文件流
     */
    @SaCheckPermission("bd:workshop:export")
    @Log(title = "车间", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(BdWorkshopBo bo, HttpServletResponse response) {
        List<BdWorkshopVo> list = bdWorkshopService.selectList(bo);
        ExcelUtil.exportExcel(list, "车间数据", BdWorkshopVo.class, response);
    }

    /**
     * 根据主键获取车间详细信息
     * <p>权限标识：{@code bd:workshop:query}</p>
     *
     * @param id 车间 ID
     * @return 车间详细信息
     */
    @SaCheckPermission("bd:workshop:query")
    @GetMapping("/{id}")
    public R<BdWorkshopVo> getInfo(@PathVariable Long id) {
        return R.ok(bdWorkshopService.selectById(id));
    }

    /**
     * 新增车间
     * <p>权限标识：{@code bd:workshop:add}</p>
     * <p>日志记录：title="车间"，businessType=INSERT</p>
     *
     * @param bo 车间业务对象，包含车间基本信息字段
     * @return 操作结果，成功返回 {@code R.success()}，失败返回 {@code R.fail()}
     */
    @SaCheckPermission("bd:workshop:add")
    @Log(title = "车间", businessType = BusinessType.INSERT)
    @PostMapping
    public R<Void> add(@Validated @RequestBody BdWorkshopBo bo) {
        return toAjax(bdWorkshopService.insertByBo(bo));
    }

    /**
     * 修改车间
     * <p>权限标识：{@code bd:workshop:edit}</p>
     * <p>日志记录：title="车间"，businessType=UPDATE</p>
     *
     * @param bo 车间业务对象，包含需要更新的字段信息
     * @return 操作结果，成功返回 {@code R.success()}，失败返回 {@code R.fail()}
     */
    @SaCheckPermission("bd:workshop:edit")
    @Log(title = "车间", businessType = BusinessType.UPDATE)
    @PutMapping
    public R<Void> edit(@Validated @RequestBody BdWorkshopBo bo) {
        return toAjax(bdWorkshopService.updateByBo(bo));
    }

    /**
     * 批量删除车间
     * <p>权限标识：{@code bd:workshop:remove}</p>
     * <p>日志记录：title="车间"，businessType=DELETE</p>
     *
     * @param ids 车间 ID 数组（支持批量删除）
     * @return 操作结果，成功返回 {@code R.success()}，失败返回 {@code R.fail()}
     */
    @SaCheckPermission("bd:workshop:remove")
    @Log(title = "车间", businessType = BusinessType.DELETE)
    @DeleteMapping("/{ids}")
    public R<Void> remove(@PathVariable Long[] ids) {
        return toAjax(bdWorkshopService.deleteByIds(ids));
    }
}
