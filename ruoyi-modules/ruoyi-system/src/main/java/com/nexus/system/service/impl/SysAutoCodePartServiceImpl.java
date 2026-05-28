package com.nexus.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import com.nexus.system.domain.SysAutoCodePart;
import com.nexus.system.domain.vo.SysAutoCodePartVo;
import com.nexus.system.mapper.SysAutoCodePartMapper;
import com.nexus.system.service.ISysAutoCodePartService;
import org.springframework.stereotype.Service;

import java.util.List;

@RequiredArgsConstructor
@Service
public class SysAutoCodePartServiceImpl implements ISysAutoCodePartService {

    private final SysAutoCodePartMapper baseMapper;

    @Override
    public List<SysAutoCodePart> listPart(SysAutoCodePart part) {
        LambdaQueryWrapper<SysAutoCodePart> wrapper = new LambdaQueryWrapper<>();
        if (part.getRuleId() != null) {
            wrapper.eq(SysAutoCodePart::getRuleId, part.getRuleId());
        }
        if (part.getPartIndex() != null) {
            wrapper.eq(SysAutoCodePart::getPartIndex, part.getPartIndex());
        }
        if (part.getPartType() != null && !part.getPartType().isEmpty()) {
            wrapper.eq(SysAutoCodePart::getPartType, part.getPartType());
        }
        if (part.getPartCode() != null && !part.getPartCode().isEmpty()) {
            wrapper.eq(SysAutoCodePart::getPartCode, part.getPartCode());
        }
        if (part.getPartName() != null && !part.getPartName().isEmpty()) {
            wrapper.like(SysAutoCodePart::getPartName, part.getPartName());
        }
        wrapper.orderByAsc(SysAutoCodePart::getPartIndex);
        return baseMapper.selectList(wrapper);
    }

    @Override
    public SysAutoCodePartVo findById(Long partId) {
        return baseMapper.selectVoById(partId);
    }

    @Override
    public boolean checkPartUnique(SysAutoCodePart part) {
        LambdaQueryWrapper<SysAutoCodePart> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysAutoCodePart::getRuleId, part.getRuleId())
            .and(w -> w.eq(SysAutoCodePart::getPartCode, part.getPartCode())
                .or().eq(SysAutoCodePart::getPartName, part.getPartName())
                .or().eq(SysAutoCodePart::getPartIndex, part.getPartIndex()));
        SysAutoCodePart exist = baseMapper.selectOne(wrapper);
        if (exist == null) {
            return true;
        }
        return part.getPartId() != null && part.getPartId().equals(exist.getPartId());
    }

    @Override
    public int insertPart(SysAutoCodePart part) {
        return baseMapper.insert(part);
    }

    @Override
    public int updatePart(SysAutoCodePart part) {
        return baseMapper.updateById(part);
    }

    @Override
    public int deleteByIds(Long[] partIds) {
        for (Long id : partIds) {
            baseMapper.deleteById(id);
        }
        return 1;
    }
}
