package com.nexus.erp.sch.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import com.nexus.common.core.utils.MapstructUtils;
import com.nexus.common.core.utils.StringUtils;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.erp.sch.domain.SchHoliday;
import com.nexus.erp.sch.domain.bo.SchHolidayBo;
import com.nexus.erp.sch.domain.vo.SchHolidayVo;
import com.nexus.erp.sch.mapper.SchHolidayMapper;
import com.nexus.erp.sch.service.ISchHolidayService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.dynamic.datasource.annotation.DS;
import java.util.List;

/**
 * 节假日设置Service业务层处理
 *
 * @author nexus
 * @date 2025-12-17
 */
@RequiredArgsConstructor
@DS("erp")
@Service
public class SchHolidayServiceImpl implements ISchHolidayService {

    private final SchHolidayMapper baseMapper;

    /**
     * 分页查询节假日设置列表
     *
     * @param bo        查询参数
     * @param pageQuery 分页参数
     * @return 节假日设置分页列表
     */
    @Override
    public TableDataInfo<SchHolidayVo> selectPageList(SchHolidayBo bo, PageQuery pageQuery) {
        LambdaQueryWrapper<SchHoliday> lqw = buildQueryWrapper(bo);
        Page<SchHolidayVo> page = baseMapper.selectVoPage(pageQuery.build(), lqw);
        return TableDataInfo.build(page);
    }

    /**
     * 查询节假日设置列表
     *
     * @param bo 查询参数
     * @return 节假日设置列表
     */
    @Override
    public List<SchHolidayVo> selectList(SchHolidayBo bo) {
        return baseMapper.selectVoList(buildQueryWrapper(bo));
    }

    /**
     * 根据ID获取节假日设置详情
     *
     * @param id 节假日ID
     * @return 节假日设置详情
     */
    @Override
    public SchHolidayVo selectById(Long id) {
        return baseMapper.selectVoById(id);
    }

    /**
     * 新增节假日设置
     *
     * @param bo 节假日设置业务对象
     * @return 影响行数
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int insertByBo(SchHolidayBo bo) {
        SchHoliday entity = MapstructUtils.convert(bo, SchHoliday.class);
        return baseMapper.insert(entity);
    }

    /**
     * 修改节假日设置
     *
     * @param bo 节假日设置业务对象
     * @return 影响行数
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updateByBo(SchHolidayBo bo) {
        SchHoliday entity = MapstructUtils.convert(bo, SchHoliday.class);
        return baseMapper.updateById(entity);
    }

    /**
     * 批量删除节假日设置
     *
     * @param ids 节假日ID数组
     * @return 影响行数
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteByIds(Long[] ids) {
        return baseMapper.deleteBatchIds(List.of(ids));
    }

    private LambdaQueryWrapper<SchHoliday> buildQueryWrapper(SchHolidayBo bo) {
        LambdaQueryWrapper<SchHoliday> lqw = Wrappers.lambdaQuery();
        if (bo != null) {
            lqw.eq(bo.getHolidayDate() != null, SchHoliday::getHolidayDate, bo.getHolidayDate());
            lqw.like(StringUtils.isNotBlank(bo.getHolidayName()), SchHoliday::getHolidayName, bo.getHolidayName());
            lqw.eq(bo.getYear() != null, SchHoliday::getYear, bo.getYear());
            lqw.eq(bo.getHolidayType() != null, SchHoliday::getHolidayType, bo.getHolidayType());
        }
        return lqw;
    }
}
