package org.dromara.erp.sch.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.utils.MapstructUtils;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.erp.sch.domain.SchPunchRule;
import org.dromara.erp.sch.domain.bo.SchPunchRuleBo;
import org.dromara.erp.sch.domain.vo.SchPunchRuleVo;
import org.dromara.erp.sch.mapper.SchPunchRuleMapper;
import org.dromara.erp.sch.service.ISchPunchRuleService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.dynamic.datasource.annotation.DS;
import java.util.List;

/**
 * 打卡规则Service业务层处理
 *
 * @author nexus
 * @date 2026-01-07
 */
@RequiredArgsConstructor
@DS("erp")
@Service
public class SchPunchRuleServiceImpl implements ISchPunchRuleService {

    private final SchPunchRuleMapper baseMapper;

    @Override
    public TableDataInfo<SchPunchRuleVo> selectPageList(SchPunchRuleBo bo, PageQuery pageQuery) {
        LambdaQueryWrapper<SchPunchRule> lqw = buildQueryWrapper(bo);
        Page<SchPunchRuleVo> page = baseMapper.selectVoPage(pageQuery.build(), lqw);
        return TableDataInfo.build(page);
    }

    @Override
    public List<SchPunchRuleVo> selectList(SchPunchRuleBo bo) {
        return baseMapper.selectVoList(buildQueryWrapper(bo));
    }

    @Override
    public SchPunchRuleVo selectById(Long id) {
        return baseMapper.selectVoById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int insertByBo(SchPunchRuleBo bo) {
        SchPunchRule entity = MapstructUtils.convert(bo, SchPunchRule.class);
        return baseMapper.insert(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updateByBo(SchPunchRuleBo bo) {
        SchPunchRule entity = MapstructUtils.convert(bo, SchPunchRule.class);
        return baseMapper.updateById(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteByIds(Long[] ids) {
        return baseMapper.deleteBatchIds(List.of(ids));
    }

    private LambdaQueryWrapper<SchPunchRule> buildQueryWrapper(SchPunchRuleBo bo) {
        LambdaQueryWrapper<SchPunchRule> lqw = Wrappers.lambdaQuery();
        if (bo != null) {
            lqw.like(StringUtils.isNotBlank(bo.getRuleName()), SchPunchRule::getRuleName, bo.getRuleName());
            lqw.eq(bo.getIsEnabled() != null, SchPunchRule::getIsEnabled, bo.getIsEnabled());
        }
        return lqw;
    }
}
