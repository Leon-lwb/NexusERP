package com.nexus.core.wm.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;

import java.util.List;

import com.nexus.common.utils.DateUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;

import com.nexus.common.utils.StringUtils;
import org.springframework.transaction.annotation.Transactional;
import com.nexus.core.wm.domain.WmArrivalNoticeLine;
import com.nexus.core.wm.mapper.WmArrivalNoticeMapper;
import com.nexus.core.wm.domain.WmArrivalNotice;
import com.nexus.core.wm.service.IWmArrivalNoticeService;

/**
 * 到货通知Service业务层处理
 *
 * @author nexus
 * @date 2025-12-04
 */
@Service
public class WmArrivalNoticeServiceImpl extends ServiceImpl<WmArrivalNoticeMapper, WmArrivalNotice> implements IWmArrivalNoticeService {

    @Autowired
    private WmArrivalNoticeMapper wmArrivalNoticeMapper;

    /**
     * 查询到货通知
     *
     * @param noticeId 到货通知主键
     * @return 到货通知
     */
    @Override
    public WmArrivalNotice selectWmArrivalNoticeByNoticeId(Long noticeId) {
        return wmArrivalNoticeMapper.selectWmArrivalNoticeByNoticeId(noticeId);
    }

    /**
     * 查询到货通知列表
     *
     * @param wmArrivalNotice 到货通知
     * @return 到货通知
     */
    @Override
    public List<WmArrivalNotice> selectWmArrivalNoticeList(WmArrivalNotice wmArrivalNotice) {
        return wmArrivalNoticeMapper.selectWmArrivalNoticeList(wmArrivalNotice);
    }

    /**
     * 新增到货通知
     *
     * @param wmArrivalNotice 到货通知
     * @return 结果
     */
    @Transactional
    @Override
    public int insertWmArrivalNotice(WmArrivalNotice wmArrivalNotice) {
        wmArrivalNotice.setCreateTime(DateUtils.getNowDate());
        int rows = wmArrivalNoticeMapper.insertWmArrivalNotice(wmArrivalNotice);
        insertWmArrivalNoticeLine(wmArrivalNotice);
        return rows;
    }

    /**
     * 修改到货通知
     *
     * @param wmArrivalNotice 到货通知
     * @return 结果
     */
    @Transactional
    @Override
    public int updateWmArrivalNotice(WmArrivalNotice wmArrivalNotice) {
        wmArrivalNotice.setUpdateTime(DateUtils.getNowDate());
        wmArrivalNoticeMapper.deleteWmArrivalNoticeLineByNoticeId(wmArrivalNotice.getNoticeId());
        insertWmArrivalNoticeLine(wmArrivalNotice);
        return wmArrivalNoticeMapper.updateWmArrivalNotice(wmArrivalNotice);
    }

    /**
     * 批量删除到货通知
     *
     * @param noticeIds 需要删除的到货通知主键
     * @return 结果
     */
    @Transactional
    @Override
    public int deleteWmArrivalNoticeByNoticeIds(Long[] noticeIds) {
        wmArrivalNoticeMapper.deleteWmArrivalNoticeLineByNoticeIds(noticeIds);
        return wmArrivalNoticeMapper.deleteWmArrivalNoticeByNoticeIds(noticeIds);
    }

    /**
     * 删除到货通知信息
     *
     * @param noticeId 到货通知主键
     * @return 结果
     */
    @Transactional
    @Override
    public int deleteWmArrivalNoticeByNoticeId(Long noticeId) {
        wmArrivalNoticeMapper.deleteWmArrivalNoticeLineByNoticeId(noticeId);
        return wmArrivalNoticeMapper.deleteWmArrivalNoticeByNoticeId(noticeId);
    }

    /**
     * 新增到货通知单行信息
     *
     * @param wmArrivalNotice 到货通知对象
     */
    public void insertWmArrivalNoticeLine(WmArrivalNotice wmArrivalNotice) {
        List<WmArrivalNoticeLine> wmArrivalNoticeLineList = wmArrivalNotice.getWmArrivalNoticeLineList();
        Long noticeId = wmArrivalNotice.getNoticeId();
        if (StringUtils.isNotNull(wmArrivalNoticeLineList)) {
            List<WmArrivalNoticeLine> list = new ArrayList<WmArrivalNoticeLine>();
            for (WmArrivalNoticeLine wmArrivalNoticeLine : wmArrivalNoticeLineList) {
                wmArrivalNoticeLine.setNoticeId(noticeId);
                list.add(wmArrivalNoticeLine);
            }
            if (list.size() > 0) {
                wmArrivalNoticeMapper.batchWmArrivalNoticeLine(list);
            }
        }
    }
}
