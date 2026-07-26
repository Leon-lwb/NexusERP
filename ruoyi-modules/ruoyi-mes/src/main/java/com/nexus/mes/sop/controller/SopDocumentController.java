package com.nexus.mes.sop.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import lombok.RequiredArgsConstructor;
import com.nexus.common.core.domain.R;
import com.nexus.common.log.annotation.Log;
import com.nexus.common.log.enums.BusinessType;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.common.satoken.utils.LoginHelper;
import com.nexus.common.sse.dto.SseMessageDto;
import com.nexus.common.sse.utils.SseMessageUtils;
import com.nexus.common.json.utils.JsonUtils;
import com.nexus.common.web.core.BaseController;
import com.nexus.mes.sop.domain.bo.SopDocumentBo;
import com.nexus.mes.sop.domain.bo.SopVersionBo;
import com.nexus.mes.sop.domain.vo.SopDocumentVo;
import com.nexus.mes.sop.service.ISopDocumentService;
import com.nexus.mes.sop.service.ISopProcessBindingService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * MES SOP文档 Controller
 * <p>提供 SOP文档 CRUD + 版本/审批/归档 状态机接口 + 工位推送契约端点。</p>
 *
 * @author Nexus
 */
@Slf4j
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/mes/sop/document")
public class SopDocumentController extends BaseController {

    private final ISopDocumentService sopDocumentService;
    private final ISopProcessBindingService sopProcessBindingService;

    /**
     * 分页查询SOP文档列表
     */
    @SaCheckPermission("mes:sop:document:list")
    @GetMapping("/list")
    public TableDataInfo<SopDocumentVo> list(SopDocumentBo bo, PageQuery pageQuery) {
        return sopDocumentService.selectPageList(bo, pageQuery);
    }

    /**
     * 根据ID获取SOP详情
     */
    @SaCheckPermission("mes:sop:document:query")
    @GetMapping("/{sopId}")
    public R<SopDocumentVo> getInfo(@PathVariable Long sopId) {
        return R.ok(sopDocumentService.selectById(sopId));
    }

    /**
     * 新增SOP(编码自动生成 + 首版本 PENDING)
     */
    @SaCheckPermission("mes:sop:document:add")
    @Log(title = "SOP新增", businessType = BusinessType.INSERT)
    @PostMapping
    public R<Void> add(@RequestBody SopDocumentBo bo) {
        return toAjax(sopDocumentService.insertByBo(bo));
    }

    /**
     * 修改SOP(仅 DRAFT/ARCHIVED 可编辑)
     */
    @SaCheckPermission("mes:sop:document:edit")
    @Log(title = "SOP修改", businessType = BusinessType.UPDATE)
    @PutMapping
    public R<Void> edit(@RequestBody SopDocumentBo bo) {
        return toAjax(sopDocumentService.updateByBo(bo));
    }

    /**
     * 批量删除SOP(孤儿守卫:PUBLISHED 不可删;存在绑定/查阅记录不可删)
     */
    @SaCheckPermission("mes:sop:document:remove")
    @Log(title = "SOP删除", businessType = BusinessType.DELETE)
    @DeleteMapping("/{ids}")
    public R<Void> remove(@PathVariable Long[] ids) {
        return toAjax(sopDocumentService.deleteByIds(ids));
    }

    /**
     * 新增版本(DRAFT 或 PUBLISHED 上修订) -> PENDING, sop_document.status DRAFT
     */
    @SaCheckPermission("mes:sop:document:add")
    @Log(title = "SOP新增版本", businessType = BusinessType.INSERT)
    @PostMapping("/version")
    public R<Void> addVersion(@RequestBody SopVersionBo bo) {
        return toAjax(sopDocumentService.addVersion(bo));
    }

    /**
     * 提交审批: DRAFT -> IN_REVIEW (selectFlowId 非空走 Flowable, 否则直接 IN_REVIEW)
     */
    @SaCheckPermission("mes:sop:document:submit")
    @Log(title = "SOP提交审批", businessType = BusinessType.UPDATE)
    @PostMapping("/submit/{sopId}")
    public R<Void> submit(@PathVariable Long sopId,
                          @RequestParam(required = false) String selectFlowId) {
        return toAjax(sopDocumentService.submitForApproval(sopId, selectFlowId));
    }

    /**
     * 兜底审批通过(管理员强制, R11): IN_REVIEW -> PUBLISHED
     */
    @SaCheckPermission("mes:sop:document:approve")
    @Log(title = "SOP审批通过", businessType = BusinessType.UPDATE)
    @PutMapping("/approve/{sopId}")
    public R<Void> approve(@PathVariable Long sopId,
                           @RequestParam(required = false) String remark) {
        return toAjax(sopDocumentService.approve(sopId, remark));
    }

    /**
     * 兜底驳回: IN_REVIEW -> DRAFT
     */
    @SaCheckPermission("mes:sop:document:reject")
    @Log(title = "SOP审批驳回", businessType = BusinessType.UPDATE)
    @PutMapping("/reject/{sopId}")
    public R<Void> reject(@PathVariable Long sopId,
                          @RequestParam(required = false) String reason) {
        return toAjax(sopDocumentService.reject(sopId, reason));
    }

    /**
     * 归档(废止): PUBLISHED -> ARCHIVED
     */
    @SaCheckPermission("mes:sop:document:edit")
    @Log(title = "SOP归档", businessType = BusinessType.UPDATE)
    @PutMapping("/archive/{sopId}")
    public R<Void> archive(@PathVariable Long sopId,
                           @RequestParam(required = false) String reason) {
        return toAjax(sopDocumentService.archive(sopId, reason));
    }

    /**
     * 契约: 按工序查SOP(工位推送用) -> 返回默认SOP(含文件签名URL)
     */
    @SaCheckPermission("mes:sop:document:list")
    @GetMapping("/by-process/{processId}")
    public R<SopDocumentVo> getByProcess(@PathVariable Long processId) {
        return R.ok(sopProcessBindingService.getSopForProcess(processId));
    }

    /**
     * 契约: 按流转卡工序记录查SOP(经 ProFlowCardStep.processId)
     */
    @SaCheckPermission("mes:sop:document:list")
    @GetMapping("/by-step/{stepRecordId}")
    public R<SopDocumentVo> getByStep(@PathVariable Long stepRecordId) {
        return R.ok(sopProcessBindingService.getSopForStep(stepRecordId));
    }

    /**
     * 契约: 按物料查全部工序SOP(工单开工预加载) -> Map<processId, SopDocumentVo>
     */
    @SaCheckPermission("mes:sop:document:list")
    @GetMapping("/by-material/{materialId}")
    public R<Map<Long, SopDocumentVo>> getByMaterial(@PathVariable Long materialId) {
        return R.ok(sopProcessBindingService.getSopForMaterial(materialId));
    }

    /**
     * 契约: 触发SSE推送当前工序SOP到工位(P0-3 startStep 调用)
     * <p>推送当前登录用户(工位操作员);无在线用户时仅返回SOP供主动拉取。</p>
     */
    @SaCheckPermission("mes:sop:document:list")
    @PostMapping("/push/{stepRecordId}")
    public R<SopDocumentVo> push(@PathVariable Long stepRecordId) {
        SopDocumentVo sop = sopProcessBindingService.getSopForStep(stepRecordId);
        if (sop == null) {
            return R.fail("该工序未绑定已发布SOP");
        }
        if (SseMessageUtils.isEnable()) {
            try {
                Map<String, Object> payload = new HashMap<>();
                payload.put("type", "SOP");
                payload.put("action", "PUSH");
                payload.put("data", sop);
                SseMessageDto dto = new SseMessageDto();
                dto.setUserIds(List.of(LoginHelper.getUserId()));
                dto.setMessage(JsonUtils.toJsonString(payload));
                SseMessageUtils.publishMessage(dto);
            } catch (Exception e) {
                log.warn("SOP SSE推送失败(不阻断,可主动拉取): stepRecordId={}", stepRecordId, e);
            }
        }
        return R.ok(sop);
    }
}
