package com.nexus.erp.prd.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import com.nexus.common.core.utils.MapstructUtils;
import com.nexus.common.core.utils.StringUtils;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.erp.prd.domain.PrdWorkOrderResource;
import com.nexus.erp.prd.domain.bo.PrdWorkOrderResourceBo;
import com.nexus.erp.prd.domain.vo.PrdWorkOrderResourceVo;
import com.nexus.erp.prd.mapper.PrdWorkOrderResourceMapper;
import com.nexus.erp.prd.service.IPrdWorkOrderResourceService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.dynamic.datasource.annotation.DS;
import java.util.List;

/**
 * 生产工单资源分配 Service 业务层实现
 * <p>
 * 实现生产工单资源分配相关的业务逻辑，包括设备、工装、人力等资源的分配查询与管理。
 *
 * @author ruoyi
 */
@DS("erp")
@RequiredArgsConstructor
@Service
public class PrdWorkOrderResourceServiceImpl implements IPrdWorkOrderResourceService {

    private final PrdWorkOrderResourceMapper baseMapper;

    /**
     * 分页查询生产工单资源分配列表
     *
     * @param bo        查询参数
     * @param pageQuery 分页参数
     * @return 资源分配分页列表
     */
    @Override
    public TableDataInfo<PrdWorkOrderResourceVo> selectPageList(PrdWorkOrderResourceBo bo, PageQuery pageQuery) {
        LambdaQueryWrapper<PrdWorkOrderResource> lqw = buildQueryWrapper(bo);
        Page<PrdWorkOrderResourceVo> page = baseMapper.selectVoPage(pageQuery.build(), lqw);
        return TableDataInfo.build(page);
    }

    /**
     * 查询生产工单资源分配列表（不分页）
     *
     * @param bo 查询参数
     * @return 资源分配列表
     */
    @Override
    public List<PrdWorkOrderResourceVo> selectList(PrdWorkOrderResourceBo bo) {
        return baseMapper.selectVoList(buildQueryWrapper(bo));
    }

    /**
     * 根据 ID 查询资源分配详情
     *
     * @param id 资源分配记录 ID
     * @return 资源分配详细信息
     */
    @Override
    public PrdWorkOrderResourceVo selectById(Long id) {
        return baseMapper.selectVoById(id);
    }

    /**
     * 查询生产工单资源分配列表（按实体条件）
     *
     * @param prdWorkOrderResource 资源分配查询实体
     * @return 资源分配列表
     */
    @Override
    public List<PrdWorkOrderResource> selectPrdWorkOrderResourceList(PrdWorkOrderResource prdWorkOrderResource) {
        LambdaQueryWrapper<PrdWorkOrderResource> lqw = buildEntityQueryWrapper(prdWorkOrderResource);
        return baseMapper.selectList(lqw);
    }

    /**
     * 根据工单 ID 查询资源分配列表
     *
     * @param woId 生产工单 ID
     * @return 资源分配列表
     */
    @Override
    public List<PrdWorkOrderResource> selectByWoId(Long woId) {
        LambdaQueryWrapper<PrdWorkOrderResource> lqw = Wrappers.lambdaQuery();
        lqw.eq(PrdWorkOrderResource::getWoId, woId);
        lqw.orderByAsc(PrdWorkOrderResource::getSeq);
        return baseMapper.selectList(lqw);
    }

    /**
     * 根据工单 ID 和资源类型查询资源分配列表
     *
     * @param woId         生产工单 ID
     * @param resourceType 资源类型
     * @return 资源分配列表
     */
    @Override
    public List<PrdWorkOrderResource> selectByWoIdAndType(Long woId, String resourceType) {
        LambdaQueryWrapper<PrdWorkOrderResource> lqw = Wrappers.lambdaQuery();
        lqw.eq(PrdWorkOrderResource::getWoId, woId);
        lqw.eq(PrdWorkOrderResource::getResourceType, resourceType);
        lqw.orderByAsc(PrdWorkOrderResource::getSeq);
        return baseMapper.selectList(lqw);
    }

    /**
     * 新增生产工单资源分配
     *
     * @param bo 资源分配业务对象
     * @return 影响的行数
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int insertByBo(PrdWorkOrderResourceBo bo) {
        PrdWorkOrderResource entity = MapstructUtils.convert(bo, PrdWorkOrderResource.class);
        return baseMapper.insert(entity);
    }

    /**
     * 修改生产工单资源分配
     *
     * @param bo 资源分配业务对象
     * @return 影响的行数
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updateByBo(PrdWorkOrderResourceBo bo) {
        PrdWorkOrderResource entity = MapstructUtils.convert(bo, PrdWorkOrderResource.class);
        return baseMapper.updateById(entity);
    }

    /**
     * 批量删除生产工单资源分配
     *
     * @param ids 要删除的资源分配记录 ID 数组
     * @return 影响的行数
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteByIds(Long[] ids) {
        return baseMapper.deleteBatchIds(List.of(ids));
    }

    /**
     * 批量保存工单资源分配
     * <p>
     * 先删除该工单的原有资源分配，再保存新的资源分配列表。
     *
     * @param woId         生产工单 ID
     * @param resourceList 资源分配列表
     * @return 是否保存成功
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean saveBatch(Long woId, List<PrdWorkOrderResource> resourceList) {
        // 先删除该工单的所有资源分配
        deleteByWoId(woId);

        // 保存新的资源分配
        if (resourceList != null && !resourceList.isEmpty()) {
            for (int i = 0; i < resourceList.size(); i++) {
                PrdWorkOrderResource resource = resourceList.get(i);
                resource.setId(null);
                resource.setWoId(woId);
                resource.setSeq(i + 1);
                resource.setStatus(PrdWorkOrderResource.STATUS_PENDING);
                baseMapper.insert(resource);
            }
        }
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)/**
     * 根据工单 ID 删除资源分配
     *
     * @param woId 生产工单 ID
     * @return 是否删除成功
     */
    @Override
    public boolean deleteByWoId(Long woId) {
        LambdaQueryWrapper<PrdWorkOrderResource> lqw = Wrappers.lambdaQuery();
        lqw.eq(PrdWorkOrderResource::getWoId, woId);
        return baseMapper.delete(lqw) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)/**
     * 根据工单 ID 和资源类型删除资源分配
     *
     * @param woId         生产工单 ID
     * @param resourceType 资源类型
     * @return 是否删除成功
     */
    @Override
    public boolean deleteByWoIdAndType(Long woId, String resourceType) {
        LambdaQueryWrapper<PrdWorkOrderResource> lqw = Wrappers.lambdaQuery();
        lqw.eq(PrdWorkOrderResource::getWoId, woId);
        lqw.eq(PrdWorkOrderResource::getResourceType, resourceType);
        return baseMapper.delete(lqw) > 0;
    }

    private LambdaQueryWrapper<PrdWorkOrderResource> buildQueryWrapper(PrdWorkOrderResourceBo bo) {
        LambdaQueryWrapper<PrdWorkOrderResource> lqw = Wrappers.lambdaQuery();
        lqw.eq(bo.getWoId() != null, PrdWorkOrderResource::getWoId, bo.getWoId());
        lqw.eq(StringUtils.isNotBlank(bo.getResourceType()), PrdWorkOrderResource::getResourceType, bo.getResourceType());
        lqw.eq(bo.getResourceId() != null, PrdWorkOrderResource::getResourceId, bo.getResourceId());
        lqw.like(StringUtils.isNotBlank(bo.getResourceCode()), PrdWorkOrderResource::getResourceCode, bo.getResourceCode());
        lqw.like(StringUtils.isNotBlank(bo.getResourceName()), PrdWorkOrderResource::getResourceName, bo.getResourceName());
        lqw.eq(bo.getParentId() != null, PrdWorkOrderResource::getParentId, bo.getParentId());
        lqw.eq(StringUtils.isNotBlank(bo.getParentType()), PrdWorkOrderResource::getParentType, bo.getParentType());
        lqw.eq(StringUtils.isNotBlank(bo.getStatus()), PrdWorkOrderResource::getStatus, bo.getStatus());
        lqw.orderByAsc(PrdWorkOrderResource::getSeq);
        return lqw;
    }

    private LambdaQueryWrapper<PrdWorkOrderResource> buildEntityQueryWrapper(PrdWorkOrderResource entity) {
        LambdaQueryWrapper<PrdWorkOrderResource> lqw = Wrappers.lambdaQuery();
        lqw.eq(entity.getWoId() != null, PrdWorkOrderResource::getWoId, entity.getWoId());
        lqw.eq(StringUtils.isNotBlank(entity.getResourceType()), PrdWorkOrderResource::getResourceType, entity.getResourceType());
        lqw.eq(entity.getResourceId() != null, PrdWorkOrderResource::getResourceId, entity.getResourceId());
        lqw.like(StringUtils.isNotBlank(entity.getResourceCode()), PrdWorkOrderResource::getResourceCode, entity.getResourceCode());
        lqw.like(StringUtils.isNotBlank(entity.getResourceName()), PrdWorkOrderResource::getResourceName, entity.getResourceName());
        lqw.eq(entity.getParentId() != null, PrdWorkOrderResource::getParentId, entity.getParentId());
        lqw.eq(StringUtils.isNotBlank(entity.getParentType()), PrdWorkOrderResource::getParentType, entity.getParentType());
        lqw.eq(StringUtils.isNotBlank(entity.getStatus()), PrdWorkOrderResource::getStatus, entity.getStatus());
        lqw.orderByAsc(PrdWorkOrderResource::getSeq);
        return lqw;
    }
}
