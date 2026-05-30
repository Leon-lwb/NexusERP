package com.nexus.erp.bd.service;

import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.erp.bd.domain.bo.BdWorkstationBo;
import com.nexus.erp.bd.domain.vo.BdWorkstationVo;

import java.util.List;

/**
 * 工作中心Service接口
 *
 * @author wanghuan
 */
public interface IBdWorkstationService {

    /**
     * 分页查询工作中心列表
     *
     * @param bo 查询参数，包含工作中心名称、编码等筛选条件
     * @param pageQuery 分页参数，包含页码、每页条数及排序信息
     * @return 工作中心分页列表
     */
    TableDataInfo<BdWorkstationVo> selectPageList(BdWorkstationBo bo, PageQuery pageQuery);

    /**
     * 查询工作中心列表（不分页）
     *
     * @param bo 查询参数，包含工作中心名称、编码等筛选条件
     * @return 工作中心列表
     */
    List<BdWorkstationVo> selectList(BdWorkstationBo bo);

    /**
     * 根据ID查询工作中心
     *
     * @param id 工作中心ID
     * @return 工作中心详细信息
     */
    BdWorkstationVo selectById(Long id);

    /**
     * 新增工作中心
     *
     * @param bo 工作中心业务对象，包含工作中心基本信息字段
     * @return 影响行数
     */
    int insertByBo(BdWorkstationBo bo);

    /**
     * 修改工作中心
     *
     * @param bo 工作中心业务对象，包含需要更新的字段信息
     * @return 影响行数
     */
    int updateByBo(BdWorkstationBo bo);

    /**
     * 批量删除工作中心
     *
     * @param ids 工作中心ID数组
     * @return 影响行数
     */
    int deleteByIds(Long[] ids);
}
