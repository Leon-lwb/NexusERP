package com.nexus.iot.firmware.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import lombok.RequiredArgsConstructor;
import com.nexus.common.core.domain.R;
import com.nexus.common.log.annotation.Log;
import com.nexus.common.log.enums.BusinessType;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.common.web.core.BaseController;
import com.nexus.iot.firmware.domain.IotFirmware;
import com.nexus.iot.firmware.domain.bo.IotFirmwareBo;
import com.nexus.iot.firmware.service.IIotFirmwareService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * IoT固件登记 Controller（OTA MVP）。
 * <p>
 * 固件包本体不在此上传：复用 {@code ruoyi-common-oss} 的通用上传端点拿到 {@code ossId} 后，
 * 通过本控制器的 add/edit 登记 {@code fileOssId}/{@code fileName}/{@code fileSize}/{@code checksum}。
 * 发布/停用走独立状态机端点 {@code PUT /iot/firmware/{firmwareId}/status}。
 *
 * @author nexus
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/iot/firmware")
public class IotFirmwareController extends BaseController {

    private final IIotFirmwareService firmwareService;

    /**
     * 分页查询固件列表
     */
    @SaCheckPermission("iot:firmware:list")
    @GetMapping("/list")
    public TableDataInfo<IotFirmware> list(IotFirmwareBo bo, PageQuery pageQuery) {
        return firmwareService.selectPageList(bo, pageQuery);
    }

    /**
     * 查询固件列表（不分页，供下拉选择）
     */
    @SaCheckPermission("iot:firmware:list")
    @GetMapping("/optionList")
    public R<List<IotFirmware>> optionList(IotFirmwareBo bo) {
        return R.ok(firmwareService.selectList(bo));
    }

    /**
     * 查询固件详情
     */
    @SaCheckPermission("iot:firmware:query")
    @GetMapping("/{firmwareId}")
    public R<IotFirmware> getInfo(@PathVariable Long firmwareId) {
        return R.ok(firmwareService.selectById(firmwareId));
    }

    /**
     * 登记固件（服务端强制 DRAFT）
     */
    @SaCheckPermission("iot:firmware:add")
    @Log(title = "IoT固件-登记", businessType = BusinessType.INSERT)
    @PostMapping
    public R<Void> add(@Validated @RequestBody IotFirmwareBo bo) {
        return toAjax(firmwareService.insertByBo(bo));
    }

    /**
     * 修改固件（仅草稿可改）
     */
    @SaCheckPermission("iot:firmware:edit")
    @Log(title = "IoT固件-修改", businessType = BusinessType.UPDATE)
    @PutMapping
    public R<Void> edit(@Validated @RequestBody IotFirmwareBo bo) {
        return toAjax(firmwareService.updateByBo(bo));
    }

    /**
     * 批量删除固件（已发布固件拒绝删除）
     */
    @SaCheckPermission("iot:firmware:remove")
    @Log(title = "IoT固件-删除", businessType = BusinessType.DELETE)
    @DeleteMapping("/{ids}")
    public R<Void> remove(@PathVariable Long[] ids) {
        return toAjax(firmwareService.deleteByIds(ids));
    }

    /**
     * 发布/停用固件（状态机 + CAS）
     *
     * @param firmwareId 固件ID
     * @param status     目标状态: RELEASED（发布）/ DISABLED（停用）
     * @param remark     变更说明（可空）
     */
    @SaCheckPermission("iot:firmware:release")
    @Log(title = "IoT固件-发布停用", businessType = BusinessType.UPDATE)
    @PutMapping("/{firmwareId}/status")
    public R<IotFirmware> changeStatus(@PathVariable Long firmwareId,
                                       @RequestParam String status,
                                       @RequestParam(required = false) String remark) {
        return R.ok(firmwareService.changeStatus(firmwareId, status, remark));
    }
}
