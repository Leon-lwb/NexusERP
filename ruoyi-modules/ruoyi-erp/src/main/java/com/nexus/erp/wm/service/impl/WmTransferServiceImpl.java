package com.nexus.erp.wm.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.nexus.common.core.domain.R;
import com.nexus.common.core.utils.MapstructUtils;
import com.nexus.common.core.utils.StringUtils;
import com.nexus.common.flowable.core.domain.ActStatus;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.erp.wm.domain.WmTransfer;
import com.nexus.erp.wm.domain.WmTransferLine;
import com.nexus.erp.wm.domain.bo.WmTransferBo;
import com.nexus.erp.wm.domain.vo.WmTransferVo;
import com.nexus.erp.wm.mapper.WmTransferLineMapper;
import com.nexus.erp.wm.mapper.WmTransferMapper;
import com.nexus.erp.wm.service.IWmTransferService;
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
import com.nexus.system.strategy.AutoCodeUtil;

import java.math.BigDecimal;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@RequiredArgsConstructor
@DS("erp")
@Service("wmTransferService")
public class WmTransferServiceImpl implements IWmTransferService {

    private final WmTransferMapper baseMapper;
    private final WmTransferLineMapper lineMapper;
    private final WmStockOperationServiceImpl stockOperationService;
    private final AutoCodeUtil autoCodeUtil;
    private final IWfProcessService processService;
    private final WfMyBusinessServiceImpl wfMyBusinessServiceImpl;
    private final RuntimeService runtimeService;
    private final HistoryService historyService;

    @Override
    public TableDataInfo<WmTransferVo> selectPageList(WmTransferBo bo, PageQuery pageQuery) {
        LambdaQueryWrapper<WmTransfer> lqw = buildQueryWrapper(bo);
        Page<WmTransferVo> page = baseMapper.selectVoPage(pageQuery.build(), lqw);
        return TableDataInfo.build(page);
    }

    @Override
    public List<WmTransferVo> selectList(WmTransferBo bo) {
        return baseMapper.selectVoList(buildQueryWrapper(bo));
    }

    @Override
    public WmTransferVo selectById(Long id) {
        WmTransferVo vo = baseMapper.selectVoById(id);
        if (vo != null && vo.getTransferId() != null) {
            LambdaQueryWrapper<WmTransferLine> lineLqw = Wrappers.lambdaQuery();
            lineLqw.eq(WmTransferLine::getTransferId, vo.getTransferId());
            vo.setWmTransferLineList(lineMapper.selectList(lineLqw));
        }
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int insertByBo(WmTransferBo bo) {
        WmTransfer entity = MapstructUtils.convert(bo, WmTransfer.class);
        if (StringUtils.isBlank(entity.getTransferNo())) {
            entity.setTransferNo(autoCodeUtil.genSerialCode("WM_TRANSFER_CODE", null));
        }
        entity.setStatus(STATUS_DRAFT);
        int rows = baseMapper.insert(entity);
        if (bo.getWmTransferLineList() != null) {
            for (WmTransferLine line : bo.getWmTransferLineList()) {
                line.setTransferId(entity.getTransferId());
                lineMapper.insert(line);
            }
        }
        return rows;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updateByBo(WmTransferBo bo) {
        WmTransfer entity = MapstructUtils.convert(bo, WmTransfer.class);
        deleteLinesByTransferId(entity.getTransferId());
        if (bo.getWmTransferLineList() != null) {
            for (WmTransferLine line : bo.getWmTransferLineList()) {
                line.setTransferId(entity.getTransferId());
                lineMapper.insert(line);
            }
        }
        return baseMapper.updateById(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteByIds(Long[] ids) {
        for (Long id : ids) {
            deleteLinesByTransferId(id);
        }
        return baseMapper.deleteBatchIds(List.of(ids));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void execute(Long transferId) {
        WmTransfer transfer = baseMapper.selectById(transferId);
        if (transfer == null) {
            throw new RuntimeException("调拨单不存在");
        }
        if (STATUS_EXECUTED.equals(transfer.getStatus())) {
            throw new RuntimeException("调拨单已执行");
        }

        LambdaQueryWrapper<WmTransferLine> lineLqw = Wrappers.lambdaQuery();
        lineLqw.eq(WmTransferLine::getTransferId, transferId);
        List<WmTransferLine> lines = lineMapper.selectList(lineLqw);

        if (lines == null || lines.isEmpty()) {
            throw new RuntimeException("调拨单无明细行");
        }

        for (WmTransferLine line : lines) {
            stockOperationService.postTransfer(line);
        }

        transfer.setStatus(STATUS_EXECUTED);
        transfer.setExecuteDate(new Date());
        baseMapper.updateById(transfer);
        log.info("调拨单执行完成, transferId={}", transferId);
    }

    // ==================== Flowable 工作流集成 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean submit(String transferIdStr, String selectFlowId) {
        Long transferId = Long.parseLong(transferIdStr);
        WmTransfer entity = baseMapper.selectById(transferId);
        if (entity == null) {
            throw new RuntimeException("调拨单不存在");
        }
        if (!STATUS_DRAFT.equals(entity.getStatus())) {
            throw new RuntimeException("只有草稿状态的调拨单才能提交审批");
        }
        if (StringUtils.isBlank(selectFlowId)) {
            if (entity.getSelectFlowId() != null) {
                selectFlowId = String.valueOf(entity.getSelectFlowId());
            } else {
                throw new RuntimeException("请先在编辑页选择审批流程并保存");
            }
        }

        Map<String, Object> variables = new HashMap<>();
        variables.put("dataId", transferIdStr);
        variables.put("requireTaskOnStart", true);
        variables.put("transferId", entity.getTransferId());
        variables.put("transferNo", entity.getTransferNo());
        R<Void> result = processService.startProcessByDataId(transferIdStr, selectFlowId, "wmTransferService", variables);
        if (R.isSuccess(result)) {
            return true;
        }
        throw new RuntimeException(result.getMsg());
    }

    @Override
    public String getProcInsId(String transferIdStr) {
        try {
            WfMyBusiness business = wfMyBusinessServiceImpl.getByDataId(transferIdStr);
            if (business != null && StringUtils.isNotBlank(business.getProcessInstanceId())) {
                return business.getProcessInstanceId();
            }
        } catch (Exception ignored) {
        }
        ProcessInstance runtime = runtimeService.createProcessInstanceQuery()
            .processInstanceBusinessKey(transferIdStr).singleResult();
        if (runtime != null) {
            return runtime.getId();
        }
        HistoricProcessInstance hist = historyService.createHistoricProcessInstanceQuery()
            .processInstanceBusinessKey(transferIdStr).singleResult();
        return hist != null ? hist.getId() : null;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void afterFlowHandle(WfMyBusiness business) {
        Long transferId = Long.parseLong(business.getDataId());
        WmTransfer entity = baseMapper.selectById(transferId);
        if (entity == null) return;
        String actStatus = business.getActStatus();
        if (ActStatus.doing.equals(actStatus)) {
            entity.setStatus(STATUS_APPROVING);
        } else if (ActStatus.pass.equals(actStatus)) {
            entity.setStatus(STATUS_APPROVED);
        } else if (ActStatus.reject.equals(actStatus) || ActStatus.retrun.equals(actStatus)) {
            entity.setStatus(STATUS_DRAFT);
        } else if (ActStatus.revoke.equals(actStatus) || ActStatus.stop.equals(actStatus) || ActStatus.recall.equals(actStatus)) {
            entity.setStatus(STATUS_DRAFT);
        }
        baseMapper.updateById(entity);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Object getBusinessDataById(String dataId) {
        return selectById(Long.parseLong(dataId));
    }

    @Override
    public Map<String, Object> flowValuesOfTask(String taskNameId, Map<String, Object> values) {
        return null;
    }

    @Override
    public List<String> flowCandidateUsernamesOfTask(String taskNameId, Map<String, Object> values) {
        return null;
    }

    private void deleteLinesByTransferId(Long transferId) {
        if (transferId != null) {
            LambdaQueryWrapper<WmTransferLine> lqw = Wrappers.lambdaQuery();
            lqw.eq(WmTransferLine::getTransferId, transferId);
            lineMapper.delete(lqw);
        }
    }

    private LambdaQueryWrapper<WmTransfer> buildQueryWrapper(WmTransferBo bo) {
        LambdaQueryWrapper<WmTransfer> lqw = Wrappers.lambdaQuery();
        lqw.eq(StringUtils.isNotBlank(bo.getTransferNo()), WmTransfer::getTransferNo, bo.getTransferNo());
        lqw.eq(bo.getFromWarehouseId() != null, WmTransfer::getFromWarehouseId, bo.getFromWarehouseId());
        lqw.eq(bo.getToWarehouseId() != null, WmTransfer::getToWarehouseId, bo.getToWarehouseId());
        lqw.eq(StringUtils.isNotBlank(bo.getTransferType()), WmTransfer::getTransferType, bo.getTransferType());
        lqw.eq(StringUtils.isNotBlank(bo.getStatus()), WmTransfer::getStatus, bo.getStatus());
        lqw.orderByDesc(WmTransfer::getCreateTime);
        return lqw;
    }
}
