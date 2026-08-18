package com.nexus.mes.flash.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.nexus.common.core.domain.R;
import com.nexus.common.log.annotation.Log;
import com.nexus.common.log.enums.BusinessType;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.common.web.core.BaseController;
import com.nexus.mes.flash.domain.bo.FirmwareBo;
import com.nexus.mes.flash.domain.vo.FirmwareVo;
import com.nexus.mes.flash.service.IFirmwareService;
import com.nexus.workflow.domain.WfCustomForm;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

/**
 * MES固件档案 Controller
 * <p>提供固件 CRUD + 上传 + 审批/归档/默认版本 状态机接口。</p>
 *
 * @author Nexus
 */
@Slf4j
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/mes/flash/firmware")
public class FirmwareController extends BaseController {

    private final IFirmwareService mesFirmwareService;

    /**
     * 分页查询固件档案列表
     */
    @SaCheckPermission("mes:flash:firmware:list")
    @GetMapping("/list")
    public TableDataInfo<FirmwareVo> list(FirmwareBo bo, PageQuery pageQuery) {
        return mesFirmwareService.selectPageList(bo, pageQuery);
    }

    /**
     * 根据ID获取固件详情
     */
    @SaCheckPermission("mes:flash:firmware:query")
    @GetMapping("/{firmwareId}")
    public R<FirmwareVo> getInfo(@PathVariable Long firmwareId) {
        return R.ok(mesFirmwareService.selectById(firmwareId));
    }

    /**
     * 新增固件(编码自动生成 + 状态 DRAFT)
     */
    @SaCheckPermission("mes:flash:firmware:add")
    @Log(title = "固件新增", businessType = BusinessType.INSERT)
    @PostMapping
    public R<Void> add(@Validated @RequestBody FirmwareBo bo) {
        return toAjax(mesFirmwareService.insertByBo(bo));
    }

    /**
     * 修改固件(仅 DRAFT/ARCHIVED 可编辑)
     */
    @SaCheckPermission("mes:flash:firmware:edit")
    @Log(title = "固件修改", businessType = BusinessType.UPDATE)
    @PutMapping
    public R<Void> edit(@Validated @RequestBody FirmwareBo bo) {
        return toAjax(mesFirmwareService.updateByBo(bo));
    }

    /**
     * 批量删除固件(仅 DRAFT 可删)
     */
    @SaCheckPermission("mes:flash:firmware:remove")
    @Log(title = "固件删除", businessType = BusinessType.DELETE)
    @DeleteMapping("/{ids}")
    public R<Void> remove(@PathVariable Long[] ids) {
        return toAjax(mesFirmwareService.deleteByIds(ids));
    }

    /**
     * 上传固件文件(MinIO, 返回 ossId/fileSize/md5 回填)
     */
    @SaCheckPermission("mes:flash:firmware:upload")
    @Log(title = "固件上传", businessType = BusinessType.UPDATE)
    @PostMapping("/upload")
    public R<Map<String, Object>> upload(@RequestParam("file") MultipartFile file) {
        return R.ok(mesFirmwareService.uploadFirmware(file));
    }

    /**
     * 提交审批: DRAFT -> IN_REVIEW (selectFlowId 非空走 Flowable, 否则仅改状态)
     */
    @SaCheckPermission("mes:flash:firmware:submit")
    @Log(title = "固件提交审批", businessType = BusinessType.UPDATE)
    @PostMapping("/submit/{firmwareId}")
    public R<Void> submit(@PathVariable Long firmwareId,
                          @RequestParam(required = false) String selectFlowId) {
        return toAjax(mesFirmwareService.submitForApproval(firmwareId, selectFlowId));
    }

    /**
     * 兜底审批通过(管理员强制): IN_REVIEW -> RELEASED
     */
    @SaCheckPermission("mes:flash:firmware:approve")
    @Log(title = "固件审批通过", businessType = BusinessType.UPDATE)
    @PutMapping("/approve/{firmwareId}")
    public R<Void> approve(@PathVariable Long firmwareId,
                           @RequestParam(required = false) String remark) {
        return toAjax(mesFirmwareService.approve(firmwareId, remark));
    }

    /**
     * 兜底驳回: IN_REVIEW -> DRAFT
     */
    @SaCheckPermission("mes:flash:firmware:reject")
    @Log(title = "固件审批驳回", businessType = BusinessType.UPDATE)
    @PutMapping("/reject/{firmwareId}")
    public R<Void> reject(@PathVariable Long firmwareId,
                          @RequestParam(required = false) String reason) {
        return toAjax(mesFirmwareService.reject(firmwareId, reason));
    }

    /**
     * 归档: RELEASED -> ARCHIVED (被绑定/有刷写记录时禁止)
     */
    @SaCheckPermission("mes:flash:firmware:archive")
    @Log(title = "固件归档", businessType = BusinessType.UPDATE)
    @PutMapping("/archive/{firmwareId}")
    public R<Void> archive(@PathVariable Long firmwareId,
                           @RequestParam(required = false) String reason) {
        return toAjax(mesFirmwareService.archive(firmwareId, reason));
    }

    /**
     * 设为物料默认版本(仅 RELEASED, 物料内唯一)
     */
    @SaCheckPermission("mes:flash:firmware:default")
    @Log(title = "固件设为默认", businessType = BusinessType.UPDATE)
    @PutMapping("/default/{firmwareId}")
    public R<Void> setDefault(@PathVariable Long firmwareId) {
        return toAjax(mesFirmwareService.setDefault(firmwareId));
    }

    /**
     * 查询可发起审批流程列表(mesFirmwareService 已注册的自定义表单, 提交审批弹窗用)
     */
    @SaCheckPermission("mes:flash:firmware:list")
    @GetMapping("/flows")
    public R<List<WfCustomForm>> flows() {
        return R.ok(mesFirmwareService.getFlows());
    }

    /**
     * 查询流程实例ID(审批表单页"查看流程"跳转用)
     * 使用 R.ok(String msg, T data) 重载, 避免 R.ok(String) 把 ID 误塞进 msg
     */
    @SaCheckPermission("mes:flash:firmware:query")
    @GetMapping("/{firmwareId}/proc")
    public R<String> getProcInsId(@PathVariable Long firmwareId) {
        return R.ok("查询成功", mesFirmwareService.getProcInsId(String.valueOf(firmwareId)));
    }
}