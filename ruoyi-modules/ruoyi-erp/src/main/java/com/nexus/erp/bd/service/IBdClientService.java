package com.nexus.erp.bd.service;

import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.erp.bd.domain.bo.BdClientBo;
import com.nexus.erp.bd.domain.vo.BdClientVo;

import java.util.List;

/**
 * 客户信息Service接口
 *
 * @author wanghuan
 */
public interface IBdClientService {

    /**
     * 分页查询客户列表
     *
     * @param bo 查询参数，包含客户名称、编码等筛选条件
     * @param pageQuery 分页参数，包含页码、每页条数及排序信息
     * @return 客户分页列表
     */
    TableDataInfo<BdClientVo> selectPageList(BdClientBo bo, PageQuery pageQuery);

    /**
     * 查询客户列表（不分页）
     *
     * @param bo 查询参数，包含客户名称、编码等筛选条件
     * @return 客户列表
     */
    List<BdClientVo> selectList(BdClientBo bo);

    /**
     * 根据ID查询客户
     *
     * @param id 客户ID
     * @return 客户详细信息
     */
    BdClientVo selectById(Long id);

    /**
     * 新增客户
     *
     * @param bo 客户业务对象，包含客户基本信息字段
     * @return 影响行数
     */
    int insertByBo(BdClientBo bo);

    /**
     * 修改客户
     *
     * @param bo 客户业务对象，包含需要更新的字段信息
     * @return 影响行数
     */
    int updateByBo(BdClientBo bo);

    /**
     * 批量删除客户
     *
     * @param ids 客户ID数组
     * @return 影响行数
     */
    int deleteByIds(Long[] ids);
}
