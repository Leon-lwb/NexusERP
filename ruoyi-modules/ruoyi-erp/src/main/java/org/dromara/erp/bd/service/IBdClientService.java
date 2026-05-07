package org.dromara.erp.bd.service;

import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.erp.bd.domain.bo.BdClientBo;
import org.dromara.erp.bd.domain.vo.BdClientVo;

import java.util.List;

/**
 * 客户信息Service接口
 *
 * @author wanghuan
 */
public interface IBdClientService {
    TableDataInfo<BdClientVo> selectPageList(BdClientBo bo, PageQuery pageQuery);
    List<BdClientVo> selectList(BdClientBo bo);
    BdClientVo selectById(Long id);
    int insertByBo(BdClientBo bo);
    int updateByBo(BdClientBo bo);
    int deleteByIds(Long[] ids);
}
