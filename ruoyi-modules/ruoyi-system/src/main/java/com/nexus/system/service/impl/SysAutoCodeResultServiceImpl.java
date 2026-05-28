package com.nexus.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import com.nexus.system.domain.SysAutoCodeResult;
import com.nexus.system.mapper.SysAutoCodeResultMapper;
import com.nexus.system.service.ISysAutoCodeResultService;
import org.springframework.stereotype.Service;

import java.util.List;

@RequiredArgsConstructor
@Service
public class SysAutoCodeResultServiceImpl implements ISysAutoCodeResultService {

    private final SysAutoCodeResultMapper baseMapper;

    @Override
    public List<SysAutoCodeResult> list(SysAutoCodeResult param) {
        LambdaQueryWrapper<SysAutoCodeResult> wrapper = new LambdaQueryWrapper<>();
        if (param.getRuleId() != null) {
            wrapper.eq(SysAutoCodeResult::getRuleId, param.getRuleId());
        }
        if (param.getLastInputChar() != null && !param.getLastInputChar().isEmpty()) {
            wrapper.eq(SysAutoCodeResult::getLastInputChar, param.getLastInputChar());
        }
        if (param.getGenDate() != null && !param.getGenDate().isEmpty()) {
            wrapper.likeRight(SysAutoCodeResult::getGenDate, param.getGenDate());
        }
        wrapper.orderByDesc(SysAutoCodeResult::getGenDate);
        return baseMapper.selectList(wrapper);
    }

    @Override
    public void saveAutoCodeResult(SysAutoCodeResult result) {
        baseMapper.insert(result);
    }

    @Override
    public void updateAutoCodeResult(SysAutoCodeResult result) {
        baseMapper.updateById(result);
    }
}
