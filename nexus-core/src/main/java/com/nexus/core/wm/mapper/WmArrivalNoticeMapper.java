package com.nexus.core.wm.mapper;

import java.util.List;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.nexus.core.wm.domain.WmArrivalNotice;
import com.nexus.core.wm.domain.WmArrivalNoticeLine;

/**
 * 到货通知Mapper接口
 *
 * @author nexus
 * @date 2025-12-04
 */
public interface WmArrivalNoticeMapper extends BaseMapper<WmArrivalNotice> {
    /**
     * 查询到货通知
     *
     * @param noticeId 到货通知主键
     * @return 到货通知
     */
    public WmArrivalNotice selectWmArrivalNoticeByNoticeId(Long noticeId);

    /**
     * 查询到货通知列表
     *
     * @param wmArrivalNotice 到货通知
     * @return 到货通知集合
     */
    public List<WmArrivalNotice> selectWmArrivalNoticeList(WmArrivalNotice wmArrivalNotice);

    /**
     * 新增到货通知
     *
     * @param wmArrivalNotice 到货通知
     * @return 结果
     */
    public int insertWmArrivalNotice(WmArrivalNotice wmArrivalNotice);

    /**
     * 修改到货通知
     *
     * @param wmArrivalNotice 到货通知
     * @return 结果
     */
    public int updateWmArrivalNotice(WmArrivalNotice wmArrivalNotice);

    /**
     * 删除到货通知
     *
     * @param noticeId 到货通知主键
     * @return 结果
     */
    public int deleteWmArrivalNoticeByNoticeId(Long noticeId);

    /**
     * 批量删除到货通知
     *
     * @param noticeIds 需要删除的数据主键集合
     * @return 结果
     */
    public int deleteWmArrivalNoticeByNoticeIds(Long[] noticeIds);

        /**
         * 批量删除到货通知单行
         *
         * @param noticeIds 需要删除的数据主键集合
         * @return 结果
         */
        public int deleteWmArrivalNoticeLineByNoticeIds(Long[] noticeIds);

        /**
         * 批量新增到货通知单行
         *
         * @param wmArrivalNoticeLineList 到货通知单行列表
         * @return 结果
         */
        public int batchWmArrivalNoticeLine(List<WmArrivalNoticeLine> wmArrivalNoticeLineList);


        /**
         * 通过到货通知主键删除到货通知单行信息
         *
         * @param noticeId 到货通知ID
         * @return 结果
         */
        public int deleteWmArrivalNoticeLineByNoticeId(Long noticeId);
}
