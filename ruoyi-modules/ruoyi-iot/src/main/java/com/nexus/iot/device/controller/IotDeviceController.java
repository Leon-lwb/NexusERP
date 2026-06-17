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
import com.nexus.iot.device.domain.bo.IotDeviceBo;
import com.nexus.iot.device.domain.vo.IotDeviceVo;
import com.nexus.iot.device.service.IIotDeviceService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * IoT设备 Controller
 * <p>
 * 管理设备实例，含在线状态、密钥、关联产品等。新增时自动生成 device_secret。
 *
 * @author nexus
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/iot/device")
public class IotDeviceController extends BaseController {

    private final IIotDeviceService iotDeviceService;

    /**
     * 分页查询设备列表
     */
    @SaCheckPermission("iot:device:list")
    @GetMapping("/list")
    public TableDataInfo<IotDeviceVo> list(IotDeviceBo bo, PageQuery pageQuery) {
        return iotDeviceService.selectPageList(bo, pageQuery);
    }

    /**
     * 导出设备数据到 Excel
     */
    @SaCheckPermission("iot:device:export")
    @Log(title = "IoT设备", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(IotDeviceBo bo, HttpServletResponse response) {
        List<IotDeviceVo> list = iotDeviceService.selectList(bo);
        ExcelUtil.exportExcel(list, "IoT设备", IotDeviceVo.class, response);
    }

    /**
     * 根据主键获取设备详情
     */
    @SaCheckPermission("iot:device:query")
    @GetMapping("/{id}")
    public R<IotDeviceVo> getInfo(@PathVariable Long id) {
        return R.ok(iotDeviceService.selectById(id));
    }

    /**
     * 新增设备（自动生成 device_secret，回填 product_name）
     */
    @SaCheckPermission("iot:device:add")
    @Log(title = "IoT设备", businessType = BusinessType.INSERT)
    @PostMapping
    public R<Void> add(@Validated @RequestBody IotDeviceBo bo) {
        return toAjax(iotDeviceService.insertByBo(bo));
    }

    /**
     * 修改设备
     */
    @SaCheckPermission("iot:device:edit")
    @Log(title = "IoT设备", businessType = BusinessType.UPDATE)
    @PutMapping
    public R<Void> edit(@Validated @RequestBody IotDeviceBo bo) {
        return toAjax(iotDeviceService.updateByBo(bo));
    }

    /**
     * 批量删除设备
     */
    @SaCheckPermission("iot:device:remove")
    @Log(title = "IoT设备", businessType = BusinessType.DELETE)
    @DeleteMapping("/{ids}")
    public R<Void> remove(@PathVariable Long[] ids) {
        return toAjax(iotDeviceService.deleteByIds(ids));
    }
}
