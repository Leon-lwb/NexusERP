package org.dromara.erp.wm.controller;

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
import org.dromara.erp.wm.domain.bo.WmBarcodeConfigBo;
import org.dromara.erp.wm.domain.vo.WmBarcodeConfigVo;
import org.dromara.erp.wm.service.IWmBarcodeConfigService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 条码配置 Controller
 * <p>
 * 提供条码规则配置的 RESTful API 接口，条码生成逻辑从此读取基础规则数据。
 *
 * @author ruoyi
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/erp/wm/barcode/config")
public class WmBarcodeConfigController extends BaseController {

    private final IWmBarcodeConfigService wmBarcodeConfigService;

    /**
     * 分页查询条码配置列表
     *
     * @param bo        查询参数
     * @param pageQuery 分页参数
     * @return 条码配置分页列表
     */
    @SaCheckPermission("wm:barcodeConfig:list")
    @GetMapping("/list")
    public TableDataInfo<WmBarcodeConfigVo> list(WmBarcodeConfigBo bo, PageQuery pageQuery) {
        return wmBarcodeConfigService.selectPageList(bo, pageQuery);
    }

    /**
     * 导出条码配置数据到 Excel
     * <p>权限标识：{@code wm:barcodeConfig:export}</p>
     * <p>日志记录：title="条码配置"，businessType=EXPORT</p>
     *
     * @param bo        查询参数
     * @param response  HTTP 响应对象
     */
    @SaCheckPermission("wm:barcodeConfig:export")
    @Log(title = "条码配置", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(WmBarcodeConfigBo bo, HttpServletResponse response) {
        List<WmBarcodeConfigVo> list = wmBarcodeConfigService.selectList(bo);
        ExcelUtil.exportExcel(list, "条码配置数据", WmBarcodeConfigVo.class, response);
    }

    /**
     * 获取条码配置详细信息
     * <p>权限标识：{@code wm:barcodeConfig:query}</p>
     *
     * @param id 条码配置ID
     * @return 条码配置详细信息
     */
    @SaCheckPermission("wm:barcodeConfig:query")
    @GetMapping("/{id}")
    public R<WmBarcodeConfigVo> getInfo(@PathVariable Long id) {
        return R.ok(wmBarcodeConfigService.selectById(id));
    }

    /**
     * 新增条码配置
     * <p>权限标识：{@code wm:barcodeConfig:add}</p>
     * <p>日志记录：title="条码配置"，businessType=INSERT</p>
     *
     * @param bo 条码配置业务对象
     * @return 操作结果
     */
    @SaCheckPermission("wm:barcodeConfig:add")
    @Log(title = "条码配置", businessType = BusinessType.INSERT)
    @PostMapping
    public R<Void> add(@Validated @RequestBody WmBarcodeConfigBo bo) {
        return toAjax(wmBarcodeConfigService.insertByBo(bo));
    }

    /**
     * 修改条码配置
     * <p>权限标识：{@code wm:barcodeConfig:edit}</p>
     * <p>日志记录：title="条码配置"，businessType=UPDATE</p>
     *
     * @param bo 条码配置业务对象
     * @return 操作结果
     */
    @SaCheckPermission("wm:barcodeConfig:edit")
    @Log(title = "条码配置", businessType = BusinessType.UPDATE)
    @PutMapping
    public R<Void> edit(@Validated @RequestBody WmBarcodeConfigBo bo) {
        return toAjax(wmBarcodeConfigService.updateByBo(bo));
    }

    /**
     * 删除条码配置
     * <p>权限标识：{@code wm:barcodeConfig:remove}</p>
     * <p>日志记录：title="条码配置"，businessType=DELETE</p>
     *
     * @param ids 条码配置ID数组
     * @return 操作结果
     */
    @SaCheckPermission("wm:barcodeConfig:remove")
    @Log(title = "条码配置", businessType = BusinessType.DELETE)
    @DeleteMapping("/{ids}")
    public R<Void> remove(@PathVariable Long[] ids) {
        return toAjax(wmBarcodeConfigService.deleteByIds(ids));
    }
}
