package org.dromara.erp.pu.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.erp.pu.domain.PuPurchaseOrder;
import org.dromara.erp.pu.domain.PuPurchaseOrderLine;
import org.dromara.erp.pu.mapper.PuPurchaseOrderLineMapper;
import org.dromara.erp.pu.mapper.PuPurchaseOrderMapper;
import org.dromara.erp.pu.service.PuPurchaseReceivingService;
import org.dromara.erp.wm.domain.WmArrivalNotice;
import org.dromara.erp.wm.domain.WmArrivalNoticeLine;
import org.dromara.erp.wm.domain.WmMaterialReceive;
import org.dromara.erp.wm.domain.WmMaterialReceiveLine;
import org.dromara.erp.wm.mapper.WmArrivalNoticeLineMapper;
import org.dromara.erp.wm.mapper.WmArrivalNoticeMapper;
import org.dromara.erp.wm.mapper.WmMaterialReceiveLineMapper;
import org.dromara.erp.wm.mapper.WmMaterialReceiveMapper;
import org.dromara.system.strategy.AutoCodeUtil;
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
 *   <li>创建物料收货单（WM_MATERIAL_RECEIVE）及其明细行</li>
 * </ol>
 * 以上操作在同一个事务中完成，保证数据一致性。
 *
 * @author ruoyi
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
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createArrivalNoticeAndReceive(Long orderId) {
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

        // 1. 创建到货通知单
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
            WmArrivalNoticeLine noticeLine = new WmArrivalNoticeLine();
            noticeLine.setNoticeId(notice.getNoticeId());
            noticeLine.setMaterialId(orderLine.getMaterialId());
            noticeLine.setMaterialCode(orderLine.getMaterialCode());
            noticeLine.setMaterialName(orderLine.getMaterialName());
            noticeLine.setMaterialSpec(orderLine.getMaterialSpec());
            noticeLine.setBaseUomId(null);
            noticeLine.setBaseUomName(orderLine.getUnit());
            noticeLine.setQuantityArrival(orderLine.getOrderQuantity());
            noticeLine.setQuantityQuanlified(orderLine.getOrderQuantity());
            noticeLine.setIqcCheck("N");
            noticeLine.setRemark(orderLine.getRemark());
            arrivalNoticeLineMapper.insert(noticeLine);
        }

        // 3. 创建物料收货单
        WmMaterialReceive receive = new WmMaterialReceive();
        receive.setReceiveCode(autoCodeUtil.genSerialCode("WM_MATERIAL_RECEIVE_CODE", null));
        receive.setReceiveName("采购到货收货单");
        receive.setNoticeId(notice.getNoticeId());
        receive.setNoticeCode(notice.getNoticeCode());
        receive.setPurId(order.getOrderId());
        receive.setPurCode(order.getOrderNo());
        receive.setVendorId(order.getVendorId());
        receive.setVendorCode(order.getVendorCode());
        receive.setVendorName(order.getVendorName());
        receive.setReceiveDate(new Date());
        receive.setStatus("APPROVED");
        receive.setRemark("采购订单自动联动生成");
        materialReceiveMapper.insert(receive);

        // 4. 创建物料收货明细行
        for (PuPurchaseOrderLine orderLine : orderLines) {
            WmMaterialReceiveLine receiveLine = new WmMaterialReceiveLine();
            receiveLine.setReceiveId(receive.getReceiveId());
            receiveLine.setMaterialId(orderLine.getMaterialId());
            receiveLine.setMaterialCode(orderLine.getMaterialCode());
            receiveLine.setMaterialName(orderLine.getMaterialName());
            receiveLine.setMaterialSpec(orderLine.getMaterialSpec());
            receiveLine.setBaseUomName(orderLine.getUnit());
            receiveLine.setQuantityReceived(orderLine.getOrderQuantity());
            receiveLine.setRemark(orderLine.getRemark());
            materialReceiveLineMapper.insert(receiveLine);
        }

        return notice.getNoticeId();
    }
}
