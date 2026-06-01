package com.nexus.erp.wm.service;

import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.erp.wm.domain.bo.WmArrivalNoticeLineBo;
import com.nexus.erp.wm.domain.vo.WmArrivalNoticeLineVo;

import java.util.List;

/**
 * 到货通知单行Service接口
 *
 * @author nexus
 */
public interface IWmArrivalNoticeLineService {

    TableDataInfo<WmArrivalNoticeLineVo> selectPageList(WmArrivalNoticeLineBo bo, PageQuery pageQuery);

    List<WmArrivalNoticeLineVo> selectList(WmArrivalNoticeLineBo bo);

    WmArrivalNoticeLineVo selectById(Long id);

    int insertByBo(WmArrivalNoticeLineBo bo);

    int updateByBo(WmArrivalNoticeLineBo bo);

    int deleteByIds(Long[] ids);
}
