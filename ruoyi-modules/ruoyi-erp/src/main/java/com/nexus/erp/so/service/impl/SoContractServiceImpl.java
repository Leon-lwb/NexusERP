package com.nexus.erp.so.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
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
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.erp.so.domain.SoContract;
import com.nexus.erp.so.domain.SoContractLine;
import com.nexus.erp.so.domain.bo.SoContractBo;
import com.nexus.erp.so.domain.vo.SoContractVo;
import com.nexus.erp.so.mapper.SoContractLineMapper;
import com.nexus.erp.so.mapper.SoContractMapper;
import com.nexus.erp.so.service.ISoContractService;
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

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 销售合同Service业务层处理
 *
 * @author nexus
 * @date 2026-06-14
 */
@Slf4j
@RequiredArgsConstructor
@DS("erp")
@Service("soContractService")
public class SoContractServiceImpl implements ISoContractService {

    private final SoContractMapper baseMapper;
    private final SoContractLineMapper lineMapper;
    private final IWfProcessService processService;
    private final WfMyBusinessServiceImpl wfMyBusinessServiceImpl;
    private final RuntimeService runtimeService;
    private final HistoryService historyService;

    /**
     * 分页查询销售合同列表
     */
    @Override
    public TableDataInfo<SoContractVo> selectPageList(SoContractBo bo, PageQuery pageQuery) {
        LambdaQueryWrapper<SoContract> lqw = buildQueryWrapper(bo);
        Page<SoContractVo> page = baseMapper.selectVoPage(pageQuery.build(), lqw);
        return TableDataInfo.build(page);
    }

    /**
     * 查询销售合同列表（不分页）
     */
    @Override
    public List<SoContractVo> selectList(SoContractBo bo) {
        return baseMapper.selectVoList(buildQueryWrapper(bo));
    }

    /**
     * 根据ID查询销售合同（含明细行）
     */
    @Override
    public SoContractVo selectById(Long id) {
        SoContractVo vo = baseMapper.selectVoById(id);
        if (vo != null) {
            LambdaQueryWrapper<SoContractLine> lqw = Wrappers.lambdaQuery();
            lqw.eq(SoContractLine::getContractId, vo.getContractId());
            lqw.orderByAsc(SoContractLine::getSortOrder);
            vo.setSoContractLineList(lineMapper.selectList(lqw));
        }
        return vo;
    }

    /**
     * 新增销售合同（含明细行），默认状态为草稿
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int insertByBo(SoContractBo bo) {
        SoContract entity = MapstructUtils.convert(bo, SoContract.class);
        if (StringUtils.isBlank(entity.getStatus())) {
            entity.setStatus(STATUS_DRAFT);
        }
        int rows = baseMapper.insert(entity);
        if (bo.getSoContractLineList() != null && !bo.getSoContractLineList().isEmpty()) {
            for (SoContractLine line : bo.getSoContractLineList()) {
                line.setContractId(entity.getContractId());
                lineMapper.insert(line);
            }
        }
        return rows;
    }

    /**
     * 修改销售合同（含明细行），仅草稿和已驳回状态可编辑
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updateByBo(SoContractBo bo) {
        SoContract existing = baseMapper.selectById(bo.getContractId());
        if (existing != null && !STATUS_DRAFT.equals(existing.getStatus()) && !STATUS_REJECTED.equals(existing.getStatus())) {
            throw new ServiceException("当前状态【{}】不允许修改合同", existing.getStatus());
        }
        SoContract entity = MapstructUtils.convert(bo, SoContract.class);
        int rows = baseMapper.updateById(entity);
        if (bo.getSoContractLineList() != null) {
            lineMapper.delete(Wrappers.lambdaQuery(SoContractLine.class)
                .eq(SoContractLine::getContractId, entity.getContractId()));
            for (SoContractLine line : bo.getSoContractLineList()) {
                line.setContractId(entity.getContractId());
                lineMapper.insert(line);
            }
        }
        return rows;
    }

    /**
     * 批量删除销售合同（含明细行），仅草稿状态可删除
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteByIds(Long[] ids) {
        for (Long id : ids) {
            SoContract existing = baseMapper.selectById(id);
            if (existing != null && !STATUS_DRAFT.equals(existing.getStatus())) {
                throw new ServiceException("合同【{}】当前状态不允许删除", existing.getContractNo());
            }
            lineMapper.delete(Wrappers.lambdaQuery(SoContractLine.class)
                .eq(SoContractLine::getContractId, id));
        }
        return baseMapper.deleteBatchIds(List.of(ids));
    }

    // ==================== Flowable 工作流集成 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean submit(String contractIdStr, String selectFlowId) {
        Long contractId = Long.parseLong(contractIdStr);
        SoContract contract = baseMapper.selectById(contractId);
        if (contract == null) {
            throw new ServiceException("销售合同不存在");
        }

        if (StringUtils.isBlank(selectFlowId)) {
            if (contract.getSelectFlowId() != null) {
                selectFlowId = String.valueOf(contract.getSelectFlowId());
            } else {
                throw new ServiceException("请先在编辑页选择审批流程并保存");
            }
        }

        Map<String, Object> variables = new HashMap<>();
        variables.put("dataId", contractIdStr);
        variables.put("requireTaskOnStart", true);
        variables.put("contractId", contract.getContractId());
        variables.put("contractNo", contract.getContractNo());
        variables.put("contractName", contract.getContractName());
        variables.put("clientName", contract.getClientName());
        variables.put("totalAmount", contract.getTotalAmount());
        R<Void> result = processService.startProcessByDataId(contractIdStr, selectFlowId, "soContractService", variables);
        if (R.isSuccess(result)) {
            return true;
        }
        throw new ServiceException(result.getMsg());
    }

    @Override
    public String getProcInsId(String contractIdStr) {
        try {
            WfMyBusiness business = wfMyBusinessServiceImpl.getByDataId(contractIdStr);
            if (business != null && StringUtils.isNotBlank(business.getProcessInstanceId())) {
                return business.getProcessInstanceId();
            }
        } catch (Exception ignored) {
        }

        ProcessInstance runtime = runtimeService.createProcessInstanceQuery()
            .processInstanceBusinessKey(contractIdStr).singleResult();
        if (runtime != null) {
            return runtime.getId();
        }

        HistoricProcessInstance hist = historyService.createHistoricProcessInstanceQuery()
            .processInstanceBusinessKey(contractIdStr).singleResult();
        return hist != null ? hist.getId() : null;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void afterFlowHandle(WfMyBusiness business) {
        Long contractId = Long.parseLong(business.getDataId());
        SoContract contract = baseMapper.selectById(contractId);
        if (contract == null) {
            return;
        }
        String actStatus = business.getActStatus();
        if (ActStatus.doing.equals(actStatus)) {
            contract.setStatus(STATUS_APPROVING);
        } else if (ActStatus.pass.equals(actStatus)) {
            contract.setStatus(STATUS_EFFECTIVE);
        } else if (ActStatus.reject.equals(actStatus) || ActStatus.retrun.equals(actStatus)) {
            contract.setStatus(STATUS_REJECTED);
        } else if (ActStatus.revoke.equals(actStatus) || ActStatus.stop.equals(actStatus) || ActStatus.recall.equals(actStatus)) {
            contract.setStatus(STATUS_DRAFT);
        }
        baseMapper.updateById(contract);
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

    /**
     * 构建查询条件
     */
    private LambdaQueryWrapper<SoContract> buildQueryWrapper(SoContractBo bo) {
        LambdaQueryWrapper<SoContract> lqw = Wrappers.lambdaQuery();
        lqw.like(StringUtils.isNotBlank(bo.getContractNo()), SoContract::getContractNo, bo.getContractNo());
        lqw.like(StringUtils.isNotBlank(bo.getContractName()), SoContract::getContractName, bo.getContractName());
        lqw.eq(bo.getClientId() != null, SoContract::getClientId, bo.getClientId());
        lqw.like(StringUtils.isNotBlank(bo.getClientName()), SoContract::getClientName, bo.getClientName());
        lqw.eq(StringUtils.isNotBlank(bo.getStatus()), SoContract::getStatus, bo.getStatus());
        lqw.eq(StringUtils.isNotBlank(bo.getContractType()), SoContract::getContractType, bo.getContractType());
        lqw.orderByDesc(SoContract::getCreateTime);
        return lqw;
    }
}
