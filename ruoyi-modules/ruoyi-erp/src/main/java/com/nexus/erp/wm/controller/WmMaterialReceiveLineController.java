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
import com.nexus.erp.wm.domain.bo.WmMaterialReceiveLineBo;
import com.nexus.erp.wm.domain.vo.WmMaterialReceiveLineVo;
import com.nexus.erp.wm.service.IWmMaterialReceiveLineService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 物料入库单行 Controller
 * <p>
 * 管理物料入库单的行数据，记录每一行物料的入库数量、批次等信息。
 *
 * @author nexus
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/erp/wm/receiveline")
public class WmMaterialReceiveLineController extends BaseController {

    /** 物料领用行业务服务，提供物料领用行相关能力 */
    private final IWmMaterialReceiveLineService wmMaterialReceiveLineService;

    /**
     * 分页查询物料入库单行列表
     * <p>
     * 权限标识：{@code wm:receive:list}</p>
     *
     * @param bo        查询参数
     * @param pageQuery 分页参数，包含页码、每页条数及排序信息
     * @return 物料入库单行分页列表
     */
    @SaCheckPermission("wm:receive:list")
    @GetMapping("/list")
    public TableDataInfo<WmMaterialReceiveLineVo> list(WmMaterialReceiveLineBo bo, PageQuery pageQuery) {
        return wmMaterialReceiveLineService.selectPageList(bo, pageQuery);
    }

    /**
     * 导出物料入库单行数据到 Excel
     * <p>权限标识：{@code wm:receive:export}</p>
     * <p>日志记录：title="物料入库单行"，businessType=EXPORT</p>
     *
     * @param bo        查询参数
     * @param response  HTTP 响应对象，用于输出 Excel 文件流
     */
    @SaCheckPermission("wm:receive:export")
    @Log(title = "物料入库单行", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(WmMaterialReceiveLineBo bo, HttpServletResponse response) {
        List<WmMaterialReceiveLineVo> list = wmMaterialReceiveLineService.selectList(bo);
        ExcelUtil.exportExcel(list, "物料入库单行数据", WmMaterialReceiveLineVo.class, response);
    }

    /**
     * 根据主键获取物料入库单行详细信息
     * <p>
     * 权限标识：{@code wm:receive:query}</p>
     *
     * @param id 入库单行 ID
     * @return 入库单行详细信息
     */
    @SaCheckPermission("wm:receive:query")
    @GetMapping("/{id}")
    public R<WmMaterialReceiveLineVo> getInfo(@PathVariable Long id) {
        return R.ok(wmMaterialReceiveLineService.selectById(id));
    }

    /**
     * 新增物料入库单行
     * <p>
     * 权限标识：{@code wm:receive:add}</p>
     * <p>日志记录：title="物料入库单行"，businessType=INSERT</p>
     *
     * @param bo 入库单行业务对象
     * @return 操作结果，成功返回 {@code R.success()}，失败返回 {@code R.fail()}
     */
    @SaCheckPermission("wm:receive:add")
    @Log(title = "物料入库单行", businessType = BusinessType.INSERT)
    @PostMapping
    public R<Void> add(@Validated @RequestBody WmMaterialReceiveLineBo bo) {
        return toAjax(wmMaterialReceiveLineService.insertByBo(bo));
    }

    /**
     * 修改物料入库单行
     * <p>
     * 权限标识：{@code wm:receive:edit}</p>
     * <p>日志记录：title="物料入库单行"，businessType=UPDATE</p>
     *
     * @param bo 入库单行业务对象，包含需要更新的字段信息
     * @return 操作结果，成功返回 {@code R.success()}，失败返回 {@code R.fail()}
     */
    @SaCheckPermission("wm:receive:edit")
    @Log(title = "物料入库单行", businessType = BusinessType.UPDATE)
    @PutMapping
    public R<Void> edit(@Validated @RequestBody WmMaterialReceiveLineBo bo) {
        return toAjax(wmMaterialReceiveLineService.updateByBo(bo));
    }

    /**
     * 批量删除物料入库单行
     * <p>
     * 权限标识：{@code wm:receive:remove}</p>
     * <p>日志记录：title="物料入库单行"，businessType=DELETE</p>
     *
     * @param ids 要删除的入库单行 ID 数组（支持批量删除）
     * @return 操作结果，成功返回 {@code R.success()}，失败返回 {@code R.fail()}
     */
    @SaCheckPermission("wm:receive:remove")
    @Log(title = "物料入库单行", businessType = BusinessType.DELETE)
    @DeleteMapping("/{ids}")
    public R<Void> remove(@PathVariable Long[] ids) {
        return toAjax(wmMaterialReceiveLineService.deleteByIds(ids));
    }
}
