package com.nexus.mes.sop.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.nexus.common.core.domain.R;
import com.nexus.common.core.utils.StringUtils;
import com.nexus.common.flowable.core.domain.ActStatus;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.common.satoken.utils.LoginHelper;
import com.nexus.mes.sop.domain.SopDocument;
import com.nexus.mes.sop.domain.SopProcessBinding;
import com.nexus.mes.sop.domain.SopVersion;
import com.nexus.mes.sop.domain.SopViewLog;
import com.nexus.mes.sop.domain.bo.SopDocumentBo;
import com.nexus.mes.sop.domain.bo.SopVersionBo;
import com.nexus.mes.sop.domain.vo.SopDocumentVo;
import com.nexus.mes.sop.domain.vo.SopVersionVo;
import com.nexus.mes.sop.mapper.SopDocumentMapper;
import com.nexus.mes.sop.mapper.SopProcessBindingMapper;
import com.nexus.mes.sop.mapper.SopVersionMapper;
import com.nexus.mes.sop.mapper.SopViewLogMapper;
import com.nexus.mes.sop.service.ISopDocumentService;
import com.nexus.system.strategy.AutoCodeUtil;
import com.nexus.workflow.domain.WfMyBusiness;
import com.nexus.workflow.service.IWfProcessService;
import com.nexus.workflow.service.WfCallBackServiceI;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * MES SOP文档 Service 业务层实现
 * <p>
 * 状态机: DRAFT -> IN_REVIEW -> PUBLISHED -> ARCHIVED
 * 版本机: sop_version PENDING -> CURRENT -> HISTORICAL (doPublish 时旧 CURRENT -> HISTORICAL)
 * 审批流: 实现 WfCallBackServiceI (Flowable afterFlowHandle 驱动状态机) + 管理员兜底 approve/reject (R11)
 * 编码生成: autoCodeUtil.genSerialCode("MES_SOP_CODE", null) -> SOP+yyyyMMdd+3位日循环
 * </p>
 *
 * @author Nexus
 */
@Slf4j
@RequiredArgsConstructor
@DS("mes")
@Service("sopDocumentService")
public class SopDocumentServiceImpl implements ISopDocumentService, WfCallBackServiceI {

    private final SopDocumentMapper baseMapper;
    private final SopVersionMapper versionMapper;
    private final SopProcessBindingMapper bindingMapper;
    private final SopViewLogMapper viewLogMapper;
    private final AutoCodeUtil autoCodeUtil;
    private final IWfProcessService processService;

    @Override
    public TableDataInfo<SopDocumentVo> selectPageList(SopDocumentBo bo, PageQuery pageQuery) {
        LambdaQueryWrapper<SopDocument> lqw = buildQueryWrapper(bo);
        Page<SopDocumentVo> page = baseMapper.selectVoPage(pageQuery.build(), lqw);
        return TableDataInfo.build(page);
    }

    @Override
    public SopDocumentVo selectById(Long sopId) {
        SopDocumentVo vo = baseMapper.selectVoById(sopId);
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean insertByBo(SopDocumentBo bo) {
        if (StringUtils.isBlank(bo.getSopCode())) {
            bo.setSopCode(generateSopCode());
        }
        Long existCount = baseMapper.selectCount(Wrappers.<SopDocument>lambdaQuery()
            .eq(SopDocument::getSopCode, bo.getSopCode()));
        if (existCount != null && existCount > 0) {
            throw new RuntimeException("SOP编码已存在: " + bo.getSopCode());
        }
        if (StringUtils.isBlank(bo.getFileOssId())) {
            throw new RuntimeException("首版本文件不能为空");
        }
        // 1) 建 SOP 主档 (DRAFT)
        SopDocument doc = new SopDocument();
        doc.setSopCode(bo.getSopCode());
        doc.setSopName(bo.getSopName());
        doc.setSopType(bo.getSopType());
        doc.setStatus(STATUS_DRAFT);
        doc.setFileType(bo.getFileType());
        doc.setDescription(bo.getDescription());
        doc.setRemark(bo.getRemark());
        baseMapper.insert(doc);
        // 2) 建首版本 v1.0 (PENDING, 待发布)
        SopVersion v = new SopVersion();
        v.setSopId(doc.getSopId());
        v.setVersionNo("1.0");
        v.setFileOssId(bo.getFileOssId());
        v.setFileType(bo.getFileType());
        v.setChangeLog(bo.getDescription());
        v.setStatus(VERSION_PENDING);
        versionMapper.insert(v);
        log.info("新增SOP成功: code={}, type={}, 首版本 1.0 PENDING", bo.getSopCode(), bo.getSopType());
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean updateByBo(SopDocumentBo bo) {
        SopDocument doc = baseMapper.selectById(bo.getSopId());
        if (doc == null) {
            throw new RuntimeException("SOP不存在: " + bo.getSopId());
        }
        // 仅 DRAFT/ARCHIVED 可编辑基础信息;PUBLISHED 需先新增版本修订
        if (!STATUS_DRAFT.equals(doc.getStatus()) && !STATUS_ARCHIVED.equals(doc.getStatus())) {
            throw new RuntimeException("仅草稿/归档状态可编辑,当前: " + doc.getStatus() + "(已发布请新增版本修订)");
        }
        doc.setSopName(bo.getSopName());
        doc.setSopType(bo.getSopType());
        doc.setDescription(bo.getDescription());
        doc.setRemark(bo.getRemark());
        baseMapper.updateById(doc);
        log.info("修改SOP成功: sopId={}", bo.getSopId());
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean deleteByIds(Long[] ids) {
        if (ids == null || ids.length == 0) {
            return false;
        }
        List<Long> idList = Arrays.asList(ids);
        // 孤儿守卫1: PUBLISHED 不可删(先归档)
        Long publishedCount = baseMapper.selectCount(Wrappers.<SopDocument>lambdaQuery()
            .in(SopDocument::getSopId, idList)
            .eq(SopDocument::getStatus, STATUS_PUBLISHED));
        if (publishedCount != null && publishedCount > 0) {
            throw new RuntimeException("已发布SOP不可删除,请先归档");
        }
        // 孤儿守卫2: 存在工序绑定不可删(追溯完整性)
        Long bindingCount = bindingMapper.selectCount(Wrappers.<SopProcessBinding>lambdaQuery()
            .in(SopProcessBinding::getSopId, idList));
        if (bindingCount != null && bindingCount > 0) {
            throw new RuntimeException("存在工序绑定,不可删除(追溯完整性)");
        }
        // 孤儿守卫3: 存在查阅记录不可删
        Long viewCount = viewLogMapper.selectCount(Wrappers.<SopViewLog>lambdaQuery()
            .in(SopViewLog::getSopId, idList));
        if (viewCount != null && viewCount > 0) {
            throw new RuntimeException("存在查阅记录,不可删除(追溯完整性)");
        }
        // 级联删除版本
        versionMapper.delete(Wrappers.<SopVersion>lambdaQuery().in(SopVersion::getSopId, idList));
        int rows = baseMapper.deleteByIds(idList);
        log.info("批量删除SOP: count={}", ids.length);
        return rows > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean addVersion(SopVersionBo bo) {
        SopDocument doc = baseMapper.selectById(bo.getSopId());
        if (doc == null) {
            throw new RuntimeException("SOP不存在: " + bo.getSopId());
        }
        // PUBLISHED 上修订: 状态回退 DRAFT (新建修订版本);ARCHIVED 不可修订
        if (STATUS_ARCHIVED.equals(doc.getStatus())) {
            throw new RuntimeException("已归档SOP不可新增版本");
        }
        if (STATUS_PUBLISHED.equals(doc.getStatus()) || STATUS_IN_REVIEW.equals(doc.getStatus())) {
            doc.setStatus(STATUS_DRAFT);
            baseMapper.updateById(doc);
        }
        // 版本号: 留空则自动递增 (现有版本数 = 下一个 minor, 1.0->1.1->1.2)
        String versionNo = bo.getVersionNo();
        if (StringUtils.isBlank(versionNo)) {
            Long existCount = versionMapper.selectCount(Wrappers.<SopVersion>lambdaQuery()
                .eq(SopVersion::getSopId, bo.getSopId()));
            versionNo = "1." + (existCount == null ? 0 : existCount);
        }
        SopVersion v = new SopVersion();
        v.setSopId(bo.getSopId());
        v.setVersionNo(versionNo);
        v.setFileOssId(bo.getFileOssId());
        v.setFileType(bo.getFileType());
        v.setChangeLog(bo.getChangeLog());
        v.setStatus(VERSION_PENDING);
        v.setRemark(bo.getRemark());
        versionMapper.insert(v);
        log.info("SOP新增版本: sopId={}, versionNo={}, PENDING", bo.getSopId(), versionNo);
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean submitForApproval(Long sopId, String selectFlowId) {
        SopDocument doc = baseMapper.selectById(sopId);
        if (doc == null) {
            throw new RuntimeException("SOP不存在: " + sopId);
        }
        if (!STATUS_DRAFT.equals(doc.getStatus())) {
            throw new RuntimeException("仅草稿状态可提交审批,当前: " + doc.getStatus());
        }
        // CAS DRAFT -> IN_REVIEW
        int rows = baseMapper.update(null, Wrappers.<SopDocument>lambdaUpdate()
            .set(SopDocument::getStatus, STATUS_IN_REVIEW)
            .eq(SopDocument::getSopId, sopId)
            .eq(SopDocument::getStatus, STATUS_DRAFT));
        if (rows == 0) {
            throw new RuntimeException("SOP状态已变更,提交审批失败");
        }
        // selectFlowId 非空则走 Flowable;否则仅改状态(管理员兜底审批)
        if (StringUtils.isNotBlank(selectFlowId)) {
            Map<String, Object> variables = new HashMap<>();
            variables.put("dataId", sopId.toString());
            variables.put("requireTaskOnStart", true);
            variables.put("sopId", sopId);
            variables.put("sopCode", doc.getSopCode());
            variables.put("sopName", doc.getSopName());
            variables.put("sopType", doc.getSopType());
            try {
                R<Void> result = processService.startProcessByDataId(sopId.toString(), selectFlowId, "sopDocumentService", variables);
                if (!R.isSuccess(result)) {
                    log.warn("Flowable启动失败,降级为直接IN_REVIEW: sopId={}, msg={}", sopId, result.getMsg());
                }
            } catch (Exception e) {
                // Flowable未部署/异常时降级: 状态已为 IN_REVIEW, 管理员可走兜底审批
                log.warn("Flowable启动异常,降级为直接IN_REVIEW: sopId={}", sopId, e);
            }
        }
        log.info("SOP提交审批: sopId={}, selectFlowId={}", sopId, selectFlowId);
        return true;
    }

    /**
     * Flowable 回调: 驱动 SOP 状态机
     * <p>doing->IN_REVIEW ; pass->doPublish(PUBLISHED) ; reject/retrun->DRAFT ; stop/recall/revoke->DRAFT</p>
     */
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void afterFlowHandle(WfMyBusiness business) {
        Long sopId = Long.parseLong(business.getDataId());
        SopDocument doc = baseMapper.selectById(sopId);
        if (doc == null) {
            return;
        }
        String actStatus = business.getActStatus();
        if (ActStatus.doing.equals(actStatus)) {
            updateStatus(sopId, STATUS_IN_REVIEW);
        } else if (ActStatus.pass.equals(actStatus)) {
            doPublish(sopId);
        } else if (ActStatus.reject.equals(actStatus) || ActStatus.retrun.equals(actStatus)) {
            updateStatus(sopId, STATUS_DRAFT);
        } else if (ActStatus.revoke.equals(actStatus) || ActStatus.stop.equals(actStatus) || ActStatus.recall.equals(actStatus)) {
            updateStatus(sopId, STATUS_DRAFT);
        }
    }

    @Override
    public Object getBusinessDataById(String dataId) {
        try {
            return baseMapper.selectVoById(Long.parseLong(dataId));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    @Override
    public Map<String, Object> flowValuesOfTask(String taskNameId, Map<String, Object> values) {
        return null;
    }

    @Override
    public List<String> flowCandidateUsernamesOfTask(String taskNameId, Map<String, Object> values) {
        return null;
    }

    /**
     * 兜底审批通过(管理员强制, R11): IN_REVIEW -> PUBLISHED, 内部调 doPublish
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean approve(Long sopId, String remark) {
        SopDocument doc = baseMapper.selectById(sopId);
        if (doc == null) {
            throw new RuntimeException("SOP不存在: " + sopId);
        }
        if (!STATUS_IN_REVIEW.equals(doc.getStatus())) {
            throw new RuntimeException("仅审批中状态可审批通过,当前: " + doc.getStatus());
        }
        doPublish(sopId);
        // 记录审批信息
        baseMapper.update(null, Wrappers.<SopDocument>lambdaUpdate()
            .set(SopDocument::getApprovalBy, LoginHelper.getUserId())
            .set(SopDocument::getApprovalTime, new Date())
            .set(StringUtils.isNotBlank(remark), SopDocument::getApprovalRemark, remark)
            .eq(SopDocument::getSopId, sopId));
        log.info("SOP兜底审批通过: sopId={}, remark={}", sopId, remark);
        return true;
    }

    /**
     * 兜底驳回: IN_REVIEW -> DRAFT
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean reject(Long sopId, String reason) {
        SopDocument doc = baseMapper.selectById(sopId);
        if (doc == null) {
            throw new RuntimeException("SOP不存在: " + sopId);
        }
        if (!STATUS_IN_REVIEW.equals(doc.getStatus())) {
            throw new RuntimeException("仅审批中状态可驳回,当前: " + doc.getStatus());
        }
        int rows = baseMapper.update(null, Wrappers.<SopDocument>lambdaUpdate()
            .set(SopDocument::getStatus, STATUS_DRAFT)
            .set(SopDocument::getApprovalBy, LoginHelper.getUserId())
            .set(SopDocument::getApprovalTime, new Date())
            .set(StringUtils.isNotBlank(reason), SopDocument::getApprovalRemark, reason)
            .eq(SopDocument::getSopId, sopId)
            .eq(SopDocument::getStatus, STATUS_IN_REVIEW));
        if (rows == 0) {
            throw new RuntimeException("SOP状态已变更,驳回失败");
        }
        log.info("SOP兜底驳回: sopId={}, reason={}", sopId, reason);
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean archive(Long sopId, String reason) {
        SopDocument doc = baseMapper.selectById(sopId);
        if (doc == null) {
            throw new RuntimeException("SOP不存在: " + sopId);
        }
        if (!STATUS_PUBLISHED.equals(doc.getStatus())) {
            throw new RuntimeException("仅已发布状态可归档,当前: " + doc.getStatus());
        }
        int rows = baseMapper.update(null, Wrappers.<SopDocument>lambdaUpdate()
            .set(SopDocument::getStatus, STATUS_ARCHIVED)
            .set(StringUtils.isNotBlank(reason), SopDocument::getArchiveReason, reason)
            .eq(SopDocument::getSopId, sopId)
            .eq(SopDocument::getStatus, STATUS_PUBLISHED));
        if (rows == 0) {
            throw new RuntimeException("SOP状态已变更,归档失败");
        }
        log.info("SOP归档: sopId={}, reason={}", sopId, reason);
        return true;
    }

    // ==================== 私有辅助方法 ====================

    /**
     * 内部发布: 最新 PENDING 版本 -> CURRENT, 旧 CURRENT -> HISTORICAL,
     * sop_document.status PUBLISHED, current_version/file_oss_id/file_type 同步
     */
    private void doPublish(Long sopId) {
        // 找最新 PENDING 版本
        SopVersion pending = versionMapper.selectOne(Wrappers.<SopVersion>lambdaQuery()
            .eq(SopVersion::getSopId, sopId)
            .eq(SopVersion::getStatus, VERSION_PENDING)
            .orderByDesc(SopVersion::getCreateTime)
            .last("LIMIT 1"));
        if (pending == null) {
            throw new RuntimeException("无待发布版本,无法发布: sopId=" + sopId);
        }
        Date now = new Date();
        Long userId = LoginHelper.getUserId();
        // 旧 CURRENT -> HISTORICAL
        versionMapper.update(null, Wrappers.<SopVersion>lambdaUpdate()
            .set(SopVersion::getStatus, VERSION_HISTORICAL)
            .eq(SopVersion::getSopId, sopId)
            .eq(SopVersion::getStatus, VERSION_CURRENT));
        // 新版本 PENDING -> CURRENT
        versionMapper.update(null, Wrappers.<SopVersion>lambdaUpdate()
            .set(SopVersion::getStatus, VERSION_CURRENT)
            .set(SopVersion::getPublishedTime, now)
            .set(SopVersion::getPublishedBy, userId)
            .eq(SopVersion::getVersionId, pending.getVersionId())
            .eq(SopVersion::getStatus, VERSION_PENDING));
        // sop_document: PUBLISHED + current_version + file_oss_id/file_type 同步
        baseMapper.update(null, Wrappers.<SopDocument>lambdaUpdate()
            .set(SopDocument::getStatus, STATUS_PUBLISHED)
            .set(SopDocument::getCurrentVersion, pending.getVersionNo())
            .set(SopDocument::getFileOssId, pending.getFileOssId())
            .set(SopDocument::getFileType, pending.getFileType())
            .set(SopDocument::getApprovalBy, userId)
            .set(SopDocument::getApprovalTime, now)
            .eq(SopDocument::getSopId, sopId));
        log.info("SOP发布成功: sopId={}, version={}", sopId, pending.getVersionNo());
    }

    private void updateStatus(Long sopId, String status) {
        baseMapper.update(null, Wrappers.<SopDocument>lambdaUpdate()
            .set(SopDocument::getStatus, status)
            .eq(SopDocument::getSopId, sopId));
    }

    /**
     * 生成SOP编码: 走系统自动编码规则 MES_SOP_CODE (SOP + yyyyMMdd + 3位日循环流水号)
     */
    private String generateSopCode() {
        return autoCodeUtil.genSerialCode("MES_SOP_CODE", null);
    }

    private LambdaQueryWrapper<SopDocument> buildQueryWrapper(SopDocumentBo bo) {
        return Wrappers.<SopDocument>lambdaQuery()
            .eq(StringUtils.isNotBlank(bo.getSopCode()), SopDocument::getSopCode, bo.getSopCode())
            .like(StringUtils.isNotBlank(bo.getSopName()), SopDocument::getSopName, bo.getSopName())
            .eq(StringUtils.isNotBlank(bo.getSopType()), SopDocument::getSopType, bo.getSopType())
            .eq(StringUtils.isNotBlank(bo.getStatus()), SopDocument::getStatus, bo.getStatus())
            .orderByDesc(SopDocument::getCreateTime);
    }
}
