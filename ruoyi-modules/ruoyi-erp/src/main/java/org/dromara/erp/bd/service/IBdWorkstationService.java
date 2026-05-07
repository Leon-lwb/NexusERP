package org.dromara.erp.bd.service;

import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.erp.bd.domain.bo.BdWorkstationBo;
import org.dromara.erp.bd.domain.vo.BdWorkstationVo;

import java.util.List;

/**
 * 工作中心Service接口
 *
 * @author wanghuan
 */
public interface IBdWorkstationService {
    TableDataInfo<BdWorkstationVo> selectPageList(BdWorkstationBo bo, PageQuery pageQuery);
    List<BdWorkstationVo> selectList(BdWorkstationBo bo);
    BdWorkstationVo selectById(Long id);
    int insertByBo(BdWorkstationBo bo);
    int updateByBo(BdWorkstationBo bo);
    int deleteByIds(Long[] ids);
}
