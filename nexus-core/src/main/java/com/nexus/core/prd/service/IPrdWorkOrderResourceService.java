package com.nexus.core.prd.service;

import java.util.List;
import com.baomidou.mybatisplus.extension.service.IService;
import com.nexus.core.prd.domain.PrdWorkOrderResource;

/**
 * 生产工单资源分配Service接口
 *
 * @author nexus
 * @date 2025-12-16
 */
public interface IPrdWorkOrderResourceService extends IService<PrdWorkOrderResource> {

    /**
     * 查询生产工单资源分配列表
     *
     * @param prdWorkOrderResource 生产工单资源分配
     * @return 生产工单资源分配集合
     */
    public List<PrdWorkOrderResource> selectPrdWorkOrderResourceList(PrdWorkOrderResource prdWorkOrderResource);

    /**
     * 根据工单ID查询资源分配列表
     *
     * @param woId 工单ID
     * @return 资源分配集合
     */
    public List<PrdWorkOrderResource> selectByWoId(Long woId);

    /**
     * 根据工单ID和资源类型查询资源分配列表
     *
     * @param woId 工单ID
     * @param resourceType 资源类型
     * @return 资源分配集合
     */
    public List<PrdWorkOrderResource> selectByWoIdAndType(Long woId, String resourceType);

    /**
     * 批量保存工单资源分配
     *
     * @param woId 工单ID
     * @param resourceList 资源分配列表
     * @return 结果
     */
    public boolean saveBatch(Long woId, List<PrdWorkOrderResource> resourceList);

    /**
     * 根据工单ID删除资源分配
     *
     * @param woId 工单ID
     * @return 结果
     */
    public boolean deleteByWoId(Long woId);

    /**
     * 根据工单ID和资源类型删除资源分配
     *
     * @param woId 工单ID
     * @param resourceType 资源类型
     * @return 结果
     */
    public boolean deleteByWoIdAndType(Long woId, String resourceType);
}
