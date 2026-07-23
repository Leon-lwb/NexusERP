package com.nexus.erp.wm.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.nexus.common.core.domain.R;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.core.utils.MapstructUtils;
import com.nexus.common.core.utils.StringUtils;
import com.nexus.common.flowable.core.domain.ActStatus;
import com.nexus.common.mq.constant.ExchangeConstants;
import com.nexus.common.mq.constant.RoutingKeyConstants;
import com.nexus.common.mq.message.ReceiveApprovedMessage;
import com.nexus.common.mq.producer.ReliableMessageProducer;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.common.tenant.helper.TenantHelper;
import com.nexus.system.strategy.AutoCodeUtil;
import com.nexus.erp.mq.message.StockInMessage;
import com.nexus.erp.wm.domain.WmArrivalNoticeLine;
import com.nexus.erp.wm.domain.WmMaterialReceive;
import com.nexus.erp.wm.domain.WmMaterialReceiveDetail;
import com.nexus.erp.wm.domain.WmMaterialReceiveLine;
import com.nexus.erp.wm.domain.bo.WmMaterialReceiveBo;
import com.nexus.erp.wm.domain.bo.WmMaterialReceiveDetailBo;
import com.nexus.erp.wm.domain.bo.WmMaterialReceiveLineBo;
import com.nexus.erp.wm.domain.vo.WmMaterialReceiveDetailVo;
import com.nexus.erp.wm.domain.vo.WmMaterialReceiveLineVo;
import com.nexus.erp.wm.domain.vo.WmMaterialReceiveVo;
import com.nexus.erp.wm.mapper.WmArrivalNoticeLineMapper;
import com.nexus.erp.wm.mapper.WmMaterialReceiveDetailMapper;
import com.nexus.erp.wm.mapper.WmMaterialReceiveLineMapper;
import com.nexus.erp.wm.mapper.WmMaterialReceiveMapper;
import com.nexus.erp.wm.service.IWmMaterialReceiveCrudService;
import com.nexus.common.satoken.utils.LoginHelper;
import com.nexus.erp.wm.service.IWmMaterialReceiveExecutorService;
import com.baomidou.dynamic.datasource.toolkit.DynamicDataSourceContextHolder;
import com.nexus.system.domain.vo.SysUserVo;
import com.nexus.system.service.ISysUserService;
import com.nexus.workflow.domain.WfMyBusiness;
import com.nexus.workflow.service.IWfProcessService;
import com.nexus.workflow.service.impl.WfMyBusinessServiceImpl;
import org.flowable.engine.HistoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.history.HistoricProcessInstance;
import org.flowable.engine.runtime.ProcessInstance;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.baomidou.dynamic.datasource.annotation.DS;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 物料入库单CRUD Service实现类，负责基础CRUD、校验和工作流回调
 *
 * @author nexus
 */
@RequiredArgsConstructor
@DS("erp")
@Slf4j
@Service("wmMaterialReceiveCrudService")
public class WmMaterialReceiveCrudServiceImpl implements IWmMaterialReceiveCrudService {

    /** 基础数据访问映射器（Mapper） */
    private final WmMaterialReceiveMapper baseMapper;
    /** 领用行数据访问映射器（Mapper） */
    private final WmMaterialReceiveLineMapper receiveLineMapper;
    /** 领用明细数据访问映射器（Mapper） */
    private final WmMaterialReceiveDetailMapper receiveDetailMapper;
    /** 到货通知行数据访问映射器（用于查询 iqcCheck 门控 IQC 自动创建） */
    private final WmArrivalNoticeLineMapper arrivalNoticeLineMapper;
    /** 物料领用CRUD业务服务，提供物料领用CRUD相关能力 */
    private final IWfProcessService processService;
    /** 物料领用CRUD业务服务，提供物料领用CRUD相关能力 */
    private final WfMyBusinessServiceImpl wfMyBusinessServiceImpl;
    /** 物料领用CRUD业务服务，提供物料领用CRUD相关能力 */
    private final RuntimeService runtimeService;
    /** 物料领用CRUD业务服务，提供物料领用CRUD相关能力 */
    private final HistoryService historyService;
    /** 执行器业务服务，提供执行器相关能力 */
    private final IWmMaterialReceiveExecutorService executorService;
    /** 消息提供器 */
    private final ReliableMessageProducer messageProducer;
    /** 物料领用CRUD业务服务，提供物料领用CRUD相关能力 */
    private final ISysUserService sysUserService;
    /** 自动编码工具类 */
    private final AutoCodeUtil autoCodeUtil;

    /**
     * 分页查询物料入库单列表
     *
     * @param bo         查询条件
     * @param pageQuery  分页参数
     * @return 分页结果
     */
    @Override
    public TableDataInfo<WmMaterialReceiveVo> selectPageList(WmMaterialReceiveBo bo, PageQuery pageQuery) {
        LambdaQueryWrapper<WmMaterialReceive> lqw = buildQueryWrapper(bo);
        Page<WmMaterialReceiveVo> page = baseMapper.selectVoPage(pageQuery.build(), lqw);
        fillShelfByName(page.getRecords());
        return TableDataInfo.build(page);
    }

    /**
     * 查询物料入库单列表
     *
     * @param bo 查询条件
     * @return 入库单列表
     */
    @Override
    public List<WmMaterialReceiveVo> selectList(WmMaterialReceiveBo bo) {
        List<WmMaterialReceiveVo> list = baseMapper.selectVoList(buildQueryWrapper(bo));
        fillShelfByName(list);
        return list;
    }

    /**
     * 根据ID查询物料入库单（含行和明细）
     *
     * @param id 入库单ID
     * @return 入库单详情
     */
    @Override
    public WmMaterialReceiveVo selectById(Long id) {
        WmMaterialReceiveVo vo = baseMapper.selectVoById(id);
        if (vo != null) {
            List<WmMaterialReceiveLine> lineList = receiveLineMapper.selectList(
                new LambdaQueryWrapper<WmMaterialReceiveLine>()
                    .eq(WmMaterialReceiveLine::getReceiveId, id));
            List<WmMaterialReceiveLineVo> lineVoList = MapstructUtils.convert(lineList, WmMaterialReceiveLineVo.class);

            for (WmMaterialReceiveLineVo lineVo : lineVoList) {
                List<WmMaterialReceiveDetail> detailList = receiveDetailMapper.selectList(
                    new LambdaQueryWrapper<WmMaterialReceiveDetail>()
                        .eq(WmMaterialReceiveDetail::getLineId, lineVo.getLineId()));
                lineVo.setWmMaterialReceiveLineDetailList(
                    MapstructUtils.convert(detailList, WmMaterialReceiveDetailVo.class));
            }

            vo.setWmMaterialReceiveLineList(lineVoList);
            fillShelfByName(List.of(vo));
        }
        return vo;
    }

    /**
     * 新增物料入库单
     *
     * @param bo 入库单业务对象
     * @return 影响行数
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int insertByBo(WmMaterialReceiveBo bo) {
        WmMaterialReceive entity = MapstructUtils.convert(bo, WmMaterialReceive.class);
        if (StringUtils.isBlank(entity.getReceiveCode())) {
            entity.setReceiveCode(autoCodeUtil.genSerialCode("WM_MATERIAL_RECEIVE_CODE", null));
        }
        if (StringUtils.isBlank(entity.getStatus())) {
            entity.setStatus(STATUS_DRAFT);
        }
        int rows = baseMapper.insert(entity);
        insertLinesAndDetails(entity.getReceiveId(), bo.getWmMaterialReceiveLineList());
        return rows;
    }

    /**
     * 修改物料入库单
     *
     * @param bo 入库单业务对象
     * @return 影响行数
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updateByBo(WmMaterialReceiveBo bo) {
        WmMaterialReceive entity = MapstructUtils.convert(bo, WmMaterialReceive.class);
        // 校验状态：只有草稿和已驳回状态才能修改
        WmMaterialReceive existing = baseMapper.selectById(entity.getReceiveId());
        if (existing != null && !STATUS_DRAFT.equals(existing.getStatus()) && !STATUS_REJECTED.equals(existing.getStatus())) {
            throw new ServiceException("当前状态不允许修改，只有草稿和已驳回状态才能修改！");
        }
        deleteLinesAndDetailsByReceiveId(entity.getReceiveId());
        insertLinesAndDetails(entity.getReceiveId(), bo.getWmMaterialReceiveLineList());
        return baseMapper.updateById(entity);
    }

    /**
     * 批量删除物料入库单
     *
     * @param ids 入库单ID数组
     * @return 影响行数
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteByIds(Long[] ids) {
        for (Long receiveId : ids) {
            // 校验状态：只有草稿状态才能删除
            WmMaterialReceive existing = baseMapper.selectById(receiveId);
            if (existing != null && !STATUS_DRAFT.equals(existing.getStatus())) {
                throw new ServiceException("当前状态不允许删除，只有草稿状态才能删除！");
            }
            deleteLinesAndDetailsByReceiveId(receiveId);
        }
        return baseMapper.deleteBatchIds(List.of(ids));
    }

    /**
     * 带校验的更新物料入库单
     *
     * @param bo 入库单业务对象
     * @return 是否成功
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean updateWithValidation(WmMaterialReceiveBo bo) {
        if (StringUtils.isBlank(bo.getReceiveCode())) {
            throw new ServiceException("编号不能为空！");
        }

        LambdaQueryWrapper<WmMaterialReceive> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(WmMaterialReceive::getReceiveCode, bo.getReceiveCode());
        List<WmMaterialReceive> existingReceives = baseMapper.selectList(queryWrapper);

        if (!existingReceives.isEmpty()) {
            WmMaterialReceive existingReceive = existingReceives.get(0);
            Long receiveId = bo.getReceiveId() == null ? -1L : bo.getReceiveId();
            if (!existingReceive.getReceiveId().equals(receiveId)) {
                throw new ServiceException("编号已存在！");
            }
        }

        if ("PENDING_SHELF".equals(bo.getStatus())) {
            if (bo.getWmMaterialReceiveLineList() == null || bo.getWmMaterialReceiveLineList().isEmpty()) {
                throw new ServiceException("入库单行不能为空！");
            }
        }

        if ("PENDING_STORAGE".equals(bo.getStatus())) {
            validateDetailsForApproval(bo);
        }

        WmMaterialReceive entity = MapstructUtils.convert(bo, WmMaterialReceive.class);
        deleteLinesAndDetailsByReceiveId(entity.getReceiveId());
        insertLinesAndDetails(entity.getReceiveId(), bo.getWmMaterialReceiveLineList());
        return baseMapper.updateById(entity) > 0;
    }

    /**
     * 提交物料入库单审批
     *
     * @param receiveIdStr  入库单ID字符串
     * @param selectFlowId  审批流程ID
     * @return 是否成功
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean submit(String receiveIdStr, String selectFlowId) {
        Long receiveId = Long.parseLong(receiveIdStr);
        WmMaterialReceive receive = baseMapper.selectById(receiveId);
        if (receive == null) {
            throw new ServiceException("入库单不存在");
        }

        // 校验状态：只有草稿和已驳回状态才能提交审批
        if (!STATUS_DRAFT.equals(receive.getStatus()) && !STATUS_REJECTED.equals(receive.getStatus())) {
            throw new ServiceException("当前状态不允许提交审批，只有草稿和已驳回状态才能提交！");
        }

        if (StringUtils.isBlank(selectFlowId)) {
            if (receive.getSelectFlowId() != null) {
                selectFlowId = String.valueOf(receive.getSelectFlowId());
            } else {
                throw new ServiceException("请先在编辑页选择审批流程并保存");
            }
        }

        Map<String, Object> variables = new HashMap<>();
        variables.put("dataId", receiveIdStr);
        variables.put("requireTaskOnStart", true);
        variables.put("receiveId", receive.getReceiveId());
        variables.put("receiveCode", receive.getReceiveCode());
        variables.put("receiveName", receive.getReceiveName());
        variables.put("vendorId", receive.getVendorId());
        variables.put("vendorName", receive.getVendorName());
        variables.put("receiveDate", receive.getReceiveDate());
        R<Void> result = processService.startProcessByDataId(receiveIdStr, selectFlowId, "wmMaterialReceiveCrudService", variables);
        if (R.isSuccess(result)) {
            return true;
        }
        throw new ServiceException(result.getMsg());
    }

    /**
     * 获取入库单流程实例ID
     *
     * @param receiveIdStr 入库单ID字符串
     * @return 流程实例ID
     */
    @Override
    public String getProcInsId(String receiveIdStr) {
        try {
            WfMyBusiness business = wfMyBusinessServiceImpl.getByDataId(receiveIdStr);
            if (business != null && StringUtils.isNotBlank(business.getProcessInstanceId())) {
                return business.getProcessInstanceId();
            }
        } catch (Exception ignored) {
        }

        ProcessInstance runtime = runtimeService.createProcessInstanceQuery()
            .processInstanceBusinessKey(receiveIdStr).singleResult();
        if (runtime != null) {
            return runtime.getId();
        }

        HistoricProcessInstance hist = historyService.createHistoricProcessInstanceQuery()
            .processInstanceBusinessKey(receiveIdStr).singleResult();
        return hist != null ? hist.getId() : null;
    }

    /**
     * 确认上架：将入库单从"待上架"(APPROVED)变为"待入库"(SHELVED)
     *
     * @param receiveId 入库单ID
     * @return 是否成功
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean confirmShelf(Long receiveId) {
        WmMaterialReceive receive = baseMapper.selectById(receiveId);
        if (receive == null) {
            throw new ServiceException("入库单不存在");
        }
        if (!STATUS_APPROVED.equals(receive.getStatus())) {
            throw new ServiceException("当前状态不允许上架确认，只有待上架状态才能执行上架确认操作！");
        }

        // 校验必须有上架明细且库位信息完整
        List<WmMaterialReceiveLine> lines = receiveLineMapper.selectList(
            new LambdaQueryWrapper<WmMaterialReceiveLine>()
                .eq(WmMaterialReceiveLine::getReceiveId, receiveId));
        if (lines == null || lines.isEmpty()) {
            throw new ServiceException("入库单行不能为空");
        }

        boolean hasDetail = false;
        for (WmMaterialReceiveLine line : lines) {
            List<WmMaterialReceiveDetail> details = receiveDetailMapper.selectList(
                new LambdaQueryWrapper<WmMaterialReceiveDetail>()
                    .eq(WmMaterialReceiveDetail::getLineId, line.getLineId()));
            if (details != null && !details.isEmpty()) {
                hasDetail = true;
                for (WmMaterialReceiveDetail detail : details) {
                    if (detail.getLocationId() == null) {
                        throw new ServiceException("存在未分配库位的上架明细，请先完善库位信息");
                    }
                }
            }
        }
        if (!hasDetail) {
            throw new ServiceException("请先添加上架明细并分配库位");
        }

        // IQC 门禁：需检物料(iqc_check=1)必须IQC合格才允许上架(待上架->待入库)
        // 按到货通知行校验 iqc_status；无到货通知或无需检料则跳过(无到货通知的收货单由 MES 端手动建 IQC)
        if (receive.getNoticeId() != null) {
            List<WmArrivalNoticeLine> noticeLines = arrivalNoticeLineMapper.selectList(
                new LambdaQueryWrapper<WmArrivalNoticeLine>()
                    .eq(WmArrivalNoticeLine::getNoticeId, receive.getNoticeId())
                    .eq(WmArrivalNoticeLine::getIqcCheck, "1"));
            if (noticeLines != null && !noticeLines.isEmpty()) {
                StringBuilder pending = new StringBuilder();
                StringBuilder unqualified = new StringBuilder();
                for (WmArrivalNoticeLine nl : noticeLines) {
                    if ("UNQUALIFIED".equals(nl.getIqcStatus())) {
                        if (unqualified.length() > 0) {
                            unqualified.append("、");
                        }
                        unqualified.append(nl.getMaterialCode());
                    } else if (!"QUALIFIED".equals(nl.getIqcStatus())) {
                        // null 或其他值 -> 尚未完成检验
                        if (pending.length() > 0) {
                            pending.append("、");
                        }
                        pending.append(nl.getMaterialCode());
                    }
                }
                if (pending.length() > 0) {
                    throw new ServiceException("以下需检物料尚未完成来料检验(IQC)，禁止上架确认：" + pending);
                }
                if (unqualified.length() > 0) {
                    throw new ServiceException("以下物料来料检验(IQC)不合格，禁止上架确认：" + unqualified);
                }
            }
        }

        receive.setStatus(STATUS_SHELVED);
        receive.setShelfBy(LoginHelper.getUserId());
        receive.setShelfTime(new Date());
        receive.setUpdateTime(new Date());
        return baseMapper.updateById(receive) > 0;
    }

    /**
     * 工作流审批回调处理
     * <p>
     * 状态流转：
     * <ul>
     *   <li>doing → APPROVING（审批中，不可修改和操作）</li>
     *   <li>pass → APPROVED（待上架，可执行上架操作）</li>
     *   <li>reject/return → REJECTED（已驳回，可修改后重新提交）</li>
     *   <li>revoke/stop/recall → DRAFT（草稿，可修改和删除）</li>
     * </ul>
     *
     * @param business 工作流业务对象
     */
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void afterFlowHandle(WfMyBusiness business) {
        Long receiveId = Long.parseLong(business.getDataId());
        WmMaterialReceive receive = baseMapper.selectById(receiveId);
        if (receive == null) {
            return;
        }
        String actStatus = business.getActStatus();
        if (ActStatus.doing.equals(actStatus)) {
            // 审批中：审批流程进行中，不可修改和操作
            receive.setStatus(STATUS_APPROVING);
        } else if (ActStatus.pass.equals(actStatus)) {
            // 审批通过：状态变更为"待上架"，等待仓库人员执行上架操作
            // 注意：不再发送StockInMessage，入库过账统一由用户手动触发（executeReceive），
            // 避免审批通过时库位尚未分配导致MQ异步过账失败或重复入库
            receive.setStatus(STATUS_APPROVED);
            baseMapper.updateById(receive);
            // 审批通过后，事务提交后再发送 ReceiveApprovedMessage 给 MES 自动创建 IQC（确保 ERP 状态已持久化）
            registerReceiveApprovedMessage(receive);
            return;
        } else if (ActStatus.reject.equals(actStatus) || ActStatus.retrun.equals(actStatus)) {
            // 审批驳回：已驳回状态，可修改后重新提交
            receive.setStatus(STATUS_REJECTED);
        } else if (ActStatus.revoke.equals(actStatus) || ActStatus.stop.equals(actStatus) || ActStatus.recall.equals(actStatus)) {
            // 撤回/终止：回到草稿状态
            receive.setStatus(STATUS_DRAFT);
        }
        baseMapper.updateById(receive);
    }

    /**
     * 根据业务数据ID获取业务数据
     *
     * @param dataId 业务数据ID
     * @return 业务数据对象
     */
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Object getBusinessDataById(String dataId) {
        return selectById(Long.parseLong(dataId));
    }

    /**
     * 获取流程任务变量
     *
     * @param taskNameId 任务名称ID
     * @param values     变量集合
     * @return 流程任务变量
     */
    @Override
    public Map<String, Object> flowValuesOfTask(String taskNameId, Map<String, Object> values) {
        return null;
    }

    /**
     * 获取流程任务候选人用户名列表
     *
     * @param taskNameId 任务名称ID
     * @param values     变量集合
     * @return 候选人用户名列表
     */
    @Override
    public List<String> flowCandidateUsernamesOfTask(String taskNameId, Map<String, Object> values) {
        return null;
    }

    private void validateDetailsForApproval(WmMaterialReceiveBo bo) {
        StringBuilder errorMsg = new StringBuilder();
        boolean isValid = true;

        if (bo.getWmMaterialReceiveLineList() == null || bo.getWmMaterialReceiveLineList().isEmpty()) {
            throw new ServiceException("入库单行不能为空！");
        }

        for (WmMaterialReceiveLineBo line : bo.getWmMaterialReceiveLineList()) {
            if (line.getWmMaterialReceiveLineDetailList() == null
                || line.getWmMaterialReceiveLineDetailList().isEmpty()) {
                isValid = false;
                errorMsg.append("物料[").append(line.getMaterialName())
                    .append("]未分配对应的明细行！");
            } else {
                for (WmMaterialReceiveDetailBo detail : line.getWmMaterialReceiveLineDetailList()) {
                    if (detail.getQuantity().compareTo(line.getQuantityReceived()) != 0) {
                        isValid = false;
                        errorMsg.append("物料[").append(line.getMaterialName())
                            .append("]明细数量与入库数量不一致！");
                    }
                }
            }
        }

        if (!isValid) {
            throw new ServiceException(errorMsg.toString());
        }
    }

    private void insertLinesAndDetails(Long receiveId, List<WmMaterialReceiveLineBo> lineBoList) {
        if (lineBoList != null && !lineBoList.isEmpty()) {
            List<WmMaterialReceiveLine> lineList = MapstructUtils.convert(lineBoList, WmMaterialReceiveLine.class);
            for (WmMaterialReceiveLine line : lineList) {
                line.setLineId(null);
                line.setReceiveId(receiveId);
            }
            receiveLineMapper.insertBatch(lineList);

            for (int i = 0; i < lineBoList.size(); i++) {
                WmMaterialReceiveLineBo lineBo = lineBoList.get(i);
                if (lineBo.getWmMaterialReceiveLineDetailList() != null
                    && !lineBo.getWmMaterialReceiveLineDetailList().isEmpty()) {
                    List<WmMaterialReceiveDetail> detailList = MapstructUtils.convert(
                        lineBo.getWmMaterialReceiveLineDetailList(), WmMaterialReceiveDetail.class);
                    for (WmMaterialReceiveDetail detail : detailList) {
                        detail.setDetailId(null);
                        detail.setReceiveId(receiveId);
                        detail.setLineId(lineList.get(i).getLineId());
                    }
                    receiveDetailMapper.insertBatch(detailList);
                }
            }
        }
    }

    private void deleteLinesAndDetailsByReceiveId(Long receiveId) {
        List<WmMaterialReceiveLine> lines = receiveLineMapper.selectList(
            new LambdaQueryWrapper<WmMaterialReceiveLine>()
                .eq(WmMaterialReceiveLine::getReceiveId, receiveId));
        for (WmMaterialReceiveLine line : lines) {
            receiveDetailMapper.delete(new LambdaQueryWrapper<WmMaterialReceiveDetail>()
                .eq(WmMaterialReceiveDetail::getLineId, line.getLineId()));
        }
        receiveLineMapper.delete(new LambdaQueryWrapper<WmMaterialReceiveLine>()
            .eq(WmMaterialReceiveLine::getReceiveId, receiveId));
    }

    /**
     * 批量填充上架人姓名
     * 注：需要手动切换到主数据源查询 sys_user 表
     */
    private void fillShelfByName(List<WmMaterialReceiveVo> list) {
        if (list == null || list.isEmpty()) {
            return;
        }
        List<Long> userIds = list.stream()
            .map(WmMaterialReceiveVo::getShelfBy)
            .filter(id -> id != null && id > 0)
            .distinct()
            .toList();
        if (userIds.isEmpty()) {
            return;
        }
        DynamicDataSourceContextHolder.push("master");
        try {
            List<SysUserVo> users = sysUserService.selectUserByIds(userIds, null);
            Map<Long, String> userNameMap = users.stream()
                .collect(HashMap::new, (m, u) -> m.put(u.getUserId(), u.getUserName()), HashMap::putAll);
            for (WmMaterialReceiveVo vo : list) {
                if (vo.getShelfBy() != null) {
                    vo.setShelfByName(userNameMap.getOrDefault(vo.getShelfBy(), ""));
                }
            }
        } finally {
            DynamicDataSourceContextHolder.poll();
        }
    }

    private LambdaQueryWrapper<WmMaterialReceive> buildQueryWrapper(WmMaterialReceiveBo bo) {
        LambdaQueryWrapper<WmMaterialReceive> lqw = Wrappers.<WmMaterialReceive>lambdaQuery()
            .eq(StringUtils.isNotBlank(bo.getReceiveCode()), WmMaterialReceive::getReceiveCode, bo.getReceiveCode())
            .like(StringUtils.isNotBlank(bo.getReceiveName()), WmMaterialReceive::getReceiveName, bo.getReceiveName())
            .eq(bo.getIqcId() != null, WmMaterialReceive::getIqcId, bo.getIqcId())
            .eq(StringUtils.isNotBlank(bo.getIqcCode()), WmMaterialReceive::getIqcCode, bo.getIqcCode())
            .eq(bo.getNoticeId() != null, WmMaterialReceive::getNoticeId, bo.getNoticeId())
            .eq(StringUtils.isNotBlank(bo.getNoticeCode()), WmMaterialReceive::getNoticeCode, bo.getNoticeCode())
            .eq(bo.getPurId() != null, WmMaterialReceive::getPurId, bo.getPurId())
            .eq(StringUtils.isNotBlank(bo.getPurCode()), WmMaterialReceive::getPurCode, bo.getPurCode())
            .eq(bo.getVendorId() != null, WmMaterialReceive::getVendorId, bo.getVendorId())
            .eq(StringUtils.isNotBlank(bo.getVendorCode()), WmMaterialReceive::getVendorCode, bo.getVendorCode())
            .like(StringUtils.isNotBlank(bo.getVendorName()), WmMaterialReceive::getVendorName, bo.getVendorName())
            .eq(StringUtils.isNotBlank(bo.getVendorNick()), WmMaterialReceive::getVendorNick, bo.getVendorNick())
            .eq(bo.getWarehouseId() != null, WmMaterialReceive::getWarehouseId, bo.getWarehouseId())
            .eq(StringUtils.isNotBlank(bo.getWarehouseCode()), WmMaterialReceive::getWarehouseCode, bo.getWarehouseCode())
            .like(StringUtils.isNotBlank(bo.getWarehouseName()), WmMaterialReceive::getWarehouseName, bo.getWarehouseName())
            .eq(bo.getAreaId() != null, WmMaterialReceive::getAreaId, bo.getAreaId())
            .eq(StringUtils.isNotBlank(bo.getAreaCode()), WmMaterialReceive::getAreaCode, bo.getAreaCode())
            .like(StringUtils.isNotBlank(bo.getAreaName()), WmMaterialReceive::getAreaName, bo.getAreaName())
            .eq(bo.getLocationId() != null, WmMaterialReceive::getLocationId, bo.getLocationId())
            .eq(StringUtils.isNotBlank(bo.getLocationCode()), WmMaterialReceive::getLocationCode, bo.getLocationCode())
            .like(StringUtils.isNotBlank(bo.getLocationName()), WmMaterialReceive::getLocationName, bo.getLocationName())
            .eq(bo.getReceiveDate() != null, WmMaterialReceive::getReceiveDate, bo.getReceiveDate())
            .eq(StringUtils.isNotBlank(bo.getStatus()), WmMaterialReceive::getStatus, bo.getStatus());
        return lqw;
    }

    /**
     * 注册 afterCommit 回调，事务提交后按"需检物料"逐条发 ReceiveApprovedMessage 给 MES 自动创建 IQC。
     * <p>
     * 触发点：收货单工作流审批通过（afterFlowHandle 的 ActStatus.pass）。
     * <p>
     * 物料/数量来源：wm_material_receive_line（收货行，收货单创建时即有），
     * 不再依赖 wm_material_receive_detail（上架明细，审批后才补），避免"审批时明细为空 -> MQ 被跳过"的时序耦合。
     * <p>
     * 门控：到货通知行 iqc_check="1" 的物料才发；同一收货单同一物料聚合并去重（仅发一条）。
     * 无到货通知关联的收货单跳过（由 MES 端手动建 IQC）。
     * 显式设置 tenantId 避免消费端退化为 000000（参考 sendWorkOrderReleaseMessage 同类缺陷）。
     */
    private void registerReceiveApprovedMessage(WmMaterialReceive receive) {
        try {
            // 无到货通知关联：跳过（无到货通知的收货单由 MES 端手动建 IQC）
            if (receive.getNoticeId() == null) {
                log.warn("收货单 {} 无到货通知关联，跳过 IQC 自动创建 MQ 投递", receive.getReceiveId());
                return;
            }

            // 收集到货通知行中需检（iqcCheck="1"）的物料ID
            List<WmArrivalNoticeLine> noticeLines = arrivalNoticeLineMapper.selectList(
                new LambdaQueryWrapper<WmArrivalNoticeLine>()
                    .eq(WmArrivalNoticeLine::getNoticeId, receive.getNoticeId()));
            if (noticeLines == null || noticeLines.isEmpty()) {
                log.warn("收货单 {} 关联到货通知无明细行，跳过 IQC 自动创建 MQ 投递", receive.getReceiveId());
                return;
            }
            Set<Long> needIqcMaterialIds = new HashSet<>();
            for (WmArrivalNoticeLine l : noticeLines) {
                if ("1".equals(l.getIqcCheck()) && l.getMaterialId() != null) {
                    needIqcMaterialIds.add(l.getMaterialId());
                }
            }
            if (needIqcMaterialIds.isEmpty()) {
                log.info("收货单 {} 所有关联到货通知行均免检，跳过 IQC 自动创建 MQ 投递", receive.getReceiveId());
                return;
            }

            // 收货行：按需检物料聚合数量（同物料多行求和），首行用于取物料编码/名称
            List<WmMaterialReceiveLine> receiveLines = receiveLineMapper.selectList(
                new LambdaQueryWrapper<WmMaterialReceiveLine>()
                    .eq(WmMaterialReceiveLine::getReceiveId, receive.getReceiveId()));
            if (receiveLines == null || receiveLines.isEmpty()) {
                log.warn("收货单 {} 无收货行，跳过 IQC 自动创建 MQ 投递", receive.getReceiveId());
                return;
            }
            Map<Long, BigDecimal> qtyByMaterial = new LinkedHashMap<>();
            Map<Long, WmMaterialReceiveLine> firstLineByMaterial = new LinkedHashMap<>();
            for (WmMaterialReceiveLine line : receiveLines) {
                if (line.getMaterialId() == null || !needIqcMaterialIds.contains(line.getMaterialId())) {
                    continue;
                }
                BigDecimal qty = line.getQuantityReceived() != null ? line.getQuantityReceived() : BigDecimal.ZERO;
                qtyByMaterial.merge(line.getMaterialId(), qty, BigDecimal::add);
                firstLineByMaterial.putIfAbsent(line.getMaterialId(), line);
            }
            if (firstLineByMaterial.isEmpty()) {
                log.warn("收货单 {} 需检物料无对应收货行，跳过 IQC 自动创建 MQ 投递", receive.getReceiveId());
                return;
            }

            // 显式设置 tenantId，避免消费端退化为 000000
            long tenantIdVal = 0L;
            String tenantIdStr = TenantHelper.getTenantId();
            if (tenantIdStr != null) {
                try {
                    tenantIdVal = Long.parseLong(tenantIdStr);
                } catch (NumberFormatException ignore) {
                    tenantIdVal = 0L;
                }
            }
            final Long tenantId = tenantIdVal;

            // 逐个需检物料构建消息（一物料一条 -> MES 建一条 IQC）
            List<ReceiveApprovedMessage> messages = new ArrayList<>();
            for (Map.Entry<Long, WmMaterialReceiveLine> entry : firstLineByMaterial.entrySet()) {
                Long materialId = entry.getKey();
                WmMaterialReceiveLine line = entry.getValue();
                ReceiveApprovedMessage message = new ReceiveApprovedMessage();
                message.setReceiveId(receive.getReceiveId());
                message.setReceiveCode(receive.getReceiveCode());
                message.setMaterialId(materialId);
                message.setMaterialCode(line.getMaterialCode());
                message.setMaterialName(line.getMaterialName());
                message.setVendorId(receive.getVendorId());
                message.setVendorName(receive.getVendorName());
                message.setQuantity(qtyByMaterial.get(materialId));
                message.setSourceModule("ERP");
                message.setTenantId(tenantId);
                messages.add(message);
            }

            final List<ReceiveApprovedMessage> toSend = messages;
            TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        for (ReceiveApprovedMessage message : toSend) {
                            try {
                                messageProducer.send(
                                    ExchangeConstants.TOPIC_EXCHANGE,
                                    RoutingKeyConstants.MES_IQC_CREATE,
                                    message);
                                log.info("收货单审批通过 MQ 已发送: receiveId={}, materialId={}",
                                    receive.getReceiveId(), message.getMaterialId());
                            } catch (Exception e) {
                                log.error("收货单审批通过 MQ 发送失败: receiveId={}, materialId={}",
                                    receive.getReceiveId(), message.getMaterialId(), e);
                                // 单条失败由 MQ 补偿任务兜底，不影响其他物料
                            }
                        }
                    }
                });
        } catch (Exception e) {
            log.error("注册收货审批 MQ 失败: receiveId={}", receive.getReceiveId(), e);
        }
    }
}
