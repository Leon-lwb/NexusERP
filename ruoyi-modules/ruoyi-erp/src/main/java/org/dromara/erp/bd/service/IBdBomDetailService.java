package org.dromara.erp.bd.service;

import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.erp.bd.domain.bo.BdBomDetailBo;
import org.dromara.erp.bd.domain.vo.BdBomDetailVo;

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
