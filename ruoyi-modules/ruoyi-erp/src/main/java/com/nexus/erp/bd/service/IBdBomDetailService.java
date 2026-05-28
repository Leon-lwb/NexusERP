package com.nexus.erp.bd.service;

import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.erp.bd.domain.bo.BdBomDetailBo;
import com.nexus.erp.bd.domain.vo.BdBomDetailVo;

import java.util.List;

/**
 * BOM明细Service接口
 *
 * @author ruoyi
 */
public interface IBdBomDetailService {
    TableDataInfo<BdBomDetailVo> selectPageList(BdBomDetailBo bo, PageQuery pageQuery);
    List<BdBomDetailVo> selectList(BdBomDetailBo bo);
    BdBomDetailVo selectById(Long id);
    int insertByBo(BdBomDetailBo bo);
    int updateByBo(BdBomDetailBo bo);
    int deleteByIds(Long[] ids);
    int saveBatch(List<BdBomDetailBo> list);
}
