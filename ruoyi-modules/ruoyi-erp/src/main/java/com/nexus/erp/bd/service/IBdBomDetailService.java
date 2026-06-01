package com.nexus.erp.bd.service;

import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.erp.bd.domain.bo.BdBomDetailBo;
import com.nexus.erp.bd.domain.vo.BdBomDetailVo;

import java.util.List;

/**
 * BOM明细Service接口
 *
 * @author nexus
 */
public interface IBdBomDetailService {

    /**
     * 分页查询BOM明细列表
     *
     * @param bo 查询参数，包含BOM主表ID等筛选条件
     * @param pageQuery 分页参数，包含页码、每页条数及排序信息
     * @return BOM明细分页列表
     */
    TableDataInfo<BdBomDetailVo> selectPageList(BdBomDetailBo bo, PageQuery pageQuery);

    /**
     * 查询BOM明细列表（不分页）
     *
     * @param bo 查询参数，包含BOM主表ID等筛选条件
     * @return BOM明细列表
     */
    List<BdBomDetailVo> selectList(BdBomDetailBo bo);

    /**
     * 根据ID查询BOM明细
     *
     * @param id BOM明细ID
     * @return BOM明细详细信息
     */
    BdBomDetailVo selectById(Long id);

    /**
     * 新增BOM明细
     *
     * @param bo BOM明细业务对象，包含物料、数量和工序等信息
     * @return 影响行数
     */
    int insertByBo(BdBomDetailBo bo);

    /**
     * 修改BOM明细
     *
     * @param bo BOM明细业务对象，包含需要更新的字段信息
     * @return 影响行数
     */
    int updateByBo(BdBomDetailBo bo);

    /**
     * 批量删除BOM明细
     *
     * @param ids BOM明细ID数组
     * @return 影响行数
     */
    int deleteByIds(Long[] ids);

    /**
     * 批量保存BOM明细
     *
     * @param list BOM明细业务对象列表
     * @return 影响行数
     */
    int saveBatch(List<BdBomDetailBo> list);
}
