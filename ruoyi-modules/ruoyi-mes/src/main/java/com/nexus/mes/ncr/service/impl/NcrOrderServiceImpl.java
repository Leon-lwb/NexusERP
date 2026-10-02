package com.nexus.mes.ncr.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.lock.annotation.Lock4j;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectReader;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.core.utils.StringUtils;
import com.nexus.common.json.utils.JsonUtils;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.common.satoken.utils.LoginHelper;
import com.nexus.common.sign.constants.SignAuthMethod;
import com.nexus.common.sign.constants.SignBizType;
import com.nexus.common.sign.domain.dto.SignCommand;
import com.nexus.common.sign.service.ESignatureService;
import com.nexus.mes.ncr.domain.NcrDefect;
import com.nexus.mes.ncr.domain.NcrOrder;
import com.nexus.mes.ncr.domain.bo.NcrOrderBo;
import com.nexus.mes.ncr.domain.vo.NcrOrderVo;
import com.nexus.mes.ncr.mapper.NcrDefectMapper;
import com.nexus.mes.ncr.mapper.NcrOrderMapper;
import com.nexus.mes.ncr.service.INcrDefectService;
import com.nexus.mes.ncr.service.INcrOrderService;
import com.nexus.mes.ncr.service.INcrReworkOrderService;
import com.nexus.mes.lt.domain.LtLot;
import com.nexus.mes.lt.mapper.LtLotMapper;
import com.nexus.mes.lt.service.ILtLotService;
import com.nexus.mes.pro.service.IProAndonService;
import com.nexus.system.strategy.AutoCodeUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * MES NCR不合格品单 Service 业务层实现
 * <p>
 * 状态机: OPEN -> REVIEW -> DISPOSITION -> CLOSED
 *                         └-> IN_REWORK -> CLOSED
 * 自动创建流程: 各质检模块或工序出口发现不合格时,通过 defectInfo JSON 传入缺陷明细,
 * 解析并落真实批次(定位不到时显式 warn "未关联批次", 不伪造 null 走旁路), 同时触发批次隔离与
 * 安灯质量告警(建单期的隔离/安灯为 best-effort,不阻断主流程)。
 * 处置期相反: 报废/让步的批次动作失败必须抛异常回滚 —— 处置结果与批次动作同事务一致(审计 MES-NCR-02)。
 * </p>
 *
 * @author Nexus
 */
@Slf4j
@RequiredArgsConstructor
@DS("mes")
@Service("ncrOrderService")
public class NcrOrderServiceImpl implements INcrOrderService {

    private final NcrOrderMapper baseMapper;
    private final NcrDefectMapper defectMapper;
    private final AutoCodeUtil autoCodeUtil;
    private final ILtLotService ltLotService;
    /** 批次主档 Mapper: 解析 NCR 关联的真实批次(MES-NCR-01) */
    private final LtLotMapper ltLotMapper;
    /** IPQC 检验单 Mapper: 工序过程检验来源无物料入参, 经检验单取物料以定位批次 */
    private final com.nexus.mes.qc.mapper.QcIpqcMapper qcIpqcMapper;
    private final IProAndonService proAndonService;
    private final INcrReworkOrderService ncrReworkOrderService;
    private final INcrDefectService defectService;
    private final ESignatureService eSignatureService;

    /** MES-NCR-06: 处置方式白名单(不可逆处置必须走电子签名入口 {@link #disposeWithSignature}) */
    private static final java.util.Set<String> SUPPORTED_DISPOSITIONS =
        java.util.Set.of(DISPOSITION_REWORK, DISPOSITION_SCRAP, DISPOSITION_ACCEPT, DISPOSITION_RETURN);

    /**
     * 分页查询NCR单列表
     *
     * @param bo        查询条件
     * @param pageQuery 分页参数
     * @return NCR单分页数据
     */
    @Override
    public TableDataInfo<NcrOrderVo> selectPageList(NcrOrderBo bo, PageQuery pageQuery) {
        LambdaQueryWrapper<NcrOrder> lqw = buildQueryWrapper(bo);
        Page<NcrOrderVo> page = baseMapper.selectVoPage(pageQuery.build(), lqw);
        return TableDataInfo.build(page);
    }

    /**
     * 根据ID获取NCR单详情
     *
     * @param ncrId NCR单ID
     * @return NCR单详情
     */
    @Override
    public NcrOrderVo selectById(Long ncrId) {
        return baseMapper.selectVoById(ncrId);
    }

    /**
     * 新增NCR单(手工创建,来源 CUSTOMER)
     * <p>手工创建时 defectInfo 通过前台传入,解析为缺陷明细。</p>
     *
     * @param bo NCR单业务对象
     * @return 新增成功返回 true
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean insertByBo(NcrOrderBo bo) {
        bo.setSourceType(SOURCE_CUSTOMER);
        if (StringUtils.isBlank(bo.getNcrCode())) {
            bo.setNcrCode(generateNcrCode());
        }
        NcrOrder order = new NcrOrder();
        order.setNcrCode(bo.getNcrCode());
        order.setSourceType(SOURCE_CUSTOMER);
        order.setSourceId(bo.getSourceId());
        order.setWoId(bo.getWoId());
        order.setWoNo(bo.getWoNo());
        order.setCardId(bo.getCardId());
        order.setStepRecordId(bo.getStepRecordId());
        order.setProcessId(bo.getProcessId());
        order.setProcessName(bo.getProcessName());
        order.setMaterialId(bo.getMaterialId());
        order.setMaterialCode(bo.getMaterialCode());
        order.setMaterialName(bo.getMaterialName());
        order.setLotId(bo.getLotId());
        order.setLotCode(bo.getLotCode());
        order.setDefectQty(bo.getDefectQty());
        order.setStatus(STATUS_OPEN);
        order.setCreatedTime(new Date());
        order.setRemark(bo.getRemark());
        int rows = baseMapper.insert(order);
        log.info("手工创建NCR单: ncrCode={}, sourceType=CUSTOMER", bo.getNcrCode());
        return rows > 0;
    }

    /**
     * 修改NCR单(仅 OPEN/REVIEW 状态可编辑)
     * <p>MES-NCR-03: 条件更新(CAS) + 白名单字段 —— 绝不整实体 updateById,
     * 否则并发处置后的 CLOSED/DISPOSITION 状态会被本次编辑回滚。</p>
     *
     * @param bo NCR单业务对象
     * @return 修改成功返回 true
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean updateByBo(NcrOrderBo bo) {
        NcrOrder order = baseMapper.selectById(bo.getNcrId());
        if (order == null) {
            throw new RuntimeException("NCR单不存在: " + bo.getNcrId());
        }
        // 状态校验: 仅 OPEN/REVIEW 可编辑(与 CAS 条件一致, 先给出可读错误)
        if (!STATUS_OPEN.equals(order.getStatus()) && !STATUS_REVIEW.equals(order.getStatus())) {
            throw new RuntimeException("当前状态不可编辑: " + order.getStatus());
        }
        // 白名单业务字段 + 状态 CAS: rows==0 说明状态已被并发变更(如已处置/已关闭)
        com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<NcrOrder> update =
            Wrappers.<NcrOrder>lambdaUpdate()
            .set(bo.getWoId() != null, NcrOrder::getWoId, bo.getWoId())
            .set(bo.getWoNo() != null, NcrOrder::getWoNo, bo.getWoNo())
            .set(bo.getCardId() != null, NcrOrder::getCardId, bo.getCardId())
            .set(bo.getStepRecordId() != null, NcrOrder::getStepRecordId, bo.getStepRecordId())
            .set(bo.getProcessId() != null, NcrOrder::getProcessId, bo.getProcessId())
            .set(bo.getProcessName() != null, NcrOrder::getProcessName, bo.getProcessName())
            .set(bo.getMaterialId() != null, NcrOrder::getMaterialId, bo.getMaterialId())
            .set(bo.getMaterialCode() != null, NcrOrder::getMaterialCode, bo.getMaterialCode())
            .set(bo.getMaterialName() != null, NcrOrder::getMaterialName, bo.getMaterialName())
            .set(bo.getLotId() != null, NcrOrder::getLotId, bo.getLotId())
            .set(bo.getLotCode() != null, NcrOrder::getLotCode, bo.getLotCode())
            .set(bo.getDefectQty() != null, NcrOrder::getDefectQty, bo.getDefectQty())
            .set(bo.getRemark() != null, NcrOrder::getRemark, bo.getRemark())
            .eq(NcrOrder::getNcrId, bo.getNcrId())
            .in(NcrOrder::getStatus, STATUS_OPEN, STATUS_REVIEW);
        if (update.getSqlSet() == null || update.getSqlSet().isBlank()) {
            // 无任何白名单字段可写: 视为无变更(空 SET 会生成非法 SQL)
            log.info("NCR单无字段变更, 跳过更新: ncrId={}", bo.getNcrId());
            return true;
        }
        int rows = baseMapper.update(null, update);
        if (rows == 0) {
            throw new ServiceException("NCR单状态已变更，请刷新后重试");
        }
        log.info("修改NCR单: ncrId={}", bo.getNcrId());
        return true;
    }

    /**
     * 批量删除NCR单(仅 CLOSED 状态可删)
     *
     * @param ids NCR单ID数组
     * @return 删除成功返回 true
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean deleteByIds(Long[] ids) {
        if (ids == null || ids.length == 0) {
            return false;
        }
        for (Long id : ids) {
            NcrOrder order = baseMapper.selectById(id);
            if (order == null) {
                continue;
            }
            if (!STATUS_CLOSED.equals(order.getStatus())) {
                throw new RuntimeException("NCR单" + order.getNcrCode() + "未关闭,不可删除");
            }
        }
        List<Long> idList = java.util.Arrays.asList(ids);
        // 同时删除关联缺陷明细
        defectMapper.delete(Wrappers.<NcrDefect>lambdaQuery().in(NcrDefect::getNcrId, idList));
        int rows = baseMapper.deleteByIds(idList);
        log.info("批量删除NCR单: count={}", ids.length);
        return rows > 0;
    }

    // ==================== 自动创建 ====================

    /**
     * 从IQC检验自动创建NCR单
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public NcrOrderVo autoCreateFromIqc(Long iqcId, Long receiveId, Long materialId, String defectInfo) {
        // 解析真实批次: 使批次隔离/解隔离/报废动作真正执行(MES-NCR-01)
        LtLot lot = resolveLot(iqcId, materialId, SOURCE_IQC, iqcId);
        return createNcr(SOURCE_IQC, iqcId, null, materialId, null, defectInfo,
            lot == null ? null : lot.getLotId(), null, null, null, null, null, null, null);
    }

    /**
     * 从IPQC检验自动创建NCR单
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public NcrOrderVo autoCreateFromIpqc(Long ipqcId, Long mesWoId, Long cardId, Long stepRecordId,
                                          Long processId, BigDecimal defectQty, String defectInfo) {
        Long materialId = materialIdOfIpqc(ipqcId);
        LtLot lot = resolveLot(null, materialId, SOURCE_IPQC, ipqcId);
        return createNcr(SOURCE_IPQC, ipqcId, mesWoId, materialId, defectQty, defectInfo,
            lot == null ? null : lot.getLotId(), cardId, stepRecordId, processId, null, null, null, null);
    }

    /**
     * 从OQC检验自动创建NCR单
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public NcrOrderVo autoCreateFromOqc(Long oqcId, Long soId, Long materialId, String defectInfo) {
        LtLot lot = resolveLot(null, materialId, SOURCE_OQC, oqcId);
        return createNcr(SOURCE_OQC, oqcId, null, materialId, null, defectInfo,
            lot == null ? null : lot.getLotId(), null, null, null, null, null, null, null);
    }

    /**
     * 从工序出口检验自动创建NCR单
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public NcrOrderVo autoCreateFromExit(Long cardId, Long stepRecordId, Long mesWoId,
                                          BigDecimal defectQty, String defectInfo) {
        // 工序出口来源没有物料/批次键: 无法定位批次时显式记录"未关联批次", 不伪造 null 走旁路
        log.warn("NCR(工序出口)未关联批次, 批次隔离/报废/放行动作不会执行: cardId={}, stepRecordId={}, mesWoId={}",
            cardId, stepRecordId, mesWoId);
        // MES-NCR-05: sourceId 用 stepRecordId(唯一定位一次出站), 使"同一次出站"的重复上报命中幂等检查;
        // 原实现传 null 会绕过 sourceType+sourceId 查重, 每次上报都新建 NCR 单
        return createNcr(SOURCE_PROCESS, stepRecordId, mesWoId, null, defectQty, defectInfo,
            null, cardId, stepRecordId, null, null, null, null, null);
    }

    // ==================== 状态机操作 ====================

    /**
     * 评审NCR单(OPEN -> REVIEW)
     * <p>CAS 状态机: .eq(status, OPEN) + rows==0 抛"状态已变更"。</p>
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean review(Long ncrId) {
        NcrOrder order = baseMapper.selectById(ncrId);
        if (order == null) {
            throw new RuntimeException("NCR单不存在: " + ncrId);
        }
        int rows = baseMapper.update(null, Wrappers.<NcrOrder>lambdaUpdate()
            .set(NcrOrder::getStatus, STATUS_REVIEW)
            .set(NcrOrder::getReviewBy, LoginHelper.getUserId())
            .set(NcrOrder::getReviewTime, new Date())
            .eq(NcrOrder::getNcrId, ncrId)
            .eq(NcrOrder::getStatus, STATUS_OPEN));
        if (rows == 0) {
            throw new RuntimeException("NCR状态已变更,评审失败");
        }
        log.info("NCR评审: ncrId={}, reviewBy={}", ncrId, LoginHelper.getUserId());
        return true;
    }

    /**
     * 处置NCR单(OPEN/REVIEW -> DISPOSITION, 按处置方式分支流转)
     * <p>CAS 状态机: .in(status, OPEN, REVIEW) + rows==0 抛异常。
     * REWORK -> 创建返工单 -> IN_REWORK;
     * SCRAP -> 隔离批次报废 -> CLOSED;
     * ACCEPT_AS_IS -> 放行批次 -> CLOSED;
     * RETURN -> CLOSED。</p>
     */
    @Lock4j(keys = {"#ncrId"})
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean dispose(Long ncrId, String disposition, String remark) {
        NcrOrder order = baseMapper.selectById(ncrId);
        if (order == null) {
            throw new RuntimeException("NCR单不存在: " + ncrId);
        }
        // CAS: OPEN/REVIEW -> DISPOSITION
        int rows = baseMapper.update(null, Wrappers.<NcrOrder>lambdaUpdate()
            .set(NcrOrder::getStatus, STATUS_DISPOSITION)
            .set(NcrOrder::getDisposition, disposition)
            .set(NcrOrder::getDispositionBy, LoginHelper.getUserId())
            .set(NcrOrder::getDispositionTime, new Date())
            .set(StringUtils.isNotBlank(remark), NcrOrder::getRemark, remark)
            .eq(NcrOrder::getNcrId, ncrId)
            .in(NcrOrder::getStatus, STATUS_OPEN, STATUS_REVIEW));
        if (rows == 0) {
            throw new RuntimeException("NCR状态已变更,处置失败");
        }
        log.info("NCR处置: ncrId={}, disposition={}, by={}", ncrId, disposition, LoginHelper.getUserId());

        // 按处置方式分支
        return switch (disposition) {
            case DISPOSITION_REWORK -> handleReworkDisposition(order, remark);
            case DISPOSITION_SCRAP -> handleScrapDisposition(order);
            case DISPOSITION_ACCEPT -> handleAcceptDisposition(order);
            case DISPOSITION_RETURN -> handleReturnDisposition(order);
            default -> throw new RuntimeException("不支持的处置方式: " + disposition);
        };
    }

    /**
     * 返工完成回调(IN_REWORK -> CLOSED)
     * <p>注:返工工单完工时由 ProWorkOrderServiceImpl 直调 NcrOrderMapper 完成 CAS,
     * 本方法保留供内部/测试调用,不被 ProWorkOrder 调用(避免循环依赖)。</p>
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean reworkCompleted(Long ncrId) {
        NcrOrder order = baseMapper.selectById(ncrId);
        if (order == null) {
            throw new RuntimeException("NCR单不存在: " + ncrId);
        }
        int rows = baseMapper.update(null, Wrappers.<NcrOrder>lambdaUpdate()
            .set(NcrOrder::getStatus, STATUS_CLOSED)
            .set(NcrOrder::getClosedTime, new Date())
            .eq(NcrOrder::getNcrId, ncrId)
            .eq(NcrOrder::getStatus, STATUS_IN_REWORK));
        if (rows == 0) {
            throw new RuntimeException("NCR状态已变更,返工完成回调失败");
        }
        log.info("NCR返工完成关闭: ncrId={}", ncrId);
        return true;
    }

    /**
     * 绑定处置签名文件ID(P0-6 预留)
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void bindDispositionSignature(Long ncrId, Long signatureId) {
        baseMapper.update(null, Wrappers.<NcrOrder>lambdaUpdate()
            .set(NcrOrder::getDispositionSignatureId, signatureId)
            .eq(NcrOrder::getNcrId, ncrId));
        log.info("NCR签名绑定: ncrId={}, signatureId={}", ncrId, signatureId);
    }

    /**
     * NCR 处置签署：先签署再绑定，返回签名ID。
     * <p>
     * 与 SOP 查阅(记录, 失败可降级)不同, 处置是<b>授权</b>: 认证方式为 LOGIN_ONLY 时直接拒绝,
     * 签署失败也不吞异常 —— 授权不可降级, 必须让调用方感知。
     * </p>
     * <p>
     * <b>必须走 {@code signInNewTransaction}(REQUIRES_NEW), 不能走 {@code sign}:</b>
     * es_signature 只存在于 master(ry-vue)，而本服务类级 {@code @DS("mes")} 会把数据源路由到
     * ry-mes（写入即报 {@code Table 'ry-mes.es_signature' doesn't exist}）。{@code sign} 的传播行为
     * 是 REQUIRED：一旦本方法被「已在 MES 数据源上开着事务」的调用方调用（如
     * {@link #disposeWithSignature}），它会加入那个事务 —— 而事务内 @DS 切换不生效，连接已绑定
     * 在 ry-mes，签名照样写到错误的库。改用独立事务后，@DS 切面(order 最先)在开事务、取连接之前
     * 就把 master 压栈（见 {@code ESignatureServiceImpl} 类注释），签署真正落到 ry-vue，
     * 且不会被调用方事务的回滚牵连。
     * </p>
     *
     * @param ncrId      NCR 单ID
     * @param disposition 处置结论（并入签署含义）
     * @param authMethod  认证方式（不得为 LOGIN_ONLY）
     * @param credential  认证凭证
     * @return 签名ID
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long signDisposition(Long ncrId, String disposition, String authMethod, String credential) {
        // 认证等级先于落库校验(失败即拒, 不碰数据库)
        if (SignAuthMethod.LOGIN_ONLY.equals(authMethod)) {
            throw new ServiceException("NCR 处置签署必须进行二级认证");
        }
        NcrOrder order = baseMapper.selectById(ncrId);
        if (order == null) {
            throw new ServiceException("NCR 单不存在");
        }
        // disposition 可空: 不允许拼接出字面量 "null"
        String meaning = StringUtils.isBlank(disposition)
            ? "不合格品处置批准" : "不合格品处置批准：" + disposition;
        Long signatureId = eSignatureService.signInNewTransaction(new SignCommand(
            SignBizType.NCR_DISPOSITION, ncrId, order.getNcrCode(), meaning, null, authMethod, credential));
        bindDispositionSignature(ncrId, signatureId);
        log.info("NCR处置签署: ncrId={}, signatureId={}, authMethod={}", ncrId, signatureId, authMethod);
        return signatureId;
    }

    /**
     * 签署并处置NCR单(先签署, 再处置)。
     * <p>
     * <b>为什么事务边界在服务层、而不是让 Controller 连调两次：</b>签署与处置必须同生共死。
     * 让 Controller 依次调用「签署服务」和「处置服务」，两次调用之间任何一次失败都会留下
     * 「签了但没处置」或「处置了却没有绑定签名」的坏状态；把两件事收进一个有事务边界的方法，
     * Controller 保持一行转发（仓库既有风格）。
     * </p>
     * <p>
     * <b>顺序：先签署（含授权校验），后处置。</b>认证不通过（LOGIN_ONLY / 凭证错误）时业务库一行未动，
     * 不会出现「先改了状态、再发现授权失败」的中间态，也不会提前触发批隔离/安灯等分支副作用。
     * </p>
     * <p>
     * <b>事务组成（签名库与业务库不共库，物理上无法并成一个事务）：</b>
     * <ul>
     *   <li>签署记录：{@link ESignatureService#signInNewTransaction} 独立事务，先落到 master(ry-vue)；
     *       append-only 台账，见 {@link #signDisposition} 的说明。</li>
     *   <li>处置状态 CAS、分支流转与 {@code disposition_signature_id} 绑定：本方法的事务，
     *       全部落在 ry-mes，<b>同库同事务</b>提交或回滚。</li>
     * </ul>
     * 故「已处置但未绑定签名」在本设计下不可能出现（绑定与处置要么一起提交、要么一起回滚）；
     * 残余窗口只有反方向的一种：签名已提交、本事务随后回滚 —— 结果是「有授权记录、无处置动作」，
     * 业务状态未被污染、可重新发起，属于安全的失败方向。
     * </p>
     *
     * @param ncrId       NCR单ID
     * @param disposition 处置方式(REWORK/SCRAP/ACCEPT_AS_IS/RETURN)
     * @param remark      处置备注(可空)
     * @param authMethod  认证方式(LOGIN_ONLY 将被拒绝)
     * @param credential  认证凭证
     * @return 处置成功返回 true
     */
    @Lock4j(keys = {"#ncrId"})
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean disposeWithSignature(Long ncrId, String disposition, String remark,
                                        String authMethod, String credential) {
        // MES-NCR-06: 处置方式白名单 —— 先于任何落库动作校验, 未知处置不得先改状态再报错
        if (!SUPPORTED_DISPOSITIONS.contains(disposition)) {
            throw new ServiceException("不支持的处置方式: " + disposition);
        }
        // 1. 签署(含授权校验与 disposition_signature_id 绑定): 失败直接抛出, 业务库尚未被触碰
        signDisposition(ncrId, disposition, authMethod, credential);
        // 2. 处置: 与本方法同一事务, 与步骤1 的绑定同生共死
        return dispose(ncrId, disposition, remark);
    }

    // ==================== 私有方法 ====================

    /**
     * 核心创建NCR单(幂等: 按 sourceType+sourceId 查重)
     * <p>
     * 1. sourceId 非空时幂等检查,已存在直接返回;
     * 2. 生成NCR编码,构建 NcrOrder(status=OPEN);
     * 3. 解析 defectInfo JSON 为缺陷明细,批量插入;
     * 4. 批次隔离(best-effort);
     * 5. 安灯质量告警(best-effort)。
     * </p>
     *
     * @param sourceType    来源类型
     * @param sourceId      来源单据ID(可空)
     * @param mesWoId       工单ID(可空)
     * @param materialId    物料ID(可空)
     * @param defectQty     不良数量(可空)
     * @param defectInfo    缺陷信息JSON(可空)
     * @param lotId         批次ID(可空)
     * @param cardId        流转卡ID(可空)
     * @param stepRecordId  工序报工记录ID(可空)
     * @param processId     工序ID(可空)
     * @param processName   工序名称(可空)
     * @param woNo          工单编号(可空)
     * @param materialCode  物料编码(可空)
     * @param materialName  物料名称(可空)
     * @return NCR单视图对象
     */
    private NcrOrderVo createNcr(String sourceType, Long sourceId, Long mesWoId, Long materialId,
                                  BigDecimal defectQty, String defectInfo,
                                  Long lotId, Long cardId, Long stepRecordId, Long processId,
                                  String processName, String woNo, String materialCode, String materialName) {
        // 1. 幂等检查
        if (sourceId != null) {
            NcrOrderVo existing = baseMapper.selectVoOne(
                Wrappers.<NcrOrder>lambdaQuery()
                    .eq(NcrOrder::getSourceType, sourceType)
                    .eq(NcrOrder::getSourceId, sourceId));
            if (existing != null) {
                log.info("NCR单已存在(幂等跳过): sourceType={}, sourceId={}", sourceType, sourceId);
                return existing;
            }
        }

        // 2. 构建NCR单
        String ncrCode = generateNcrCode();
        NcrOrder order = new NcrOrder();
        order.setNcrCode(ncrCode);
        order.setSourceType(sourceType);
        order.setSourceId(sourceId);
        order.setWoId(mesWoId);
        order.setWoNo(woNo);
        order.setCardId(cardId);
        order.setStepRecordId(stepRecordId);
        order.setProcessId(processId);
        order.setProcessName(processName);
        order.setMaterialId(materialId);
        order.setMaterialCode(materialCode);
        order.setMaterialName(materialName);
        order.setLotId(lotId);
        // 批次编码随批次一并落库(便于界面/追溯显示); lotId 为空时保持 null, 由 resolveLot 的 warn 显式记录
        order.setLotCode(lotCodeOf(lotId));
        order.setDefectQty(defectQty != null ? defectQty : BigDecimal.ZERO);
        order.setStatus(STATUS_OPEN);
        order.setCreatedTime(new Date());
        baseMapper.insert(order);
        Long ncrId = order.getNcrId();
        log.info("创建NCR单: ncrCode={}, sourceType={}, sourceId={}", ncrCode, sourceType, sourceId);

        // 3. 解析缺陷信息
        parseAndInsertDefects(ncrId, defectInfo);

        // 4. 批次隔离(best-effort)
        quarantineLotIfNeeded(ncrId, lotId, ncrCode);

        // 5. 安灯质量告警(best-effort)
        callAndonIfNeeded(mesWoId, cardId, stepRecordId, ncrCode);

        return baseMapper.selectVoById(ncrId);
    }

    /**
     * 解析 defectInfo JSON 并插入缺陷明细
     * <p>
     * defectInfo 格式: [{"code":"SCRATCH","qty":5,"desc":"划痕"}]
     * 数值型数量直接解析为 BigDecimal，且仅接受完整的单个 JSON 根；reader 选项不修改共享 mapper。
     * JSON 或必填数量解析失败时 log.warn 跳过,不阻断主流程；写入异常必须向上传播。
     * </p>
     */
    private void parseAndInsertDefects(Long ncrId, String defectInfo) {
        if (StringUtils.isBlank(defectInfo)) {
            return;
        }
        List<NcrDefect> defects = new ArrayList<>();
        try {
            ObjectReader reader = JsonUtils.getObjectMapper()
                .readerFor(new TypeReference<List<Map<String, Object>>>() {})
                .with(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS,
                    DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
            List<Map<String, Object>> defectMaps = reader.readValue(defectInfo);
            if (defectMaps == null || defectMaps.isEmpty()) {
                return;
            }
            for (Map<String, Object> map : defectMaps) {
                NcrDefect defect = new NcrDefect();
                defect.setNcrId(ncrId);
                defect.setDefectCode(map.get("code") != null ? map.get("code").toString() : null);
                if (map.get("qty") == null) {
                    throw new IllegalArgumentException("缺陷数量不能为空");
                }
                defect.setDefectQty(new BigDecimal(map.get("qty").toString()));
                defect.setRootCauseDesc(map.get("desc") != null ? map.get("desc").toString() : null);
                defects.add(defect);
            }
        } catch (Exception e) {
            log.warn("解析defectInfo失败,跳过缺陷明细: ncrId={}, defectInfo={}", ncrId, defectInfo, e);
            return;
        }
        // 写入失败不是解析失败：保留原异常，交由调用方事务处理，不得返回创建成功。
        if (!defects.isEmpty()) {
            defectService.batchInsertForNcr(ncrId, defects);
        }
    }

    /**
     * 批次隔离(best-effort)
     */
    private void quarantineLotIfNeeded(Long ncrId, Long lotId, String ncrCode) {
        if (lotId == null) {
            return;
        }
        try {
            ltLotService.quarantineByNcr(lotId, ncrId, "NCR自动隔离: " + ncrCode);
            log.info("NCR触发批次隔离: lotId={}, ncrCode={}", lotId, ncrCode);
        } catch (Exception e) {
            log.warn("NCR批次隔离失败(不阻断): lotId={}, ncrCode={}", lotId, ncrCode, e);
        }
    }

    /**
     * 安灯质量告警(best-effort)
     */
    private void callAndonIfNeeded(Long mesWoId, Long cardId, Long stepRecordId, String ncrCode) {
        if (mesWoId == null) {
            return;
        }
        try {
            proAndonService.callAndon(mesWoId, cardId, stepRecordId, "QUALITY", "NCR自动触发: " + ncrCode);
            log.info("NCR触发安灯告警: woId={}, ncrCode={}", mesWoId, ncrCode);
        } catch (Exception e) {
            log.warn("NCR安灯告警失败(不阻断): woId={}, ncrCode={}", mesWoId, ncrCode, e);
        }
    }

    /**
     * 处置: 返工分支 -> 创建返工单,状态 IN_REWORK
     */
    private Boolean handleReworkDisposition(NcrOrder order, String remark) {
        try {
            ncrReworkOrderService.deriveReworkOrder(order.getNcrId(), order.getWoId(),
                order.getDefectQty(), remark);
            int rows = baseMapper.update(null, Wrappers.<NcrOrder>lambdaUpdate()
                .set(NcrOrder::getStatus, STATUS_IN_REWORK)
                .eq(NcrOrder::getNcrId, order.getNcrId())
                .eq(NcrOrder::getStatus, STATUS_DISPOSITION));
            if (rows == 0) {
                throw new RuntimeException("NCR状态已变更,返工状态流转失败");
            }
            log.info("NCR处置返工: ncrId={}, 已创建返工单", order.getNcrId());
        } catch (Exception e) {
            log.error("NCR返工单创建失败: ncrId={}", order.getNcrId(), e);
            throw new RuntimeException("返工单创建失败: " + e.getMessage(), e);
        }
        return true;
    }

    /**
     * 处置: 报废分支 -> 批次报废,状态 CLOSED
     * <p>批次报废失败必须抛出回滚(不得 catch 后仍 CAS 到 CLOSED): 处置结果与批次动作同事务一致,
     * 失败时 NCR 停在 DISPOSITION, 用户可重试(审计 MES-NCR-02)。</p>
     */
    private Boolean handleScrapDisposition(NcrOrder order) {
        if (order.getLotId() != null) {
            try {
                ltLotService.scrap(order.getLotId(), "NCR(" + order.getNcrCode() + ")报废处置");
            } catch (Exception e) {
                log.error("NCR批次报废失败, 处置事务回滚: ncrId={}, lotId={}", order.getNcrId(), order.getLotId(), e);
                throw new ServiceException("批次报废失败, 处置已回滚(请处理后重试): " + e.getMessage());
            }
        }
        int rows = baseMapper.update(null, Wrappers.<NcrOrder>lambdaUpdate()
            .set(NcrOrder::getStatus, STATUS_CLOSED)
            .set(NcrOrder::getClosedTime, new Date())
            .eq(NcrOrder::getNcrId, order.getNcrId())
            .eq(NcrOrder::getStatus, STATUS_DISPOSITION));
        if (rows == 0) {
            throw new RuntimeException("NCR状态已变更,报废关闭失败");
        }
        log.info("NCR处置报废关闭: ncrId={}", order.getNcrId());
        return true;
    }

    /**
     * 处置: 让步接收分支 -> 批次放行,状态 CLOSED
     * <p>批次放行失败必须抛出回滚(不得 catch 后仍 CAS 到 CLOSED): 处置结果与批次动作同事务一致
     * (审计 MES-NCR-02)。</p>
     */
    private Boolean handleAcceptDisposition(NcrOrder order) {
        if (order.getLotId() != null) {
            try {
                ltLotService.release(order.getLotId());
            } catch (Exception e) {
                log.error("NCR批次放行失败, 处置事务回滚: ncrId={}, lotId={}", order.getNcrId(), order.getLotId(), e);
                throw new ServiceException("批次放行失败, 处置已回滚(请处理后重试): " + e.getMessage());
            }
        }
        int rows = baseMapper.update(null, Wrappers.<NcrOrder>lambdaUpdate()
            .set(NcrOrder::getStatus, STATUS_CLOSED)
            .set(NcrOrder::getClosedTime, new Date())
            .eq(NcrOrder::getNcrId, order.getNcrId())
            .eq(NcrOrder::getStatus, STATUS_DISPOSITION));
        if (rows == 0) {
            throw new RuntimeException("NCR状态已变更,让步接收关闭失败");
        }
        log.info("NCR处置让步接收关闭: ncrId={}", order.getNcrId());
        return true;
    }

    /**
     * 处置: 退货分支 -> 状态 CLOSED
     */
    private Boolean handleReturnDisposition(NcrOrder order) {
        int rows = baseMapper.update(null, Wrappers.<NcrOrder>lambdaUpdate()
            .set(NcrOrder::getStatus, STATUS_CLOSED)
            .set(NcrOrder::getClosedTime, new Date())
            .eq(NcrOrder::getNcrId, order.getNcrId())
            .eq(NcrOrder::getStatus, STATUS_DISPOSITION));
        if (rows == 0) {
            throw new RuntimeException("NCR状态已变更,退货关闭失败");
        }
        log.info("NCR处置退货关闭: ncrId={}", order.getNcrId());
        return true;
    }

    /**
     * 解析 NCR 关联的真实批次(审计 MES-NCR-01)。
     * <p>优先复用已建立的"批次↔IQC 检验单"关联; 否则仅当"该物料下唯一一个待检且尚未关联 IQC 的批次"
     * 时才建立关联(多候选视为不确定, 不做猜测, 避免把报废/放行作用到错误批次)。无法确定时记 warn
     * 显式声明"未关联批次", 不伪造 null 走旁路。</p>
     *
     * @param iqcId      IQC 检验单 ID(可空)
     * @param materialId 物料 ID(可空)
     * @param sourceType NCR 来源类型(仅用于日志)
     * @param sourceId   来源单据 ID(仅用于日志)
     * @return 解析到的批次, 无法确定时返回 null
     */
    private LtLot resolveLot(Long iqcId, Long materialId, String sourceType, Long sourceId) {
        if (iqcId != null) {
            List<LtLot> linked = ltLotMapper.selectList(Wrappers.<LtLot>lambdaQuery()
                .eq(LtLot::getIqcId, iqcId));
            if (linked != null && !linked.isEmpty()) {
                return linked.get(0);
            }
        }
        if (materialId != null) {
            List<LtLot> candidates = ltLotMapper.selectList(Wrappers.<LtLot>lambdaQuery()
                .eq(LtLot::getMaterialId, materialId)
                .eq(LtLot::getQcStatus, "PENDING")
                .isNull(LtLot::getIqcId));
            if (candidates != null && candidates.size() == 1) {
                LtLot lot = candidates.get(0);
                if (iqcId != null) {
                    // 建立批次↔IQC 关联: 使 IQC 结果回写(applyIqcResult)与 NCR 处置定位到同一批次
                    ltLotMapper.update(null, Wrappers.<LtLot>lambdaUpdate()
                        .set(LtLot::getIqcId, iqcId)
                        .eq(LtLot::getLotId, lot.getLotId()));
                    log.info("NCR建立批次↔IQC关联: iqcId={}, lotId={}", iqcId, lot.getLotId());
                }
                return lot;
            }
        }
        log.warn("NCR未关联批次(批次隔离/报废/放行动作不会执行): sourceType={}, sourceId={}, iqcId={}, materialId={}",
            sourceType, sourceId, iqcId, materialId);
        return null;
    }

    /**
     * 批次编码查询(仅用于冗余落库, 查不到返回 null)
     */
    private String lotCodeOf(Long lotId) {
        if (lotId == null) {
            return null;
        }
        LtLot lot = ltLotMapper.selectById(lotId);
        return lot == null ? null : lot.getLotCode();
    }

    /**
     * 取 IPQC 检验单的物料 ID(工序过程检验来源无物料入参)
     */
    private Long materialIdOfIpqc(Long ipqcId) {
        if (ipqcId == null) {
            return null;
        }
        com.nexus.mes.qc.domain.QcIpqc ipqc = qcIpqcMapper.selectById(ipqcId);
        return ipqc == null ? null : ipqc.getMaterialId();
    }

    /**
     * 生成NCR编码: 走系统自动编码规则 MES_NCR_CODE
     */
    private String generateNcrCode() {
        return autoCodeUtil.genSerialCode("MES_NCR_CODE", null);
    }

    /**
     * 构建NCR单查询条件包装器
     */
    private LambdaQueryWrapper<NcrOrder> buildQueryWrapper(NcrOrderBo bo) {
        return Wrappers.<NcrOrder>lambdaQuery()
            .eq(StringUtils.isNotBlank(bo.getNcrCode()), NcrOrder::getNcrCode, bo.getNcrCode())
            .eq(StringUtils.isNotBlank(bo.getStatus()), NcrOrder::getStatus, bo.getStatus())
            .eq(StringUtils.isNotBlank(bo.getSourceType()), NcrOrder::getSourceType, bo.getSourceType())
            .eq(bo.getWoId() != null, NcrOrder::getWoId, bo.getWoId())
            .eq(bo.getMaterialId() != null, NcrOrder::getMaterialId, bo.getMaterialId())
            .eq(bo.getSourceId() != null, NcrOrder::getSourceId, bo.getSourceId())
            .orderByDesc(NcrOrder::getCreateTime);
    }
}