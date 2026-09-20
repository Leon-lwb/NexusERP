package com.nexus.erp.wm.service;

import com.nexus.erp.wm.service.mq.MesSpareErpMessage;

import java.math.BigDecimal;

/**
 * MES 备件 → ERP 仓储过账服务（MES-EQ-04）
 * <p>
 * 对外可复用的<b>过账入口</b>：把 MES 备件的出入库过账到 ERP 唯一账本 {@code wm_material_stock}，
 * 并写 {@code wm_material_stock_tx} 流水。本接口不改动 wm 内核既有方法语义，只在既有原子
 * Mapper 能力（{@code deductStockAvailable} / {@code addStock}）之上编排。
 * <p>
 * 幂等：{@code bizKey}（{@code EQSP:<tenantId>:<logId>}）在 Redis 原子占位 + 流水查重双重去重，
 * 重复消息不会二次增减库存。
 *
 * @author Nexus
 */
public interface IWmSpareStockPostingService {

    /** 备件过账来源单据类型（写入 wm_material_stock_tx.source_doc_type） */
    String SOURCE_DOC_TYPE = "MES_SPARE";

    /**
     * 查询 ERP 可用量（口径：在库 − 已预留，且 {@code frozen_flag = 0}）
     *
     * @param materialId  物料ID
     * @param warehouseId 仓库ID(可空表示全部仓库)
     * @return 可用量合计
     */
    BigDecimal availableOf(Long materialId, Long warehouseId);

    /**
     * 过账一笔 MES 备件出入库
     *
     * @param message 过账消息
     * @return 过账结果（是否重复消息 + 过账后 ERP 可用量）
     */
    WmSpareStockPostingResult postSpareStock(MesSpareErpMessage message);

    /**
     * 备件过账结果
     *
     * @param duplicated    是否为重复消息（幂等命中，未再次增减库存）
     * @param erpAvailable  过账后 ERP 可用量
     */
    record WmSpareStockPostingResult(boolean duplicated, BigDecimal erpAvailable) {
    }
}
