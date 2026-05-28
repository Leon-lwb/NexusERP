package com.nexus.erp.prd.controller;

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
import com.nexus.erp.prd.domain.PrdWorkOrderResource;
import com.nexus.erp.prd.domain.bo.PrdWorkOrderResourceBo;
import com.nexus.erp.prd.domain.vo.PrdWorkOrderResourceVo;
import com.nexus.erp.prd.service.IPrdWorkOrderResourceService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 生产工单资源分配 Controller
 * <p>
 * 管理生产工单的设备、工装、人力等资源分配信息。
 *
 * @author ruoyi
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/erp/prd/workOrderResource")
public class PrdWorkOrderResourceController extends BaseController {

    private final IPrdWorkOrderResourceService prdWorkOrderResourceService;

    /**
     * 分页查询生产工单资源分配列表
     * <p>
     * 权限标识：{@code prd:workOrderResource:list}</p>
     *
     * @param bo        查询参数，包含工单编号、资源类型等筛选条件
     * @param pageQuery 分页参数，包含页码、每页条数及排序信息
     * @return 资源分配分页列表
     */
    @SaCheckPermission("prd:workOrderResource:list")
    @GetMapping("/list")
    public TableDataInfo<PrdWorkOrderResourceVo> list(PrdWorkOrderResourceBo bo, PageQuery pageQuery) {
        return prdWorkOrderResourceService.selectPageList(bo, pageQuery);
    }

    /**
     * 导出生产工单资源分配数据到 Excel
     * <p>权限标识：{@code prd:workOrderResource:export}</p>
     * <p>日志记录：title="生产工单资源分配"，businessType=EXPORT</p>
     *
     * @param bo        查询参数
     * @param response  HTTP 响应对象，用于输出 Excel 文件流
     */
    @SaCheckPermission("prd:workOrderResource:export")
    @Log(title = "生产工单资源分配", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(PrdWorkOrderResourceBo bo, HttpServletResponse response) {
        List<PrdWorkOrderResourceVo> list = prdWorkOrderResourceService.selectList(bo);
        ExcelUtil.exportExcel(list, "生产工单资源分配数据", PrdWorkOrderResourceVo.class, response);
    }

    /**
     * 根据主键获取资源分配详细信息
     * <p>
     * 权限标识：{@code prd:workOrderResource:query}</p>
     *
     * @param id 资源分配记录 ID
     * @return 资源分配详细信息
     */
    @SaCheckPermission("prd:workOrderResource:query")
    @GetMapping("/{id}")
    public R<PrdWorkOrderResourceVo> getInfo(@PathVariable Long id) {
        return R.ok(prdWorkOrderResourceService.selectById(id));
    }

    /**
     * 新增生产工单资源分配
     * <p>
     * 权限标识：{@code prd:workOrderResource:add}</p>
     * <p>日志记录：title="生产工单资源分配"，businessType=INSERT</p>
     *
     * @param bo 资源分配业务对象，包含资源类型、数量等信息
     * @return 操作结果，成功返回 {@code R.success()}，失败返回 {@code R.fail()}
     */
    @SaCheckPermission("prd:workOrderResource:add")
    @Log(title = "生产工单资源分配", businessType = BusinessType.INSERT)
    @PostMapping
    public R<Void> add(@Validated @RequestBody PrdWorkOrderResourceBo bo) {
        return toAjax(prdWorkOrderResourceService.insertByBo(bo));
    }

    /**
     * 修改生产工单资源分配
     * <p>
     * 权限标识：{@code prd:workOrderResource:edit}</p>
     * <p>日志记录：title="生产工单资源分配"，businessType=UPDATE</p>
     *
     * @param bo 资源分配业务对象，包含需要更新的字段信息
     * @return 操作结果，成功返回 {@code R.success()}，失败返回 {@code R.fail()}
     */
    @SaCheckPermission("prd:workOrderResource:edit")
    @Log(title = "生产工单资源分配", businessType = BusinessType.UPDATE)
    @PutMapping
    public R<Void> edit(@Validated @RequestBody PrdWorkOrderResourceBo bo) {
        return toAjax(prdWorkOrderResourceService.updateByBo(bo));
    }

    /**
     * 批量删除生产工单资源分配
     * <p>
     * 权限标识：{@code prd:workOrderResource:remove}</p>
     * <p>日志记录：title="生产工单资源分配"，businessType=DELETE</p>
     *
     * @param ids 要删除的资源分配记录 ID 数组（支持批量删除）
     * @return 操作结果，成功返回 {@code R.success()}，失败返回 {@code R.fail()}
     */
    @SaCheckPermission("prd:workOrderResource:remove")
    @Log(title = "生产工单资源分配", businessType = BusinessType.DELETE)
    @DeleteMapping("/{ids}")
    public R<Void> remove(@PathVariable Long[] ids) {
        return toAjax(prdWorkOrderResourceService.deleteByIds(ids));
    }

    /**
     * 根据工单 ID 查询资源分配列表
     * <p>
     * 权限标识：{@code prd:workOrderResource:query}</p>
     *
     * @param woId 生产工单 ID
     * @return 资源分配列表
     */
    @SaCheckPermission("prd:workOrderResource:query")
    @GetMapping("/wo/{woId}")
    public R<List<PrdWorkOrderResource>> getByWoId(@PathVariable Long woId) {
        return R.ok(prdWorkOrderResourceService.selectByWoId(woId));
    }

    /**
     * 根据工单 ID 和资源类型查询资源分配列表
     * <p>
     * 权限标识：{@code prd:workOrderResource:query}</p>
     *
     * @param woId         生产工单 ID
     * @param resourceType 资源类型
     * @return 资源分配列表
     */
    @SaCheckPermission("prd:workOrderResource:query")
    @GetMapping("/wo/{woId}/type/{resourceType}")
    public R<List<PrdWorkOrderResource>> getByWoIdAndType(@PathVariable Long woId,
                                                          @PathVariable String resourceType) {
        return R.ok(prdWorkOrderResourceService.selectByWoIdAndType(woId, resourceType));
    }

    /**
     * 批量保存工单资源分配
     * <p>权限标识：{@code prd:workOrderResource:add}</p>
     * <p>日志记录：title="生产工单资源分配"，businessType=INSERT</p>
     *
     * @param woId         生产工单 ID
     * @param resourceList 资源分配列表
     * @return 操作结果
     */
    @SaCheckPermission("prd:workOrderResource:add")
    @Log(title = "生产工单资源分配", businessType = BusinessType.INSERT)
    @PostMapping("/batch/{woId}")
    public R<Void> saveBatch(@PathVariable Long woId, @RequestBody List<PrdWorkOrderResource> resourceList) {
        return toAjax(prdWorkOrderResourceService.saveBatch(woId, resourceList));
    }
}
