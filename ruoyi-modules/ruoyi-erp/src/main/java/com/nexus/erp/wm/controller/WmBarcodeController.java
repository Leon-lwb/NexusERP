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
import com.nexus.erp.wm.domain.bo.WmBarcodeBo;
import com.nexus.erp.wm.domain.vo.WmBarcodeVo;
import com.nexus.erp.wm.service.IWmBarcodeService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.Date;
import java.util.List;

/**
 * 条码实例 Controller
 * <p>
 * 提供系统生成的条码实例的维护以及条码生成接口。
 *
 * @author nexus
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/erp/wm/barcode")
public class WmBarcodeController extends BaseController {

    private final IWmBarcodeService wmBarcodeService;

    /**
     * 分页查询条码实例列表
     *
     * @param bo        查询参数
     * @param pageQuery 分页参数
     * @return 条码实例分页列表
     */
    @SaCheckPermission("wm:barcode:list")
    @GetMapping("/list")
    public TableDataInfo<WmBarcodeVo> list(WmBarcodeBo bo, PageQuery pageQuery) {
        return wmBarcodeService.selectPageList(bo, pageQuery);
    }

    /**
     * 导出条码实例数据到 Excel
     * <p>权限标识：{@code wm:barcode:export}</p>
     * <p>日志记录：title="条码实例"，businessType=EXPORT</p>
     *
     * @param bo        查询参数
     * @param response  HTTP 响应对象
     */
    @SaCheckPermission("wm:barcode:export")
    @Log(title = "条码实例", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(WmBarcodeBo bo, HttpServletResponse response) {
        List<WmBarcodeVo> list = wmBarcodeService.selectList(bo);
        ExcelUtil.exportExcel(list, "条码实例数据", WmBarcodeVo.class, response);
    }

    /**
     * 获取条码实例详细信息
     * <p>权限标识：{@code wm:barcode:query}</p>
     *
     * @param id 条码实例ID
     * @return 条码实例详细信息
     */
    @SaCheckPermission("wm:barcode:query")
    @GetMapping("/{id}")
    public R<WmBarcodeVo> getInfo(@PathVariable Long id) {
        return R.ok(wmBarcodeService.selectById(id));
    }

    /**
     * 新增条码实例
     * <p>权限标识：{@code wm:barcode:add}</p>
     * <p>日志记录：title="条码实例"，businessType=INSERT</p>
     *
     * @param bo 条码实例业务对象
     * @return 操作结果
     */
    @SaCheckPermission("wm:barcode:add")
    @Log(title = "条码实例", businessType = BusinessType.INSERT)
    @PostMapping
    public R<Void> add(@Validated @RequestBody WmBarcodeBo bo) {
        return toAjax(wmBarcodeService.insertByBo(bo));
    }

    /**
     * 修改条码实例
     * <p>权限标识：{@code wm:barcode:edit}</p>
     * <p>日志记录：title="条码实例"，businessType=UPDATE</p>
     *
     * @param bo 条码实例业务对象
     * @return 操作结果
     */
    @SaCheckPermission("wm:barcode:edit")
    @Log(title = "条码实例", businessType = BusinessType.UPDATE)
    @PutMapping
    public R<Void> edit(@Validated @RequestBody WmBarcodeBo bo) {
        return toAjax(wmBarcodeService.updateByBo(bo));
    }

    /**
     * 删除条码实例
     * <p>权限标识：{@code wm:barcode:remove}</p>
     * <p>日志记录：title="条码实例"，businessType=DELETE</p>
     *
     * @param ids 条码实例ID数组
     * @return 操作结果
     */
    @SaCheckPermission("wm:barcode:remove")
    @Log(title = "条码实例", businessType = BusinessType.DELETE)
    @DeleteMapping("/{ids}")
    public R<Void> remove(@PathVariable Long[] ids) {
        return toAjax(wmBarcodeService.deleteByIds(ids));
    }

    /**
     * 轻量生成条码接口
     * <p>根据配置ID生成条码，后续可扩展为按业务单据/物料类别自动选择配置。</p>
     * <p>权限标识：{@code wm:barcode:add}</p>
     *
     * @param configId    条码配置ID
     * @param bizType     业务类型（可选）
     * @param bizId       业务单据ID（可选）
     * @param bizLineId   业务单据行ID（可选）
     * @param materialId  物料ID（可选）
     * @param batchId     批次ID（可选）
     * @param stockId     库存ID（可选）
     * @return 生成的条码字符串
     */
    @SaCheckPermission("wm:barcode:add")
    @PostMapping("/generate")
    public R<String> generate(@RequestParam Long configId,
                              @RequestParam(required = false) String bizType,
                              @RequestParam(required = false) Long bizId,
                              @RequestParam(required = false) Long bizLineId,
                              @RequestParam(required = false) Long materialId,
                              @RequestParam(required = false) Long batchId,
                              @RequestParam(required = false) Long stockId) {
        return R.ok(wmBarcodeService.generateAndSave(configId, bizType, bizId, bizLineId, materialId, batchId, stockId, new Date()));
    }

    /**
     * 按配置编码生成条码
     * <p>根据规则编码生成条码，适合前端直接按规则编码发起调用。</p>
     * <p>权限标识：{@code wm:barcode:add}</p>
     *
     * @param configCode  条码配置编码
     * @param bizType     业务类型（可选）
     * @param bizId       业务单据ID（可选）
     * @param bizLineId   业务单据行ID（可选）
     * @param materialId  物料ID（可选）
     * @param batchId     批次ID（可选）
     * @param stockId     库存ID（可选）
     * @return 生成的条码字符串
     */
    @SaCheckPermission("wm:barcode:add")
    @PostMapping("/generateByCode")
    public R<String> generateByCode(@RequestParam String configCode,
                                    @RequestParam(required = false) String bizType,
                                    @RequestParam(required = false) Long bizId,
                                    @RequestParam(required = false) Long bizLineId,
                                    @RequestParam(required = false) Long materialId,
                                    @RequestParam(required = false) Long batchId,
                                    @RequestParam(required = false) Long stockId) {
        return R.ok(wmBarcodeService.generateAndSaveByCode(configCode, bizType, bizId, bizLineId, materialId, batchId, stockId, new Date()));
    }
}
