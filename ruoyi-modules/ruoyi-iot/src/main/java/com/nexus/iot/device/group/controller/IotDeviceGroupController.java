package com.nexus.iot.device.group.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import lombok.RequiredArgsConstructor;
import com.nexus.common.core.domain.R;
import com.nexus.common.log.annotation.Log;
import com.nexus.common.log.enums.BusinessType;
import com.nexus.common.web.core.BaseController;
import com.nexus.iot.device.group.domain.bo.IotDeviceGroupAssignBo;
import com.nexus.iot.device.group.domain.bo.IotDeviceGroupBo;
import com.nexus.iot.device.group.domain.vo.IotDeviceGroupVo;
import com.nexus.iot.device.group.service.IIotDeviceGroupService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * IoT设备分组 Controller。
 * <p>
 * 平铺返回（前端组树），设备筛选统一走 {@code GET /iot/device/list?groupId=&includeSubGroup=}。
 *
 * @author nexus
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/iot/deviceGroup")
public class IotDeviceGroupController extends BaseController {

    private final IIotDeviceGroupService deviceGroupService;

    /**
     * 查询分组列表（平铺，含各分组已挂设备数）
     */
    @SaCheckPermission("iot:deviceGroup:list")
    @GetMapping("/list")
    public R<List<IotDeviceGroupVo>> list(IotDeviceGroupBo bo) {
        return R.ok(deviceGroupService.selectList(bo));
    }

    /**
     * 查询分组详情
     */
    @SaCheckPermission("iot:deviceGroup:query")
    @GetMapping("/{groupId}")
    public R<IotDeviceGroupVo> getInfo(@PathVariable Long groupId) {
        return R.ok(deviceGroupService.selectById(groupId));
    }

    /**
     * 新增分组
     */
    @SaCheckPermission("iot:deviceGroup:add")
    @Log(title = "IoT设备分组", businessType = BusinessType.INSERT)
    @PostMapping
    public R<Void> add(@Validated @RequestBody IotDeviceGroupBo bo) {
        return toAjax(deviceGroupService.insertByBo(bo));
    }

    /**
     * 修改分组（父分组变更时级联重写后代层级路径）
     */
    @SaCheckPermission("iot:deviceGroup:edit")
    @Log(title = "IoT设备分组", businessType = BusinessType.UPDATE)
    @PutMapping
    public R<Void> edit(@Validated @RequestBody IotDeviceGroupBo bo) {
        return toAjax(deviceGroupService.updateByBo(bo));
    }

    /**
     * 批量删除分组（存在子分组或已挂设备时拒绝）
     */
    @SaCheckPermission("iot:deviceGroup:remove")
    @Log(title = "IoT设备分组", businessType = BusinessType.DELETE)
    @DeleteMapping("/{ids}")
    public R<Void> remove(@PathVariable Long[] ids) {
        return toAjax(deviceGroupService.deleteByIds(ids));
    }

    /**
     * 批量把设备挂入/移出分组（remove=true 表示移出）
     */
    @SaCheckPermission("iot:deviceGroup:assign")
    @Log(title = "IoT设备挂分组", businessType = BusinessType.UPDATE)
    @PutMapping("/assign")
    public R<Void> assign(@Validated @RequestBody IotDeviceGroupAssignBo bo) {
        return toAjax(deviceGroupService.assignDevices(bo));
    }

    /**
     * 查询分组（含子树）下的设备ID集合
     */
    @SaCheckPermission("iot:deviceGroup:query")
    @GetMapping("/{groupId}/deviceIds")
    public R<List<Long>> deviceIds(@PathVariable Long groupId,
                                   @RequestParam(required = false, defaultValue = "false") Boolean includeSubGroup) {
        return R.ok(deviceGroupService.selectDeviceIds(groupId, includeSubGroup));
    }
}
