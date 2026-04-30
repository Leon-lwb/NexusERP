package org.dromara.erp.wm.service;

import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.erp.wm.domain.bo.WmArrivalNoticeLineBo;
import org.dromara.erp.wm.domain.vo.WmArrivalNoticeLineVo;

import java.util.List;

/**
 * 到货通知单行Service接口
 *
 * @author ruoyi
 */
public interface IWmArrivalNoticeLineService {

    TableDataInfo<WmArrivalNoticeLineVo> selectPageList(WmArrivalNoticeLineBo bo, PageQuery pageQuery);

    List<WmArrivalNoticeLineVo> selectList(WmArrivalNoticeLineBo bo);

    WmArrivalNoticeLineVo selectById(Long id);

    int insertByBo(WmArrivalNoticeLineBo bo);

    int updateByBo(WmArrivalNoticeLineBo bo);

    int deleteByIds(Long[] ids);
}
