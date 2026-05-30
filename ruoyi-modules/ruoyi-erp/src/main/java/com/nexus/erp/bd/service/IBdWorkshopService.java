package com.nexus.erp.bd.service;

import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.erp.bd.domain.bo.BdWorkshopBo;
import com.nexus.erp.bd.domain.vo.BdWorkshopVo;

import java.util.List;

/**
 * 车间信息Service接口
 *
 * @author wanghuan
 */
public interface IBdWorkshopService {

    /**
     * 分页查询车间列表
     *
     * @param bo 查询参数，包含车间名称、编码等筛选条件
     * @param pageQuery 分页参数，包含页码、每页条数及排序信息
     * @return 车间分页列表
     */
    TableDataInfo<BdWorkshopVo> selectPageList(BdWorkshopBo bo, PageQuery pageQuery);

    /**
     * 查询车间列表（不分页）
     *
     * @param bo 查询参数，包含车间名称、编码等筛选条件
     * @return 车间列表
     */
    List<BdWorkshopVo> selectList(BdWorkshopBo bo);

    /**
     * 根据ID查询车间
     *
     * @param id 车间ID
     * @return 车间详细信息
     */
    BdWorkshopVo selectById(Long id);

    /**
     * 新增车间
     *
     * @param bo 车间业务对象，包含车间基本信息字段
     * @return 影响行数
     */
    int insertByBo(BdWorkshopBo bo);

    /**
     * 修改车间
     *
     * @param bo 车间业务对象，包含需要更新的字段信息
     * @return 影响行数
     */
    int updateByBo(BdWorkshopBo bo);

    /**
     * 批量删除车间
     *
     * @param ids 车间ID数组
     * @return 影响行数
     */
    int deleteByIds(Long[] ids);
}
