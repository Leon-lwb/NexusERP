package com.nexus.mes.pro.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.lang.Dict;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.nexus.common.core.utils.MapstructUtils;
import com.nexus.common.core.utils.SpringUtils;
import com.nexus.common.core.utils.StringUtils;
import com.nexus.common.core.domain.R;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.json.utils.JsonUtils;
import com.nexus.common.flowable.core.domain.ActStatus;
import com.nexus.common.flowable.helper.FlowableProcInsIdHelper;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.common.mq.message.WorkOrderReleaseMessage;
import com.nexus.common.tenant.helper.TenantHelper;
import com.nexus.mes.common.bridge.IMesErpBridgeService;
import com.nexus.mes.eq.domain.EqEquipment;
import com.nexus.mes.eq.mapper.EqEquipmentMapper;
import com.nexus.mes.flash.config.FwProperties;
import com.nexus.mes.flash.domain.Firmware;
import com.nexus.mes.flash.mapper.FirmwareMapper;
import com.nexus.mes.flash.service.IFirmwareService;
import com.nexus.mes.common.mq.MesMqSendService;
import com.nexus.mes.ncr.domain.NcrOrder;
import com.nexus.mes.ncr.domain.NcrReworkOrder;
import com.nexus.mes.ncr.mapper.NcrOrderMapper;
import com.nexus.mes.ncr.mapper.NcrReworkOrderMapper;
import com.nexus.mes.pro.domain.ProWorkOrder;
import com.nexus.mes.pro.domain.ProWorkOrderBomLine;
import com.nexus.mes.pro.domain.ProWorkOrderStep;
import com.nexus.mes.pro.domain.bo.ProWorkOrderBo;
import com.nexus.mes.pro.domain.bo.UpdateStepFirmwareBo;
import com.nexus.mes.pro.domain.vo.ProWorkOrderBomLineVo;
import com.nexus.mes.pro.domain.vo.ProWorkOrderStepVo;
import com.nexus.mes.pro.domain.vo.ProWorkOrderVo;
import com.nexus.mes.pro.mapper.ProWorkOrderBomLineMapper;
import com.nexus.mes.pro.mapper.ProWorkOrderMapper;
import com.nexus.mes.pro.mapper.ProWorkOrderStepMapper;
import com.nexus.mes.pro.service.IMesMbomService;
import com.nexus.mes.pro.domain.vo.MesMbomVo;
import com.nexus.mes.pro.domain.vo.MesMbomLineVo;
import com.nexus.mes.pro.service.IProWorkOrderService;
import com.nexus.mes.qc.domain.QcIpqc;
import com.nexus.mes.qc.mapper.QcIpqcMapper;
import com.nexus.mes.release.domain.vo.ReleasePackageVo;
import com.nexus.mes.release.domain.vo.ReleasePkgBomItemVo;
import com.nexus.mes.release.domain.vo.ReleasePkgConfigVo;
import com.nexus.mes.release.domain.vo.ReleasePkgFirmwareVo;
import com.nexus.mes.release.service.IReleasePackageService;
import com.nexus.workflow.domain.WfMyBusiness;
import com.nexus.workflow.service.IWfProcessService;
import com.nexus.workflow.service.impl.WfMyBusinessServiceImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * MES工单 Service 业务层实现
 * <p>属于 MES 生产管理模块，实现 工单 的业务逻辑，操作 MES 数据源并桥接 ERP 主数据。</p>
 *
 * @author Nexus
 */
@Slf4j
@RequiredArgsConstructor
@DS("mes")
@Service("proWorkOrderService")
public class ProWorkOrderServiceImpl implements IProWorkOrderService {

    private final ProWorkOrderMapper baseMapper;
    private final ProWorkOrderStepMapper stepMapper;
    private final ProWorkOrderBomLineMapper woBomLineMapper;
    private final IMesErpBridgeService mesErpBridgeService;
    private final IMesMbomService mesMbomService;
    private final IWfProcessService processService;
    private final WfMyBusinessServiceImpl wfMyBusinessServiceImpl;
    private final FlowableProcInsIdHelper procInsIdHelper;
    private final MesMqSendService mesMqSendService;
    private final EqEquipmentMapper equipmentMapper;
    /**
     * IPQC Mapper：completeWo 门禁查关键工序检验结果。
     * 直调 Mapper 而非 IQcIpqcService，避免与 QcIpqcServiceImpl(依赖 IProWorkOrderService) 形成循环依赖；
     * 本类 @DS("mes")，completeWo 经代理走 mes 上下文，直调 mes 库 Mapper 安全。
     */
    private final QcIpqcMapper qcIpqcMapper;
    /**
     * NCR 返工工单/不合格单 Mapper(P0-4)：返工工单完工时直调 Mapper 回写 NCR 状态。
     * 直调 Mapper 而非 INcrReworkOrderService，避免与 NcrReworkOrderServiceImpl(依赖 IProWorkOrderService) 形成循环依赖，同 QcIpqcMapper 模式。
     */
    private final NcrReworkOrderMapper ncrReworkOrderMapper;
    private final NcrOrderMapper ncrOrderMapper;
    /** 刷写配置: 工序打标关键字(nexus.mes.flash) */
    private final FwProperties fwProperties;
    /** 固件 Mapper: FLASH 工序自动带出物料默认 RELEASED 固件 */
    private final FirmwareMapper firmwareMapper;
    /** 发布包服务: 客户定制化配置匹配(固件映射/BOM选配/配置项覆写工单) */
    private final IReleasePackageService releasePackageService;

    @Override
    /**
     * 查询
     * @param bo bo
     * @param pageQuery pageQuery
     * @return 分页结果
     */
    public TableDataInfo<ProWorkOrderVo> selectPageList(ProWorkOrderBo bo, PageQuery pageQuery) {
        LambdaQueryWrapper<ProWorkOrder> lqw = buildQueryWrapper(bo);
        Page<ProWorkOrderVo> page = baseMapper.selectVoPage(pageQuery.build(), lqw);
        return TableDataInfo.build(page);
    }

    @Override
    /**
     * 查询
     * @param bo bo
     * @return 结果列表
     */
    public List<ProWorkOrderVo> selectList(ProWorkOrderBo bo) {
        return baseMapper.selectVoList(buildQueryWrapper(bo));
    }

    @Override
    /**
     * 查询
     * @param mesWoId MES工单ID
     * @return 返回视图对象
     */
    public ProWorkOrderVo selectById(Long mesWoId) {
        ProWorkOrderVo vo = baseMapper.selectVoById(mesWoId);
        if (vo != null) {
            vo.setSteps(selectStepsByWoId(mesWoId));
            // 加载MBOM快照明细
            vo.setBomLines(selectBomLinesByWoId(mesWoId));
            // 富集工艺路线信息(若routingId/bomId存在)
            enrichWorkOrderVo(vo);
        }
        return vo;
    }

    /**
     * 查询工单MBOM快照明细
     */
    private List<ProWorkOrderBomLineVo> selectBomLinesByWoId(Long mesWoId) {
        return woBomLineMapper.selectVoList(Wrappers.lambdaQuery(ProWorkOrderBomLine.class)
            .eq(ProWorkOrderBomLine::getMesWoId, mesWoId)
            .orderByAsc(ProWorkOrderBomLine::getSortOrder));
    }

    /**
     * 富集工单VO: 填充工艺路线编码/名称、BOM版本
     */
    private void enrichWorkOrderVo(ProWorkOrderVo vo) {
        if (vo.getRoutingId() != null) {
            Map<String, Object> routingHeader = mesErpBridgeService.getRoutingHeader(vo.getRoutingId());
            if (routingHeader != null) {
                vo.setRoutingCode((String) routingHeader.get("routing_code"));
                vo.setRoutingName((String) routingHeader.get("routing_name"));
            }
        }
        if (vo.getBomId() != null) {
            Map<String, Object> bomHeader = mesErpBridgeService.getBomHeader(vo.getBomId());
            if (bomHeader != null) {
                vo.setBomVersion((String) bomHeader.get("bom_version"));
            }
        }
        // P1-6: 填充关联设备编码/名称
        if (vo.getEquipmentId() != null) {
            EqEquipment eq = equipmentMapper.selectById(vo.getEquipmentId());
            if (eq != null) {
                vo.setEquipmentCode(eq.getEquipmentCode());
                vo.setEquipmentName(eq.getEquipmentName());
            }
        }
    }

    @Override
    /**
     * 查询
     * @param erpWoId ERP工单ID
     * @return 返回视图对象
     */
    public ProWorkOrderVo selectByErpWoId(Long erpWoId) {
        LambdaQueryWrapper<ProWorkOrder> lqw = Wrappers.<ProWorkOrder>lambdaQuery()
            .eq(ProWorkOrder::getErpWoId, erpWoId);
        return baseMapper.selectVoOne(lqw, false);
    }

    @Override
    /**
     * 查询
     * @param mesWoId MES工单ID
     * @return 结果列表
     */
    public List<ProWorkOrderStepVo> selectStepsByWoId(Long mesWoId) {
        LambdaQueryWrapper<ProWorkOrderStep> lqw = Wrappers.<ProWorkOrderStep>lambdaQuery()
            .eq(ProWorkOrderStep::getMesWoId, mesWoId)
            .orderByAsc(ProWorkOrderStep::getSequenceNum);
        return stepMapper.selectVoList(lqw);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    /**
     * 新增
     * @param bo bo
     * @return 受影响行数
     */
    public int insertByBo(ProWorkOrderBo bo) {
        ProWorkOrder entity = MapstructUtils.convert(bo, ProWorkOrder.class);
        if (StringUtils.isBlank(entity.getWoStatus())) {
            entity.setWoStatus(STATUS_CREATED);
        }
        int rows = baseMapper.insert(entity);
        // 回写雪花ID到BO，供Controller返回查询
        bo.setMesWoId(entity.getMesWoId());
        return rows;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    /**
     * 更新
     * @param bo bo
     * @return 受影响行数
     */
    public int updateByBo(ProWorkOrderBo bo) {
        ProWorkOrder entity = MapstructUtils.convert(bo, ProWorkOrder.class);
        return baseMapper.updateById(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    /**
     * 删除
     * @param ids 批量操作ID集合
     * @return 受影响行数
     */
    public int deleteByIds(Long[] ids) {
        // 仅新建或已取消状态的工单可删除
        List<ProWorkOrder> orders = baseMapper.selectBatchIds(List.of(ids));
        boolean hasNonDeletable = orders.stream()
            .anyMatch(o -> !STATUS_CREATED.equals(o.getWoStatus()) && !STATUS_CANCELLED.equals(o.getWoStatus()));
        if (hasNonDeletable) {
            throw new RuntimeException("仅新建或已取消状态的工单可删除");
        }
        return baseMapper.deleteBatchIds(List.of(ids));
    }

    @Override
    /**
     * 下达
     * @param mesWoId MES工单ID
     */
    public void release(Long mesWoId) {
        // 先读取工单信息(在事务外,通过桥接服务读取ERP数据)
        ProWorkOrder wo = baseMapper.selectById(mesWoId);
        if (wo == null) {
            throw new RuntimeException("工单不存在: mesWoId=" + mesWoId);
        }
        if (!STATUS_CREATED.equals(wo.getWoStatus())) {
            throw new RuntimeException("仅新建状态工单可下达");
        }
        // 事务外桥接读取ERP数据(MES来源工单的路由步骤+MBOM)
        List<Map<String, Object>> routingSteps = null;
        if (wo.getRoutingId() != null) {
            routingSteps = mesErpBridgeService.getRoutingSteps(wo.getRoutingId());
        }
        MesMbomVo mbom = null;
        if (wo.getBomId() != null) {
            mbom = mesMbomService.selectByBomId(wo.getBomId());
        }
        // 手动来源工单可选定发布包: 幂等生成工序时按包覆写(固件映射/BOM选配/FLASH配置)
        ReleasePackageVo releasePackage = null;
        if (wo.getReleasePackageId() != null) {
            releasePackage = releasePackageService.selectDetailById(wo.getReleasePackageId());
            if (releasePackage == null) {
                throw new ServiceException("工单关联的发布包不存在: releasePackageId=" + wo.getReleasePackageId());
            }
        }
        // 事务内: 幂等快照步骤/BOM -> FLASH门禁 -> CAS状态冻结
        // 经 AOP 代理调用以使 @Transactional 生效(同类直调绕过代理, 事务实际不生效 M2)
        SpringUtils.getAopProxy(this).doReleaseAndSnapshot(mesWoId, wo, routingSteps, mbom, releasePackage);
    }

    /**
     * 事务内下达 + 幂等快照(ERP来源工单可能已在createFromErpMessage时快照,此处幂等保护)
     * <p>顺序: 幂等快照(步骤/MBOM) -> FLASH门禁 -> CAS状态冻结。
     * 快照先于CAS: 手动来源工单的工序在此处才生成, 门禁在校验时点须已可见全部工序;
     * 门禁先于CAS: 拦截时工单状态未变, 生产负责人修正刷写绑定后可重新下达。</p>
     */
    @Transactional(rollbackFor = Exception.class)
    public void doReleaseAndSnapshot(Long mesWoId, ProWorkOrder wo,
                                     List<Map<String, Object>> routingSteps, MesMbomVo mbom,
                                     ReleasePackageVo releasePackage) {
        // 幂等快照步骤(若尚未生成)
        Long stepCount = stepMapper.selectCount(Wrappers.lambdaQuery(ProWorkOrderStep.class)
            .eq(ProWorkOrderStep::getMesWoId, mesWoId));
        if ((stepCount == null || stepCount == 0) && routingSteps != null && !routingSteps.isEmpty()) {
            generateStepsFromRouting(wo, routingSteps, releasePackage);
        }
        // 幂等快照MBOM(若尚未生成)
        Long bomLineCount = woBomLineMapper.selectCount(Wrappers.lambdaQuery(ProWorkOrderBomLine.class)
            .eq(ProWorkOrderBomLine::getMesWoId, mesWoId));
        if ((bomLineCount == null || bomLineCount == 0) && mbom != null && mbom.getLines() != null && !mbom.getLines().isEmpty()) {
            snapshotBomLines(wo, mbom, releasePackage);
        }
        // FLASH门禁: 每个刷写工序必须已绑定刷写内容(固件/配置/池), 未绑定的下达拦截
        assertFlashStepsBound(mesWoId);
        // CAS更新状态(冻结固件/BOM绑定, 下达后不可再改)
        int rows = baseMapper.update(null, Wrappers.<ProWorkOrder>lambdaUpdate()
            .set(ProWorkOrder::getWoStatus, STATUS_RELEASED)
            .eq(ProWorkOrder::getMesWoId, mesWoId)
            .eq(ProWorkOrder::getWoStatus, STATUS_CREATED));
        if (rows == 0) {
            throw new RuntimeException("工单状态已变更或不存在，仅新建状态工单可下达");
        }
        // 手动来源工单绑定发布包: 回填发布包快照字段(编码/版本/配置项, ERP来源工单创建时已落)
        if (releasePackage != null && wo.getReleasePackageId() != null) {
            baseMapper.update(null, Wrappers.<ProWorkOrder>lambdaUpdate()
                .set(ProWorkOrder::getReleasePackageCode, releasePackage.getPackageCode())
                .set(ProWorkOrder::getReleasePackageVer, releasePackage.getVersion())
                .set(ProWorkOrder::getReleaseConfig, buildReleaseConfigSnapshot(releasePackage))
                .eq(ProWorkOrder::getMesWoId, mesWoId));
        }
        log.info("MES工单下达成功: mesWoId={}, releasePackage={}", mesWoId,
            releasePackage != null ? releasePackage.getPackageCode() + " v" + releasePackage.getVersion() : "无");
    }

    /**
     * FLASH门禁: 校验工单下所有刷写工序均已绑定刷写内容(单类型 contentRefId 或多类型 stepFlashConfig)
     * <p>挂在 release() 直达路径与 submit() 审批提交前双入口;
     * 审批通过回调(afterFlowHandle)直接置 RELEASED 不再重复校验, 由提交时点校验兜底。</p>
     */
    private void assertFlashStepsBound(Long mesWoId) {
        List<ProWorkOrderStep> flashSteps = stepMapper.selectList(Wrappers.<ProWorkOrderStep>lambdaQuery()
            .eq(ProWorkOrderStep::getMesWoId, mesWoId)
            .eq(ProWorkOrderStep::getStepType, "FLASH"));
        if (flashSteps == null || flashSteps.isEmpty()) {
            return;
        }
        StringBuilder unbound = new StringBuilder();
        for (ProWorkOrderStep step : flashSteps) {
            boolean bound = step.getContentRefId() != null || StringUtils.isNotBlank(step.getStepFlashConfig());
            if (!bound) {
                if (unbound.length() > 0) {
                    unbound.append("、");
                }
                unbound.append("工序[").append(step.getSequenceNum()).append("-").append(step.getProcessName()).append("]");
            }
        }
        if (unbound.length() > 0) {
            throw new ServiceException("以下刷写工序未绑定刷写内容(固件/配置), 请先确认后再下达: " + unbound);
        }
    }

    /**
     * 更新工单工序的固件版本(生产负责人在工单下达前确认/更改, 下达后冻结)
     * <p>
     * 校验链: 工单 CREATED -> 步骤属于该工单且为 FLASH 工序 -> 固件 RELEASED -> 固件适用物料与工单产品一致。
     * 更新单类型绑定(contentRefType/contentRefId, 门禁依据)并置 firmware_source=MANUAL;
     * 若步骤存在多类型刷写绑定JSON(stepFlashConfig), 其中的固件项同步改写——
     * 门禁解析 stepFlashConfig 优先于单类型绑定, 两者不一致会导致固件刷写漏检。
     * </p>
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateStepFirmware(Long mesWoId, UpdateStepFirmwareBo bo) {
        ProWorkOrder wo = baseMapper.selectById(mesWoId);
        if (wo == null) {
            throw new ServiceException("工单不存在: mesWoId=" + mesWoId);
        }
        if (!STATUS_CREATED.equals(wo.getWoStatus())) {
            throw new ServiceException("仅新建状态工单可更改工序固件, 当前: " + wo.getWoStatus());
        }
        ProWorkOrderStep step = stepMapper.selectById(bo.getStepId());
        if (step == null || !mesWoId.equals(step.getMesWoId())) {
            throw new ServiceException("工序步骤不存在或不属于该工单: stepId=" + bo.getStepId());
        }
        if (!"FLASH".equals(step.getStepType())) {
            throw new ServiceException("非刷写工序不可绑定固件: " + step.getProcessName());
        }
        Firmware fw = firmwareMapper.selectById(bo.getFirmwareId());
        if (fw == null) {
            throw new ServiceException("固件不存在: firmwareId=" + bo.getFirmwareId());
        }
        if (!IFirmwareService.STATUS_RELEASED.equals(fw.getStatus())) {
            throw new ServiceException("仅已发布(RELEASED)固件可用于生产, 固件 " + fw.getFirmwareCode() + " 当前: " + fw.getStatus());
        }
        if (wo.getMaterialId() != null && !wo.getMaterialId().equals(fw.getMaterialId())) {
            throw new ServiceException("固件 " + fw.getFirmwareCode() + " 适用物料与工单产品不一致, 不可绑定");
        }
        String flashConfig = rewriteFlashConfigFirmware(step.getStepFlashConfig(), bo.getFirmwareId());
        int rows = stepMapper.update(null, Wrappers.<ProWorkOrderStep>lambdaUpdate()
            .set(ProWorkOrderStep::getContentRefType, "FIRMWARE")
            .set(ProWorkOrderStep::getContentRefId, bo.getFirmwareId())
            .set(ProWorkOrderStep::getFirmwareSource, "MANUAL")
            .set(ProWorkOrderStep::getStepFlashConfig, flashConfig)
            .eq(ProWorkOrderStep::getStepId, bo.getStepId())
            .eq(ProWorkOrderStep::getMesWoId, mesWoId));
        if (rows == 0) {
            throw new ServiceException("工序固件更新失败, 步骤状态已变更: stepId=" + bo.getStepId());
        }
        log.info("工单工序固件更改: mesWoId={}, stepId={}, process={}, firmware={}({}), 来源=MANUAL",
            mesWoId, bo.getStepId(), step.getProcessName(), fw.getFirmwareCode(), fw.getVersion());
    }

    /**
     * 改写多类型刷写绑定JSON中的固件项(其余刷写类型项保持不变, 固件项置顶)
     *
     * @return 改写后的JSON; 原绑定为空时返回 null(单类型绑定已表达, 无需多类型JSON)
     */
    private String rewriteFlashConfigFirmware(String stepFlashConfig, Long firmwareId) {
        if (StringUtils.isBlank(stepFlashConfig)) {
            return null;
        }
        List<Dict> entries;
        try {
            entries = JsonUtils.parseArrayMap(stepFlashConfig);
        } catch (Exception e) {
            log.warn("工序多类型刷写绑定JSON解析失败, 忽略改写: {}", stepFlashConfig, e);
            return null;
        }
        if (entries == null || entries.isEmpty()) {
            return null;
        }
        List<Map<String, Object>> result = new ArrayList<>();
        Map<String, Object> fwEntry = new HashMap<>();
        fwEntry.put("flashTypeCode", "firmware_file");
        fwEntry.put("contentRefId", firmwareId);
        result.add(fwEntry);
        for (Dict entry : entries) {
            if ("firmware_file".equals(entry.getStr("flashTypeCode"))) {
                continue;
            }
            Map<String, Object> item = new HashMap<>();
            item.put("flashTypeCode", entry.getStr("flashTypeCode"));
            item.put("contentRefId", entry.getLong("contentRefId"));
            result.add(item);
        }
        return JsonUtils.toJsonString(result);
    }

    /**
     * 消费ERP工单下达消息，创建MES工单及工序
     * <p>
     * 架构设计：桥接查询（跨erp数据源）在事务外执行，DB写入在事务内执行，
     * 避免 @Transactional 绑定 mes 数据源后桥接查询无法切换到 erp 的问题。
     */
    @Override
    public void createFromErpMessage(WorkOrderReleaseMessage message) {
        log.info("开始创建MES工单: erpWoId={}, woNo={}", message.getWoId(), message.getWoNo());

        // 1. 幂等校验：通过erpWoId查重
        ProWorkOrderVo existing = selectByErpWoId(message.getWoId());
        if (existing != null) {
            log.info("MES工单已存在,跳过创建: erpWoId={}, mesWoId={}", message.getWoId(), existing.getMesWoId());
            return;
        }

        // 2. 【事务外】桥接查询所有ERP主数据（只读，跨erp数据源）
        Long unitId = null;
        String unitName = null;
        if (message.getMaterialId() != null) {
            Map<String, Object> material = mesErpBridgeService.getMaterial(message.getMaterialId());
            if (material != null) {
                unitId = toLong(material.get("base_uom_id"));
                if (unitId != null) {
                    unitName = mesErpBridgeService.getUnitName(unitId);
                }
            }
        }

        String workshopName = null;
        if (message.getWorkshopId() != null) {
            Map<String, Object> workshop = mesErpBridgeService.getWorkshop(message.getWorkshopId());
            if (workshop != null) {
                workshopName = (String) workshop.get("workshop_name");
            }
        }

        Long routingId = message.getRoutingId();
        if (routingId == null && message.getMaterialId() != null) {
            routingId = mesErpBridgeService.getDefaultRoutingId(message.getMaterialId());
            log.info("通过桥接服务查询默认工艺路线: materialId={}, routingId={}", message.getMaterialId(), routingId);
        }

        List<Map<String, Object>> routingSteps = null;
        if (routingId != null) {
            routingSteps = mesErpBridgeService.getRoutingSteps(routingId);
        }

        // 读取MBOM明细(若BOM已编制MBOM)
        MesMbomVo mbom = null;
        if (message.getBomId() != null) {
            mbom = mesMbomService.selectByBomId(message.getBomId());
        }

        // 匹配客户定制化发布包(事务外加载):
        // 匹配规则: 客户+物料+客户产品代号精确 -> 客户+物料默认包, 仅取 RELEASED 且有效期内;
        // 无匹配返回 null, 工单按标准工艺路线/标准BOM生成(传统方式, 完全兼容)
        ReleasePackageVo releasePackage = null;
        if (message.getClientId() != null && message.getMaterialId() != null) {
            releasePackage = releasePackageService.matchPackage(
                message.getClientId(), message.getMaterialId(), message.getCustomerProductCode());
            if (releasePackage != null) {
                log.info("匹配到客户定制化发布包: woNo={}, clientId={}, packageCode={}, version={}",
                    message.getWoNo(), message.getClientId(), releasePackage.getPackageCode(), releasePackage.getVersion());
            }
        }

        // 3. 【事务内】创建MES工单及工序及BOM快照（仅写mes库; 经 AOP 代理调用以使事务生效）
        SpringUtils.getAopProxy(this).doCreateWorkOrderAndSteps(message, unitId, unitName, workshopName, routingId, routingSteps, mbom, releasePackage);
    }

    /**
     * 事务内创建工单及工序（仅操作mes数据源）
     *
     * @param releasePackage 匹配到的客户定制化发布包(可空, 空则按标准工艺路线/标准BOM生成)
     */
    @Transactional(rollbackFor = Exception.class)
    public void doCreateWorkOrderAndSteps(WorkOrderReleaseMessage message,
                                          Long unitId, String unitName,
                                          String workshopName, Long routingId,
                                          List<Map<String, Object>> routingSteps,
                                          MesMbomVo mbom,
                                          ReleasePackageVo releasePackage) {
        // 创建MES工单主记录
        ProWorkOrder workOrder = new ProWorkOrder();
        workOrder.setErpWoId(message.getWoId());
        workOrder.setWoNo(message.getWoNo());
        workOrder.setMaterialId(message.getMaterialId());
        workOrder.setMaterialCode(message.getMaterialCode());
        workOrder.setMaterialName(message.getMaterialName());
        workOrder.setPlannedQty(message.getPlannedQty());
        workOrder.setUnitId(unitId);
        workOrder.setUnitName(unitName);
        workOrder.setPlanStartDate(message.getPlannedStartDate());
        workOrder.setPlanEndDate(message.getPlannedEndDate());
        workOrder.setWorkshopId(message.getWorkshopId());
        workOrder.setWorkshopName(workshopName);
        workOrder.setBomId(message.getBomId());
        workOrder.setRoutingId(routingId);
        workOrder.setWoStatus(STATUS_CREATED);
        workOrder.setPriority(5);
        // 发布包快照落工单(可追溯): 工单按哪个包的哪个版本生成
        if (releasePackage != null) {
            workOrder.setReleasePackageId(releasePackage.getPackageId());
            workOrder.setReleasePackageCode(releasePackage.getPackageCode());
            workOrder.setReleasePackageVer(releasePackage.getVersion());
            workOrder.setReleaseConfig(buildReleaseConfigSnapshot(releasePackage));
        }
        baseMapper.insert(workOrder);
        log.info("MES工单创建成功: mesWoId={}, woNo={}, releasePackage={}",
            workOrder.getMesWoId(), workOrder.getWoNo(),
            releasePackage != null ? releasePackage.getPackageCode() + " v" + releasePackage.getVersion() : "无");

        // 按工艺路线步骤生成工序(发布包固件映射覆写固件绑定/注入多类型刷写配置)
        if (routingSteps != null && !routingSteps.isEmpty()) {
            generateStepsFromRouting(workOrder, routingSteps, releasePackage);
        } else {
            log.warn("未找到工艺路线或无步骤,跳过工序生成: woNo={}, routingId={}", message.getWoNo(), routingId);
        }

        // MBOM快照: 将MBOM明细快照到 pro_work_order_bom_line(隔离后续MBOM/ERP变更); 发布包BOM选配覆写(REPLACE/ADD/REMOVE)
        if (mbom != null && mbom.getLines() != null && !mbom.getLines().isEmpty()) {
            snapshotBomLines(workOrder, mbom, releasePackage);
        }

        log.info("MES工单及工序创建完成: mesWoId={}, woNo={}", workOrder.getMesWoId(), workOrder.getWoNo());
    }

    /**
     * 按ERP工艺路线步骤生成MES工单工序（事务内调用，routingSteps已预加载）
     * <p>
     * 刷写内容绑定优先级(仅FLASH工序):
     * ① 发布包固件映射(routingStepId 精确 -> processId 宽泛, 客户定制覆写, 映射固件须仍为 RELEASED 且物料匹配, 否则 fail-loudly);
     * ② 工艺路线步骤默认固件(bd_routing_step.firmware_id, 不限工序类型, 保持原行为);
     * ③ 物料默认 RELEASED 固件(全局兜底)。
     * 发布包 FLASH 组配置项按工序注入 stepFlashConfig(非固件类型: config_file/mac/sn/external_*)。
     * </p>
     */
    private void generateStepsFromRouting(ProWorkOrder workOrder, List<Map<String, Object>> routingSteps,
                                          ReleasePackageVo releasePackage) {
        List<ProWorkOrderStep> steps = new ArrayList<>();
        for (Map<String, Object> rs : routingSteps) {
            ProWorkOrderStep step = new ProWorkOrderStep();
            step.setMesWoId(workOrder.getMesWoId());
            step.setSequenceNum(toInt(rs.get("sequence_num")));
            step.setProcessId(toLong(rs.get("process_id")));
            step.setProcessCode((String) rs.get("process_code"));
            step.setProcessName((String) rs.get("process_name"));
            step.setWorkcenterId(toLong(rs.get("workstation_id")));
            step.setWorkcenterCode((String) rs.get("workstation_code"));
            // 工序打标: 按 process_code 关键字落 step_type (FLASH/TEST/GENERAL), 计划员可改标
            step.setStepType(resolveStepType(step.getProcessCode()));
            Long routingStepId = toLong(rs.get("step_id"));

            Long firmwareId = null;
            String firmwareSource = null;
            if ("FLASH".equals(step.getStepType())) {
                // ① 发布包固件映射(客户定制, 优先级最高)
                if (releasePackage != null) {
                    firmwareId = matchFirmwareFromPackage(
                        releasePackage.getFirmwareMappings(), routingStepId, step.getProcessId());
                    if (firmwareId != null) {
                        assertFirmwareUsable(firmwareId, workOrder, releasePackage);
                        firmwareSource = "PACKAGE";
                    }
                }
                // ② 工艺路线步骤默认固件
                if (firmwareId == null) {
                    firmwareId = toLong(rs.get("firmware_id"));
                    if (firmwareId != null) {
                        firmwareSource = "ROUTING";
                    }
                }
                // ③ 物料默认固件(全局兜底)
                if (firmwareId == null && workOrder.getMaterialId() != null) {
                    Firmware defaultFw = firmwareMapper.selectOne(Wrappers.<Firmware>lambdaQuery()
                        .eq(Firmware::getMaterialId, workOrder.getMaterialId())
                        .eq(Firmware::getIsDefault, IFirmwareService.IS_DEFAULT_YES)
                        .eq(Firmware::getStatus, IFirmwareService.STATUS_RELEASED)
                        .last("LIMIT 1"));
                    if (defaultFw != null) {
                        firmwareId = defaultFw.getFirmwareId();
                        firmwareSource = "MATERIAL_DEFAULT";
                        log.info("FLASH工序自动带出物料默认固件: woNo={}, processCode={}, firmwareId={}", workOrder.getWoNo(), step.getProcessCode(), firmwareId);
                    }
                }
            } else {
                // 非FLASH工序保持原行为: 工艺路线步骤显式固件仍然生效
                firmwareId = toLong(rs.get("firmware_id"));
                if (firmwareId != null) {
                    firmwareSource = "ROUTING";
                }
            }
            if (firmwareId != null) {
                step.setContentRefType("FIRMWARE");
                step.setContentRefId(firmwareId);
                step.setFirmwareSource(firmwareSource);
            }

            // 发布包 FLASH 组配置项注入多类型刷写绑定(JSON数组, 固件绑定同时写入避免门禁漏检)
            if (releasePackage != null && "FLASH".equals(step.getStepType())) {
                String flashConfig = buildStepFlashConfig(releasePackage.getConfigItems(), step.getProcessId(), firmwareId);
                if (flashConfig != null) {
                    step.setStepFlashConfig(flashConfig);
                }
            }

            step.setPlannedQty(workOrder.getPlannedQty());
            step.setStepStatus(STEP_PENDING);
            step.setIsKeyProcess(toInt(rs.get("is_key_process")));
            steps.add(step);
        }

        // 批量插入工序步骤
        for (ProWorkOrderStep step : steps) {
            stepMapper.insert(step);
        }
        log.info("工序步骤生成完成: mesWoId={}, stepCount={}, releasePackage={}",
            workOrder.getMesWoId(), steps.size(), releasePackage != null ? releasePackage.getPackageCode() : "无");
    }

    /**
     * 从发布包固件映射中匹配固件ID: routingStepId 精确匹配优先, processId 宽泛匹配兜底
     * <p>processId 兜底不要求映射行的 routingStepId 为空: 工艺路线升版后 stepId 变化,
     * 按工序匹配仍能命中(客户定制意图挂在工序维度)。</p>
     */
    private Long matchFirmwareFromPackage(List<ReleasePkgFirmwareVo> mappings,
                                           Long routingStepId, Long processId) {
        if (mappings == null || mappings.isEmpty()) {
            return null;
        }
        if (routingStepId != null) {
            for (ReleasePkgFirmwareVo m : mappings) {
                if (routingStepId.equals(m.getRoutingStepId())) {
                    return m.getFirmwareId();
                }
            }
        }
        if (processId != null) {
            for (ReleasePkgFirmwareVo m : mappings) {
                if (processId.equals(m.getProcessId())) {
                    return m.getFirmwareId();
                }
            }
        }
        return null;
    }

    /**
     * 校验发布包映射的固件在工单生成时点仍可用(RELEASED + 物料匹配)。
     * <p>fail-loudly: 定制固件失效时拒绝静默回退到默认固件——定制工单刷错固件的代价远大于停线排查。</p>
     */
    private void assertFirmwareUsable(Long firmwareId, ProWorkOrder workOrder, ReleasePackageVo releasePackage) {
        Firmware fw = firmwareMapper.selectById(firmwareId);
        if (fw == null) {
            throw new ServiceException(String.format(
                "发布包[%s v%s]映射的固件不存在(firmwareId=%s), 请更新发布包后再生成工单",
                releasePackage.getPackageCode(), releasePackage.getVersion(), firmwareId));
        }
        if (!IFirmwareService.STATUS_RELEASED.equals(fw.getStatus())) {
            throw new ServiceException(String.format(
                "发布包[%s v%s]映射的固件 %s 当前状态为 %s(非已发布), 请更新发布包后再生成工单",
                releasePackage.getPackageCode(), releasePackage.getVersion(), fw.getFirmwareCode(), fw.getStatus()));
        }
        if (workOrder.getMaterialId() != null && !workOrder.getMaterialId().equals(fw.getMaterialId())) {
            throw new ServiceException(String.format(
                "发布包[%s v%s]映射的固件 %s 适用物料与工单产品不一致, 请更新发布包后再生成工单",
                releasePackage.getPackageCode(), releasePackage.getVersion(), fw.getFirmwareCode()));
        }
    }

    /**
     * 从发布包 FLASH 组配置项构建工序的多类型刷写绑定JSON
     * <p>
     * 格式: [{"flashTypeCode":"firmware_file","contentRefId":123},{"flashTypeCode":"config_file","contentRefId":456}]。
     * config_key=刷写类型编码(mes_flash_type.type_code), config_value=内容源记录ID。
     * ① 固件绑定同时写入数组首位——门禁解析 stepFlashConfig 优先于单类型 contentRefId,
     *    若只写配置项会导致固件绑定漏检;
     * ② flashTypeCode=firmware_file 的配置项跳过(固件绑定走固件映射表, 避免双来源冲突);
     * ③ FLASH 组配置项须绑定工序(processId), 全局项不注入步骤仅随 releaseConfig 快照。
     * </p>
     *
     * @return 注入JSON; 无配置项命中返回 null
     */
    private String buildStepFlashConfig(List<ReleasePkgConfigVo> configs, Long processId, Long firmwareId) {
        if (configs == null || configs.isEmpty()) {
            return null;
        }
        List<Map<String, Object>> entries = new ArrayList<>();
        if (firmwareId != null) {
            Map<String, Object> fwEntry = new HashMap<>();
            fwEntry.put("flashTypeCode", "firmware_file");
            fwEntry.put("contentRefId", firmwareId);
            entries.add(fwEntry);
        }
        for (ReleasePkgConfigVo c : configs) {
            if (!"FLASH".equals(c.getConfigGroup())) {
                continue;
            }
            if (c.getProcessId() == null || !c.getProcessId().equals(processId)) {
                continue;
            }
            if ("firmware_file".equals(c.getConfigKey())) {
                continue;
            }
            Long contentRefId;
            try {
                contentRefId = Long.parseLong(c.getConfigValue());
            } catch (NumberFormatException e) {
                log.warn("发布包FLASH配置项值非内容源ID(数字), 跳过注入: key={}, value={}", c.getConfigKey(), c.getConfigValue());
                continue;
            }
            Map<String, Object> entry = new HashMap<>();
            entry.put("flashTypeCode", c.getConfigKey());
            entry.put("contentRefId", contentRefId);
            entries.add(entry);
        }
        // 仅固件项(无配置项命中)时无需写多类型JSON, 单类型绑定已表达
        if (entries.size() <= (firmwareId != null ? 1 : 0)) {
            return null;
        }
        return JsonUtils.toJsonString(entries);
    }

    /**
     * 构建发布包配置项快照JSON(全量, 含非FLASH组), 落 pro_work_order.release_config 供工位/报表消费
     */
    private String buildReleaseConfigSnapshot(ReleasePackageVo releasePackage) {
        if (releasePackage.getConfigItems() == null || releasePackage.getConfigItems().isEmpty()) {
            return null;
        }
        List<Map<String, Object>> snapshot = new ArrayList<>();
        for (ReleasePkgConfigVo c : releasePackage.getConfigItems()) {
            Map<String, Object> item = new HashMap<>();
            item.put("configGroup", c.getConfigGroup());
            item.put("configKey", c.getConfigKey());
            item.put("configValue", c.getConfigValue());
            item.put("configType", c.getConfigType());
            item.put("processId", c.getProcessId());
            item.put("processCode", c.getProcessCode());
            snapshot.add(item);
        }
        return JsonUtils.toJsonString(snapshot);
    }

    /**
     * 工序打标: 按 process_code 关键字匹配 nexus.mes.flash.{flash|test}-keywords 落 step_type。
     * FLASH: 刷写工序; TEST: 测试工序; 未命中 GENERAL(不阻塞)。
     */
    private String resolveStepType(String processCode) {
        if (StringUtils.isBlank(processCode)) {
            return "GENERAL";
        }
        String upper = processCode.toUpperCase();
        if (fwProperties.getFlashKeywords() != null
            && fwProperties.getFlashKeywords().stream().anyMatch(upper::contains)) {
            return "FLASH";
        }
        if (fwProperties.getTestKeywords() != null
            && fwProperties.getTestKeywords().stream().anyMatch(upper::contains)) {
            return "TEST";
        }
        return "GENERAL";
    }

    /**
     * 快照MBOM明细到工单BOM快照表(事务内调用, MBOM行已含富集信息)
     * <p>发布包BOM选配(REPLACE/ADD/REMOVE)在快照副本上覆写, 不修改MBOM主数据。</p>
     */
    private void snapshotBomLines(ProWorkOrder workOrder, MesMbomVo mbom, ReleasePackageVo releasePackage) {
        // 深拷贝行副本: 选配覆写(REPLACE 原位改写行对象)不得泄漏修改 MBOM 查询结果
        List<MesMbomLineVo> lines = new ArrayList<>();
        for (MesMbomLineVo line : mbom.getLines()) {
            lines.add(BeanUtil.toBean(line, MesMbomLineVo.class));
        }
        if (lines.isEmpty()) {
            return;
        }
        // 发布包BOM选配覆写(仅在工单快照上生效)
        if (releasePackage != null) {
            applyBomItems(lines, releasePackage.getBomItems());
        }
        List<ProWorkOrderBomLine> snapshots = new ArrayList<>();
        for (int i = 0; i < lines.size(); i++) {
            MesMbomLineVo line = lines.get(i);
            ProWorkOrderBomLine snapshot = new ProWorkOrderBomLine();
            snapshot.setMesWoId(workOrder.getMesWoId());
            snapshot.setMbomLineId(line.getMbomLineId());
            snapshot.setBomId(mbom.getBomId());
            snapshot.setBomDetailId(line.getBomDetailId());
            snapshot.setChildMaterialId(line.getChildMaterialId());
            snapshot.setChildMaterialCode(line.getChildMaterialCode());
            snapshot.setChildMaterialName(line.getChildMaterialName());
            snapshot.setQuantityPerParent(line.getQuantityPerParent());
            snapshot.setLossRate(line.getLossRate() != null ? line.getLossRate() : BigDecimal.ZERO);
            // 计算含损耗总用量
            BigDecimal qty = line.getQuantityPerParent() != null ? line.getQuantityPerParent() : BigDecimal.ZERO;
            BigDecimal loss = line.getLossRate() != null ? line.getLossRate() : BigDecimal.ZERO;
            snapshot.setTotalQuantity(qty.multiply(BigDecimal.ONE.add(loss.divide(new BigDecimal("100"), 6, java.math.RoundingMode.HALF_UP))));
            snapshot.setRoutingStepId(line.getRoutingStepId());
            snapshot.setSequenceNum(line.getRoutingStepSequence());
            snapshot.setProcessId(line.getProcessId());
            snapshot.setProcessCode(line.getProcessCode());
            snapshot.setProcessName(line.getProcessName());
            snapshot.setWorkstationId(line.getWorkstationId());
            snapshot.setWorkstationCode(line.getWorkstationCode());
            snapshot.setWorkstationName(line.getWorkstationName());
            snapshot.setOperationContent(line.getOperationContent());
            snapshot.setBackflushFlag(line.getBackflushFlag());
            snapshot.setSortOrder(line.getSortOrder() != null ? line.getSortOrder() : i + 1);
            snapshot.setRemark(line.getRemark());
            snapshots.add(snapshot);
        }
        for (ProWorkOrderBomLine snapshot : snapshots) {
            woBomLineMapper.insert(snapshot);
        }
        log.info("MBOM快照生成完成: mesWoId={}, bomLineCount={}, releasePackage={}",
            workOrder.getMesWoId(), snapshots.size(), releasePackage != null ? releasePackage.getPackageCode() : "无");
    }

    /**
     * 应用发布包BOM选配(REPLACE/ADD/REMOVE)到MBOM快照行副本
     * <p>
     * 定位方式: bomDetailId 精确指定优先, 否则按原物料 childMaterialId 定位——
     * 按子件物料定位抗 BOM/MBOM 版本变更, 语义为"把某物料换成/移除某物料"。
     * 覆写行追加备注标记"发布包选配", 供前端展示与追溯。
     * </p>
     */
    private void applyBomItems(List<MesMbomLineVo> lines, List<ReleasePkgBomItemVo> items) {
        if (items == null || items.isEmpty()) {
            return;
        }
        for (ReleasePkgBomItemVo item : items) {
            String action = item.getActionType();
            switch (action) {
                case "REPLACE":
                    boolean replaced = false;
                    for (MesMbomLineVo line : lines) {
                        if (!matchBomLine(line, item)) {
                            continue;
                        }
                        line.setChildMaterialId(item.getTargetMaterialId());
                        line.setChildMaterialCode(item.getTargetMaterialCode());
                        line.setChildMaterialName(item.getTargetMaterialName());
                        if (item.getQuantityPerParent() != null) {
                            line.setQuantityPerParent(item.getQuantityPerParent());
                        }
                        line.setRemark(appendRemark(line.getRemark(),
                            "发布包选配(替换: " + item.getChildMaterialCode() + " -> " + item.getTargetMaterialCode() + ")"));
                        replaced = true;
                    }
                    if (!replaced) {
                        log.warn("发布包BOM选配[REPLACE]未命中快照行: 原物料={}, bomDetailId={}",
                            item.getChildMaterialCode(), item.getBomDetailId());
                    }
                    break;
                case "ADD":
                    MesMbomLineVo newLine = new MesMbomLineVo();
                    newLine.setChildMaterialId(item.getTargetMaterialId());
                    newLine.setChildMaterialCode(item.getTargetMaterialCode());
                    newLine.setChildMaterialName(item.getTargetMaterialName());
                    newLine.setQuantityPerParent(item.getQuantityPerParent());
                    newLine.setLossRate(BigDecimal.ZERO);
                    newLine.setSortOrder(lines.size() + 1);
                    newLine.setRemark(appendRemark(null, "发布包选配(新增)"));
                    lines.add(newLine);
                    break;
                case "REMOVE":
                    boolean removed = lines.removeIf(line -> matchBomLine(line, item));
                    if (!removed) {
                        log.warn("发布包BOM选配[REMOVE]未命中快照行: 原物料={}, bomDetailId={}",
                            item.getChildMaterialCode(), item.getBomDetailId());
                    }
                    break;
                default:
                    log.warn("发布包BOM选配操作类型未知, 跳过: {}", action);
            }
        }
    }

    /**
     * BOM选配定位: bomDetailId 精确指定优先, 否则按原物料ID匹配
     */
    private boolean matchBomLine(MesMbomLineVo line, ReleasePkgBomItemVo item) {
        if (item.getBomDetailId() != null) {
            return item.getBomDetailId().equals(line.getBomDetailId());
        }
        return item.getChildMaterialId() != null && item.getChildMaterialId().equals(line.getChildMaterialId());
    }

    /**
     * 追加备注(保留原备注, 以分号分隔)
     */
    private String appendRemark(String original, String extra) {
        return StringUtils.isBlank(original) ? extra : original + "; " + extra;
    }

    // ==================== Flowable 审批流方法 ====================

    /**
     * 提交审批(CREATED -> APPROVING)
     * <p>
     * 注意：不加 @Transactional，避免持有 mes 连接覆盖整个 Flowable 启动过程。
     * 仅有一个 selectById 只读查询，无需事务。
     */
    @Override
    public boolean submit(String mesWoIdStr, String selectFlowId) {
        Long mesWoId = Long.parseLong(mesWoIdStr);
        ProWorkOrder entity = baseMapper.selectById(mesWoId);
        if (entity == null) {
            throw new RuntimeException("MES工单不存在");
        }
        if (!STATUS_CREATED.equals(entity.getWoStatus())) {
            throw new RuntimeException("只有新建状态的工单才能提交审批");
        }
        // FLASH门禁前置校验: 审批通过回调(afterFlowHandle)将直接置 RELEASED 不再走 doReleaseAndSnapshot,
        // 提交时点拦截未绑定刷写内容的工序, 覆盖审批下达路径(与 release() 直达路径双入口)
        assertFlashStepsBound(mesWoId);
        // 解析selectFlowId
        if (StringUtils.isBlank(selectFlowId)) {
            if (entity.getSelectFlowId() != null) {
                selectFlowId = String.valueOf(entity.getSelectFlowId());
            } else {
                throw new RuntimeException("请先在编辑页选择审批流程并保存");
            }
        }
        // 构建流程变量
        Map<String, Object> variables = new HashMap<>();
        variables.put("dataId", mesWoIdStr);
        variables.put("requireTaskOnStart", true);
        variables.put("mesWoId", entity.getMesWoId());
        variables.put("woNo", entity.getWoNo());
        // 调用流程服务（processService 自身 @DS("master") + REQUIRES_NEW，会自行切换数据源）
        R<Void> result = processService.startProcessByDataId(
            mesWoIdStr, selectFlowId, "proWorkOrderService", variables);
        if (R.isSuccess(result)) {
            return true;
        }
        throw new RuntimeException(result.getMsg());
    }

    /**
     * 查询流程实例ID
     * <p>
     * 必须加 @DS("master")：Flowable 表在 master 库，
     * 类级 @DS("mes") 会导致查询 mes 库报错。
     */
    @Override
    @DS("master")
    public String getProcInsId(String mesWoIdStr) {
        return procInsIdHelper.resolve(mesWoIdStr, () -> {
            WfMyBusiness business = wfMyBusinessServiceImpl.getByDataId(mesWoIdStr);
            return business != null ? business.getProcessInstanceId() : null;
        });
    }

    /**
     * Flowable 审批回调
     * <p>
     * 显式声明 @DS("mes") 确保写入 mes 库，不依赖类级注解的 AOP 顺序。
     * REQUIRES_NEW 隔离事务，避免 Flowable master 事务回滚影响 mes 写入。
     */
    @Override
    @DS("mes")
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void afterFlowHandle(WfMyBusiness business) {
        Long mesWoId = Long.parseLong(business.getDataId());
        ProWorkOrder entity = baseMapper.selectById(mesWoId);
        if (entity == null) {
            return;
        }
        String actStatus = business.getActStatus();
        boolean changed = false;
        if (ActStatus.doing.equals(actStatus)) {
            entity.setWoStatus(STATUS_APPROVING);
            changed = true;
        } else if (ActStatus.pass.equals(actStatus)) {
            entity.setWoStatus(STATUS_RELEASED);
            changed = true;
        } else if (ActStatus.reject.equals(actStatus) || ActStatus.retrun.equals(actStatus)
            || ActStatus.revoke.equals(actStatus) || ActStatus.stop.equals(actStatus)
            || ActStatus.recall.equals(actStatus) || ActStatus.err.equals(actStatus)) {
            entity.setWoStatus(STATUS_CREATED);
            changed = true;
        }
        // delegate/assign/start 不改变状态，跳过更新
        if (changed) {
            baseMapper.updateById(entity);
            log.info("MES工单审批回调: mesWoId={}, actStatus={}, newWoStatus={}", mesWoId, actStatus, entity.getWoStatus());
        }
    }

    @Override
    /**
     * 获取
     * <p>必须显式 @DS("mes") + @Transactional(REQUIRES_NEW): 该回调由 Flowable(master 事务)经 getBean 直接调用,
     * 外层 master 事务已绑定连接, 仅靠 @DS 改 holder 无法切换(查询会落到 ry-vue 报 pro_work_order 不存在);
     * REQUIRES_NEW 挂起外层事务、在 holder top="mes" 时另起新事务绑定 mes 连接(同 ERP WmArrivalNotice 先例)。
     * 原始实现缺 REQUIRES_NEW 导致工单审批流程从未成功启动过(流程实例数为 0)。</p>
     * @param dataId ID
     * @return 返回结果
     */
    @DS("mes")
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW, readOnly = true)
    public Object getBusinessDataById(String dataId) {
        return selectById(Long.parseLong(dataId));
    }

    @Override
    /**
     * flowValuesOfTask 操作
     * @param taskNameId ID
     * @param Map<String Map<String
     * @param values values
     * @return 返回结果映射
     */
    public Map<String, Object> flowValuesOfTask(String taskNameId, Map<String, Object> values) {
        return null;
    }

    @Override
    /**
     * flowCandidateUsernamesOfTask 操作
     * @param taskNameId ID
     * @param Map<String Map<String
     * @param values values
     * @return 结果列表
     */
    public List<String> flowCandidateUsernamesOfTask(String taskNameId, Map<String, Object> values) {
        return null;
    }

    /**
     * 工单完工(RELEASED/IN_PROGRESS/PARTIAL_COMPLETED -> COMPLETED) + MQ通知ERP
     * <p>
     * 由流转卡完工聚合触发(仅当工单下所有流转卡均完工时调用)。CAS更新状态，事务提交后异步发送MQ消息。
     * 兼容工单从未进入 IN_PROGRESS 的场景(如流转卡在未开工状态下被直接完工):
     * 此时一并补写 actualStartDate，避免完工工单缺失开工时间。
     * <p>
     * 部分完工(PARTIAL_COMPLETED)由 completeCard 在仍有在制流转卡时置位, 不走此方法, 不通知ERP。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void completeWo(Long mesWoId) {
        ProWorkOrder wo = baseMapper.selectById(mesWoId);
        if (wo == null) {
            log.warn("MES工单不存在，跳过完工: mesWoId={}", mesWoId);
            return;
        }
        if (!STATUS_IN_PROGRESS.equals(wo.getWoStatus())
            && !STATUS_RELEASED.equals(wo.getWoStatus())
            && !STATUS_PARTIAL_COMPLETED.equals(wo.getWoStatus())) {
            log.warn("工单状态不满足完工条件，跳过: mesWoId={}, woStatus={}", mesWoId, wo.getWoStatus());
            return;
        }

        // IPQC 门禁：关键工序(isKeyProcess=1)必须有 QUALIFIED 的 IPQC 才允许工单完工
        List<ProWorkOrderStep> keySteps = stepMapper.selectList(Wrappers.<ProWorkOrderStep>lambdaQuery()
            .eq(ProWorkOrderStep::getMesWoId, mesWoId)
            .eq(ProWorkOrderStep::getIsKeyProcess, 1));
        if (keySteps != null && !keySteps.isEmpty()) {
            List<QcIpqc> ipqcList = qcIpqcMapper.selectList(Wrappers.<QcIpqc>lambdaQuery()
                .eq(QcIpqc::getMesWoId, mesWoId));
            Map<Long, String> resultByProcess = new HashMap<>();
            if (ipqcList != null) {
                for (QcIpqc ipqc : ipqcList) {
                    resultByProcess.putIfAbsent(ipqc.getProcessId(), ipqc.getInspectResult());
                }
            }
            StringBuilder pending = new StringBuilder();
            StringBuilder unqualified = new StringBuilder();
            for (ProWorkOrderStep ks : keySteps) {
                String result = resultByProcess.get(ks.getProcessId());
                if ("QUALIFIED".equals(result)) {
                    continue;
                }
                if ("UNQUALIFIED".equals(result)) {
                    if (unqualified.length() > 0) {
                        unqualified.append("、");
                    }
                    unqualified.append(ks.getProcessName());
                } else {
                    if (pending.length() > 0) {
                        pending.append("、");
                    }
                    pending.append(ks.getProcessName());
                }
            }
            if (pending.length() > 0) {
                throw new RuntimeException("以下关键工序尚未完成IPQC过程检验，禁止工单完工：" + pending);
            }
            if (unqualified.length() > 0) {
                throw new RuntimeException("以下关键工序IPQC检验不合格，禁止工单完工：" + unqualified);
            }
        }

        // CAS更新 RELEASED/IN_PROGRESS/PARTIAL_COMPLETED -> COMPLETED; 若工单从未开工(actualStartDate为空)则补写开工时间
        Date now = new Date();
        int rows = baseMapper.update(null, Wrappers.<ProWorkOrder>lambdaUpdate()
            .set(ProWorkOrder::getWoStatus, STATUS_COMPLETED)
            .set(ProWorkOrder::getActualEndDate, now)
            .set(wo.getActualStartDate() == null, ProWorkOrder::getActualStartDate, now)
            .eq(ProWorkOrder::getMesWoId, mesWoId)
            .in(ProWorkOrder::getWoStatus, STATUS_RELEASED, STATUS_IN_PROGRESS, STATUS_PARTIAL_COMPLETED));
        if (rows == 0) {
            log.warn("工单状态并发变更，跳过完工: mesWoId={}", mesWoId);
            return;
        }

        log.info("MES工单完工: mesWoId={}, woNo={}, completedQty={}, scrapQty={}",
            mesWoId, wo.getWoNo(), wo.getCompletedQty(), wo.getScrapQty());

        // 事务提交后异步发送MQ消息通知ERP(completed_qty/scrap_qty 已由 completeCard 在本事务内聚合)
        final BigDecimal completedQty = wo.getCompletedQty() != null ? wo.getCompletedQty() : BigDecimal.ZERO;
        final BigDecimal scrappedQty = wo.getScrapQty() != null ? wo.getScrapQty() : BigDecimal.ZERO;
        final String woNo = wo.getWoNo();
        final Long erpWoId = wo.getErpWoId();

        // P0-4: 返工工单(source_type=REWORK)完工 -> 直调 Mapper 回写 NCR 状态(同事务原子; 直调 Mapper 避免 Service 循环依赖, 同 QcIpqcMapper 模式)
        if ("REWORK".equals(wo.getSourceType())) {
            NcrReworkOrder ro = ncrReworkOrderMapper.selectOne(
                Wrappers.<NcrReworkOrder>lambdaQuery().eq(NcrReworkOrder::getReworkWoId, mesWoId));
            if (ro != null) {
                ncrReworkOrderMapper.update(null,
                    Wrappers.<NcrReworkOrder>lambdaUpdate()
                        .set(NcrReworkOrder::getStatus, "COMPLETED")
                        .eq(NcrReworkOrder::getReworkId, ro.getReworkId())
                        .ne(NcrReworkOrder::getStatus, "COMPLETED"));
                ncrOrderMapper.update(null,
                    Wrappers.<NcrOrder>lambdaUpdate()
                        .set(NcrOrder::getStatus, "CLOSED")
                        .set(NcrOrder::getClosedTime, new Date())
                        .eq(NcrOrder::getNcrId, ro.getNcrId())
                        .eq(NcrOrder::getStatus, "IN_REWORK"));
                log.info("返工工单完工回写NCR: mesWoId={}, ncrId={}", mesWoId, ro.getNcrId());
            }
            return;  // 返工工单是 MES 内部事务, 不通知 ERP, 不注册 MQ afterCommit
        }

        TransactionSynchronizationManager.registerSynchronization(
            new TransactionSynchronization() {
                @Override
                /**
                 * afterCommit 操作
                 */
                public void afterCommit() {
                    String tenantId = TenantHelper.getTenantId();
                    mesMqSendService.sendWorkOrderCompleted(
                        mesWoId, erpWoId, woNo, completedQty, scrappedQty, tenantId);
                }
            });
    }

    // ==================== 私有辅助方法 ====================

    private LambdaQueryWrapper<ProWorkOrder> buildQueryWrapper(ProWorkOrderBo bo) {
        LambdaQueryWrapper<ProWorkOrder> lqw = Wrappers.<ProWorkOrder>lambdaQuery()
            .eq(StringUtils.isNotBlank(bo.getWoNo()), ProWorkOrder::getWoNo, bo.getWoNo())
            .eq(bo.getErpWoId() != null, ProWorkOrder::getErpWoId, bo.getErpWoId())
            .eq(bo.getMaterialId() != null, ProWorkOrder::getMaterialId, bo.getMaterialId())
            .like(StringUtils.isNotBlank(bo.getMaterialName()), ProWorkOrder::getMaterialName, bo.getMaterialName())
            .eq(bo.getWorkshopId() != null, ProWorkOrder::getWorkshopId, bo.getWorkshopId())
            .eq(StringUtils.isNotBlank(bo.getWoStatus()), ProWorkOrder::getWoStatus, bo.getWoStatus())
            .orderByDesc(ProWorkOrder::getCreateTime);
        return lqw;
    }

    private Long toLong(Object obj) {
        if (obj == null) return null;
        if (obj instanceof Long) return (Long) obj;
        if (obj instanceof Number) return ((Number) obj).longValue();
        try {
            return Long.parseLong(obj.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Integer toInt(Object obj) {
        if (obj == null) return null;
        if (obj instanceof Integer) return (Integer) obj;
        if (obj instanceof Number) return ((Number) obj).intValue();
        try {
            return Integer.parseInt(obj.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
