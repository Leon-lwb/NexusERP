package org.dromara.erp.wm.service;

import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.erp.wm.domain.bo.WmArrivalNoticeBo;
import org.dromara.erp.wm.domain.vo.WmArrivalNoticeVo;

import java.util.List;

/**
 * 到货通知Service接口
 *
 * @author ruoyi
 */
public interface IWmArrivalNoticeService {

    TableDataInfo<WmArrivalNoticeVo> selectPageList(WmArrivalNoticeBo bo, PageQuery pageQuery);

    List<WmArrivalNoticeVo> selectList(WmArrivalNoticeBo bo);

    WmArrivalNoticeVo selectById(Long id);

    int insertByBo(WmArrivalNoticeBo bo);

    int updateByBo(WmArrivalNoticeBo bo);

    int deleteByIds(Long[] ids);
}
