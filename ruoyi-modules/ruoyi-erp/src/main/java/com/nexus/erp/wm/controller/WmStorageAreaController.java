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
import com.nexus.erp.wm.domain.bo.WmStorageAreaBo;
import com.nexus.erp.wm.domain.vo.WmStorageAreaVo;
import com.nexus.erp.wm.service.IWmStorageAreaService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 库区 Controller
 * <p>
 * 管理仓库区域的划分信息，包括库区编码、名称、所属仓库等。
 *
 * @author nexus
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/erp/wm/storagearea")
public class WmStorageAreaController extends BaseController {

    /** 存储区域业务服务，提供存储区域相关能力 */
    private final IWmStorageAreaService wmStorageAreaService;

    /**
     * 分页查询库区列表
     * <p>
     * 权限标识：{@code wm:storagearea:list}</p>
     *
     * @param bo        查询参数，包含库区名称、编码、所属仓库等筛选条件
     * @param pageQuery 分页参数，包含页码、每页条数及排序信息
     * @return 库区分页列表
     */
    @SaCheckPermission("wm:storagearea:list")
    @GetMapping("/list")
    public TableDataInfo<WmStorageAreaVo> list(WmStorageAreaBo bo, PageQuery pageQuery) {
        return wmStorageAreaService.selectPageList(bo, pageQuery);
    }

    /**
     * 导出库区数据到 Excel
     * <p>权限标识：{@code wm:storagearea:export}</p>
     * <p>日志记录：title="库区"，businessType=EXPORT</p>
     *
     * @param bo        查询参数
     * @param response  HTTP 响应对象，用于输出 Excel 文件流
     */
    @SaCheckPermission("wm:storagearea:export")
    @Log(title = "库区", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(WmStorageAreaBo bo, HttpServletResponse response) {
        List<WmStorageAreaVo> list = wmStorageAreaService.selectList(bo);
        ExcelUtil.exportExcel(list, "库区数据", WmStorageAreaVo.class, response);
    }

    /**
     * 根据主键获取库区详细信息
     * <p>
     * 权限标识：{@code wm:storagearea:query}</p>
     *
     * @param id 库区 ID
     * @return 库区详细信息
     */
    @SaCheckPermission("wm:storagearea:query")
    @GetMapping("/{id}")
    public R<WmStorageAreaVo> getInfo(@PathVariable Long id) {
        return R.ok(wmStorageAreaService.selectById(id));
    }

    /**
     * 新增库区
     * <p>
     * 权限标识：{@code wm:storagearea:add}</p>
     * <p>日志记录：title="库区"，businessType=INSERT</p>
     *
     * @param bo 库区业务对象，包含库区编码、名称、所属仓库等信息
     * @return 操作结果，成功返回 {@code R.success()}，失败返回 {@code R.fail()}
     */
    @SaCheckPermission("wm:storagearea:add")
    @Log(title = "库区", businessType = BusinessType.INSERT)
    @PostMapping
    public R<Void> add(@Validated @RequestBody WmStorageAreaBo bo) {
        return toAjax(wmStorageAreaService.insertByBo(bo));
    }

    /**
     * 修改库区
     * <p>
     * 权限标识：{@code wm:storagearea:edit}</p>
     * <p>日志记录：title="库区"，businessType=UPDATE</p>
     *
     * @param bo 库区业务对象，包含需要更新的字段信息
     * @return 操作结果，成功返回 {@code R.success()}，失败返回 {@code R.fail()}
     */
    @SaCheckPermission("wm:storagearea:edit")
    @Log(title = "库区", businessType = BusinessType.UPDATE)
    @PutMapping
    public R<Void> edit(@Validated @RequestBody WmStorageAreaBo bo) {
        return toAjax(wmStorageAreaService.updateByBo(bo));
    }

    /**
     * 批量删除库区
     * <p>
     * 权限标识：{@code wm:storagearea:remove}</p>
     * <p>日志记录：title="库区"，businessType=DELETE</p>
     *
     * @param ids 要删除的库区 ID 数组（支持批量删除）
     * @return 操作结果，成功返回 {@code R.success()}，失败返回 {@code R.fail()}
     */
    @SaCheckPermission("wm:storagearea:remove")
    @Log(title = "库区", businessType = BusinessType.DELETE)
    @DeleteMapping("/{ids}")
    public R<Void> remove(@PathVariable Long[] ids) {
        return toAjax(wmStorageAreaService.deleteByIds(ids));
    }

    /**
     * 根据仓库ID查询库区列表（下拉选择用）
     * <p>
     * 权限标识：{@code wm:storagearea:list}</p>
     *
     * @param warehouseId 仓库ID
     * @return 该仓库下启用的库区列表
     */
    @SaCheckPermission("wm:storagearea:list")
    @GetMapping("/list-by-warehouse/{warehouseId}")
    public R<List<WmStorageAreaVo>> listAreaByWarehouse(@PathVariable Long warehouseId) {
        WmStorageAreaBo bo = new WmStorageAreaBo();
        bo.setWarehouseId(warehouseId);
        bo.setEnableFlag(1L);
        return R.ok(wmStorageAreaService.selectList(bo));
    }
}
