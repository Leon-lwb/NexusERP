package com.nexus.core.prd.mapper;

import java.util.List;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.nexus.core.prd.domain.PrdWorkOrderResource;
import org.apache.ibatis.annotations.Param;

/**
 * 生产工单资源分配Mapper接口
 *
 * @author nexus
 * @date 2025-12-16
 */
public interface PrdWorkOrderResourceMapper extends BaseMapper<PrdWorkOrderResource> {

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
    public List<PrdWorkOrderResource> selectByWoId(@Param("woId") Long woId);

    /**
     * 根据工单ID和资源类型查询资源分配列表
     *
     * @param woId 工单ID
     * @param resourceType 资源类型
     * @return 资源分配集合
     */
    public List<PrdWorkOrderResource> selectByWoIdAndType(@Param("woId") Long woId, @Param("resourceType") String resourceType);

    /**
     * 批量删除生产工单资源分配
     *
     * @param ids 需要删除的数据ID
     * @return 结果
     */
    public int deletePrdWorkOrderResourceByIds(Long[] ids);

    /**
     * 根据工单ID删除资源分配
     *
     * @param woId 工单ID
     * @return 结果
     */
    public int deleteByWoId(@Param("woId") Long woId);

    /**
     * 根据工单ID和资源类型删除资源分配
     *
     * @param woId 工单ID
     * @param resourceType 资源类型
     * @return 结果
     */
    public int deleteByWoIdAndType(@Param("woId") Long woId, @Param("resourceType") String resourceType);
}
