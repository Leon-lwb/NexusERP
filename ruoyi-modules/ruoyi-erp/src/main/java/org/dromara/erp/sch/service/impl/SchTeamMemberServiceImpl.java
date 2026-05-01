package org.dromara.erp.sch.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.utils.MapstructUtils;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.erp.sch.domain.SchTeamMember;
import org.dromara.erp.sch.domain.bo.SchTeamMemberBo;
import org.dromara.erp.sch.domain.vo.SchTeamMemberVo;
import org.dromara.erp.sch.mapper.SchTeamMemberMapper;
import org.dromara.erp.sch.service.ISchTeamMemberService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.dynamic.datasource.annotation.DS;
import java.util.List;

/**
 * 班组成员Service业务层处理
 *
 * @author nexus
 * @date 2025-12-24
 */
@RequiredArgsConstructor
@DS("erp")
@Service
public class SchTeamMemberServiceImpl implements ISchTeamMemberService {

    private final SchTeamMemberMapper baseMapper;

    @Override
    public TableDataInfo<SchTeamMemberVo> selectPageList(SchTeamMemberBo bo, PageQuery pageQuery) {
        LambdaQueryWrapper<SchTeamMember> lqw = buildQueryWrapper(bo);
        Page<SchTeamMemberVo> page = baseMapper.selectVoPage(pageQuery.build(), lqw);
        return TableDataInfo.build(page);
    }

    @Override
    public List<SchTeamMemberVo> selectList(SchTeamMemberBo bo) {
        return baseMapper.selectVoList(buildQueryWrapper(bo));
    }

    @Override
    public SchTeamMemberVo selectById(Long id) {
        return baseMapper.selectVoById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int insertByBo(SchTeamMemberBo bo) {
        SchTeamMember entity = MapstructUtils.convert(bo, SchTeamMember.class);
        return baseMapper.insert(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updateByBo(SchTeamMemberBo bo) {
        SchTeamMember entity = MapstructUtils.convert(bo, SchTeamMember.class);
        return baseMapper.updateById(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteByIds(Long[] ids) {
        return baseMapper.deleteBatchIds(List.of(ids));
    }

    private LambdaQueryWrapper<SchTeamMember> buildQueryWrapper(SchTeamMemberBo bo) {
        LambdaQueryWrapper<SchTeamMember> lqw = Wrappers.lambdaQuery();
        if (bo != null) {
            lqw.eq(bo.getTeamId() != null, SchTeamMember::getTeamId, bo.getTeamId());
            lqw.eq(bo.getWorkerId() != null, SchTeamMember::getWorkerId, bo.getWorkerId());
            lqw.eq(bo.getStatus() != null, SchTeamMember::getStatus, bo.getStatus());
        }
        return lqw;
    }
}
