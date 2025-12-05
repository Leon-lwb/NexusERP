package com.nexus.core.wm.service;

import java.util.List;
import com.nexus.core.wm.domain.WmArrivalNotice;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * 到货通知Service接口
 *
 * @author nexus
 * @date 2025-12-04
 */
public interface IWmArrivalNoticeService extends IService<WmArrivalNotice> {
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
     * 批量删除到货通知
     *
     * @param noticeIds 需要删除的到货通知主键集合
     * @return 结果
     */
    public int deleteWmArrivalNoticeByNoticeIds(Long[] noticeIds);

    /**
     * 删除到货通知信息
     *
     * @param noticeId 到货通知主键
     * @return 结果
     */
    public int deleteWmArrivalNoticeByNoticeId(Long noticeId);
}
