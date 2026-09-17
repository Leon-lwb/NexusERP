package com.nexus.mes.sop.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.nexus.common.core.domain.R;
import com.nexus.common.core.exception.ServiceException;
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

    /**
     * 分页查询SOP文档列表
     *
     * @param bo        查询参数
     * @param pageQuery 分页参数
     * @return 分页结果
     */
    @Override
    public TableDataInfo<SopDocumentVo> selectPageList(SopDocumentBo bo, PageQuery pageQuery) {
        LambdaQueryWrapper<SopDocument> lqw = buildQueryWrapper(bo);
        Page<SopDocumentVo> page = baseMapper.selectVoPage(pageQuery.build(), lqw);
        return TableDataInfo.build(page);
    }

    /**
     * 根据ID获取SOP详情
     *
     * @param sopId SOP文档ID
     * @return SOP详情
     */
    @Override
    public SopDocumentVo selectById(Long sopId) {
        SopDocumentVo vo = baseMapper.selectVoById(sopId);
        return vo;
    }

    /**
     * 新增SOP(自动生成编码 + 建首版本 PENDING)
     * <p>编码为空时自动生成 MES_SOP_CODE; 首版本 v1.0 PENDING 态。
     * 编码唯一性校验, 首版本文件不得为空。</p>
     *
     * @param bo SOP业务对象(含文件OssId)
     * @return 新增成功返回 true
     */
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

    /**
     * 修改SOP(仅 DRAFT/ARCHIVED 可编辑; PUBLISHED 需先新增版本修订)
     * <p>状态守卫: 非 DRAFT/ARCHIVED 时抛异常。</p>
     *
     * @param bo SOP业务对象
     * @return 修改成功返回 true
     */
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

    /**
     * 批量删除SOP(孤儿守卫: PUBLISHED 不可删; 存在绑定/查阅记录不可删)
     * <p>级联删除版本。发布状态、工序绑定、查阅记录均阻止删除以保障追溯完整性。</p>
     *
     * @param ids SOP文档ID数组
     * @return 删除成功返回 true
     */
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
            throw new ServiceException("已发布SOP不可删除，请先归档");
        }
        // 孤儿守卫2: 存在工序绑定不可删(追溯完整性)
        Long bindingCount = bindingMapper.selectCount(Wrappers.<SopProcessBinding>lambdaQuery()
            .in(SopProcessBinding::getSopId, idList));
        if (bindingCount != null && bindingCount > 0) {
            throw new ServiceException("该SOP仍被工序绑定，不可删除（追溯完整性），请先解除工序绑定");
        }
        // 孤儿守卫3: 存在查阅记录不可删
        Long viewCount = viewLogMapper.selectCount(Wrappers.<SopViewLog>lambdaQuery()
            .in(SopViewLog::getSopId, idList));
        if (viewCount != null && viewCount > 0) {
            throw new ServiceException("该SOP已产生查阅记录，不可删除：查阅记录与其电子签名（biz_type=SOP_VIEW）"
                + "共同构成追溯链，删掉文档会使这些签名失去对应正文、无法复核。如需停用请改用「归档」。");
        }
        // 级联删除版本
        versionMapper.delete(Wrappers.<SopVersion>lambdaQuery().in(SopVersion::getSopId, idList));
        int rows = baseMapper.deleteByIds(idList);
        log.info("批量删除SOP: count={}", ids.length);
        return rows > 0;
    }

    /**
     * 新增版本(DRAFT 或 PUBLISHED 上修订) -> PENDING, sop_document.status DRAFT
     * <p>PUBLISHED/IN_REVIEW 下修订时主档回退 DRAFT; ARCHIVED 不可修订。
     * 版本号留空则自动递增 (1.0 -> 1.1 -> 1.2)。</p>
     *
     * @param bo 版本业务对象(含文件OssId)
     * @return 新增成功返回 true
     */
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

    /**
     * 提交审批: DRAFT -> IN_REVIEW (CAS 状态守卫)
     * <p>selectFlowId 非空则走 Flowable 启动流程; 异常时降级为仅改状态(管理员兜底)。
     * CAS 更新(sopId + DRAFT) 确保并发安全。</p>
     *
     * @param sopId        SOP文档ID
     * @param selectFlowId 流程定义ID(可空, 空则仅改状态)
     * @return 提交成功返回 true
     */
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
        // selectFlowId 非空则走 Flowable; 否则仅改状态(管理员兜底审批)
        if (StringUtils.isNotBlank(selectFlowId)) {
            // 启动前不得先改业务行/持行锁: 启动时 Flowable 会回调 afterFlowHandle(doing) 在 REQUIRES_NEW(mes) 里 UPDATE 同一行,
            // 若外层先 UPDATE 持锁则回调等待外层、外层等流程返回 -> 死锁循环 -> 锁等待超时(同 Firmware 修复)。
            // 失败即抛错回滚, 不做静默降级(状态由回调推进, 无半途 IN_REVIEW 残留)。
            Map<String, Object> variables = new HashMap<>();
            variables.put("dataId", sopId.toString());
            variables.put("requireTaskOnStart", true);
            variables.put("sopId", sopId);
            variables.put("sopCode", doc.getSopCode());
            variables.put("sopName", doc.getSopName());
            variables.put("sopType", doc.getSopType());
            R<Void> result = processService.startProcessByDataId(sopId.toString(), selectFlowId, "sopDocumentService", variables);
            if (!R.isSuccess(result)) {
                throw new RuntimeException("发起审批流程失败: " + result.getMsg());
            }
        } else {
            // CAS DRAFT -> IN_REVIEW
            int rows = baseMapper.update(null, Wrappers.<SopDocument>lambdaUpdate()
                .set(SopDocument::getStatus, STATUS_IN_REVIEW)
                .eq(SopDocument::getSopId, sopId)
                .eq(SopDocument::getStatus, STATUS_DRAFT));
            if (rows == 0) {
                throw new RuntimeException("SOP状态已变更,提交审批失败");
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
    @DS("mes")
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
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

    /**
     * Flowable 回调钩子: 根据业务数据ID获取业务表单数据
     * <p>必须显式 @DS("mes") + @Transactional(REQUIRES_NEW): 该回调由 Flowable(master 事务)经 getBean 直接调用,
     * 外层 master 事务已绑定连接, 仅靠类级 @DS 改 holder 无法切换(查询会落到 ry-vue 报 sop_document 不存在);
     * REQUIRES_NEW 挂起外层事务、在 holder top="mes"(方法级 @DS 已 push) 时另起新事务绑定 mes 连接
     * (同 ERP WmArrivalNotice 先例)。</p>
     *
     * @param dataId 业务数据ID(sopId 字符串)
     * @return SopDocumentVo 或 null(解析失败时)
     */
    @Override
    @DS("mes")
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public Object getBusinessDataById(String dataId) {
        try {
            return baseMapper.selectVoById(Long.parseLong(dataId));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Flowable 回调钩子: 返回当前节点的流程变量
     * <p>SOP 审批无需额外变量, 返回 null。</p>
     *
     * @param taskNameId 节点定义ID
     * @param values     前端传入的变量(含dataId)
     * @return 始终返回 null
     */
    @Override
    public Map<String, Object> flowValuesOfTask(String taskNameId, Map<String, Object> values) {
        return null;
    }

    /**
     * Flowable 回调钩子: 返回当前节点的候选人用户名
     * <p>SOP 审批无特殊候选人逻辑, 返回 null。</p>
     *
     * @param taskNameId 节点定义ID
     * @param values     前端传入的变量(含dataId)
     * @return 始终返回 null
     */
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

    /**
     * 归档(废止): PUBLISHED -> ARCHIVED (CAS 状态守卫)
     * <p>仅已发布状态可归档, CAS 更新(sopId + PUBLISHED) 确保并发安全。</p>
     *
     * @param sopId  SOP文档ID
     * @param reason 归档原因(可空)
     * @return 归档成功返回 true
     */
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

    /**
     * 直接更新 SOP 状态(无 CAS 守卫, 供 Flowable 回调使用)
     */
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

    /**
     * 构建查询条件: sopCode精确/sopName模糊/sopType精确/status精确, 按创建时间倒序
     */
    private LambdaQueryWrapper<SopDocument> buildQueryWrapper(SopDocumentBo bo) {
        return Wrappers.<SopDocument>lambdaQuery()
            .eq(StringUtils.isNotBlank(bo.getSopCode()), SopDocument::getSopCode, bo.getSopCode())
            .like(StringUtils.isNotBlank(bo.getSopName()), SopDocument::getSopName, bo.getSopName())
            .eq(StringUtils.isNotBlank(bo.getSopType()), SopDocument::getSopType, bo.getSopType())
            .eq(StringUtils.isNotBlank(bo.getStatus()), SopDocument::getStatus, bo.getStatus())
            .orderByDesc(SopDocument::getCreateTime);
    }
}
