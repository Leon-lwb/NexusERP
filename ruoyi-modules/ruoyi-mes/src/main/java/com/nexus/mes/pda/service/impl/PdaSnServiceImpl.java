package com.nexus.mes.pda.service.impl;

import cn.hutool.core.date.DateUtil;
import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.nexus.common.core.utils.StringUtils;
import com.nexus.common.satoken.utils.LoginHelper;
import com.nexus.mes.lt.domain.LtLot;
import com.nexus.mes.lt.domain.LtSnBinding;
import com.nexus.mes.lt.mapper.LtLotMapper;
import com.nexus.mes.lt.mapper.LtSnBindingMapper;
import com.nexus.mes.pda.domain.bo.PdaSnBindBo;
import com.nexus.mes.pda.domain.bo.PdaSnUnbindBo;
import com.nexus.mes.pda.domain.vo.MesActionResultVo;
import com.nexus.mes.pda.domain.vo.PdaSnBindingVo;
import com.nexus.mes.pda.service.IPdaSnService;
import com.nexus.mes.pro.domain.ProSn;
import com.nexus.mes.pro.mapper.ProSnMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;

/**
 * PDA SN 绑定/解绑作业实现
 * <p>
 * 绑定语义: SN -> 批次, 物料随批次带出 (批次不存在则拒绝)。
 * 直接经 Mapper 落库: 既有 insertByBo 的 BO 不含 snCode/lotCode 冗余字段。
 * <p>
 * 修复要点(MES-PDA-01):
 * ① {@code lt_sn_binding.sn_id} 为 NOT NULL, 必须由 {@code snCode} 反查 {@link ProSn} 写入真实 SN ID
 * (修复前未赋值导致 100% SQL 1364, 且唯一键 {@code uk_sn_material} 完全失效);
 * ② 重复绑定按幂等处理(同批次返回成功), 并发由唯一键兜底捕获 {@link DuplicateKeyException}。
 */
@Slf4j
@DS("mes")
@Service
@RequiredArgsConstructor
public class PdaSnServiceImpl implements IPdaSnService {

    private final LtSnBindingMapper snBindingMapper;
    private final LtLotMapper lotMapper;
    private final ProSnMapper proSnMapper;
    private final com.nexus.mes.trace.MesSnCreationWriter productLots;
    private final com.nexus.mes.trace.MesSerialReferenceService serialReferences;

    @Override
    public PdaSnBindingVo query(String snCode) {
        if (StringUtils.isBlank(snCode)) {
            return null;
        }
        LtSnBinding binding = selectBySn(snCode.trim());
        return binding == null ? null : toVo(binding);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MesActionResultVo bind(PdaSnBindBo bo) {
        String snCode = bo.getSnCode().trim();
        String lotCode = bo.getLotCode().trim();

        // 1. 解析 SN 主档: sn_id NOT NULL, 无法解析即拒绝(任意字符串都能绑定本身也是错的)
        ProSn sn = selectSnByCode(snCode);
        if (sn == null) {
            return MesActionResultVo.fail("SN不存在: " + snCode);
        }
        LtLot lot = lotMapper.selectOne(Wrappers.<LtLot>lambdaQuery().eq(LtLot::getLotCode, lotCode));
        if (lot == null) {
            return MesActionResultVo.fail("批次不存在: " + lotCode);
        }

        if(!java.util.Objects.equals(sn.getMaterialId(),lot.getMaterialId()))
            return MesActionResultVo.fail("组件批次必须通过实际数量、工序和来源消耗账分配，不能作为成品批次绑定");

        // 2. 幂等: 同一 SN 已绑定同一批次 -> 视为成功; 绑定到其他批次 -> 拒绝
        LtSnBinding existing = selectBySn(snCode);
        if (existing != null) {
            if(!sameBinding(existing,lot)) return MesActionResultVo.fail("成品批次已确定，请走业务纠正保留原凭据");
            productLots.attachProductLot(sn.getSnId(),lot.getLotId());
            return MesActionResultVo.ok("SN 已绑定该成品批次");
        }
        productLots.attachProductLot(sn.getSnId(),lot.getLotId());

        LtSnBinding binding = new LtSnBinding();
        binding.setSnId(sn.getSnId());
        binding.setSnCode(StringUtils.isNotBlank(sn.getSnCode()) ? sn.getSnCode() : snCode);
        binding.setLotId(lot.getLotId());
        binding.setLotCode(lot.getLotCode());
        binding.setMaterialId(lot.getMaterialId());
        binding.setBoundTime(new Date());
        binding.setBoundBy(LoginHelper.getUserId());
        try {
            snBindingMapper.insert(binding);
        } catch (DuplicateKeyException e) {
            // 并发兜底: uk_sn_material(tenant_id, sn_id, material_id) 冲突说明已被并发绑定
            log.warn("[PDA SN绑定] 唯一键冲突, 按幂等处理: snCode={}, lotCode={}", snCode, lotCode);
            LtSnBinding concurrent = selectBySn(snCode);
            if(concurrent!=null && sameBinding(concurrent,lot)) return MesActionResultVo.ok("SN 已绑定该成品批次");
            throw new com.nexus.common.core.exception.ServiceException("批次绑定并发冲突，修改已回滚，请重试");
        }
        return MesActionResultVo.ok("绑定成功");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MesActionResultVo unbind(PdaSnUnbindBo bo) {
        String snCode = bo.getSnCode().trim();
        LtSnBinding existing = selectBySn(snCode);
        if (existing == null) {
            return MesActionResultVo.fail("SN 未绑定: " + snCode);
        }
        return MesActionResultVo.fail("批次绑定凭据不可删除；组件请引用原分录冲正，成品批次请走业务纠正");
    }

    /**
     * 按 SN 编码查询绑定关系
     * <p>补 {@code boundTime} 倒序: 同 SN 存在多行绑定(历史脏数据/一 SN 多物料)时结果确定(MES-PDA-09)。</p>
     */
    private LtSnBinding selectBySn(String snCode) {
        var reference=serialReferences.resolve(snCode);if(reference==null)return null;
        return snBindingMapper.selectOne(Wrappers.<LtSnBinding>lambdaQuery()
            .eq(LtSnBinding::getSnId,reference.localSnId()).eq(LtSnBinding::getMaterialId,reference.materialId()));
    }

    private ProSn selectSnByCode(String snCode) {
        var reference=serialReferences.resolve(snCode);return reference==null?null:reference.sn();
    }

    private boolean sameBinding(LtSnBinding binding, LtLot lot) {
        return binding.getLotId() != null && binding.getLotId().equals(lot.getLotId());
    }

    private PdaSnBindingVo toVo(LtSnBinding binding) {
        PdaSnBindingVo vo = new PdaSnBindingVo();
        vo.setBindingId(binding.getBindingId());
        vo.setSnCode(binding.getSnCode());
        vo.setLotCode(binding.getLotCode());
        vo.setBoundTime(binding.getBoundTime() != null ? DateUtil.formatDateTime(binding.getBoundTime()) : null);
        // 物料信息从批次冗余带出(批次可能已删, 缺失时留空)
        if (binding.getLotId() != null) {
            LtLot lot = lotMapper.selectById(binding.getLotId());
            if (lot != null) {
                vo.setMaterialCode(lot.getMaterialCode());
                vo.setMaterialName(lot.getMaterialName());
            }
        }
        return vo;
    }
}
