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
    /**
     * 查询BOM分页列表
     *
     * @param bo 查询参数
     * @param pageQuery 分页参数
     * @return BOM分页列表
     */
    TableDataInfo<BdBomVo> selectPageList(BdBomBo bo, PageQuery pageQuery);

    /**
     * 查询BOM列表
     *
     * @param bo 查询参数
     * @return BOM列表
     */
    List<BdBomVo> selectList(BdBomBo bo);

    /**
     * 根据ID查询BOM
     *
     * @param id BOM ID
     * @return BOM信息
     */
    BdBomVo selectById(Long id);

    /**
     * 新增BOM
     *
     * @param bo BOM信息
     * @return 结果
     */
    int insertByBo(BdBomBo bo);

    /**
     * 修改BOM
     *
     * @param bo BOM信息
     * @return 结果
     */
    int updateByBo(BdBomBo bo);

    /**
     * 批量删除BOM
     *
     * @param ids BOM ID数组
     * @return 结果
     */
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
