package com.nexus.common.ai.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.nexus.common.ai.domain.bo.AiSqlExampleBo;
import com.nexus.common.ai.domain.entity.AiSqlExample;
import com.nexus.common.ai.domain.vo.AiSqlExampleVo;
import com.nexus.common.ai.mapper.AiSqlExampleMapper;
import com.nexus.common.ai.service.IAiSqlExampleService;
import com.nexus.common.core.utils.MapstructUtils;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * AI SQL Few-Shot 示例 Service 实现
 *
 * @author NexusERP
 */
@RequiredArgsConstructor
@Service
public class AiSqlExampleServiceImpl implements IAiSqlExampleService {

    private final AiSqlExampleMapper baseMapper;

    /**
     * 分页查询 SQL 示例
     */
    @Override
    public TableDataInfo<AiSqlExampleVo> selectPageList(AiSqlExampleBo bo, PageQuery pageQuery) {
        LambdaQueryWrapper<AiSqlExample> lqw = buildQueryWrapper(bo);
        Page<AiSqlExampleVo> page = baseMapper.selectVoPage(pageQuery.build(), lqw);
        return TableDataInfo.build(page);
    }

    /**
     * 查询 SQL 示例列表（不分页）
     */
    @Override
    public List<AiSqlExampleVo> selectList(AiSqlExampleBo bo) {
        LambdaQueryWrapper<AiSqlExample> lqw = buildQueryWrapper(bo);
        return baseMapper.selectVoList(lqw);
    }

    /**
     * 根据 ID 查询 SQL 示例
     */
    @Override
    public AiSqlExampleVo selectById(Long id) {
        return baseMapper.selectVoById(id);
    }

    /**
     * 新增 SQL 示例
     * <p>自动设置默认值：sortOrder=0, status=1</p>
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int insertByBo(AiSqlExampleBo bo) {
        AiSqlExample entity = MapstructUtils.convert(bo, AiSqlExample.class);
        if (entity.getSortOrder() == null) {
            entity.setSortOrder(0);
        }
        if (entity.getStatus() == null) {
            entity.setStatus("1");
        }
        return baseMapper.insert(entity);
    }

    /**
     * 修改 SQL 示例
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updateByBo(AiSqlExampleBo bo) {
        AiSqlExample entity = MapstructUtils.convert(bo, AiSqlExample.class);
        return baseMapper.updateById(entity);
    }

    /**
     * 批量删除 SQL 示例
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteByIds(Long[] ids) {
        return baseMapper.deleteByIds(List.of(ids));
    }

    /**
     * 构建查询条件
     *
     * @param bo 查询条件
     * @return LambdaQueryWrapper
     */
    private LambdaQueryWrapper<AiSqlExample> buildQueryWrapper(AiSqlExampleBo bo) {
        LambdaQueryWrapper<AiSqlExample> lqw = Wrappers.lambdaQuery();
        lqw.like(bo.getQuestion() != null, AiSqlExample::getQuestion, bo.getQuestion());
        lqw.eq(bo.getCategory() != null, AiSqlExample::getCategory, bo.getCategory());
        lqw.eq(bo.getStatus() != null, AiSqlExample::getStatus, bo.getStatus());
        lqw.orderByAsc(AiSqlExample::getSortOrder);
        return lqw;
    }
}
