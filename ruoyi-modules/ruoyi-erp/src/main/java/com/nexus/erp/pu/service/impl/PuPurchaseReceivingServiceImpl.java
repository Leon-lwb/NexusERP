package com.nexus.erp.pu.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.core.utils.StringUtils;
import com.nexus.erp.pu.domain.PuPurchaseOrder;
import com.nexus.erp.pu.domain.PuPurchaseOrderLine;
import com.nexus.erp.pu.mapper.PuPurchaseOrderLineMapper;
import com.nexus.erp.pu.mapper.PuPurchaseOrderMapper;
import com.nexus.erp.bd.domain.BdMaterial;
import com.nexus.erp.bd.mapper.BdMaterialMapper;
import com.nexus.erp.pu.service.PuPurchaseReceivingService;
import com.nexus.erp.wm.domain.WmArrivalNotice;
import com.nexus.erp.wm.domain.WmArrivalNoticeLine;
import com.nexus.erp.wm.domain.WmMaterialReceive;
import com.nexus.erp.wm.domain.WmMaterialReceiveLine;
import com.nexus.erp.wm.mapper.WmArrivalNoticeLineMapper;
import com.nexus.erp.wm.mapper.WmArrivalNoticeMapper;
import com.nexus.erp.wm.mapper.WmMaterialReceiveLineMapper;
import com.nexus.erp.wm.mapper.WmMaterialReceiveMapper;
import com.nexus.system.strategy.AutoCodeUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;

/**
 * 采购收货联动服务实现类
 * <p>
 * 实现采购订单到货通知和物料收货的联动创建流程：
 * <ol>
 *   <li>校验采购订单及明细行是否存在</li>
 *   <li>创建到货通知单（WM_ARRIVAL_NOTICE）及其明细行</li>
 *   <li>到货通知审批通过后，创建物料收货单（WM_MATERIAL_RECEIVE）及其明细行</li>
 * </ol>
 * 以上操作在同一个事务中完成，保证数据一致性。
 *
 * @author nexus
 */
@RequiredArgsConstructor
@DS("erp")
@Service
public class PuPurchaseReceivingServiceImpl implements PuPurchaseReceivingService {

    private final PuPurchaseOrderMapper purchaseOrderMapper;
    private final PuPurchaseOrderLineMapper purchaseOrderLineMapper;
    private final WmArrivalNoticeMapper arrivalNoticeMapper;
    private final WmArrivalNoticeLineMapper arrivalNoticeLineMapper;
    private final WmMaterialReceiveMapper materialReceiveMapper;
    private final WmMaterialReceiveLineMapper materialReceiveLineMapper;
    private final BdMaterialMapper materialMapper;
    private final AutoCodeUtil autoCodeUtil;

    /**
     * 根据采购订单创建到货通知单并自动生成物料收货单
     * <p>
     * 流程说明：
     * <ol>
     *   <li>校验采购订单是否存在且订单号不为空</li>
     *   <li>查询采购订单明细行，无明细则抛异常</li>
     *   <li>生成到货通知单（状态为草稿），遍历明细行拷贝物料信息插入</li>
     *   <li>生成物料收货单（状态为已批准），同样遍历明细行插入</li>
     * </ol>
     *
     * @param orderId 采购订单ID
     * @return 到货通知单ID
     * @deprecated 已废弃，使用 {@link #createArrivalNotice(Long)} 替代
     */
    @Override
    @Deprecated
    @Transactional(rollbackFor = Exception.class)
    public Long createArrivalNoticeAndReceive(Long orderId) {
        Long noticeId = createArrivalNotice(orderId);
        createMaterialReceiveFromNotice(noticeId);
        return noticeId;
    }

    /**
     * 根据采购订单创建到货通知单（仅生成到货通知，状态为 DRAFT）
     *
     * @param orderId 采购订单ID
     * @return 到货通知单ID
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createArrivalNotice(Long orderId) {
        PuPurchaseOrder order = purchaseOrderMapper.selectById(orderId);
        if (order == null) {
            throw new ServiceException("采购订单不存在");
        }
        if (StringUtils.isBlank(order.getOrderNo())) {
            throw new ServiceException("采购订单号不能为空");
        }

        List<PuPurchaseOrderLine> orderLines = purchaseOrderLineMapper.selectList(
            new LambdaQueryWrapper<PuPurchaseOrderLine>().eq(PuPurchaseOrderLine::getOrderId, orderId));
        if (orderLines == null || orderLines.isEmpty()) {
            throw new ServiceException("采购订单没有明细，无法生成到货通知");
        }

        // 1. 创建到货通知单（草稿状态）
        WmArrivalNotice notice = new WmArrivalNotice();
        notice.setNoticeCode(autoCodeUtil.genSerialCode("WM_ARRIVAL_NOTICE_CODE", null));
        notice.setNoticeName("采购订单到货通知");
        notice.setPurOrderCode(order.getOrderNo());
        notice.setVendorId(order.getVendorId());
        notice.setVendorCode(order.getVendorCode());
        notice.setVendorName(order.getVendorName());
        notice.setArrivalDate(new Date());
        notice.setStatus("DRAFT");
        notice.setRemark("由采购订单自动生成");
        arrivalNoticeMapper.insert(notice);

        // 2. 创建到货通知明细行（逐行拷贝采购订单物料信息）
        for (PuPurchaseOrderLine orderLine : orderLines) {
            BdMaterial material = resolveMaterial(orderLine);
            WmArrivalNoticeLine noticeLine = new WmArrivalNoticeLine();
            noticeLine.setNoticeId(notice.getNoticeId());
            noticeLine.setMaterialId(orderLine.getMaterialId());
            noticeLine.setMaterialCode(orderLine.getMaterialCode());
            noticeLine.setMaterialName(orderLine.getMaterialName());
            noticeLine.setMaterialSpec(orderLine.getMaterialSpec());
            noticeLine.setBaseUomId(String.valueOf(material != null ? material.getBaseUomId() : null));
            noticeLine.setBaseUomName(orderLine.getUnit());
            noticeLine.setQuantityArrival(orderLine.getOrderQuantity());
            noticeLine.setQuantityQuanlified(orderLine.getOrderQuantity());
            noticeLine.setIqcCheck("0");
            noticeLine.setRemark(orderLine.getRemark());
            arrivalNoticeLineMapper.insert(noticeLine);
        }

        return notice.getNoticeId();
    }

    /**
     * 根据到货通知创建物料收货单（到货通知审批通过后调用，状态为 DRAFT）
     *
     * @param noticeId 到货通知ID
     * @return 物料收货单ID
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createMaterialReceiveFromNotice(Long noticeId) {
        WmArrivalNotice notice = arrivalNoticeMapper.selectById(noticeId);
        if (notice == null) {
            throw new ServiceException("到货通知不存在");
        }
        if (StringUtils.isBlank(notice.getPurOrderCode())) {
            throw new ServiceException("到货通知关联的采购订单号不能为空");
        }

        // 查询采购订单（用于关联 purId）
        PuPurchaseOrder order = purchaseOrderMapper.selectOne(
            new LambdaQueryWrapper<PuPurchaseOrder>().eq(PuPurchaseOrder::getOrderNo, notice.getPurOrderCode()));
        if (order == null) {
            throw new ServiceException("到货通知关联的采购订单不存在");
        }

        // 查询到货通知明细行
        List<WmArrivalNoticeLine> noticeLines = arrivalNoticeLineMapper.selectList(
            new LambdaQueryWrapper<WmArrivalNoticeLine>().eq(WmArrivalNoticeLine::getNoticeId, noticeId));
        if (noticeLines == null || noticeLines.isEmpty()) {
            throw new ServiceException("到货通知没有明细，无法生成物料收货单");
        }

        // 3. 创建物料收货单（草稿状态，需要走审批流）
        WmMaterialReceive receive = new WmMaterialReceive();
        receive.setReceiveCode(autoCodeUtil.genSerialCode("WM_MATERIAL_RECEIVE_CODE", null));
        receive.setReceiveName("采购到货收货单");
        receive.setNoticeId(notice.getNoticeId());
        receive.setNoticeCode(notice.getNoticeCode());
        receive.setPurId(order.getOrderId());
        receive.setPurCode(order.getOrderNo());
        receive.setVendorId(notice.getVendorId());
        receive.setVendorCode(notice.getVendorCode());
        receive.setVendorName(notice.getVendorName());
        receive.setReceiveDate(new Date());
        receive.setStatus("DRAFT");
        receive.setRemark("到货通知审批通过后自动生成");
        materialReceiveMapper.insert(receive);

        // 4. 创建物料收货明细行（从到货通知行拷贝）
        for (WmArrivalNoticeLine noticeLine : noticeLines) {
            WmMaterialReceiveLine receiveLine = new WmMaterialReceiveLine();
            receiveLine.setReceiveId(receive.getReceiveId());
            receiveLine.setMaterialId(noticeLine.getMaterialId());
            receiveLine.setMaterialCode(noticeLine.getMaterialCode());
            receiveLine.setMaterialName(noticeLine.getMaterialName());
            receiveLine.setMaterialSpec(noticeLine.getMaterialSpec());
            receiveLine.setBaseUomName(noticeLine.getBaseUomName());
            receiveLine.setQuantityReceived(noticeLine.getQuantityArrival());
            receiveLine.setRemark(noticeLine.getRemark());
            materialReceiveLineMapper.insert(receiveLine);
        }

        return receive.getReceiveId();
    }

    /**
     * 解析物料信息，如果物料ID为空则根据编码查询
     */
    private BdMaterial resolveMaterial(PuPurchaseOrderLine orderLine) {
        BdMaterial material = null;
        if (orderLine.getMaterialId() == null) {
            if (StringUtils.isBlank(orderLine.getMaterialCode())) {
                throw new ServiceException("采购订单明细行物料编码和物料ID均为空，无法生成到货通知");
            }
            material = materialMapper.selectOne(
                new LambdaQueryWrapper<BdMaterial>().eq(BdMaterial::getMaterialCode, orderLine.getMaterialCode()));
            if (material == null) {
                throw new ServiceException("采购订单明细行物料编码【" + orderLine.getMaterialCode() + "】在物料主数据中不存在，请先完善物料信息");
            }
            orderLine.setMaterialId(material.getMaterialId());
        }
        if (material == null) {
            material = materialMapper.selectById(orderLine.getMaterialId());
        }
        return material;
    }
}
