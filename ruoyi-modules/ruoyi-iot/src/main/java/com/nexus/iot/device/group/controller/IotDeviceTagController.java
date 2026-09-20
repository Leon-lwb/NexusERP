package com.nexus.iot.device.group.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import lombok.RequiredArgsConstructor;
import com.nexus.common.core.domain.R;
import com.nexus.common.log.annotation.Log;
import com.nexus.common.log.enums.BusinessType;
import com.nexus.common.web.core.BaseController;
import com.nexus.iot.device.group.domain.bo.IotDeviceTagBindBo;
import com.nexus.iot.device.group.domain.bo.IotDeviceTagBo;
import com.nexus.iot.device.group.domain.vo.IotDeviceTagVo;
import com.nexus.iot.device.group.service.IIotDeviceTagService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * IoT设备标签 Controller。
 *
 * @author nexus
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/iot/deviceTag")
public class IotDeviceTagController extends BaseController {

    private final IIotDeviceTagService deviceTagService;

    /**
     * 查询标签列表（含已绑定设备数）
     */
    @SaCheckPermission("iot:deviceTag:list")
    @GetMapping("/list")
    public R<List<IotDeviceTagVo>> list(IotDeviceTagBo bo) {
        return R.ok(deviceTagService.selectList(bo));
    }

    /**
     * 标签下拉选项（等价于列表，语义化端点便于前端下拉复用）
     */
    @SaCheckPermission("iot:deviceTag:query")
    @GetMapping("/optionList")
    public R<List<IotDeviceTagVo>> optionList(IotDeviceTagBo bo) {
        return R.ok(deviceTagService.selectList(bo));
    }

    /**
     * 查询标签详情
     */
    @SaCheckPermission("iot:deviceTag:query")
    @GetMapping("/{tagId}")
    public R<IotDeviceTagVo> getInfo(@PathVariable Long tagId) {
        return R.ok(deviceTagService.selectById(tagId));
    }

    /**
     * 查询某设备已绑定的标签ID集合
     */
    @SaCheckPermission("iot:deviceTag:query")
    @GetMapping("/device/{deviceId}")
    public R<List<Long>> tagIdsByDevice(@PathVariable Long deviceId) {
        return R.ok(deviceTagService.selectTagIdsByDevice(deviceId));
    }

    /**
     * 新增标签
     */
    @SaCheckPermission("iot:deviceTag:add")
    @Log(title = "IoT设备标签", businessType = BusinessType.INSERT)
    @PostMapping
    public R<Void> add(@Validated @RequestBody IotDeviceTagBo bo) {
        return toAjax(deviceTagService.insertByBo(bo));
    }

    /**
     * 修改标签
     */
    @SaCheckPermission("iot:deviceTag:edit")
    @Log(title = "IoT设备标签", businessType = BusinessType.UPDATE)
    @PutMapping
    public R<Void> edit(@Validated @RequestBody IotDeviceTagBo bo) {
        return toAjax(deviceTagService.updateByBo(bo));
    }

    /**
     * 批量删除标签（级联清理设备-标签关系）
     */
    @SaCheckPermission("iot:deviceTag:remove")
    @Log(title = "IoT设备标签", businessType = BusinessType.DELETE)
    @DeleteMapping("/{ids}")
    public R<Void> remove(@PathVariable Long[] ids) {
        return toAjax(deviceTagService.deleteByIds(ids));
    }

    /**
     * 批量绑定/解绑标签（remove=true 表示解绑）
     */
    @SaCheckPermission("iot:deviceTag:bind")
    @Log(title = "IoT设备打标签", businessType = BusinessType.UPDATE)
    @PutMapping("/bind")
    public R<Void> bind(@Validated @RequestBody IotDeviceTagBindBo bo) {
        return toAjax(deviceTagService.bindTags(bo));
    }
}
