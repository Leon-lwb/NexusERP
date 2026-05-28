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
 * 生产工单资源分配Service业务层处理
 *
 * @author ruoyi
 */
@RequiredArgsConstructor
@DS("erp")
@Service
public class PrdWorkOrderResourceServiceImpl implements IPrdWorkOrderResourceService {

    private final PrdWorkOrderResourceMapper baseMapper;

    @Override
    public TableDataInfo<PrdWorkOrderResourceVo> selectPageList(PrdWorkOrderResourceBo bo, PageQuery pageQuery) {
        LambdaQueryWrapper<PrdWorkOrderResource> lqw = buildQueryWrapper(bo);
        Page<PrdWorkOrderResourceVo> page = baseMapper.selectVoPage(pageQuery.build(), lqw);
        return TableDataInfo.build(page);
    }

    @Override
    public List<PrdWorkOrderResourceVo> selectList(PrdWorkOrderResourceBo bo) {
        return baseMapper.selectVoList(buildQueryWrapper(bo));
    }

    @Override
    public PrdWorkOrderResourceVo selectById(Long id) {
        return baseMapper.selectVoById(id);
    }

    @Override
    public List<PrdWorkOrderResource> selectPrdWorkOrderResourceList(PrdWorkOrderResource prdWorkOrderResource) {
        LambdaQueryWrapper<PrdWorkOrderResource> lqw = buildEntityQueryWrapper(prdWorkOrderResource);
        return baseMapper.selectList(lqw);
    }

    @Override
    public List<PrdWorkOrderResource> selectByWoId(Long woId) {
        LambdaQueryWrapper<PrdWorkOrderResource> lqw = Wrappers.lambdaQuery();
        lqw.eq(PrdWorkOrderResource::getWoId, woId);
        lqw.orderByAsc(PrdWorkOrderResource::getSeq);
        return baseMapper.selectList(lqw);
    }

    @Override
    public List<PrdWorkOrderResource> selectByWoIdAndType(Long woId, String resourceType) {
        LambdaQueryWrapper<PrdWorkOrderResource> lqw = Wrappers.lambdaQuery();
        lqw.eq(PrdWorkOrderResource::getWoId, woId);
        lqw.eq(PrdWorkOrderResource::getResourceType, resourceType);
        lqw.orderByAsc(PrdWorkOrderResource::getSeq);
        return baseMapper.selectList(lqw);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int insertByBo(PrdWorkOrderResourceBo bo) {
        PrdWorkOrderResource entity = MapstructUtils.convert(bo, PrdWorkOrderResource.class);
        return baseMapper.insert(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updateByBo(PrdWorkOrderResourceBo bo) {
        PrdWorkOrderResource entity = MapstructUtils.convert(bo, PrdWorkOrderResource.class);
        return baseMapper.updateById(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteByIds(Long[] ids) {
        return baseMapper.deleteBatchIds(List.of(ids));
    }

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
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteByWoId(Long woId) {
        LambdaQueryWrapper<PrdWorkOrderResource> lqw = Wrappers.lambdaQuery();
        lqw.eq(PrdWorkOrderResource::getWoId, woId);
        return baseMapper.delete(lqw) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
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
