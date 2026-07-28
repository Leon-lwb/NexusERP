package com.nexus.mes.ncr.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.lock.annotation.Lock4j;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.nexus.common.core.utils.StringUtils;
import com.nexus.common.json.utils.JsonUtils;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.common.satoken.utils.LoginHelper;
import com.nexus.mes.ncr.domain.NcrDefect;
import com.nexus.mes.ncr.domain.NcrOrder;
import com.nexus.mes.ncr.domain.bo.NcrOrderBo;
import com.nexus.mes.ncr.domain.vo.NcrOrderVo;
import com.nexus.mes.ncr.mapper.NcrDefectMapper;
import com.nexus.mes.ncr.mapper.NcrOrderMapper;
import com.nexus.mes.ncr.service.INcrDefectService;
import com.nexus.mes.ncr.service.INcrOrderService;
import com.nexus.mes.ncr.service.INcrReworkOrderService;
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
 * 同时触发批次隔离和安灯质量告警(均为 best-effort,不阻断主流程)。
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
    private final IProAndonService proAndonService;
    private final INcrReworkOrderService ncrReworkOrderService;
    private final INcrDefectService defectService;

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
        // 状态校验: 仅 OPEN/REVIEW 可编辑
        if (!STATUS_OPEN.equals(order.getStatus()) && !STATUS_REVIEW.equals(order.getStatus())) {
            throw new RuntimeException("当前状态不可编辑: " + order.getStatus());
        }
        // 更新可编辑字段
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
        order.setRemark(bo.getRemark());
        int rows = baseMapper.updateById(order);
        log.info("修改NCR单: ncrId={}", bo.getNcrId());
        return rows > 0;
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
        return createNcr(SOURCE_IQC, iqcId, null, materialId, null, defectInfo,
            null, null, null, null, null, null, null, null);
    }

    /**
     * 从IPQC检验自动创建NCR单
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public NcrOrderVo autoCreateFromIpqc(Long ipqcId, Long mesWoId, Long cardId, Long stepRecordId,
                                          Long processId, BigDecimal defectQty, String defectInfo) {
        return createNcr(SOURCE_IPQC, ipqcId, mesWoId, null, defectQty, defectInfo,
            null, cardId, stepRecordId, processId, null, null, null, null);
    }

    /**
     * 从OQC检验自动创建NCR单
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public NcrOrderVo autoCreateFromOqc(Long oqcId, Long soId, Long materialId, String defectInfo) {
        return createNcr(SOURCE_OQC, oqcId, null, materialId, null, defectInfo,
            null, null, null, null, null, null, null, null);
    }

    /**
     * 从工序出口检验自动创建NCR单
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public NcrOrderVo autoCreateFromExit(Long cardId, Long stepRecordId, Long mesWoId,
                                          BigDecimal defectQty, String defectInfo) {
        return createNcr(SOURCE_PROCESS, null, mesWoId, null, defectQty, defectInfo,
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
        order.setLotCode(null);
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
     * 解析失败时 log.warn 跳过,不阻断主流程。
     * </p>
     */
    private void parseAndInsertDefects(Long ncrId, String defectInfo) {
        if (StringUtils.isBlank(defectInfo)) {
            return;
        }
        try {
            List<Map> defectMaps = JsonUtils.parseArray(defectInfo, Map.class);
            if (defectMaps == null || defectMaps.isEmpty()) {
                return;
            }
            List<NcrDefect> defects = new ArrayList<>();
            for (Map map : defectMaps) {
                NcrDefect defect = new NcrDefect();
                defect.setNcrId(ncrId);
                defect.setDefectCode(map.get("code") != null ? map.get("code").toString() : null);
                if (map.get("qty") != null) {
                    defect.setDefectQty(new BigDecimal(map.get("qty").toString()));
                }
                defect.setRootCauseDesc(map.get("desc") != null ? map.get("desc").toString() : null);
                defects.add(defect);
            }
            if (!defects.isEmpty()) {
                defectService.batchInsertForNcr(ncrId, defects);
            }
        } catch (Exception e) {
            log.warn("解析defectInfo失败,跳过缺陷明细: ncrId={}, defectInfo={}", ncrId, defectInfo, e);
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
     */
    private Boolean handleScrapDisposition(NcrOrder order) {
        // 批次报废(best-effort)
        if (order.getLotId() != null) {
            try {
                ltLotService.scrap(order.getLotId(), "NCR(" + order.getNcrCode() + ")报废处置");
            } catch (Exception e) {
                log.warn("NCR批次报废失败(不阻断): lotId={}", order.getLotId(), e);
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
     */
    private Boolean handleAcceptDisposition(NcrOrder order) {
        // 批次放行(best-effort)
        if (order.getLotId() != null) {
            try {
                ltLotService.release(order.getLotId());
            } catch (Exception e) {
                log.warn("NCR批次放行失败(不阻断): lotId={}", order.getLotId(), e);
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