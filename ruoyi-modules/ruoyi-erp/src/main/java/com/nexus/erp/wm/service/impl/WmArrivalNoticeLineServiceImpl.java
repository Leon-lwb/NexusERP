package com.nexus.erp.wm.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.nexus.common.core.utils.MapstructUtils;
import com.nexus.common.core.utils.StringUtils;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.erp.wm.domain.WmArrivalNoticeLine;
import com.nexus.erp.wm.domain.bo.WmArrivalNoticeLineBo;
import com.nexus.erp.wm.domain.vo.WmArrivalNoticeLineVo;
import com.nexus.erp.wm.mapper.WmArrivalNoticeLineMapper;
import com.nexus.erp.wm.service.IWmArrivalNoticeLineService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.dynamic.datasource.annotation.DS;
import java.util.List;

/**
 * 到货通知单行Service业务层处理
 *
 * @author nexus
 */
@DS("erp")
@Service
public class WmArrivalNoticeLineServiceImpl extends ServiceImpl<WmArrivalNoticeLineMapper, WmArrivalNoticeLine> implements IWmArrivalNoticeLineService {

    @Override
    public TableDataInfo<WmArrivalNoticeLineVo> selectPageList(WmArrivalNoticeLineBo bo, PageQuery pageQuery) {
        LambdaQueryWrapper<WmArrivalNoticeLine> lqw = buildQueryWrapper(bo);
        Page<WmArrivalNoticeLineVo> page = baseMapper.selectVoPage(pageQuery.build(), lqw);
        return TableDataInfo.build(page);
    }

    @Override
    public List<WmArrivalNoticeLineVo> selectList(WmArrivalNoticeLineBo bo) {
        return baseMapper.selectVoList(buildQueryWrapper(bo));
    }

    @Override
    public WmArrivalNoticeLineVo selectById(Long id) {
        return baseMapper.selectVoById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int insertByBo(WmArrivalNoticeLineBo bo) {
        WmArrivalNoticeLine entity = MapstructUtils.convert(bo, WmArrivalNoticeLine.class);
        return baseMapper.insert(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updateByBo(WmArrivalNoticeLineBo bo) {
        WmArrivalNoticeLine entity = MapstructUtils.convert(bo, WmArrivalNoticeLine.class);
        return baseMapper.updateById(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteByIds(Long[] ids) {
        return baseMapper.deleteBatchIds(List.of(ids));
    }

    private LambdaQueryWrapper<WmArrivalNoticeLine> buildQueryWrapper(WmArrivalNoticeLineBo bo) {
        LambdaQueryWrapper<WmArrivalNoticeLine> lqw = Wrappers.lambdaQuery();
        lqw.eq(bo.getNoticeId() != null, WmArrivalNoticeLine::getNoticeId, bo.getNoticeId());
        lqw.eq(bo.getMaterialId() != null, WmArrivalNoticeLine::getMaterialId, bo.getMaterialId());
        lqw.eq(StringUtils.isNotBlank(bo.getMaterialCode()), WmArrivalNoticeLine::getMaterialCode, bo.getMaterialCode());
        lqw.like(StringUtils.isNotBlank(bo.getMaterialName()), WmArrivalNoticeLine::getMaterialName, bo.getMaterialName());
        lqw.eq(StringUtils.isNotBlank(bo.getMaterialSpec()), WmArrivalNoticeLine::getMaterialSpec, bo.getMaterialSpec());
        lqw.eq(StringUtils.isNotBlank(bo.getIqcCheck()), WmArrivalNoticeLine::getIqcCheck, bo.getIqcCheck());
        return lqw;
    }
}
