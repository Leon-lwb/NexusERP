package com.nexus.erp.wm.controller;

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
import com.nexus.erp.wm.domain.bo.WmWarehouseBo;
import com.nexus.erp.wm.domain.vo.WmWarehouse3dVo;
import com.nexus.erp.wm.domain.vo.WmWarehouseTreeVo;
import com.nexus.erp.wm.domain.vo.WmWarehouseVo;
import com.nexus.erp.wm.service.IWmWarehouseService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 仓库设置 Controller
 * <p>
 * 管理企业仓库基础信息，包括仓库编码、名称、地址、状态等。
 *
 * @author nexus
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/erp/wm/warehouse")
public class WmWarehouseController extends BaseController {

    private final IWmWarehouseService wmWarehouseService;

    /**
     * 分页查询仓库设置列表
     * <p>
     * 权限标识：{@code wm:warehouse:list}</p>
     *
     * @param bo        查询参数，包含仓库编码、名称、状态等筛选条件
     * @param pageQuery 分页参数，包含页码、每页条数及排序信息
     * @return 仓库设置分页列表
     */
    @SaCheckPermission("wm:warehouse:list")
    @GetMapping("/list")
    public TableDataInfo<WmWarehouseVo> list(WmWarehouseBo bo, PageQuery pageQuery) {
        return wmWarehouseService.selectPageList(bo, pageQuery);
    }

    /**
     * 导出仓库设置数据到 Excel
     * <p>权限标识：{@code wm:warehouse:export}</p>
     * <p>日志记录：title="仓库设置"，businessType=EXPORT</p>
     *
     * @param bo        查询参数
     * @param response  HTTP 响应对象，用于输出 Excel 文件流
     */
    @SaCheckPermission("wm:warehouse:export")
    @Log(title = "仓库设置", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(WmWarehouseBo bo, HttpServletResponse response) {
        List<WmWarehouseVo> list = wmWarehouseService.selectList(bo);
        ExcelUtil.exportExcel(list, "仓库设置数据", WmWarehouseVo.class, response);
    }

    /**
     * 根据主键获取仓库详细信息
     * <p>
     * 权限标识：{@code wm:warehouse:query}</p>
     *
     * @param id 仓库 ID
     * @return 仓库详细信息
     */
    @SaCheckPermission("wm:warehouse:query")
    @GetMapping("/{id}")
    public R<WmWarehouseVo> getInfo(@PathVariable Long id) {
        return R.ok(wmWarehouseService.selectById(id));
    }

    /**
     * 新增仓库设置
     * <p>
     * 权限标识：{@code wm:warehouse:add}</p>
     * <p>日志记录：title="仓库设置"，businessType=INSERT</p>
     *
     * @param bo 仓库业务对象，包含仓库编码、名称、地址等信息
     * @return 操作结果，成功返回 {@code R.success()}，失败返回 {@code R.fail()}
     */
    @SaCheckPermission("wm:warehouse:add")
    @Log(title = "仓库设置", businessType = BusinessType.INSERT)
    @PostMapping
    public R<Void> add(@Validated @RequestBody WmWarehouseBo bo) {
        return toAjax(wmWarehouseService.insertByBo(bo));
    }

    /**
     * 修改仓库设置
     * <p>
     * 权限标识：{@code wm:warehouse:edit}</p>
     * <p>日志记录：title="仓库设置"，businessType=UPDATE</p>
     *
     * @param bo 仓库业务对象，包含需要更新的字段信息
     * @return 操作结果，成功返回 {@code R.success()}，失败返回 {@code R.fail()}
     */
    @SaCheckPermission("wm:warehouse:edit")
    @Log(title = "仓库设置", businessType = BusinessType.UPDATE)
    @PutMapping
    public R<Void> edit(@Validated @RequestBody WmWarehouseBo bo) {
        return toAjax(wmWarehouseService.updateByBo(bo));
    }

    /**
     * 批量删除仓库设置
     * <p>
     * 权限标识：{@code wm:warehouse:remove}</p>
     * <p>日志记录：title="仓库设置"，businessType=DELETE</p>
     *
     * @param ids 要删除的仓库 ID 数组（支持批量删除）
     * @return 操作结果，成功返回 {@code R.success()}，失败返回 {@code R.fail()}
     */
    @SaCheckPermission("wm:warehouse:remove")
    @Log(title = "仓库设置", businessType = BusinessType.DELETE)
    @DeleteMapping("/{ids}")
    public R<Void> remove(@PathVariable Long[] ids) {
        return toAjax(wmWarehouseService.deleteByIds(ids));
    }

    /**
     * 查询仓库树形列表
     * <p>
     * 权限标识：{@code wm:warehouse:list}</p>
     *
     * @return 仓库树形结构列表
     */
    @SaCheckPermission("wm:warehouse:list")
    @GetMapping("/tree")
    public R<List<WmWarehouseTreeVo>> getWareHouseTree() {
        return R.ok(wmWarehouseService.getWareHouseTree());
    }

    /**
     * 获取仓库 3D 可视化聚合数据
     * <p>
     * 权限标识：{@code wm:warehouse:query}</p>
     * <p>
     * 返回仓库基础信息 + 库区列表（含库位 3D 坐标与库存状态），供前端 three.js 渲染。
     * </p>
     *
     * @param id 仓库 ID
     * @return 仓库 3D 可视化聚合数据
     */
    @SaCheckPermission("wm:warehouse:query")
    @GetMapping("/{id}/3d")
    public R<WmWarehouse3dVo> getWarehouse3d(@PathVariable Long id) {
        return R.ok(wmWarehouseService.getWarehouse3d(id));
    }
}
