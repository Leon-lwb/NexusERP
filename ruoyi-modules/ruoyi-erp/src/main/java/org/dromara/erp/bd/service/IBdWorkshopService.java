package org.dromara.erp.bd.service;

import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.erp.bd.domain.bo.BdWorkshopBo;
import org.dromara.erp.bd.domain.vo.BdWorkshopVo;

import java.util.List;

/**
 * 车间信息Service接口
 *
 * @author wanghuan
 */
public interface IBdWorkshopService {
    TableDataInfo<BdWorkshopVo> selectPageList(BdWorkshopBo bo, PageQuery pageQuery);
    List<BdWorkshopVo> selectList(BdWorkshopBo bo);
    BdWorkshopVo selectById(Long id);
    int insertByBo(BdWorkshopBo bo);
    int updateByBo(BdWorkshopBo bo);
    int deleteByIds(Long[] ids);
}
