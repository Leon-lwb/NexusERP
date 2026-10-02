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
import com.nexus.erp.pu.service.PuCertifiedPurchaseDocumentGuard;
import com.nexus.common.tenant.helper.TenantHelper;
import java.util.LinkedHashSet;
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
import com.nexus.workflow.domain.WfMyBusiness;
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
 *   <li>审批通过后状态变为 APPROVED，并自动生成草稿物料收货单；不改采购订单履约状态</li>
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
    private final PuCertifiedPurchaseDocumentGuard certifiedDocumentGuard;
    /** 到货通知业务服务，提供到货通知相关能力 */
    private final PuPurchaseReceivingService purchaseReceivingService;
    /** WM 公共依赖（AutoCodeUtil / 流程服务 / 流程实例查询） */
    private final WmServiceHelper wm;

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
        if (bo == null) throw arrivalSourceUnknown();
        requireLegacyOrder(bo.getPurOrderCode());
        WmArrivalNotice entity = MapstructUtils.convert(bo, WmArrivalNotice.class);
        if (StringUtils.isBlank(entity.getNoticeCode())) {
            entity.setNoticeCode(wm.autoCodeUtil.genSerialCode("WM_ARRIVAL_NOTICE_CODE", null));
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
        if (bo == null) throw arrivalSourceUnknown();
        WmArrivalNotice persisted = lockNotice(bo.getNoticeId());
        requireLegacyOrder(persisted.getPurOrderCode());
        requireLegacyOrder(bo.getPurOrderCode());
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
        if (ids == null || ids.length == 0) throw arrivalSourceUnknown();
        LinkedHashSet<Long> unique = new LinkedHashSet<>();
        for (Long id : ids) {
            if (id == null || id <= 0 || !unique.add(id)) throw arrivalSourceUnknown();
        }
        // 所有来源先预检，不能先删除前几张旧单据再发现认证来源。
        for (Long id : unique) requireLegacyOrder(lockNotice(id).getPurOrderCode());
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

        requireLegacyOrder(notice.getPurOrderCode());
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
        R<Void> result = wm.processService.startProcessByDataId(noticeIdStr, selectFlowId, "wmArrivalNoticeService", variables);
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
        return wm.procInsIdHelper.resolve(noticeIdStr, () -> {
            WfMyBusiness business = wm.wfMyBusinessServiceImpl.getByDataId(noticeIdStr);
            return business != null ? business.getProcessInstanceId() : null;
        });
    }

    /**
     * 工作流审批回调处理
     * <p>
     * 审批通过后：
     * <ol>
     *   <li>更新到货通知状态为 APPROVED</li>
     *   <li>自动生成物料收货单（DRAFT），失败则抛异常回滚</li>
     *   <li>不提前更新采购订单履约状态</li>
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

        requireLegacyOrder(notice.getPurOrderCode());
        String actStatus = business.getActStatus();
        if (ActStatus.doing.equals(actStatus)) {
            notice.setStatus(STATUS_APPROVING);
        } else if (ActStatus.pass.equals(actStatus)) {
            // 先尝试生成物料收货单，成功后再更新状态
            // 若生成失败，抛异常导致本事务回滚，到货通知保持原状态
            purchaseReceivingService.createMaterialReceiveFromNotice(noticeId);

            notice.setStatus(STATUS_APPROVED);
            baseMapper.updateById(notice);

            // 此处仅创建 DRAFT/零实收收货单，不代表实际履约，不得提前设置 PO RECEIVED。
            return;
        } else if (ActStatus.reject.equals(actStatus) || ActStatus.retrun.equals(actStatus)) {
            notice.setStatus(STATUS_REJECTED);
        } else if (ActStatus.revoke.equals(actStatus) || ActStatus.stop.equals(actStatus) || ActStatus.recall.equals(actStatus)) {
            notice.setStatus(STATUS_DRAFT);
        }
        baseMapper.updateById(notice);
    }

    /**
     * 核验到货通知当前来源；客户端改订单号不能洗掉原认证归属
     */
    private WmArrivalNotice lockNotice(Long id) {
        if (id == null || id <= 0) throw arrivalSourceUnknown();
        WmArrivalNotice value;
        try { value = baseMapper.selectById(id); }
        catch (RuntimeException error) { throw arrivalSourceUnknown(); }
        if (value == null || !id.equals(value.getNoticeId())) throw arrivalSourceUnknown();
        return value;
    }

    private void requireLegacyOrder(String orderNo) {
        String tenant = TenantHelper.getTenantId();
        if (StringUtils.isBlank(orderNo) || StringUtils.isBlank(tenant)) throw arrivalSourceUnknown();
        PuPurchaseOrder order;
        try {
            order = purchaseOrderMapper.selectOne(Wrappers.lambdaQuery(PuPurchaseOrder.class)
                .eq(PuPurchaseOrder::getOrderNo, orderNo).apply("tenant_id = {0}", tenant).last("FOR UPDATE"));
        } catch (RuntimeException error) { throw arrivalSourceUnknown(); }
        if (order == null || order.getOrderId() == null || order.getOrderId() <= 0
            || !orderNo.equals(order.getOrderNo())) throw arrivalSourceUnknown();
        certifiedDocumentGuard.requireLegacyOrderMutation(order.getOrderId());
    }

    private ServiceException arrivalSourceUnknown() {
        return new ServiceException("到货通知采购来源事实未知，禁止写入；新增来源请使用 /erp/pu/requisition/certified，认证单据须走受保护生命周期");
    }


    /**
     * 获取到货通知详情
     *
     * @param dataId data ID
     * @return 结果
     */
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
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
