package com.nexus.iot.device.controller;

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
import com.nexus.iot.device.domain.bo.IotProductBo;
import com.nexus.iot.device.domain.vo.IotProductVo;
import com.nexus.iot.device.service.IIotProductService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * IoT产品 Controller
 * <p>
 * 管理设备模板（产品），含编码、名称、协议类型等。
 *
 * @author nexus
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/iot/product")
public class IotProductController extends BaseController {

    private final IIotProductService iotProductService;

    /**
     * 分页查询产品列表
     */
    @SaCheckPermission("iot:product:list")
    @GetMapping("/list")
    public TableDataInfo<IotProductVo> list(IotProductBo bo, PageQuery pageQuery) {
        return iotProductService.selectPageList(bo, pageQuery);
    }

    /**
     * 导出产品数据到 Excel
     */
    @SaCheckPermission("iot:product:export")
    @Log(title = "IoT产品", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(IotProductBo bo, HttpServletResponse response) {
        List<IotProductVo> list = iotProductService.selectList(bo);
        ExcelUtil.exportExcel(list, "IoT产品", IotProductVo.class, response);
    }

    /**
     * 根据主键获取产品详情
     */
    @SaCheckPermission("iot:product:query")
    @GetMapping("/{id}")
    public R<IotProductVo> getInfo(@PathVariable Long id) {
        return R.ok(iotProductService.selectById(id));
    }

    /**
     * 新增产品
     */
    @SaCheckPermission("iot:product:add")
    @Log(title = "IoT产品", businessType = BusinessType.INSERT)
    @PostMapping
    public R<Void> add(@Validated @RequestBody IotProductBo bo) {
        return toAjax(iotProductService.insertByBo(bo));
    }

    /**
     * 修改产品
     */
    @SaCheckPermission("iot:product:edit")
    @Log(title = "IoT产品", businessType = BusinessType.UPDATE)
    @PutMapping
    public R<Void> edit(@Validated @RequestBody IotProductBo bo) {
        return toAjax(iotProductService.updateByBo(bo));
    }

    /**
     * 批量删除产品
     */
    @SaCheckPermission("iot:product:remove")
    @Log(title = "IoT产品", businessType = BusinessType.DELETE)
    @DeleteMapping("/{ids}")
    public R<Void> remove(@PathVariable Long[] ids) {
        return toAjax(iotProductService.deleteByIds(ids));
    }
}
