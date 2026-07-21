package com.nexus.erp.wm.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import com.nexus.common.core.domain.R;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.core.utils.MapstructUtils;
import com.nexus.common.core.utils.StringUtils;
import com.nexus.common.flowable.core.domain.ActStatus;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.erp.pu.domain.PuPurchaseOrder;
import com.nexus.erp.pu.mapper.PuPurchaseOrderMapper;
import com.nexus.erp.pu.service.IPuPurchaseOrderService;
import com.nexus.erp.pu.service.PuPurchaseReceivingService;
import com.nexus.erp.wm.domain.WmArrivalNotice;
import com.nexus.erp.wm.domain.WmArrivalNoticeLine;
import com.nexus.erp.wm.domain.bo.WmArrivalNoticeBo;
import com.nexus.erp.wm.domain.bo.WmArrivalNoticeLineBo;
import com.nexus.erp.wm.domain.vo.WmArrivalNoticeLineVo;
import com.nexus.erp.wm.domain.vo.WmArrivalNoticeVo;
import com.nexus.erp.wm.mapper.WmArrivalNoticeLineMapper;
import com.nexus.erp.wm.mapper.WmArrivalNoticeMapper;
import com.nexus.erp.wm.service.IWmArrivalNoticeService;
import com.nexus.system.strategy.AutoCodeUtil;
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
 * 到货通知Service业务层处理
 * <p>
 * 支持 Flowable 审批流程集成：
 * <ul>
 *   <li>提交审批后状态变为 APPROVING</li>
 *   <li>审批通过后状态变为 APPROVED，并自动生成物料收货单，同时更新采购订单为已到货</li>
 *   <li>审批驳回后状态变为 REJECTED</li>
 * </ul>
 *
 * @author nexus
 */
@RequiredArgsConstructor
@DS("erp")
@Service("wmArrivalNoticeService")
public class WmArrivalNoticeServiceImpl extends ServiceImpl<WmArrivalNoticeMapper, WmArrivalNotice> implements IWmArrivalNoticeService {

    /** 通知行数据访问映射器（Mapper） */
    private final WmArrivalNoticeLineMapper noticeLineMapper;
    /** 到货通知数据访问映射器（Mapper） */
    private final PuPurchaseOrderMapper purchaseOrderMapper;
    /** 到货通知业务服务，提供到货通知相关能力 */
    private final PuPurchaseReceivingService purchaseReceivingService;
    /** 到货通知业务服务，提供到货通知相关能力 */
    private final IWfProcessService processService;
    /** 到货通知业务服务，提供到货通知相关能力 */
    private final WfMyBusinessServiceImpl wfMyBusinessServiceImpl;
    /** 到货通知业务服务，提供到货通知相关能力 */
    private final RuntimeService runtimeService;
    /** 到货通知业务服务，提供到货通知相关能力 */
    private final HistoryService historyService;
    /** 自动编码工具类 */
    private final AutoCodeUtil autoCodeUtil;

    /**
     * 分页查询到货通知列表
     *
     * @param bo 到货通知业务对象（BO）
     * @param pageQuery 分页查询参数，包含页码、每页大小及排序条件
     * @return 结果对象
     */
    @Override
    public TableDataInfo<WmArrivalNoticeVo> selectPageList(WmArrivalNoticeBo bo, PageQuery pageQuery) {
        LambdaQueryWrapper<WmArrivalNotice> lqw = buildQueryWrapper(bo);
        Page<WmArrivalNoticeVo> page = baseMapper.selectVoPage(pageQuery.build(), lqw);
        return TableDataInfo.build(page);
    }

    /**
     * 查询到货通知列表
     *
     * @param bo 到货通知业务对象（BO）
     * @return 结果对象
     */
    @Override
    public List<WmArrivalNoticeVo> selectList(WmArrivalNoticeBo bo) {
        return baseMapper.selectVoList(buildQueryWrapper(bo));
    }

    /**
     * 查询到货通知
     *
     * @param id 主键 ID
     * @return 结果
     */
    @Override
    public WmArrivalNoticeVo selectById(Long id) {
        WmArrivalNoticeVo vo = baseMapper.selectVoById(id);
        if (vo != null) {
            List<WmArrivalNoticeLine> lineList = noticeLineMapper.selectList(
                new LambdaQueryWrapper<WmArrivalNoticeLine>()
                    .eq(WmArrivalNoticeLine::getNoticeId, id));
            vo.setWmArrivalNoticeLineList(MapstructUtils.convert(lineList, WmArrivalNoticeLineVo.class));
        }
        return vo;
    }

    /**
     * 新增到货通知
     *
     * @param bo 到货通知业务对象（BO）
     * @return 结果
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int insertByBo(WmArrivalNoticeBo bo) {
        WmArrivalNotice entity = MapstructUtils.convert(bo, WmArrivalNotice.class);
        if (StringUtils.isBlank(entity.getNoticeCode())) {
            entity.setNoticeCode(autoCodeUtil.genSerialCode("WM_ARRIVAL_NOTICE_CODE", null));
        }
        int rows = baseMapper.insert(entity);
        insertOrUpdateLines(entity.getNoticeId(), bo.getWmArrivalNoticeLineList());
        return rows;
    }

    /**
     * 修改到货通知
     *
     * @param bo 到货通知业务对象（BO）
     * @return 结果
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updateByBo(WmArrivalNoticeBo bo) {
        WmArrivalNotice entity = MapstructUtils.convert(bo, WmArrivalNotice.class);
        noticeLineMapper.delete(new LambdaQueryWrapper<WmArrivalNoticeLine>()
            .eq(WmArrivalNoticeLine::getNoticeId, entity.getNoticeId()));
        insertOrUpdateLines(entity.getNoticeId(), bo.getWmArrivalNoticeLineList());
        return baseMapper.updateById(entity);
    }

    /**
     * 删除到货通知
     *
     * @param ids 主键 ID 数组（支持批量操作）
     * @return 结果
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteByIds(Long[] ids) {
        noticeLineMapper.delete(new LambdaQueryWrapper<WmArrivalNoticeLine>()
            .in(WmArrivalNoticeLine::getNoticeId, List.of(ids)));
        return baseMapper.deleteBatchIds(List.of(ids));
    }

    /**
     * 提交到货通知审批
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean submit(String noticeIdStr, String selectFlowId) {
        Long noticeId = Long.parseLong(noticeIdStr);
        WmArrivalNotice notice = baseMapper.selectById(noticeId);
        if (notice == null) {
            throw new ServiceException("到货通知不存在");
        }

        if (StringUtils.isBlank(selectFlowId)) {
            if (notice.getSelectFlowId() != null) {
                selectFlowId = String.valueOf(notice.getSelectFlowId());
            } else {
                throw new ServiceException("请先在编辑页选择审批流程并保存");
            }
        }

        Map<String, Object> variables = new HashMap<>();
        variables.put("dataId", noticeIdStr);
        variables.put("requireTaskOnStart", true);
        variables.put("noticeId", notice.getNoticeId());
        variables.put("noticeCode", notice.getNoticeCode());
        variables.put("noticeName", notice.getNoticeName());
        variables.put("vendorId", notice.getVendorId());
        variables.put("vendorName", notice.getVendorName());
        variables.put("arrivalDate", notice.getArrivalDate());
        R<Void> result = processService.startProcessByDataId(noticeIdStr, selectFlowId, "wmArrivalNoticeService", variables);
        if (R.isSuccess(result)) {
            return true;
        }
        throw new ServiceException(result.getMsg());
    }

    /**
     * 获取到货通知
     *
     * @param noticeIdStr 参数 noticeIdStr
     * @return 结果
     */
    @Override
    public String getProcInsId(String noticeIdStr) {
        try {
            WfMyBusiness business = wfMyBusinessServiceImpl.getByDataId(noticeIdStr);
            if (business != null && StringUtils.isNotBlank(business.getProcessInstanceId())) {
                return business.getProcessInstanceId();
            }
        } catch (Exception ignored) {
        }

        ProcessInstance runtime = runtimeService.createProcessInstanceQuery()
            .processInstanceBusinessKey(noticeIdStr).singleResult();
        if (runtime != null) {
            return runtime.getId();
        }

        HistoricProcessInstance hist = historyService.createHistoricProcessInstanceQuery()
            .processInstanceBusinessKey(noticeIdStr).singleResult();
        return hist != null ? hist.getId() : null;
    }

    /**
     * 工作流审批回调处理
     * <p>
     * 审批通过后：
     * <ol>
     *   <li>更新到货通知状态为 APPROVED</li>
     *   <li>自动生成物料收货单（DRAFT），失败则抛异常回滚</li>
     *   <li>更新关联采购订单状态为 ARRIVED</li>
     * </ol>
     */
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void afterFlowHandle(WfMyBusiness business) {
        Long noticeId = Long.parseLong(business.getDataId());
        WmArrivalNotice notice = baseMapper.selectById(noticeId);
        if (notice == null) {
            return;
        }

        String actStatus = business.getActStatus();
        if (ActStatus.doing.equals(actStatus)) {
            notice.setStatus(STATUS_APPROVING);
        } else if (ActStatus.pass.equals(actStatus)) {
            // 先尝试生成物料收货单，成功后再更新状态
            // 若生成失败，抛异常导致本事务回滚，到货通知保持原状态
            purchaseReceivingService.createMaterialReceiveFromNotice(noticeId);

            notice.setStatus(STATUS_APPROVED);
            baseMapper.updateById(notice);

            // 更新关联采购订单状态为已到货
            updatePurchaseOrderToArrived(notice.getPurOrderCode());
            return;
        } else if (ActStatus.reject.equals(actStatus) || ActStatus.retrun.equals(actStatus)) {
            notice.setStatus(STATUS_REJECTED);
        } else if (ActStatus.revoke.equals(actStatus) || ActStatus.stop.equals(actStatus) || ActStatus.recall.equals(actStatus)) {
            notice.setStatus(STATUS_DRAFT);
        }
        baseMapper.updateById(notice);
    }

    /**
     * 根据采购订单号更新采购订单状态为已到货
     */
    private void updatePurchaseOrderToArrived(String purOrderCode) {
        if (StringUtils.isBlank(purOrderCode)) {
            return;
        }
        PuPurchaseOrder order = purchaseOrderMapper.selectOne(
            new LambdaQueryWrapper<PuPurchaseOrder>().eq(PuPurchaseOrder::getOrderNo, purOrderCode));
        if (order == null) {
            return;
        }
        order.setStatus(IPuPurchaseOrderService.STATUS_RECEIVED);
        purchaseOrderMapper.updateById(order);
    }

    /**
     * 获取到货通知详情
     *
     * @param dataId data ID
     * @return 结果
     */
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Object getBusinessDataById(String dataId) {
        return selectById(Long.parseLong(dataId));
    }

    /**
     * 到货通知处理（flowValuesOfTask）
     *
     * @param taskNameId taskName ID
     * @param values 参数 values
     * @return 结果对象
     */
    @Override
    public Map<String, Object> flowValuesOfTask(String taskNameId, Map<String, Object> values) {
        return null;
    }

    /**
     * 到货通知处理（flowCandidateUsernamesOfTask）
     *
     * @param taskNameId taskName ID
     * @param values 参数 values
     * @return 结果对象
     */
    @Override
    public List<String> flowCandidateUsernamesOfTask(String taskNameId, Map<String, Object> values) {
        return null;
    }

    private void insertOrUpdateLines(Long noticeId, List<WmArrivalNoticeLineBo> lineBoList) {
        if (lineBoList != null && !lineBoList.isEmpty()) {
            List<WmArrivalNoticeLine> lineList = MapstructUtils.convert(lineBoList, WmArrivalNoticeLine.class);
            for (WmArrivalNoticeLine line : lineList) {
                line.setLineId(null);
                line.setNoticeId(noticeId);
            }
            noticeLineMapper.insertBatch(lineList);
        }
    }

    private LambdaQueryWrapper<WmArrivalNotice> buildQueryWrapper(WmArrivalNoticeBo bo) {
        LambdaQueryWrapper<WmArrivalNotice> lqw = Wrappers.lambdaQuery();
        lqw.eq(StringUtils.isNotBlank(bo.getNoticeCode()), WmArrivalNotice::getNoticeCode, bo.getNoticeCode());
        lqw.like(StringUtils.isNotBlank(bo.getNoticeName()), WmArrivalNotice::getNoticeName, bo.getNoticeName());
        lqw.eq(StringUtils.isNotBlank(bo.getPurOrderCode()), WmArrivalNotice::getPurOrderCode, bo.getPurOrderCode());
        lqw.eq(bo.getVendorId() != null, WmArrivalNotice::getVendorId, bo.getVendorId());
        lqw.eq(StringUtils.isNotBlank(bo.getVendorCode()), WmArrivalNotice::getVendorCode, bo.getVendorCode());
        lqw.like(StringUtils.isNotBlank(bo.getVendorName()), WmArrivalNotice::getVendorName, bo.getVendorName());
        lqw.eq(StringUtils.isNotBlank(bo.getVendorNick()), WmArrivalNotice::getVendorNick, bo.getVendorNick());
        lqw.eq(bo.getArrivalDate() != null, WmArrivalNotice::getArrivalDate, bo.getArrivalDate());
        lqw.eq(StringUtils.isNotBlank(bo.getContact()), WmArrivalNotice::getContact, bo.getContact());
        lqw.eq(StringUtils.isNotBlank(bo.getPhone()), WmArrivalNotice::getPhone, bo.getPhone());
        lqw.eq(StringUtils.isNotBlank(bo.getStatus()), WmArrivalNotice::getStatus, bo.getStatus());
        return lqw;
    }
}
