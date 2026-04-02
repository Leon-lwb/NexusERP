package com.nexus.core.prd.service.impl;

import java.util.List;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.nexus.common.utils.DateUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.nexus.core.prd.mapper.PrdWorkOrderResourceMapper;
import com.nexus.core.prd.domain.PrdWorkOrderResource;
import com.nexus.core.prd.service.IPrdWorkOrderResourceService;

/**
 * 生产工单资源分配Service业务层处理
 *
 * @author nexus
 * @date 2025-12-16
 */
@Service
public class PrdWorkOrderResourceServiceImpl extends ServiceImpl<PrdWorkOrderResourceMapper, PrdWorkOrderResource> implements IPrdWorkOrderResourceService {

    @Autowired
    private PrdWorkOrderResourceMapper prdWorkOrderResourceMapper;

    /**
     * 查询生产工单资源分配列表
     *
     * @param prdWorkOrderResource 生产工单资源分配
     * @return 生产工单资源分配
     */
    @Override
    public List<PrdWorkOrderResource> selectPrdWorkOrderResourceList(PrdWorkOrderResource prdWorkOrderResource) {
        return prdWorkOrderResourceMapper.selectPrdWorkOrderResourceList(prdWorkOrderResource);
    }

    /**
     * 根据工单ID查询资源分配列表
     *
     * @param woId 工单ID
     * @return 资源分配集合
     */
    @Override
    public List<PrdWorkOrderResource> selectByWoId(Long woId) {
        return prdWorkOrderResourceMapper.selectByWoId(woId);
    }

    /**
     * 根据工单ID和资源类型查询资源分配列表
     *
     * @param woId 工单ID
     * @param resourceType 资源类型
     * @return 资源分配集合
     */
    @Override
    public List<PrdWorkOrderResource> selectByWoIdAndType(Long woId, String resourceType) {
        return prdWorkOrderResourceMapper.selectByWoIdAndType(woId, resourceType);
    }

    /**
     * 批量保存工单资源分配
     *
     * @param woId 工单ID
     * @param resourceList 资源分配列表
     * @return 结果
     */
    @Override
    public boolean saveBatch(Long woId, List<PrdWorkOrderResource> resourceList) {
        // 先删除该工单的所有资源分配
        deleteByWoId(woId);
        
        // 保存新的资源分配
        if (resourceList != null && !resourceList.isEmpty()) {
            for (int i = 0; i < resourceList.size(); i++) {
                PrdWorkOrderResource resource = resourceList.get(i);
                resource.setWoId(woId);
                resource.setSeq(i + 1); // 设置顺序
                resource.setStatus(PrdWorkOrderResource.STATUS_PENDING);
                prdWorkOrderResourceMapper.insert(resource);
            }
        }
        return true;
    }

    /**
     * 根据工单ID删除资源分配
     *
     * @param woId 工单ID
     * @return 结果
     */
    @Override
    public boolean deleteByWoId(Long woId) {
        return prdWorkOrderResourceMapper.deleteByWoId(woId) > 0;
    }

    /**
     * 根据工单ID和资源类型删除资源分配
     *
     * @param woId 工单ID
     * @param resourceType 资源类型
     * @return 结果
     */
    @Override
    public boolean deleteByWoIdAndType(Long woId, String resourceType) {
        return prdWorkOrderResourceMapper.deleteByWoIdAndType(woId, resourceType) > 0;
    }
}
