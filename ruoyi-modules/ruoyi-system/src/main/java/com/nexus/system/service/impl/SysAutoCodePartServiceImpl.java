package com.nexus.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import com.nexus.system.domain.SysAutoCodePart;
import com.nexus.system.domain.vo.SysAutoCodePartVo;
import com.nexus.system.mapper.SysAutoCodePartMapper;
import com.nexus.system.service.ISysAutoCodePartService;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 编码组成部分 服务实现类，实现 {@link ISysAutoCodePartService} 接口。属于 System 系统管理模块。
 */
@RequiredArgsConstructor
@Service
public class SysAutoCodePartServiceImpl implements ISysAutoCodePartService {

    /** SysAutoCodePartMapper 数据访问层组件，提供 编码组成部分 的持久化能力。 */
    private final SysAutoCodePartMapper baseMapper;

    /**
     * 根据条件查询编码组成部分列表。
     * @param part 查询条件（编码组成部分）
     * @return 匹配条件的编码组成部分列表
     */
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

    /**
     * 根据条件查询编码组成部分列表。
     * @param partId ID
     * @return 编码组成部分对象
     */
    @Override
    public SysAutoCodePartVo findById(Long partId) {
        return baseMapper.selectVoById(partId);
    }

    /**
     * 校验编码组成部分是否唯一。
     * @param part 待校验的编码组成部分
     * @return 存在/成功返回 {@code true}，否则返回 {@code false}
     */
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

    /**
     * 新增编码组成部分。
     * @param part 待新增的编码组成部分
     * @return 受影响行数
     */
    @Override
    public int insertPart(SysAutoCodePart part) {
        return baseMapper.insert(part);
    }

    /**
     * 更新编码组成部分。
     * @param part 待更新的编码组成部分
     * @return 受影响行数
     */
    @Override
    public int updatePart(SysAutoCodePart part) {
        return baseMapper.updateById(part);
    }

    /**
     * 删除记录。
     * @param partIds 待删除的 ID 数组
     * @return 受影响行数
     */
    @Override
    public int deleteByIds(Long[] partIds) {
        for (Long id : partIds) {
            baseMapper.deleteById(id);
        }
        return 1;
    }
}
