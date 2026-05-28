package com.nexus.erp.bd.service;

import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.erp.bd.domain.bo.BdClientBo;
import com.nexus.erp.bd.domain.vo.BdClientVo;

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
