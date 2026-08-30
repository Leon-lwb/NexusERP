package com.nexus.mes.pda.service.impl;

import cn.hutool.core.date.DateUtil;
import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.nexus.common.core.exception.ServiceException;
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
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;

/**
 * PDA SN 绑定/解绑作业实现
 * <p>
 * 绑定语义: SN -> 批次, 物料随批次带出 (批次不存在则拒绝)。
 * 直接经 Mapper 落库: 既有 insertByBo 的 BO 不含 snCode/lotCode 冗余字段。
 */
@DS("mes")
@Service
@RequiredArgsConstructor
public class PdaSnServiceImpl implements IPdaSnService {

    private final LtSnBindingMapper snBindingMapper;
    private final LtLotMapper lotMapper;

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

        LtSnBinding existing = selectBySn(snCode);
        if (existing != null) {
            return MesActionResultVo.fail("SN 已绑定批次 " + existing.getLotCode() + "，请先解绑");
        }
        LtLot lot = lotMapper.selectOne(Wrappers.<LtLot>lambdaQuery().eq(LtLot::getLotCode, lotCode));
        if (lot == null) {
            return MesActionResultVo.fail("批次不存在: " + lotCode);
        }

        LtSnBinding binding = new LtSnBinding();
        binding.setSnCode(snCode);
        binding.setLotId(lot.getLotId());
        binding.setLotCode(lot.getLotCode());
        binding.setMaterialId(lot.getMaterialId());
        binding.setBoundTime(new Date());
        binding.setBoundBy(LoginHelper.getUserId());
        snBindingMapper.insert(binding);
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
        snBindingMapper.deleteById(existing.getBindingId());
        return MesActionResultVo.ok("解绑成功");
    }

    private LtSnBinding selectBySn(String snCode) {
        return snBindingMapper.selectOne(Wrappers.<LtSnBinding>lambdaQuery()
            .eq(LtSnBinding::getSnCode, snCode)
            .last("limit 1"));
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
