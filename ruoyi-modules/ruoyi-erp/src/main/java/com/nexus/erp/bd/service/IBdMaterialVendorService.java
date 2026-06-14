package com.nexus.erp.bd.service;

import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.erp.bd.domain.bo.BdMaterialVendorBo;
import com.nexus.erp.bd.domain.vo.BdMaterialVendorVo;

import java.util.List;

/**
 * 物料-供应商关系Service接口
 *
 * @author nexus
 * @date 2026-06-14
 */
public interface IBdMaterialVendorService {

    /**
     * 分页查询物料-供应商关系列表
     *
     * @param bo        查询参数，包含物料ID、供应商ID等筛选条件
     * @param pageQuery 分页参数，包含页码、每页条数及排序信息
     * @return 物料-供应商关系分页列表
     */
    TableDataInfo<BdMaterialVendorVo> selectPageList(BdMaterialVendorBo bo, PageQuery pageQuery);

    /**
     * 查询物料-供应商关系列表（不分页）
     *
     * @param bo 查询参数，包含物料ID、供应商ID等筛选条件
     * @return 物料-供应商关系列表
     */
    List<BdMaterialVendorVo> selectList(BdMaterialVendorBo bo);

    /**
     * 根据ID查询物料-供应商关系
     *
     * @param id 关系ID
     * @return 物料-供应商关系详细信息
     */
    BdMaterialVendorVo selectById(Long id);

    /**
     * 新增物料-供应商关系
     *
     * @param bo 物料-供应商业务对象，包含关系基本信息字段
     * @return 影响行数
     */
    int insertByBo(BdMaterialVendorBo bo);

    /**
     * 修改物料-供应商关系
     *
     * @param bo 物料-供应商业务对象，包含需要更新的字段信息
     * @return 影响行数
     */
    int updateByBo(BdMaterialVendorBo bo);

    /**
     * 批量删除物料-供应商关系
     *
     * @param ids 关系ID数组
     * @return 影响行数
     */
    int deleteByIds(Long[] ids);
}
