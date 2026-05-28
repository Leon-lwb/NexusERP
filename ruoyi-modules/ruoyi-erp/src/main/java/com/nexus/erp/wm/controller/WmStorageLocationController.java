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
import com.nexus.erp.wm.domain.bo.WmStorageLocationBo;
import com.nexus.erp.wm.domain.vo.WmStorageLocationVo;
import com.nexus.erp.wm.service.IWmStorageLocationService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 库位 Controller
 * <p>
 * 管理仓库库位的详细信息，包括库位编码、所属库区、容量等。
 *
 * @author ruoyi
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/erp/wm/storagelocation")
public class WmStorageLocationController extends BaseController {

    private final IWmStorageLocationService wmStorageLocationService;

    /**
     * 分页查询库位列表
     * <p>
     * 权限标识：{@code wm:storagelocation:list}</p>
     *
     * @param bo        查询参数，包含库位编码、名称、所属库区等筛选条件
     * @param pageQuery 分页参数，包含页码、每页条数及排序信息
     * @return 库位分页列表
     */
    @SaCheckPermission("wm:storagelocation:list")
    @GetMapping("/list")
    public TableDataInfo<WmStorageLocationVo> list(WmStorageLocationBo bo, PageQuery pageQuery) {
        return wmStorageLocationService.selectPageList(bo, pageQuery);
    }

    /**
     * 导出库位数据到 Excel
     * <p>权限标识：{@code wm:storagelocation:export}</p>
     * <p>日志记录：title="库位"，businessType=EXPORT</p>
     *
     * @param bo        查询参数
     * @param response  HTTP 响应对象，用于输出 Excel 文件流
     */
    @SaCheckPermission("wm:storagelocation:export")
    @Log(title = "库位", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(WmStorageLocationBo bo, HttpServletResponse response) {
        List<WmStorageLocationVo> list = wmStorageLocationService.selectList(bo);
        ExcelUtil.exportExcel(list, "库位数据", WmStorageLocationVo.class, response);
    }

    /**
     * 根据主键获取库位详细信息
     * <p>
     * 权限标识：{@code wm:storagelocation:query}</p>
     *
     * @param id 库位 ID
     * @return 库位详细信息
     */
    @SaCheckPermission("wm:storagelocation:query")
    @GetMapping("/{id}")
    public R<WmStorageLocationVo> getInfo(@PathVariable Long id) {
        return R.ok(wmStorageLocationService.selectById(id));
    }

    /**
     * 新增库位
     * <p>
     * 权限标识：{@code wm:storagelocation:add}</p>
     * <p>日志记录：title="库位"，businessType=INSERT</p>
     *
     * @param bo 库位业务对象，包含库位编码、名称、所属库区等信息
     * @return 操作结果，成功返回 {@code R.success()}，失败返回 {@code R.fail()}
     */
    @SaCheckPermission("wm:storagelocation:add")
    @Log(title = "库位", businessType = BusinessType.INSERT)
    @PostMapping
    public R<Void> add(@Validated @RequestBody WmStorageLocationBo bo) {
        return toAjax(wmStorageLocationService.insertByBo(bo));
    }

    /**
     * 修改库位
     * <p>
     * 权限标识：{@code wm:storagelocation:edit}</p>
     * <p>日志记录：title="库位"，businessType=UPDATE</p>
     *
     * @param bo 库位业务对象，包含需要更新的字段信息
     * @return 操作结果，成功返回 {@code R.success()}，失败返回 {@code R.fail()}
     */
    @SaCheckPermission("wm:storagelocation:edit")
    @Log(title = "库位", businessType = BusinessType.UPDATE)
    @PutMapping
    public R<Void> edit(@Validated @RequestBody WmStorageLocationBo bo) {
        return toAjax(wmStorageLocationService.updateByBo(bo));
    }

    /**
     * 批量删除库位
     * <p>
     * 权限标识：{@code wm:storagelocation:remove}</p>
     * <p>日志记录：title="库位"，businessType=DELETE</p>
     *
     * @param ids 要删除的库位 ID 数组（支持批量删除）
     * @return 操作结果，成功返回 {@code R.success()}，失败返回 {@code R.fail()}
     */
    @SaCheckPermission("wm:storagelocation:remove")
    @Log(title = "库位", businessType = BusinessType.DELETE)
    @DeleteMapping("/{ids}")
    public R<Void> remove(@PathVariable Long[] ids) {
        return toAjax(wmStorageLocationService.deleteByIds(ids));
    }
}
