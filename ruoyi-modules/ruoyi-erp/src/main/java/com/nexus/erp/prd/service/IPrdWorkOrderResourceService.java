package com.nexus.erp.prd.service;

import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.erp.prd.domain.PrdWorkOrderResource;
import com.nexus.erp.prd.domain.bo.PrdWorkOrderResourceBo;
import com.nexus.erp.prd.domain.vo.PrdWorkOrderResourceVo;

import java.util.List;

/**
 * 生产工单资源分配 Service 接口
 * <p>
 * 定义生产工单资源分配相关的业务逻辑操作，包括设备、工装、人力等资源的分配查询与管理。
 *
 * @author ruoyi
 */
public interface IPrdWorkOrderResourceService {

    /**
     * 分页查询生产工单资源分配列表
     *
     * @param bo        查询参数，包含工单编号、资源类型等筛选条件
     * @param pageQuery 分页参数，包含页码、每页条数及排序信息
     * @return 资源分配分页列表
     */
    TableDataInfo<PrdWorkOrderResourceVo> selectPageList(PrdWorkOrderResourceBo bo, PageQuery pageQuery);

    /**
     * 查询生产工单资源分配列表（不分页）
     *
     * @param bo 查询参数，包含工单编号、资源类型等筛选条件
     * @return 资源分配列表
     */
    List<PrdWorkOrderResourceVo> selectList(PrdWorkOrderResourceBo bo);

    /**
     * 根据 ID 查询资源分配详情
     *
     * @param id 资源分配记录 ID
     * @return 资源分配详细信息
     */
    PrdWorkOrderResourceVo selectById(Long id);

    /**
     * 查询生产工单资源分配列表（按实体条件）
     *
     * @param prdWorkOrderResource 资源分配查询实体
     * @return 资源分配列表
     */
    List<PrdWorkOrderResource> selectPrdWorkOrderResourceList(PrdWorkOrderResource prdWorkOrderResource);

    /**
     * 根据工单 ID 查询资源分配列表
     *
     * @param woId 生产工单 ID
     * @return 资源分配列表
     */
    List<PrdWorkOrderResource> selectByWoId(Long woId);

    /**
     * 根据工单 ID 和资源类型查询资源分配列表
     *
     * @param woId         生产工单 ID
     * @param resourceType 资源类型
     * @return 资源分配列表
     */
    List<PrdWorkOrderResource> selectByWoIdAndType(Long woId, String resourceType);

    /**
     * 新增生产工单资源分配
     *
     * @param bo 资源分配业务对象
     * @return 影响的行数
     */
    int insertByBo(PrdWorkOrderResourceBo bo);

    /**
     * 修改生产工单资源分配
     *
     * @param bo 资源分配业务对象
     * @return 影响的行数
     */
    int updateByBo(PrdWorkOrderResourceBo bo);

    /**
     * 批量删除生产工单资源分配
     *
     * @param ids 要删除的资源分配记录 ID 数组
     * @return 影响的行数
     */
    int deleteByIds(Long[] ids);

    /**
     * 批量保存工单资源分配
     * <p>
     * 先删除该工单的原有资源分配，再保存新的资源分配列表。
     *
     * @param woId         生产工单 ID
     * @param resourceList 资源分配列表
     * @return 是否保存成功
     */
    boolean saveBatch(Long woId, List<PrdWorkOrderResource> resourceList);

    /**
     * 根据工单 ID 删除资源分配
     *
     * @param woId 生产工单 ID
     * @return 是否删除成功
     */
    boolean deleteByWoId(Long woId);

    /**
     * 根据工单 ID 和资源类型删除资源分配
     *
     * @param woId         生产工单 ID
     * @param resourceType 资源类型
     * @return 是否删除成功
     */
    boolean deleteByWoIdAndType(Long woId, String resourceType);
}
