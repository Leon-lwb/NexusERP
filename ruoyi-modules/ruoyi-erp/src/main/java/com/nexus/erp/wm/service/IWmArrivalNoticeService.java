package com.nexus.erp.wm.service;

import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.erp.wm.domain.bo.WmArrivalNoticeBo;
import com.nexus.erp.wm.domain.vo.WmArrivalNoticeVo;

import java.util.List;

/**
 * 到货通知Service接口
 *
 * @author nexus
 */
public interface IWmArrivalNoticeService {

    TableDataInfo<WmArrivalNoticeVo> selectPageList(WmArrivalNoticeBo bo, PageQuery pageQuery);

    List<WmArrivalNoticeVo> selectList(WmArrivalNoticeBo bo);

    WmArrivalNoticeVo selectById(Long id);

    int insertByBo(WmArrivalNoticeBo bo);

    int updateByBo(WmArrivalNoticeBo bo);

    int deleteByIds(Long[] ids);
}
