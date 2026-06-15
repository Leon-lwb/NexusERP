package com.nexus.erp.pu.service.impl;

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
import com.nexus.erp.pu.domain.PuContract;
import com.nexus.erp.pu.domain.PuContractLine;
import com.nexus.erp.pu.domain.bo.PuContractBo;
import com.nexus.erp.pu.domain.vo.PuContractVo;
import com.nexus.erp.pu.mapper.PuContractLineMapper;
import com.nexus.erp.pu.mapper.PuContractMapper;
import com.nexus.erp.pu.service.IPuContractService;
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
 * 采购合同Service业务层处理
 *
 * @author nexus
 * @date 2026-06-15
 */
@Slf4j
@RequiredArgsConstructor
@DS("erp")
@Service("puContractService")
public class PuContractServiceImpl implements IPuContractService {

    private final PuContractMapper baseMapper;
    private final PuContractLineMapper lineMapper;
    private final IWfProcessService processService;
    private final WfMyBusinessServiceImpl wfMyBusinessServiceImpl;
    private final RuntimeService runtimeService;
    private final HistoryService historyService;

    @Override
    public TableDataInfo<PuContractVo> selectPageList(PuContractBo bo, PageQuery pageQuery) {
        LambdaQueryWrapper<PuContract> lqw = buildQueryWrapper(bo);
        Page<PuContractVo> page = baseMapper.selectVoPage(pageQuery.build(), lqw);
        return TableDataInfo.build(page);
    }

    @Override
    public List<PuContractVo> selectList(PuContractBo bo) {
        return baseMapper.selectVoList(buildQueryWrapper(bo));
    }

    @Override
    public PuContractVo selectById(Long id) {
        PuContractVo vo = baseMapper.selectVoById(id);
        if (vo != null) {
            LambdaQueryWrapper<PuContractLine> lqw = Wrappers.lambdaQuery();
            lqw.eq(PuContractLine::getContractId, vo.getContractId());
            lqw.orderByAsc(PuContractLine::getSortOrder);
            vo.setPuContractLineList(lineMapper.selectList(lqw));
        }
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int insertByBo(PuContractBo bo) {
        PuContract entity = MapstructUtils.convert(bo, PuContract.class);
        if (StringUtils.isBlank(entity.getStatus())) {
            entity.setStatus(STATUS_DRAFT);
        }
        int rows = baseMapper.insert(entity);
        if (bo.getPuContractLineList() != null && !bo.getPuContractLineList().isEmpty()) {
            for (PuContractLine line : bo.getPuContractLineList()) {
                line.setContractId(entity.getContractId());
                lineMapper.insert(line);
            }
        }
        return rows;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updateByBo(PuContractBo bo) {
        PuContract existing = baseMapper.selectById(bo.getContractId());
        if (existing != null && !STATUS_DRAFT.equals(existing.getStatus()) && !STATUS_REJECTED.equals(existing.getStatus())) {
            throw new ServiceException("当前状态【{}】不允许修改合同", existing.getStatus());
        }
        PuContract entity = MapstructUtils.convert(bo, PuContract.class);
        int rows = baseMapper.updateById(entity);
        if (bo.getPuContractLineList() != null) {
            lineMapper.delete(Wrappers.lambdaQuery(PuContractLine.class)
                .eq(PuContractLine::getContractId, entity.getContractId()));
            for (PuContractLine line : bo.getPuContractLineList()) {
                line.setContractId(entity.getContractId());
                lineMapper.insert(line);
            }
        }
        return rows;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteByIds(Long[] ids) {
        for (Long id : ids) {
            PuContract existing = baseMapper.selectById(id);
            if (existing != null && !STATUS_DRAFT.equals(existing.getStatus())) {
                throw new ServiceException("合同【{}】当前状态不允许删除", existing.getContractNo());
            }
            lineMapper.delete(Wrappers.lambdaQuery(PuContractLine.class)
                .eq(PuContractLine::getContractId, id));
        }
        return baseMapper.deleteBatchIds(List.of(ids));
    }

    // ==================== Flowable 工作流集成 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean submit(String contractIdStr, String selectFlowId) {
        Long contractId = Long.parseLong(contractIdStr);
        PuContract contract = baseMapper.selectById(contractId);
        if (contract == null) {
            throw new ServiceException("采购合同不存在");
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
        variables.put("vendorName", contract.getVendorName());
        variables.put("totalAmount", contract.getTotalAmount());
        R<Void> result = processService.startProcessByDataId(contractIdStr, selectFlowId, "puContractService", variables);
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
        PuContract contract = baseMapper.selectById(contractId);
        if (contract == null) {
            return;
        }
        String actStatus = business.getActStatus();
        if (ActStatus.doing.equals(actStatus)) {
            contract.setStatus(STATUS_APPROVING);
        } else if (ActStatus.pass.equals(actStatus)) {
            contract.setStatus(STATUS_APPROVED);
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

    private LambdaQueryWrapper<PuContract> buildQueryWrapper(PuContractBo bo) {
        LambdaQueryWrapper<PuContract> lqw = Wrappers.lambdaQuery();
        lqw.like(StringUtils.isNotBlank(bo.getContractNo()), PuContract::getContractNo, bo.getContractNo());
        lqw.like(StringUtils.isNotBlank(bo.getContractName()), PuContract::getContractName, bo.getContractName());
        lqw.eq(bo.getVendorId() != null, PuContract::getVendorId, bo.getVendorId());
        lqw.like(StringUtils.isNotBlank(bo.getVendorName()), PuContract::getVendorName, bo.getVendorName());
        lqw.eq(StringUtils.isNotBlank(bo.getStatus()), PuContract::getStatus, bo.getStatus());
        lqw.eq(StringUtils.isNotBlank(bo.getContractType()), PuContract::getContractType, bo.getContractType());
        lqw.orderByDesc(PuContract::getCreateTime);
        return lqw;
    }
}
