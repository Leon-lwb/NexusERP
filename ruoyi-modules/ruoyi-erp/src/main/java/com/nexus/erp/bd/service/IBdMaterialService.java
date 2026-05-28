package com.nexus.erp.bd.service;

import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.erp.bd.domain.bo.BdMaterialBo;
import com.nexus.erp.bd.domain.vo.BdMaterialVo;

import java.util.List;

/**
 * 物料产品主Service接口
 *
 * @author wanghuan
 */
public interface IBdMaterialService {

    /**
     * 查询物料产品主分页列表
     *
     * @param bo 查询参数
     * @param pageQuery 分页参数
     * @return 物料产品主分页列表
     */
    TableDataInfo<BdMaterialVo> selectPageList(BdMaterialBo bo, PageQuery pageQuery);

    /**
     * 查询物料产品主列表
     *
     * @param bo 查询参数
     * @return 物料产品主列表
     */
    List<BdMaterialVo> selectList(BdMaterialBo bo);

    /**
     * 根据ID查询物料产品主
     *
     * @param id 物料产品主ID
     * @return 物料产品主信息
     */
    BdMaterialVo selectById(Long id);

    /**
     * 新增物料产品主
     *
     * @param bo 物料产品主信息
     * @return 影响行数
     */
    int insertByBo(BdMaterialBo bo);

    /**
     * 修改物料产品主
     *
     * @param bo 物料产品主信息
     * @return 影响行数
     */
    int updateByBo(BdMaterialBo bo);

    /**
     * 批量删除物料产品主
     *
     * @param ids 物料产品主ID数组
     * @return 影响行数
     */
    int deleteByIds(Long[] ids);

    /**
     * 根据分类ID列表查询物料产品主
     *
     * @param categoryIds 分类ID列表
     * @return 物料产品主列表
     */
    List<BdMaterialVo> selectByCategoryIds(List<Long> categoryIds);
}
