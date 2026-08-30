package com.nexus.web.service;

import cn.hutool.core.util.StrUtil;
import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.nexus.common.core.utils.StringUtils;
import com.nexus.erp.bd.domain.BdMaterial;
import com.nexus.erp.bd.mapper.BdMaterialMapper;
import com.nexus.erp.wm.domain.WmBatch;
import com.nexus.erp.wm.domain.WmSn;
import com.nexus.erp.wm.domain.vo.WmTraceResultVo;
import com.nexus.erp.wm.mapper.WmBatchMapper;
import com.nexus.erp.wm.mapper.WmSnMapper;
import com.nexus.erp.wm.service.IWmTraceService;
import com.baomidou.dynamic.datasource.annotation.DS;
import com.nexus.mes.lt.domain.vo.LtLotVo;
import com.nexus.mes.lt.service.ILtLotService;
import com.nexus.web.domain.vo.PdaTraceVo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * PDA 统一追溯门面 (M3 批次4)
 * <p>
 * erp/mes 模块互不依赖, 三套既有追溯能力在此聚合 (ruoyi-admin 聚合了全部模块):
 * 关键字解析(SN/批次/物料/MES批次) → ERP 库存流水正反向追溯 + MES 批次链。
 * 各注入服务自带 @DS 数据源切换, 不引入跨库手写 SQL。
 */
@Slf4j
@RequiredArgsConstructor
@DS("erp")
@Service
public class PdaTraceService {

    private final WmSnMapper snMapper;
    private final WmBatchMapper batchMapper;
    private final BdMaterialMapper materialMapper;
    private final IWmTraceService wmTraceService;
    private final ILtLotService ltLotService;

    public PdaTraceVo trace(String keyword) {
        PdaTraceVo vo = new PdaTraceVo();
        vo.setKeyword(keyword);
        PdaTraceVo.ResolvedVo resolved = new PdaTraceVo.ResolvedVo();
        vo.setResolved(resolved);

        // 1. SN 精确命中 (携带批次归属)
        WmSn sn = snMapper.selectOne(Wrappers.<WmSn>lambdaQuery().eq(WmSn::getSnCode, keyword).last("limit 1"));
        if (sn != null) {
            PdaTraceVo.SnVo snVo = new PdaTraceVo.SnVo();
            snVo.setSnId(sn.getSnId());
            snVo.setSnCode(sn.getSnCode());
            snVo.setMaterialId(sn.getMaterialId());
            snVo.setMaterialCode(sn.getMaterialCode());
            snVo.setMaterialName(sn.getMaterialName());
            snVo.setBatchCode(sn.getBatchCode());
            resolved.setSn(snVo);
        }

        // 2. 批次号命中
        WmBatch batch = batchMapper.selectOne(Wrappers.<WmBatch>lambdaQuery().eq(WmBatch::getBatchCode, keyword).last("limit 1"));
        if (batch != null) {
            PdaTraceVo.BatchVo batchVo = new PdaTraceVo.BatchVo();
            batchVo.setBatchId(batch.getBatchId());
            batchVo.setBatchCode(batch.getBatchCode());
            batchVo.setMaterialId(batch.getMaterialId());
            batchVo.setMaterialCode(batch.getMaterialCode());
            batchVo.setMaterialName(batch.getMaterialName());
            resolved.setBatch(batchVo);
        }

        // 3. 物料编码命中
        BdMaterial material = materialMapper.selectOne(
            Wrappers.<BdMaterial>lambdaQuery().eq(BdMaterial::getMaterialCode, keyword).last("limit 1"));
        if (material != null) {
            PdaTraceVo.MaterialVo materialVo = new PdaTraceVo.MaterialVo();
            materialVo.setMaterialId(material.getMaterialId());
            materialVo.setMaterialCode(material.getMaterialCode());
            materialVo.setMaterialName(material.getMaterialName());
            materialVo.setMaterialSpec(material.getMaterialSpec());
            resolved.setMaterial(materialVo);
        }

        // 4. MES 批次号命中 → 批次链(消耗/绑定/工单/SN); 查询经 ILtLotService(@DS mes) 切库
        LtLotVo lot = ltLotService.selectByCode(keyword);
        if (lot != null) {
            PdaTraceVo.LotVo lotVo = new PdaTraceVo.LotVo();
            lotVo.setLotId(lot.getLotId());
            lotVo.setLotCode(lot.getLotCode());
            lotVo.setMaterialId(lot.getMaterialId());
            lotVo.setMaterialCode(lot.getMaterialCode());
            lotVo.setMaterialName(lot.getMaterialName());
            lotVo.setStatus(lot.getStatus());
            lotVo.setQcStatus(lot.getQcStatus());
            resolved.setLot(lotVo);
            vo.setMes(ltLotService.trace(lot.getLotId()));
        }

        // 5. ERP 库存流水正反向追溯 (按最具体维度: SN > 批次 > 物料)
        Long materialId = null;
        String batchCode = null;
        String snCode = null;
        if (sn != null) {
            materialId = sn.getMaterialId();
            batchCode = sn.getBatchCode();
            snCode = sn.getSnCode();
        } else if (batch != null) {
            materialId = batch.getMaterialId();
            batchCode = batch.getBatchCode();
        } else if (material != null) {
            materialId = material.getMaterialId();
        }
        if (materialId != null) {
            WmTraceResultVo backward = traceQuietly(materialId, batchCode, snCode, false);
            vo.setBackward(backward);
            WmTraceResultVo forward = traceQuietly(materialId, batchCode, snCode, true);
            vo.setForward(forward);
        }
        return vo;
    }

    /** 单向追溯; 追溯服务异常(如流水过大)时降级为该方向无结果, 不阻断整体查询 */
    private WmTraceResultVo traceQuietly(Long materialId, String batchCode, String snCode, boolean forward) {
        try {
            return forward
                ? wmTraceService.traceForward(materialId, StrUtil.emptyToNull(batchCode), StrUtil.emptyToNull(snCode))
                : wmTraceService.traceBackward(materialId, StrUtil.emptyToNull(batchCode), StrUtil.emptyToNull(snCode));
        } catch (Exception e) {
            log.warn("[PDA追溯] {} 追溯失败: materialId={}, keyword 异常: {}", forward ? "正向" : "反向", materialId, e.getMessage());
            return null;
        }
    }
}
