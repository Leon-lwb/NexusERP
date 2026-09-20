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
import com.nexus.erp.wm.domain.bo.WmMaterialStockTxBo;
import com.nexus.erp.wm.domain.vo.WmMaterialStockTxVo;
import com.nexus.erp.wm.service.IWmMaterialStockTxService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 库存事务 Controller
 * <p>
 * 提供库存事务记录的 RESTful API 接口，用于查询物料库存变动历史。
 *
 * @author nexus
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/erp/wm/stocktx")
public class WmMaterialStockTxController extends BaseController {

    /** 物料库存流水业务服务，提供物料库存流水相关能力 */
    private final IWmMaterialStockTxService wmMaterialStockTxService;

    /**
     * 分页查询库存事务列表
     * <p>
     * 权限标识：{@code wm:stocktx:list}</p>
     *
     * @param bo        查询参数，包含物料编码、事务类型、日期范围等筛选条件
     * @param pageQuery 分页参数，包含页码、每页条数及排序信息
     * @return 库存事务分页列表
     */
    @SaCheckPermission("wm:stocktx:list")
    @GetMapping("/list")
    public TableDataInfo<WmMaterialStockTxVo> list(WmMaterialStockTxBo bo, PageQuery pageQuery) {
        return wmMaterialStockTxService.selectPageList(bo, pageQuery);
    }

    /**
     * 导出库存事务数据到 Excel
     * <p>权限标识：{@code wm:stocktx:list}</p>
     * <p>日志记录：title="库存事务"，businessType=EXPORT</p>
     *
     * @param bo        查询参数
     * @param response  HTTP 响应对象，用于输出 Excel 文件流
     */
    @SaCheckPermission("wm:stocktx:list")
    @Log(title = "库存事务", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(WmMaterialStockTxBo bo, HttpServletResponse response) {
        List<WmMaterialStockTxVo> list = wmMaterialStockTxService.selectList(bo);
        ExcelUtil.exportExcel(list, "库存事务数据", WmMaterialStockTxVo.class, response);
    }

    /**
     * 根据主键获取库存事务详细信息
     * <p>
     * 权限标识：{@code wm:stocktx:list}</p>
     *
     * @param txId 库存事务 ID
     * @return 库存事务详细信息
     */
    @SaCheckPermission("wm:stocktx:list")
    @GetMapping("/{txId}")
    public R<WmMaterialStockTxVo> getInfo(@PathVariable Long txId) {
        return R.ok(wmMaterialStockTxService.selectById(txId));
    }

    /**
     * 新增库存事务
     * <p>
     * 权限标识：{@code wm:stocktx:list}</p>
     * <p>日志记录：title="库存事务"，businessType=INSERT</p>
     *
     * @param bo 库存事务业务对象
     * @return 操作结果，成功返回 {@code R.success()}，失败返回 {@code R.fail()}
     */
    @SaCheckPermission("wm:stocktx:list")
    @Log(title = "库存事务", businessType = BusinessType.INSERT)
    @PostMapping
    public R<Void> add(@Validated @RequestBody WmMaterialStockTxBo bo) {
        return toAjax(wmMaterialStockTxService.insertByBo(bo));
    }

    /**
     * 修改库存事务
     * <p>
     * 权限标识：{@code wm:stocktx:list}</p>
     * <p>日志记录：title="库存事务"，businessType=UPDATE</p>
     *
     * @param bo 库存事务业务对象，包含需要更新的字段信息
     * @return 操作结果，成功返回 {@code R.success()}，失败返回 {@code R.fail()}
     */
    @SaCheckPermission("wm:stocktx:list")
    @Log(title = "库存事务", businessType = BusinessType.UPDATE)
    @PutMapping
    public R<Void> edit(@Validated @RequestBody WmMaterialStockTxBo bo) {
        return toAjax(wmMaterialStockTxService.updateByBo(bo));
    }

    /**
     * 批量删除库存事务
     * <p>
     * 权限标识：{@code wm:stocktx:list}</p>
     * <p>日志记录：title="库存事务"，businessType=DELETE</p>
     *
     * @param txIds 要删除的库存事务 ID 数组（支持批量删除）
     * @return 操作结果，成功返回 {@code R.success()}，失败返回 {@code R.fail()}
     */
    @SaCheckPermission("wm:stocktx:list")
    @Log(title = "库存事务", businessType = BusinessType.DELETE)
    @DeleteMapping("/{txIds}")
    public R<Void> remove(@PathVariable Long[] txIds) {
        return toAjax(wmMaterialStockTxService.deleteByIds(txIds));
    }
}
