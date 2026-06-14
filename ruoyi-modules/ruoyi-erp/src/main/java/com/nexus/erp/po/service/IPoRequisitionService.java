package com.nexus.erp.po.service;

import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.erp.po.domain.bo.PoRequisitionBo;
import com.nexus.erp.po.domain.vo.PoRequisitionVo;

import java.util.List;

/**
 * 部门请购单Service接口
 *
 * @author nexus
 * @date 2026-06-14
 */
public interface IPoRequisitionService {

    /**
     * 分页查询部门请购单列表
     *
     * @param bo        查询参数
     * @param pageQuery 分页参数
     * @return 部门请购单分页列表
     */
    TableDataInfo<PoRequisitionVo> selectPageList(PoRequisitionBo bo, PageQuery pageQuery);

    /**
     * 查询部门请购单列表（不分页）
     *
     * @param bo 查询参数
     * @return 部门请购单列表
     */
    List<PoRequisitionVo> selectList(PoRequisitionBo bo);

    /**
     * 根据ID查询部门请购单
     *
     * @param id 请购单ID
     * @return 部门请购单详细信息（含明细行）
     */
    PoRequisitionVo selectById(Long id);

    /**
     * 新增部门请购单
     *
     * @param bo 部门请购单业务对象
     * @return 影响行数
     */
    int insertByBo(PoRequisitionBo bo);

    /**
     * 修改部门请购单
     *
     * @param bo 部门请购单业务对象
     * @return 影响行数
     */
    int updateByBo(PoRequisitionBo bo);

    /**
     * 批量删除部门请购单
     *
     * @param ids 请购单ID数组
     * @return 影响行数
     */
    int deleteByIds(Long[] ids);
}
