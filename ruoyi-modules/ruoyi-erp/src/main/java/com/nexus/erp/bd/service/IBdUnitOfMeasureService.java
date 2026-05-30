package com.nexus.erp.bd.service;

import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.erp.bd.domain.bo.BdUnitOfMeasureBo;
import com.nexus.erp.bd.domain.vo.BdUnitOfMeasureVo;

import java.util.List;

/**
 * 计量单位Service接口
 *
 * @author wanghuan
 */
public interface IBdUnitOfMeasureService {

    /**
     * 分页查询计量单位列表
     *
     * @param bo 查询参数，包含计量单位名称、编码等筛选条件
     * @param pageQuery 分页参数，包含页码、每页条数及排序信息
     * @return 计量单位分页列表
     */
    TableDataInfo<BdUnitOfMeasureVo> selectPageList(BdUnitOfMeasureBo bo, PageQuery pageQuery);

    /**
     * 查询计量单位列表（不分页）
     *
     * @param bo 查询参数，包含计量单位名称、编码等筛选条件
     * @return 计量单位列表
     */
    List<BdUnitOfMeasureVo> selectList(BdUnitOfMeasureBo bo);

    /**
     * 根据ID查询计量单位
     *
     * @param id 计量单位ID
     * @return 计量单位详细信息
     */
    BdUnitOfMeasureVo selectById(Long id);

    /**
     * 根据主计量单位ID查询其所有换算单位
     *
     * @param primaryId 主计量单位ID
     * @return 换算单位列表
     */
    List<BdUnitOfMeasureVo> selectByPrimaryId(Long primaryId);

    /**
     * 查询所有计量单位类别列表
     *
     * @return 计量单位类别名称列表
     */
    List<String> selectCategories();

    /**
     * 新增计量单位
     *
     * @param bo 计量单位业务对象，包含计量单位名称、编码及换算关系等字段
     * @return 影响行数
     */
    int insertByBo(BdUnitOfMeasureBo bo);

    /**
     * 修改计量单位
     *
     * @param bo 计量单位业务对象，包含需要更新的字段信息
     * @return 影响行数
     */
    int updateByBo(BdUnitOfMeasureBo bo);

    /**
     * 批量删除计量单位
     *
     * @param ids 计量单位ID数组
     * @return 影响行数
     */
    int deleteByIds(Long[] ids);
}
