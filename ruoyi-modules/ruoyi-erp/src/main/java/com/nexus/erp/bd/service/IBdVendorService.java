package com.nexus.erp.bd.service;

import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.erp.bd.domain.bo.BdVendorBo;
import com.nexus.erp.bd.domain.vo.BdVendorVo;

import java.util.List;

/**
 * 供应商信息Service接口
 *
 * @author wanghuan
 */
public interface IBdVendorService {

    /**
     * 分页查询供应商列表
     *
     * @param bo 查询参数，包含供应商名称、编码等筛选条件
     * @param pageQuery 分页参数，包含页码、每页条数及排序信息
     * @return 供应商分页列表
     */
    TableDataInfo<BdVendorVo> selectPageList(BdVendorBo bo, PageQuery pageQuery);

    /**
     * 查询供应商列表（不分页）
     *
     * @param bo 查询参数，包含供应商名称、编码等筛选条件
     * @return 供应商列表
     */
    List<BdVendorVo> selectList(BdVendorBo bo);

    /**
     * 根据ID查询供应商
     *
     * @param id 供应商ID
     * @return 供应商详细信息
     */
    BdVendorVo selectById(Long id);

    /**
     * 新增供应商
     *
     * @param bo 供应商业务对象，包含供应商基本信息字段
     * @return 影响行数
     */
    int insertByBo(BdVendorBo bo);

    /**
     * 修改供应商
     *
     * @param bo 供应商业务对象，包含需要更新的字段信息
     * @return 影响行数
     */
    int updateByBo(BdVendorBo bo);

    /**
     * 批量删除供应商
     *
     * @param ids 供应商ID数组
     * @return 影响行数
     */
    int deleteByIds(Long[] ids);
}
