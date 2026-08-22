package com.nexus.mes.release.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.nexus.common.core.domain.R;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.core.utils.MapstructUtils;
import com.nexus.common.core.utils.StringUtils;
import com.nexus.common.flowable.core.domain.ActStatus;
import com.nexus.common.flowable.helper.FlowableProcInsIdHelper;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.common.satoken.utils.LoginHelper;
import com.nexus.mes.flash.domain.Firmware;
import com.nexus.mes.flash.mapper.FirmwareMapper;
import com.nexus.mes.flash.service.IFirmwareService;
import com.nexus.mes.release.domain.ReleasePackage;
import com.nexus.mes.release.domain.ReleasePkgBomItem;
import com.nexus.mes.release.domain.ReleasePkgConfig;
import com.nexus.mes.release.domain.ReleasePkgFirmware;
import com.nexus.mes.release.domain.bo.ReleasePackageBo;
import com.nexus.mes.release.domain.vo.ReleasePackageVo;
import com.nexus.mes.release.mapper.ReleasePackageMapper;
import com.nexus.mes.release.mapper.ReleasePkgBomItemMapper;
import com.nexus.mes.release.mapper.ReleasePkgConfigMapper;
import com.nexus.mes.release.mapper.ReleasePkgFirmwareMapper;
import com.nexus.mes.release.service.IReleasePackageService;
import com.nexus.system.strategy.AutoCodeUtil;
import com.nexus.workflow.domain.WfCustomForm;
import com.nexus.workflow.domain.WfMyBusiness;
import com.nexus.workflow.service.IWfProcessService;
import com.nexus.workflow.service.WfCallBackServiceI;
import com.nexus.workflow.service.impl.WfMyBusinessServiceImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * MES客户定制化发布包 Service 业务层实现
 * <p>
 * 状态机: DRAFT -> IN_REVIEW -> RELEASED -> ARCHIVED
 * 审批流: 实现 WfCallBackServiceI (Flowable afterFlowHandle 驱动状态机) + 管理员兜底 approve/reject
 * 编码生成: autoCodeUtil.genSerialCode("MES_RP_CODE", null) -> RP+yyyyMMdd+3位日循环
 * 默认包: is_default 客户+物料内唯一(仅 RELEASED 可设默认, 归档自动解除)
 * 子表级联: 固件映射/BOM选配/配置项 随主表先删后插(同 MBOM 先例)
 * 匹配: 客户+物料+客户产品代号精确 -> 客户+物料默认包, 仅取 RELEASED 且在有效期内的最新版本
 * </p>
 *
 * @author Nexus
 */
@Slf4j
@RequiredArgsConstructor
@DS("mes")
@Service(IReleasePackageService.SERVICE_NAME)
public class ReleasePackageServiceImpl implements IReleasePackageService, WfCallBackServiceI {

    private final ReleasePackageMapper baseMapper;
    private final ReleasePkgFirmwareMapper firmwareMapperPkg;
    private final ReleasePkgBomItemMapper bomItemMapper;
    private final ReleasePkgConfigMapper configMapper;
    /** 固件 Mapper: 校验映射的固件 RELEASED 且物料匹配 */
    private final FirmwareMapper firmwareMapper;
    private final AutoCodeUtil autoCodeUtil;
    private final IWfProcessService processService;
    private final WfMyBusinessServiceImpl wfMyBusinessServiceImpl;
    private final FlowableProcInsIdHelper procInsIdHelper;

    // ==================== CRUD ====================

    @Override
    public TableDataInfo<ReleasePackageVo> selectPageList(ReleasePackageBo bo, PageQuery pageQuery) {
        LambdaQueryWrapper<ReleasePackage> lqw = buildQueryWrapper(bo);
        Page<ReleasePackageVo> page = baseMapper.selectVoPage(pageQuery.build(), lqw);
        return TableDataInfo.build(page);
    }

    @Override
    public ReleasePackageVo selectById(Long packageId) {
        return baseMapper.selectVoById(packageId);
    }

    @Override
    public ReleasePackageVo selectDetailById(Long packageId) {
        ReleasePackageVo vo = baseMapper.selectVoById(packageId);
        if (vo != null) {
            loadDetails(vo);
        }
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean insertByBo(ReleasePackageBo bo) {
        if (StringUtils.isBlank(bo.getPackageCode())) {
            bo.setPackageCode(generatePackageCode());
        }
        Long existCode = baseMapper.selectCount(Wrappers.<ReleasePackage>lambdaQuery()
            .eq(ReleasePackage::getPackageCode, bo.getPackageCode()));
        if (existCode != null && existCode > 0) {
            throw new ServiceException("发布包编码已存在: " + bo.getPackageCode());
        }
        assertVersionUnique(bo.getClientId(), bo.getMaterialId(), bo.getVersion(), null);
        validateSubTables(bo);

        ReleasePackage pkg = MapstructUtils.convert(bo, ReleasePackage.class);
        pkg.setStatus(STATUS_DRAFT);
        pkg.setIsDefault(IS_DEFAULT_NO);
        boolean ok = baseMapper.insert(pkg) > 0;
        bo.setPackageId(pkg.getPackageId());
        saveSubTables(pkg.getPackageId(), bo);
        log.info("新增发布包成功: code={}, clientId={}, materialId={}, version={}",
            bo.getPackageCode(), bo.getClientId(), bo.getMaterialId(), bo.getVersion());
        return ok;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean updateByBo(ReleasePackageBo bo) {
        ReleasePackage pkg = baseMapper.selectById(bo.getPackageId());
        if (pkg == null) {
            throw new ServiceException("发布包不存在: " + bo.getPackageId());
        }
        if (!STATUS_DRAFT.equals(pkg.getStatus()) && !STATUS_ARCHIVED.equals(pkg.getStatus())) {
            throw new ServiceException("仅草稿/归档状态可编辑, 当前: " + pkg.getStatus());
        }
        // 客户/物料/版本变更时校验同客户+物料+版本唯一(排除自身)
        boolean keyChanged = !bo.getClientId().equals(pkg.getClientId())
            || !bo.getMaterialId().equals(pkg.getMaterialId())
            || !bo.getVersion().equals(pkg.getVersion());
        if (keyChanged) {
            assertVersionUnique(bo.getClientId(), bo.getMaterialId(), bo.getVersion(), bo.getPackageId());
        }
        validateSubTables(bo);

        ReleasePackage update = MapstructUtils.convert(bo, ReleasePackage.class);
        boolean ok = baseMapper.updateById(update) > 0;
        saveSubTables(bo.getPackageId(), bo);
        log.info("修改发布包成功: packageId={}", bo.getPackageId());
        return ok;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean deleteByIds(Long[] ids) {
        if (ids == null || ids.length == 0) {
            return false;
        }
        List<Long> idList = Arrays.asList(ids);
        Long nonDraft = baseMapper.selectCount(Wrappers.<ReleasePackage>lambdaQuery()
            .in(ReleasePackage::getPackageId, idList)
            .ne(ReleasePackage::getStatus, STATUS_DRAFT));
        if (nonDraft != null && nonDraft > 0) {
            throw new ServiceException("仅草稿状态发布包可删除(审批/发布/归档请先处理)");
        }
        int rows = baseMapper.deleteByIds(idList);
        log.info("批量删除发布包: count={}", idList.size());
        return rows > 0;
    }

    // ==================== 状态机 ====================

    /**
     * 提交审批: DRAFT -> IN_REVIEW
     * <p>selectFlowId 非空则走 Flowable: 启动流程(失败抛错回滚, 不静默降级)。<b>启动前不得先改业务行/持行锁</b>——
     * 启动过程中 Flowable(master) 会回调 afterFlowHandle(doing), 其在 REQUIRES_NEW(mes) 里 UPDATE 同一行;
     * 若外层先 UPDATE 持锁, 回调将等待外层提交, 而外层又在等流程启动返回 -> 死锁循环 -> 锁等待超时
     * (同固件/ERP WmArrivalNotice.submit 先例: 启动前不碰业务行, 状态推进全交给回调)。
     * selectFlowId 在流程启动成功、回调事务已提交后再持久化(此时无锁冲突), 便于流转追溯。
     * selectFlowId 为空则仅改状态(管理员兜底审批)。</p>
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean submitForApproval(Long packageId, String selectFlowId) {
        ReleasePackage pkg = baseMapper.selectById(packageId);
        if (pkg == null) {
            throw new ServiceException("发布包不存在: " + packageId);
        }
        if (!STATUS_DRAFT.equals(pkg.getStatus())) {
            throw new ServiceException("仅草稿状态可提交审批, 当前: " + pkg.getStatus());
        }
        if (StringUtils.isNotBlank(selectFlowId)) {
            // 走 Flowable: 先启动(回调 afterFlowHandle(doing) 负责置 IN_REVIEW), 成功后再持久化审批流
            Map<String, Object> variables = new HashMap<>();
            variables.put("dataId", packageId.toString());
            variables.put("requireTaskOnStart", true);
            variables.put("packageId", packageId);
            variables.put("packageCode", pkg.getPackageCode());
            variables.put("clientName", pkg.getClientName());
            variables.put("materialName", pkg.getMaterialName());
            variables.put("version", pkg.getVersion());
            R<Void> result = processService.startProcessByDataId(packageId.toString(), selectFlowId, SERVICE_NAME, variables);
            if (!R.isSuccess(result)) {
                throw new ServiceException("发起审批流程失败: " + result.getMsg());
            }
            // 流程已启动且回调事务已提交(锁已释放), 此刻持久化 select_flow_id 无锁冲突
            baseMapper.update(null, Wrappers.<ReleasePackage>lambdaUpdate()
                .set(ReleasePackage::getSelectFlowId, Long.valueOf(selectFlowId))
                .eq(ReleasePackage::getPackageId, packageId));
        } else {
            // 管理员兜底: 无流程, CAS DRAFT -> IN_REVIEW
            int rows = baseMapper.update(null, Wrappers.<ReleasePackage>lambdaUpdate()
                .set(ReleasePackage::getStatus, STATUS_IN_REVIEW)
                .eq(ReleasePackage::getPackageId, packageId)
                .eq(ReleasePackage::getStatus, STATUS_DRAFT));
            if (rows == 0) {
                throw new ServiceException("发布包状态已变更, 提交审批失败");
            }
        }
        log.info("发布包提交审批: packageId={}, selectFlowId={}", packageId, selectFlowId);
        return true;
    }

    /**
     * Flowable 回调: 驱动发布包状态机
     * <p>doing->IN_REVIEW ; pass->RELEASED(记录发布人/时间) ; reject/retrun/stop/recall/revoke->DRAFT</p>
     * <p>必须显式 @DS("mes") + REQUIRES_NEW: 回调由 Flowable(master 上下文)触发,
     * 类级 @DS("mes") 在跨上下文调用中不可靠, REQUIRES_NEW 隔离事务避免 Flowable master 事务回滚影响 mes 写入。</p>
     */
    @Override
    @DS("mes")
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void afterFlowHandle(WfMyBusiness business) {
        Long packageId = Long.parseLong(business.getDataId());
        ReleasePackage pkg = baseMapper.selectById(packageId);
        if (pkg == null) {
            return;
        }
        String actStatus = business.getActStatus();
        if (ActStatus.doing.equals(actStatus)) {
            updateStatus(packageId, STATUS_IN_REVIEW);
        } else if (ActStatus.pass.equals(actStatus)) {
            doPublish(packageId);
        } else if (ActStatus.reject.equals(actStatus) || ActStatus.retrun.equals(actStatus)
            || ActStatus.stop.equals(actStatus) || ActStatus.recall.equals(actStatus) || ActStatus.revoke.equals(actStatus)) {
            updateStatus(packageId, STATUS_DRAFT);
        }
    }

    /**
     * Flowable 回调钩子: 根据业务数据ID获取业务表单数据(发布包详情)
     * <p>必须显式 @DS("mes") + @Transactional(REQUIRES_NEW): 该回调由 Flowable(master 事务)经 getBean 直接调用,
     * 外层 master 事务已绑定连接, 仅靠 @DS 改 holder 无法切换连接(查 mes_release_package 会落到 ry-vue 报表不存在);
     * REQUIRES_NEW 挂起外层事务、在 holder top="mes" 时另起新事务绑定 mes 连接(同固件先例)。</p>
     */
    @Override
    @DS("mes")
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public Object getBusinessDataById(String dataId) {
        try {
            return selectDetailById(Long.parseLong(dataId));
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

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean approve(Long packageId, String remark) {
        ReleasePackage pkg = baseMapper.selectById(packageId);
        if (pkg == null) {
            throw new ServiceException("发布包不存在: " + packageId);
        }
        if (!STATUS_IN_REVIEW.equals(pkg.getStatus())) {
            throw new ServiceException("仅审批中状态可审批通过, 当前: " + pkg.getStatus());
        }
        doPublish(packageId);
        log.info("发布包兜底审批通过: packageId={}, remark={}", packageId, remark);
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean reject(Long packageId, String reason) {
        ReleasePackage pkg = baseMapper.selectById(packageId);
        if (pkg == null) {
            throw new ServiceException("发布包不存在: " + packageId);
        }
        if (!STATUS_IN_REVIEW.equals(pkg.getStatus())) {
            throw new ServiceException("仅审批中状态可驳回, 当前: " + pkg.getStatus());
        }
        int rows = baseMapper.update(null, Wrappers.<ReleasePackage>lambdaUpdate()
            .set(ReleasePackage::getStatus, STATUS_DRAFT)
            .eq(ReleasePackage::getPackageId, packageId)
            .eq(ReleasePackage::getStatus, STATUS_IN_REVIEW));
        if (rows == 0) {
            throw new ServiceException("发布包状态已变更, 驳回失败");
        }
        log.info("发布包兜底驳回: packageId={}, reason={}", packageId, reason);
        return true;
    }

    /**
     * 归档: RELEASED -> ARCHIVED (CAS 状态守卫)
     * <p>归档后不再参与工单匹配(匹配仅取 RELEASED); 已生成工单的发布包快照不受影响(追溯完整)。
     * 归档自动解除默认标记。</p>
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean archive(Long packageId, String reason) {
        ReleasePackage pkg = baseMapper.selectById(packageId);
        if (pkg == null) {
            throw new ServiceException("发布包不存在: " + packageId);
        }
        if (!STATUS_RELEASED.equals(pkg.getStatus())) {
            throw new ServiceException("仅已发布发布包可归档, 当前: " + pkg.getStatus());
        }
        int rows = baseMapper.update(null, Wrappers.<ReleasePackage>lambdaUpdate()
            .set(ReleasePackage::getStatus, STATUS_ARCHIVED)
            .set(ReleasePackage::getIsDefault, IS_DEFAULT_NO)
            .eq(ReleasePackage::getPackageId, packageId)
            .eq(ReleasePackage::getStatus, STATUS_RELEASED));
        if (rows == 0) {
            throw new ServiceException("发布包状态已变更, 归档失败");
        }
        log.info("发布包归档: packageId={}, reason={}", packageId, reason);
        return true;
    }

    /**
     * 设为客户+物料默认发布包(仅 RELEASED, 客户+物料内唯一)
     * <p>同客户+物料其他默认包自动清 0, CAS 防止并发覆盖。</p>
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean setDefault(Long packageId) {
        ReleasePackage pkg = baseMapper.selectById(packageId);
        if (pkg == null) {
            throw new ServiceException("发布包不存在: " + packageId);
        }
        if (!STATUS_RELEASED.equals(pkg.getStatus())) {
            throw new ServiceException("仅已发布发布包可设为默认, 当前: " + pkg.getStatus());
        }
        baseMapper.update(null, Wrappers.<ReleasePackage>lambdaUpdate()
            .set(ReleasePackage::getIsDefault, IS_DEFAULT_NO)
            .eq(ReleasePackage::getClientId, pkg.getClientId())
            .eq(ReleasePackage::getMaterialId, pkg.getMaterialId())
            .eq(ReleasePackage::getIsDefault, IS_DEFAULT_YES));
        int rows = baseMapper.update(null, Wrappers.<ReleasePackage>lambdaUpdate()
            .set(ReleasePackage::getIsDefault, IS_DEFAULT_YES)
            .eq(ReleasePackage::getPackageId, packageId)
            .eq(ReleasePackage::getStatus, STATUS_RELEASED));
        if (rows == 0) {
            throw new ServiceException("发布包状态已变更, 设为默认失败");
        }
        log.info("发布包设为默认: packageId={}, clientId={}, materialId={}", packageId, pkg.getClientId(), pkg.getMaterialId());
        return true;
    }

    // ==================== 查询匹配 ====================

    @Override
    public ReleasePackageVo matchPackage(Long clientId, Long materialId, String customerProductCode) {
        if (clientId == null || materialId == null) {
            return null;
        }
        Date now = new Date();
        // ① 精确匹配: 客户+物料+客户产品代号 -> 最新 RELEASED(有效期内)
        ReleasePackage matched = null;
        if (StringUtils.isNotBlank(customerProductCode)) {
            matched = baseMapper.selectOne(Wrappers.<ReleasePackage>lambdaQuery()
                .eq(ReleasePackage::getClientId, clientId)
                .eq(ReleasePackage::getMaterialId, materialId)
                .eq(ReleasePackage::getCustomerProductCode, customerProductCode)
                .eq(ReleasePackage::getStatus, STATUS_RELEASED)
                .and(w -> w.isNull(ReleasePackage::getEffectiveDate).or().le(ReleasePackage::getEffectiveDate, now))
                .and(w -> w.isNull(ReleasePackage::getExpiryDate).or().gt(ReleasePackage::getExpiryDate, now))
                .orderByDesc(ReleasePackage::getPublishTime)
                .last("LIMIT 1"));
        }
        // ② 默认包: 客户+物料 is_default=1 -> RELEASED(有效期内)
        if (matched == null) {
            matched = baseMapper.selectOne(Wrappers.<ReleasePackage>lambdaQuery()
                .eq(ReleasePackage::getClientId, clientId)
                .eq(ReleasePackage::getMaterialId, materialId)
                .eq(ReleasePackage::getIsDefault, IS_DEFAULT_YES)
                .eq(ReleasePackage::getStatus, STATUS_RELEASED)
                .and(w -> w.isNull(ReleasePackage::getEffectiveDate).or().le(ReleasePackage::getEffectiveDate, now))
                .and(w -> w.isNull(ReleasePackage::getExpiryDate).or().gt(ReleasePackage::getExpiryDate, now))
                .orderByDesc(ReleasePackage::getPublishTime)
                .last("LIMIT 1"));
        }
        if (matched == null) {
            return null;
        }
        ReleasePackageVo vo = MapstructUtils.convert(matched, ReleasePackageVo.class);
        loadDetails(vo);
        return vo;
    }

    @Override
    public List<ReleasePackageVo> selectByClientMaterial(Long clientId, Long materialId) {
        return baseMapper.selectVoList(Wrappers.<ReleasePackage>lambdaQuery()
            .eq(ReleasePackage::getClientId, clientId)
            .eq(ReleasePackage::getMaterialId, materialId)
            .eq(ReleasePackage::getStatus, STATUS_RELEASED)
            .orderByDesc(ReleasePackage::getIsDefault)
            .orderByDesc(ReleasePackage::getPublishTime));
    }

    // ==================== 审批流辅助 ====================

    @Override
    public List<WfCustomForm> getFlows() {
        return processService.getProcesssByServiceName(SERVICE_NAME);
    }

    /**
     * 查询流程实例ID(审批表单页"查看流程"跳转用)
     * <p>必须加 @DS("master"): Flowable/wf_my_business 表在 master 库, 类级 @DS("mes") 会导致查询报错。</p>
     */
    @Override
    @DS("master")
    public String getProcInsId(String packageIdStr) {
        return procInsIdHelper.resolve(packageIdStr, () -> {
            WfMyBusiness business = wfMyBusinessServiceImpl.getByDataId(packageIdStr);
            return business != null ? business.getProcessInstanceId() : null;
        });
    }

    // ==================== 私有辅助方法 ====================

    /**
     * 内部发布: IN_REVIEW -> RELEASED, 记录发布人/时间
     */
    private void doPublish(Long packageId) {
        Date now = new Date();
        Long userId = LoginHelper.getUserId();
        baseMapper.update(null, Wrappers.<ReleasePackage>lambdaUpdate()
            .set(ReleasePackage::getStatus, STATUS_RELEASED)
            .set(ReleasePackage::getPublishedBy, userId)
            .set(ReleasePackage::getPublishTime, now)
            .eq(ReleasePackage::getPackageId, packageId)
            .eq(ReleasePackage::getStatus, STATUS_IN_REVIEW));
        log.info("发布包发布成功: packageId={}", packageId);
    }

    /**
     * 直接更新发布包状态(无 CAS 守卫, 供 Flowable 回调使用)
     */
    private void updateStatus(Long packageId, String status) {
        baseMapper.update(null, Wrappers.<ReleasePackage>lambdaUpdate()
            .set(ReleasePackage::getStatus, status)
            .eq(ReleasePackage::getPackageId, packageId));
    }

    /**
     * 生成发布包编码: 走系统自动编码规则 MES_RP_CODE (RP + yyyyMMdd + 3位日循环流水号)
     */
    private String generatePackageCode() {
        return autoCodeUtil.genSerialCode("MES_RP_CODE", null);
    }

    /**
     * 校验同客户+物料+版本唯一
     *
     * @param excludeId 修改时排除自身, 新增传 null
     */
    private void assertVersionUnique(Long clientId, Long materialId, String version, Long excludeId) {
        Long count = baseMapper.selectCount(Wrappers.<ReleasePackage>lambdaQuery()
            .eq(ReleasePackage::getClientId, clientId)
            .eq(ReleasePackage::getMaterialId, materialId)
            .eq(ReleasePackage::getVersion, version)
            .ne(excludeId != null, ReleasePackage::getPackageId, excludeId));
        if (count != null && count > 0) {
            throw new ServiceException("该客户+物料已存在版本 " + version + ", 请检查版本号");
        }
    }

    /**
     * 校验子表数据合法性:
     * 固件映射——固件存在且 RELEASED 且物料匹配发布包物料; 定位键(routingStepId/processId)必填其一且不重复;
     * BOM选配——actionType 合法; REPLACE/REMOVE 须填原物料; REPLACE/ADD 须填目标物料; ADD 须填单位用量
     */
    private void validateSubTables(ReleasePackageBo bo) {
        List<com.nexus.mes.release.domain.bo.ReleasePkgFirmwareBo> mappings = bo.getFirmwareMappings();
        if (mappings != null && !mappings.isEmpty()) {
            Set<String> locateKeys = new HashSet<>();
            for (com.nexus.mes.release.domain.bo.ReleasePkgFirmwareBo m : mappings) {
                if (m.getRoutingStepId() == null && m.getProcessId() == null) {
                    throw new ServiceException("固件映射[固件 " + m.getFirmwareCode() + "]须指定工艺路线步骤或工序(定位键)");
                }
                String key = m.getRoutingStepId() != null ? "S" + m.getRoutingStepId() : "P" + m.getProcessId();
                if (!locateKeys.add(key)) {
                    throw new ServiceException("固件映射定位键重复: " + (m.getProcessCode() != null ? m.getProcessCode() : key));
                }
                Firmware fw = firmwareMapper.selectById(m.getFirmwareId());
                if (fw == null) {
                    throw new ServiceException("固件不存在: firmwareId=" + m.getFirmwareId());
                }
                if (!IFirmwareService.STATUS_RELEASED.equals(fw.getStatus())) {
                    throw new ServiceException("固件 " + fw.getFirmwareCode() + " 状态为 " + fw.getStatus() + ", 仅已发布(RELEASED)固件可用于发布包");
                }
                if (!bo.getMaterialId().equals(fw.getMaterialId())) {
                    throw new ServiceException("固件 " + fw.getFirmwareCode() + " 适用物料与发布包物料不一致, 不可映射");
                }
            }
        }
        List<com.nexus.mes.release.domain.bo.ReleasePkgBomItemBo> items = bo.getBomItems();
        if (items != null && !items.isEmpty()) {
            for (com.nexus.mes.release.domain.bo.ReleasePkgBomItemBo item : items) {
                String action = item.getActionType();
                if (!"REPLACE".equals(action) && !"ADD".equals(action) && !"REMOVE".equals(action)) {
                    throw new ServiceException("BOM选配操作类型非法: " + action + " (仅 REPLACE/ADD/REMOVE)");
                }
                if (("REPLACE".equals(action) || "REMOVE".equals(action)) && item.getChildMaterialId() == null) {
                    throw new ServiceException("BOM选配[" + action + "]须指定原物料(定位键)");
                }
                if (("REPLACE".equals(action) || "ADD".equals(action)) && item.getTargetMaterialId() == null) {
                    throw new ServiceException("BOM选配[" + action + "]须指定目标物料");
                }
                if ("ADD".equals(action) && item.getQuantityPerParent() == null) {
                    throw new ServiceException("BOM选配[ADD]须指定单位用量");
                }
            }
        }
    }

    /**
     * 子表级联保存: 先按 packageId 删除旧明细, 再整批插入(同 MBOM/销售订单行先例)
     */
    private void saveSubTables(Long packageId, ReleasePackageBo bo) {
        firmwareMapperPkg.delete(Wrappers.lambdaQuery(ReleasePkgFirmware.class)
            .eq(ReleasePkgFirmware::getPackageId, packageId));
        bomItemMapper.delete(Wrappers.lambdaQuery(ReleasePkgBomItem.class)
            .eq(ReleasePkgBomItem::getPackageId, packageId));
        configMapper.delete(Wrappers.lambdaQuery(ReleasePkgConfig.class)
            .eq(ReleasePkgConfig::getPackageId, packageId));

        if (bo.getFirmwareMappings() != null && !bo.getFirmwareMappings().isEmpty()) {
            List<ReleasePkgFirmware> rows = MapstructUtils.convert(bo.getFirmwareMappings(), ReleasePkgFirmware.class);
            for (ReleasePkgFirmware row : rows) {
                row.setMappingId(null);
                row.setPackageId(packageId);
            }
            firmwareMapperPkg.insertBatch(rows);
        }
        if (bo.getBomItems() != null && !bo.getBomItems().isEmpty()) {
            List<ReleasePkgBomItem> rows = MapstructUtils.convert(bo.getBomItems(), ReleasePkgBomItem.class);
            for (ReleasePkgBomItem row : rows) {
                row.setItemId(null);
                row.setPackageId(packageId);
            }
            bomItemMapper.insertBatch(rows);
        }
        if (bo.getConfigItems() != null && !bo.getConfigItems().isEmpty()) {
            List<ReleasePkgConfig> rows = MapstructUtils.convert(bo.getConfigItems(), ReleasePkgConfig.class);
            for (ReleasePkgConfig row : rows) {
                row.setConfigId(null);
                row.setPackageId(packageId);
            }
            configMapper.insertBatch(rows);
        }
    }

    /**
     * 装载三个子表明细(固件映射/BOM选配/配置项, 按 sort_order 排序)
     */
    private void loadDetails(ReleasePackageVo vo) {
        vo.setFirmwareMappings(firmwareMapperPkg.selectVoList(Wrappers.lambdaQuery(ReleasePkgFirmware.class)
            .eq(ReleasePkgFirmware::getPackageId, vo.getPackageId())
            .orderByAsc(ReleasePkgFirmware::getSortOrder)));
        vo.setBomItems(bomItemMapper.selectVoList(Wrappers.lambdaQuery(ReleasePkgBomItem.class)
            .eq(ReleasePkgBomItem::getPackageId, vo.getPackageId())
            .orderByAsc(ReleasePkgBomItem::getSortOrder)));
        vo.setConfigItems(configMapper.selectVoList(Wrappers.lambdaQuery(ReleasePkgConfig.class)
            .eq(ReleasePkgConfig::getPackageId, vo.getPackageId())
            .orderByAsc(ReleasePkgConfig::getSortOrder)));
    }

    /**
     * 构建查询条件: packageCode精确/clientName模糊/materialId精确/clientId精确/status精确, 按创建时间倒序
     */
    private LambdaQueryWrapper<ReleasePackage> buildQueryWrapper(ReleasePackageBo bo) {
        return Wrappers.<ReleasePackage>lambdaQuery()
            .eq(StringUtils.isNotBlank(bo.getPackageCode()), ReleasePackage::getPackageCode, bo.getPackageCode())
            .like(StringUtils.isNotBlank(bo.getClientName()), ReleasePackage::getClientName, bo.getClientName())
            .eq(bo.getClientId() != null, ReleasePackage::getClientId, bo.getClientId())
            .eq(bo.getMaterialId() != null, ReleasePackage::getMaterialId, bo.getMaterialId())
            .like(StringUtils.isNotBlank(bo.getMaterialName()), ReleasePackage::getMaterialName, bo.getMaterialName())
            .eq(StringUtils.isNotBlank(bo.getCustomerProductCode()), ReleasePackage::getCustomerProductCode, bo.getCustomerProductCode())
            .eq(StringUtils.isNotBlank(bo.getStatus()), ReleasePackage::getStatus, bo.getStatus())
            .orderByDesc(ReleasePackage::getCreateTime);
    }
}
