package org.dromara.erp.bd.service;

import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.erp.bd.domain.bo.BdBomBo;
import org.dromara.erp.bd.domain.vo.BdBomVo;

import java.util.List;

/**
 * BOMService接口
 *
 * @author wanghuan
 */
public interface IBdBomService {
    TableDataInfo<BdBomVo> selectPageList(BdBomBo bo, PageQuery pageQuery);
    List<BdBomVo> selectList(BdBomBo bo);
    BdBomVo selectById(Long id);
    int insertByBo(BdBomBo bo);
    int updateByBo(BdBomBo bo);
    int deleteByIds(Long[] ids);

    /**
     * 激活BOM版本（将该版本设为当前版本，同物料其他版本设为非当前）
     */
    void activateVersion(Long bomId);

    /**
     * 校验BOM完整性（检查明细是否为空、是否有循环引用）
     * @return 校验结果消息
     */
    String verifyBom(Long bomId);
}
