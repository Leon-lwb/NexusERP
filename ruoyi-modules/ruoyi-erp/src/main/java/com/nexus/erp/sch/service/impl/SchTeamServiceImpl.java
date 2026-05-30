package com.nexus.erp.sch.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import com.nexus.common.core.utils.MapstructUtils;
import com.nexus.common.core.utils.StringUtils;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.erp.sch.domain.SchTeam;
import com.nexus.erp.sch.domain.bo.SchTeamBo;
import com.nexus.erp.sch.domain.vo.SchTeamVo;
import com.nexus.erp.sch.mapper.SchTeamMapper;
import com.nexus.erp.sch.mapper.SchTeamMemberMapper;
import com.nexus.erp.sch.service.ISchTeamService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.dynamic.datasource.annotation.DS;

import java.util.List;

/**
 * 班组设置Service业务层处理
 *
 * @author nexus
 * @date 2025-12-23
 */
@RequiredArgsConstructor
@DS("erp")
@Service
public class SchTeamServiceImpl implements ISchTeamService {

    private final SchTeamMapper baseMapper;
    private final SchTeamMemberMapper schTeamMemberMapper;

    /**
     * 分页查询班组设置列表
     *
     * @param bo        查询参数
     * @param pageQuery 分页参数
     * @return 班组设置分页列表
     */
    @Override
    public TableDataInfo<SchTeamVo> selectPageList(SchTeamBo bo, PageQuery pageQuery) {
        LambdaQueryWrapper<SchTeam> lqw = buildQueryWrapper(bo);
        Page<SchTeamVo> page = baseMapper.selectVoPage(pageQuery.build(), lqw);
        return TableDataInfo.build(page);
    }

    /**
     * 查询班组设置列表
     *
     * @param bo 查询参数
     * @return 班组设置列表
     */
    @Override
    public List<SchTeamVo> selectList(SchTeamBo bo) {
        return baseMapper.selectVoList(buildQueryWrapper(bo));
    }

    /**
     * 根据ID获取班组设置详情
     *
     * @param id 班组ID
     * @return 班组设置详情
     */
    @Override
    public SchTeamVo selectById(Long id) {
        return baseMapper.selectVoById(id);
    }

    /**
     * 新增班组设置
     * <p>若未指定班组编码，则自动生成以 "SC" 开头的时间戳编码</p>
     *
     * @param bo 班组设置业务对象
     * @return 影响行数
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int insertByBo(SchTeamBo bo) {
        SchTeam entity = MapstructUtils.convert(bo, SchTeam.class);
        // 自动生成班组编码（简化处理，保留原逻辑框架）
        if (entity.getTeamCode() == null) {
            entity.setTeamCode("SC" + System.currentTimeMillis());
        }
        int rows = baseMapper.insert(entity);
        return rows;
    }

    /**
     * 修改班组设置
     *
     * @param bo 班组设置业务对象
     * @return 影响行数
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updateByBo(SchTeamBo bo) {
        SchTeam entity = MapstructUtils.convert(bo, SchTeam.class);
        return baseMapper.updateById(entity);
    }

    /**
     * 批量删除班组设置
     *
     * @param ids 班组ID数组
     * @return 影响行数
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteByIds(Long[] ids) {
        return baseMapper.deleteBatchIds(List.of(ids));
    }

    private LambdaQueryWrapper<SchTeam> buildQueryWrapper(SchTeamBo bo) {
        LambdaQueryWrapper<SchTeam> lqw = Wrappers.lambdaQuery();
        if (bo != null) {
            lqw.like(StringUtils.isNotBlank(bo.getTeamCode()), SchTeam::getTeamCode, bo.getTeamCode());
            lqw.like(StringUtils.isNotBlank(bo.getTeamName()), SchTeam::getTeamName, bo.getTeamName());
            lqw.eq(StringUtils.isNotBlank(bo.getTeamCategory()), SchTeam::getTeamCategory, bo.getTeamCategory());
            lqw.eq(bo.getWorkshopId() != null, SchTeam::getWorkshopId, bo.getWorkshopId());
            lqw.eq(bo.getStatus() != null, SchTeam::getStatus, bo.getStatus());
        }
        return lqw;
    }
}
