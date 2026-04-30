package org.dromara.erp.sch.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.utils.MapstructUtils;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.erp.sch.domain.SchHoliday;
import org.dromara.erp.sch.domain.bo.SchHolidayBo;
import org.dromara.erp.sch.domain.vo.SchHolidayVo;
import org.dromara.erp.sch.mapper.SchHolidayMapper;
import org.dromara.erp.sch.service.ISchHolidayService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 节假日设置Service业务层处理
 *
 * @author nexus
 * @date 2025-12-17
 */
@RequiredArgsConstructor
@Service
public class SchHolidayServiceImpl implements ISchHolidayService {

    private final SchHolidayMapper baseMapper;

    @Override
    public TableDataInfo<SchHolidayVo> selectPageList(SchHolidayBo bo, PageQuery pageQuery) {
        LambdaQueryWrapper<SchHoliday> lqw = buildQueryWrapper(bo);
        Page<SchHolidayVo> page = baseMapper.selectVoPage(pageQuery.build(), lqw);
        return TableDataInfo.build(page);
    }

    @Override
    public List<SchHolidayVo> selectList(SchHolidayBo bo) {
        return baseMapper.selectVoList(buildQueryWrapper(bo));
    }

    @Override
    public SchHolidayVo selectById(Long id) {
        return baseMapper.selectVoById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int insertByBo(SchHolidayBo bo) {
        SchHoliday entity = MapstructUtils.convert(bo, SchHoliday.class);
        return baseMapper.insert(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updateByBo(SchHolidayBo bo) {
        SchHoliday entity = MapstructUtils.convert(bo, SchHoliday.class);
        return baseMapper.updateById(entity);
    }

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
