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
import com.nexus.erp.so.domain.SoInvoice;
import com.nexus.erp.so.domain.SoInvoiceLine;
import com.nexus.erp.so.domain.bo.SoInvoiceBo;
import com.nexus.erp.so.domain.vo.SoInvoiceVo;
import com.nexus.erp.so.mapper.SoInvoiceLineMapper;
import com.nexus.erp.so.mapper.SoInvoiceMapper;
import com.nexus.erp.so.service.ISoInvoiceService;
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
 * 销售发票Service业务层处理
 *
 * @author nexus
 * @date 2026-06-14
 */
@Slf4j
@RequiredArgsConstructor
@DS("erp")
@Service("soInvoiceService")
public class SoInvoiceServiceImpl implements ISoInvoiceService {

    private final SoInvoiceMapper baseMapper;
    private final SoInvoiceLineMapper lineMapper;
    private final IWfProcessService processService;
    private final WfMyBusinessServiceImpl wfMyBusinessServiceImpl;
    private final RuntimeService runtimeService;
    private final HistoryService historyService;

    /**
     * 分页查询销售发票列表
     */
    @Override
    public TableDataInfo<SoInvoiceVo> selectPageList(SoInvoiceBo bo, PageQuery pageQuery) {
        LambdaQueryWrapper<SoInvoice> lqw = buildQueryWrapper(bo);
        Page<SoInvoiceVo> page = baseMapper.selectVoPage(pageQuery.build(), lqw);
        return TableDataInfo.build(page);
    }

    /**
     * 查询销售发票列表（不分页）
     */
    @Override
    public List<SoInvoiceVo> selectList(SoInvoiceBo bo) {
        return baseMapper.selectVoList(buildQueryWrapper(bo));
    }

    /**
     * 根据ID查询销售发票（含明细行）
     */
    @Override
    public SoInvoiceVo selectById(Long id) {
        SoInvoiceVo vo = baseMapper.selectVoById(id);
        if (vo != null) {
            LambdaQueryWrapper<SoInvoiceLine> lqw = Wrappers.lambdaQuery();
            lqw.eq(SoInvoiceLine::getInvoiceId, vo.getInvoiceId());
            lqw.orderByAsc(SoInvoiceLine::getSortOrder);
            vo.setSoInvoiceLineList(lineMapper.selectList(lqw));
        }
        return vo;
    }

    /**
     * 新增销售发票（含明细行），默认状态为草稿
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int insertByBo(SoInvoiceBo bo) {
        SoInvoice entity = MapstructUtils.convert(bo, SoInvoice.class);
        if (StringUtils.isBlank(entity.getStatus())) {
            entity.setStatus(STATUS_DRAFT);
        }
        int rows = baseMapper.insert(entity);
        if (bo.getSoInvoiceLineList() != null && !bo.getSoInvoiceLineList().isEmpty()) {
            for (SoInvoiceLine line : bo.getSoInvoiceLineList()) {
                line.setInvoiceId(entity.getInvoiceId());
                lineMapper.insert(line);
            }
        }
        return rows;
    }

    /**
     * 修改销售发票（含明细行），仅草稿和已驳回状态可编辑
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updateByBo(SoInvoiceBo bo) {
        SoInvoice existing = baseMapper.selectById(bo.getInvoiceId());
        if (existing != null && !STATUS_DRAFT.equals(existing.getStatus()) && !STATUS_REJECTED.equals(existing.getStatus())) {
            throw new ServiceException("当前状态【{}】不允许修改发票", existing.getStatus());
        }
        SoInvoice entity = MapstructUtils.convert(bo, SoInvoice.class);
        int rows = baseMapper.updateById(entity);
        if (bo.getSoInvoiceLineList() != null) {
            lineMapper.delete(Wrappers.lambdaQuery(SoInvoiceLine.class)
                .eq(SoInvoiceLine::getInvoiceId, entity.getInvoiceId()));
            for (SoInvoiceLine line : bo.getSoInvoiceLineList()) {
                line.setInvoiceId(entity.getInvoiceId());
                lineMapper.insert(line);
            }
        }
        return rows;
    }

    /**
     * 批量删除销售发票（含明细行），仅草稿状态可删除
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteByIds(Long[] ids) {
        for (Long id : ids) {
            SoInvoice existing = baseMapper.selectById(id);
            if (existing != null && !STATUS_DRAFT.equals(existing.getStatus())) {
                throw new ServiceException("发票【{}】当前状态不允许删除", existing.getInvoiceNo());
            }
            lineMapper.delete(Wrappers.lambdaQuery(SoInvoiceLine.class)
                .eq(SoInvoiceLine::getInvoiceId, id));
        }
        return baseMapper.deleteBatchIds(List.of(ids));
    }

    // ==================== Flowable 工作流集成 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean submit(String invoiceIdStr, String selectFlowId) {
        Long invoiceId = Long.parseLong(invoiceIdStr);
        SoInvoice invoice = baseMapper.selectById(invoiceId);
        if (invoice == null) {
            throw new ServiceException("销售发票不存在");
        }

        if (StringUtils.isBlank(selectFlowId)) {
            if (invoice.getSelectFlowId() != null) {
                selectFlowId = String.valueOf(invoice.getSelectFlowId());
            } else {
                throw new ServiceException("请先在编辑页选择审批流程并保存");
            }
        }

        Map<String, Object> variables = new HashMap<>();
        variables.put("dataId", invoiceIdStr);
        variables.put("requireTaskOnStart", true);
        variables.put("invoiceId", invoice.getInvoiceId());
        variables.put("invoiceNo", invoice.getInvoiceNo());
        variables.put("clientName", invoice.getClientName());
        variables.put("totalAmount", invoice.getTotalAmount());
        R<Void> result = processService.startProcessByDataId(invoiceIdStr, selectFlowId, "soInvoiceService", variables);
        if (R.isSuccess(result)) {
            return true;
        }
        throw new ServiceException(result.getMsg());
    }

    @Override
    public String getProcInsId(String invoiceIdStr) {
        try {
            WfMyBusiness business = wfMyBusinessServiceImpl.getByDataId(invoiceIdStr);
            if (business != null && StringUtils.isNotBlank(business.getProcessInstanceId())) {
                return business.getProcessInstanceId();
            }
        } catch (Exception ignored) {
        }

        ProcessInstance runtime = runtimeService.createProcessInstanceQuery()
            .processInstanceBusinessKey(invoiceIdStr).singleResult();
        if (runtime != null) {
            return runtime.getId();
        }

        HistoricProcessInstance hist = historyService.createHistoricProcessInstanceQuery()
            .processInstanceBusinessKey(invoiceIdStr).singleResult();
        return hist != null ? hist.getId() : null;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void afterFlowHandle(WfMyBusiness business) {
        Long invoiceId = Long.parseLong(business.getDataId());
        SoInvoice invoice = baseMapper.selectById(invoiceId);
        if (invoice == null) {
            return;
        }
        String actStatus = business.getActStatus();
        if (ActStatus.doing.equals(actStatus)) {
            invoice.setStatus(STATUS_APPROVING);
        } else if (ActStatus.pass.equals(actStatus)) {
            invoice.setStatus(STATUS_ISSUED);
        } else if (ActStatus.reject.equals(actStatus) || ActStatus.retrun.equals(actStatus)) {
            invoice.setStatus(STATUS_REJECTED);
        } else if (ActStatus.revoke.equals(actStatus) || ActStatus.stop.equals(actStatus) || ActStatus.recall.equals(actStatus)) {
            invoice.setStatus(STATUS_DRAFT);
        }
        baseMapper.updateById(invoice);
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
    private LambdaQueryWrapper<SoInvoice> buildQueryWrapper(SoInvoiceBo bo) {
        LambdaQueryWrapper<SoInvoice> lqw = Wrappers.lambdaQuery();
        lqw.like(StringUtils.isNotBlank(bo.getInvoiceNo()), SoInvoice::getInvoiceNo, bo.getInvoiceNo());
        lqw.eq(bo.getClientId() != null, SoInvoice::getClientId, bo.getClientId());
        lqw.like(StringUtils.isNotBlank(bo.getClientName()), SoInvoice::getClientName, bo.getClientName());
        lqw.eq(StringUtils.isNotBlank(bo.getInvoiceType()), SoInvoice::getInvoiceType, bo.getInvoiceType());
        lqw.eq(StringUtils.isNotBlank(bo.getStatus()), SoInvoice::getStatus, bo.getStatus());
        lqw.eq(bo.getSoId() != null, SoInvoice::getSoId, bo.getSoId());
        lqw.eq(bo.getContractId() != null, SoInvoice::getContractId, bo.getContractId());
        lqw.orderByDesc(SoInvoice::getCreateTime);
        return lqw;
    }
}
