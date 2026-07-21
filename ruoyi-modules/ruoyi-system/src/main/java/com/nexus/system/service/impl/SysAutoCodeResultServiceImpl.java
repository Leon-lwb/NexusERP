package com.nexus.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import com.nexus.system.domain.SysAutoCodeResult;
import com.nexus.system.mapper.SysAutoCodeResultMapper;
import com.nexus.system.service.ISysAutoCodeResultService;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 编码生成结果 服务实现类，实现 {@link ISysAutoCodeResultService} 接口。属于 System 系统管理模块。
 */
@RequiredArgsConstructor
@Service
public class SysAutoCodeResultServiceImpl implements ISysAutoCodeResultService {

    /** SysAutoCodeResultMapper 数据访问层组件，提供 编码生成结果 的持久化能力。 */
    private final SysAutoCodeResultMapper baseMapper;

    /**
     * 根据条件查询编码生成结果列表。
     * @param param 查询条件（编码生成结果）
     * @return 匹配条件的编码生成结果列表
     */
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

    /**
     * 新增编码生成结果。
     * @param result 待新增的编码生成结果
     */
    @Override
    public void saveAutoCodeResult(SysAutoCodeResult result) {
        baseMapper.insert(result);
    }

    /**
     * 更新编码生成结果。
     * @param result 待更新的编码生成结果
     */
    @Override
    public void updateAutoCodeResult(SysAutoCodeResult result) {
        baseMapper.updateById(result);
    }
}
