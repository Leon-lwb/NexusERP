package com.nexus.erp.wm.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import com.nexus.common.core.utils.MapstructUtils;
import com.nexus.common.core.utils.StringUtils;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.erp.wm.domain.WmArrivalNotice;
import com.nexus.erp.wm.domain.WmArrivalNoticeLine;
import com.nexus.erp.wm.domain.bo.WmArrivalNoticeBo;
import com.nexus.erp.wm.domain.bo.WmArrivalNoticeLineBo;
import com.nexus.erp.wm.domain.vo.WmArrivalNoticeLineVo;
import com.nexus.erp.wm.domain.vo.WmArrivalNoticeVo;
import com.nexus.erp.wm.mapper.WmArrivalNoticeLineMapper;
import com.nexus.erp.wm.mapper.WmArrivalNoticeMapper;
import com.nexus.erp.wm.service.IWmArrivalNoticeService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.dynamic.datasource.annotation.DS;
import java.util.List;

/**
 * 到货通知Service业务层处理
 *
 * @author ruoyi
 */
@RequiredArgsConstructor
@DS("erp")
@Service
public class WmArrivalNoticeServiceImpl implements IWmArrivalNoticeService {

    private final WmArrivalNoticeMapper baseMapper;
    private final WmArrivalNoticeLineMapper noticeLineMapper;

    @Override
    public TableDataInfo<WmArrivalNoticeVo> selectPageList(WmArrivalNoticeBo bo, PageQuery pageQuery) {
        LambdaQueryWrapper<WmArrivalNotice> lqw = buildQueryWrapper(bo);
        Page<WmArrivalNoticeVo> page = baseMapper.selectVoPage(pageQuery.build(), lqw);
        return TableDataInfo.build(page);
    }

    @Override
    public List<WmArrivalNoticeVo> selectList(WmArrivalNoticeBo bo) {
        return baseMapper.selectVoList(buildQueryWrapper(bo));
    }

    @Override
    public WmArrivalNoticeVo selectById(Long id) {
        WmArrivalNoticeVo vo = baseMapper.selectVoById(id);
        if (vo != null) {
            List<WmArrivalNoticeLine> lineList = noticeLineMapper.selectList(
                new LambdaQueryWrapper<WmArrivalNoticeLine>()
                    .eq(WmArrivalNoticeLine::getNoticeId, id));
            vo.setWmArrivalNoticeLineList(MapstructUtils.convert(lineList, WmArrivalNoticeLineVo.class));
        }
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int insertByBo(WmArrivalNoticeBo bo) {
        WmArrivalNotice entity = MapstructUtils.convert(bo, WmArrivalNotice.class);
        int rows = baseMapper.insert(entity);
        insertOrUpdateLines(entity.getNoticeId(), bo.getWmArrivalNoticeLineList());
        return rows;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updateByBo(WmArrivalNoticeBo bo) {
        WmArrivalNotice entity = MapstructUtils.convert(bo, WmArrivalNotice.class);
        // Delete existing lines then re-insert
        noticeLineMapper.delete(new LambdaQueryWrapper<WmArrivalNoticeLine>()
            .eq(WmArrivalNoticeLine::getNoticeId, entity.getNoticeId()));
        insertOrUpdateLines(entity.getNoticeId(), bo.getWmArrivalNoticeLineList());
        return baseMapper.updateById(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteByIds(Long[] ids) {
        // Delete child lines first
        noticeLineMapper.delete(new LambdaQueryWrapper<WmArrivalNoticeLine>()
            .in(WmArrivalNoticeLine::getNoticeId, List.of(ids)));
        return baseMapper.deleteBatchIds(List.of(ids));
    }

    /**
     * Batch insert notice lines
     */
    private void insertOrUpdateLines(Long noticeId, List<WmArrivalNoticeLineBo> lineBoList) {
        if (lineBoList != null && !lineBoList.isEmpty()) {
            List<WmArrivalNoticeLine> lineList = MapstructUtils.convert(lineBoList, WmArrivalNoticeLine.class);
            for (WmArrivalNoticeLine line : lineList) {
                line.setLineId(null);
                line.setNoticeId(noticeId);
            }
            noticeLineMapper.insertBatch(lineList);
        }
    }

    private LambdaQueryWrapper<WmArrivalNotice> buildQueryWrapper(WmArrivalNoticeBo bo) {
        LambdaQueryWrapper<WmArrivalNotice> lqw = Wrappers.lambdaQuery();
        lqw.eq(StringUtils.isNotBlank(bo.getNoticeCode()), WmArrivalNotice::getNoticeCode, bo.getNoticeCode());
        lqw.like(StringUtils.isNotBlank(bo.getNoticeName()), WmArrivalNotice::getNoticeName, bo.getNoticeName());
        lqw.eq(StringUtils.isNotBlank(bo.getPurOrderCode()), WmArrivalNotice::getPurOrderCode, bo.getPurOrderCode());
        lqw.eq(bo.getVendorId() != null, WmArrivalNotice::getVendorId, bo.getVendorId());
        lqw.eq(StringUtils.isNotBlank(bo.getVendorCode()), WmArrivalNotice::getVendorCode, bo.getVendorCode());
        lqw.like(StringUtils.isNotBlank(bo.getVendorName()), WmArrivalNotice::getVendorName, bo.getVendorName());
        lqw.eq(StringUtils.isNotBlank(bo.getVendorNick()), WmArrivalNotice::getVendorNick, bo.getVendorNick());
        lqw.eq(bo.getArrivalDate() != null, WmArrivalNotice::getArrivalDate, bo.getArrivalDate());
        lqw.eq(StringUtils.isNotBlank(bo.getContact()), WmArrivalNotice::getContact, bo.getContact());
        lqw.eq(StringUtils.isNotBlank(bo.getPhone()), WmArrivalNotice::getPhone, bo.getPhone());
        lqw.eq(StringUtils.isNotBlank(bo.getStatus()), WmArrivalNotice::getStatus, bo.getStatus());
        return lqw;
    }
}
