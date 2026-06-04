package com.nexus.erp.wm.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import com.nexus.common.core.domain.R;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.core.utils.MapstructUtils;
import com.nexus.common.core.utils.StringUtils;
import com.nexus.common.flowable.core.domain.ActStatus;
import com.nexus.common.mq.constant.ExchangeConstants;
import com.nexus.common.mq.constant.RoutingKeyConstants;
import com.nexus.common.mq.producer.ReliableMessageProducer;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.erp.mq.message.StockInMessage;
import com.nexus.erp.wm.domain.WmMaterialReceive;
import com.nexus.erp.wm.domain.WmMaterialReceiveDetail;
import com.nexus.erp.wm.domain.WmMaterialReceiveLine;
import com.nexus.erp.wm.domain.bo.WmMaterialReceiveBo;
import com.nexus.erp.wm.domain.bo.WmMaterialReceiveDetailBo;
import com.nexus.erp.wm.domain.bo.WmMaterialReceiveLineBo;
import com.nexus.erp.wm.domain.vo.WmMaterialReceiveDetailVo;
import com.nexus.erp.wm.domain.vo.WmMaterialReceiveLineVo;
import com.nexus.erp.wm.domain.vo.WmMaterialReceiveVo;
import com.nexus.erp.wm.mapper.WmMaterialReceiveDetailMapper;
import com.nexus.erp.wm.mapper.WmMaterialReceiveLineMapper;
import com.nexus.erp.wm.mapper.WmMaterialReceiveMapper;
import com.nexus.erp.wm.service.IWmMaterialReceiveCrudService;
import com.nexus.erp.wm.service.IWmMaterialReceiveExecutorService;
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

import com.baomidou.dynamic.datasource.annotation.DS;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 物料入库单CRUD Service实现类，负责基础CRUD、校验和工作流回调
 *
 * @author nexus
 */
@RequiredArgsConstructor
@DS("erp")
@Service("wmMaterialReceiveCrudService")
public class WmMaterialReceiveCrudServiceImpl implements IWmMaterialReceiveCrudService {

    private final WmMaterialReceiveMapper baseMapper;
    private final WmMaterialReceiveLineMapper receiveLineMapper;
    private final WmMaterialReceiveDetailMapper receiveDetailMapper;
    private final IWfProcessService processService;
    private final WfMyBusinessServiceImpl wfMyBusinessServiceImpl;
    private final RuntimeService runtimeService;
    private final HistoryService historyService;
    private final IWmMaterialReceiveExecutorService executorService;
    private final ReliableMessageProducer messageProducer;

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
        return baseMapper.selectVoList(buildQueryWrapper(bo));
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
            // 审批通过：待上架状态，可执行上架操作
            receive.setStatus(STATUS_APPROVED);
            baseMapper.updateById(receive);
            StockInMessage stockInMsg = new StockInMessage();
            stockInMsg.setReceiveId(receiveId);
            stockInMsg.setSourceType("PURCHASE");
            stockInMsg.setSourceModule("wm");
            messageProducer.send(ExchangeConstants.DIRECT_EXCHANGE,
                RoutingKeyConstants.STOCK_IN_RECEIVE, stockInMsg);
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
}
