package com.nexus.erp.bd.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.lang.tree.Tree;
import cn.hutool.core.util.ObjectUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import com.nexus.common.core.constant.CacheNames;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.core.utils.MapstructUtils;
import com.nexus.common.core.utils.StringUtils;
import com.nexus.common.core.utils.TreeBuildUtils;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.common.mybatis.helper.DataBaseHelper;
import com.nexus.common.mybatis.utils.IdGeneratorUtil;
import com.nexus.erp.bd.domain.BdMaterialCategory;
import com.nexus.erp.bd.domain.bo.BdMaterialCategoryBo;
import com.nexus.erp.bd.domain.vo.BdMaterialCategoryVo;
import com.nexus.erp.bd.mapper.BdMaterialCategoryMapper;
import com.nexus.erp.bd.service.IBdMaterialCategoryService;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.dynamic.datasource.annotation.DS;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * 物料产品分类Service业务层处理
 *
 * @author wanghuan
 */
@RequiredArgsConstructor
@DS("erp")
@Service
public class BdMaterialCategoryServiceImpl implements IBdMaterialCategoryService {

    private final BdMaterialCategoryMapper baseMapper;

    @Override
    public TableDataInfo<BdMaterialCategoryVo> selectPageList(BdMaterialCategoryBo bo, PageQuery pageQuery) {
        LambdaQueryWrapper<BdMaterialCategory> lqw = buildQueryWrapper(bo);
        Page<BdMaterialCategoryVo> page = baseMapper.selectVoPage(pageQuery.build(), lqw);
        return TableDataInfo.build(page);
    }

    /**
     * 仅在无筛选条件时缓存全量分类列表（读多写少，租户自动隔离）。
     */
    @Override
    @Cacheable(cacheNames = CacheNames.BD_CATEGORY_TREE, key = "'list_all'",
        condition = "#bo.categoryCode == null and #bo.categoryName == null and #bo.status == null")
    public List<BdMaterialCategoryVo> selectList(BdMaterialCategoryBo bo) {
        return baseMapper.selectVoList(buildQueryWrapper(bo));
    }

    @Override
    @Cacheable(cacheNames = CacheNames.BD_CATEGORY_TREE, key = "'treeselect'")
    public List<Tree<Long>> selectCategoryTreeList() {
        List<BdMaterialCategoryVo> list = baseMapper.selectVoList(buildQueryWrapper(new BdMaterialCategoryBo()));
        return buildCategoryTreeSelect(list);
    }

    @Override
    public List<Tree<Long>> buildCategoryTreeSelect(List<BdMaterialCategoryVo> categories) {
        if (CollUtil.isEmpty(categories)) {
            return CollUtil.newArrayList();
        }
        return TreeBuildUtils.buildMultiRoot(
            categories,
            BdMaterialCategoryVo::getCategoryId,
            BdMaterialCategoryVo::getParentId,
            (node, treeNode) -> treeNode
                .setId(node.getCategoryId())
                .setParentId(node.getParentId())
                .setName(node.getCategoryName())
                .setWeight(node.getSortOrder()));
    }

    @Override
    public List<BdMaterialCategoryVo> selectListExcludeChild(Long[] excludeIds) {
        LambdaQueryWrapper<BdMaterialCategory> lqw = buildQueryWrapper(new BdMaterialCategoryBo());
        if (excludeIds != null && excludeIds.length > 0) {
            // path_ids 含自身，故 find_in_set=0 同时排除自身及其所有子孙
            for (Long id : excludeIds) {
                lqw.apply("(find_in_set({0}, path_ids) = 0 OR path_ids IS NULL)", id);
            }
        }
        return baseMapper.selectVoList(lqw);
    }

    @Override
    public BdMaterialCategoryVo selectById(Long id) {
        return baseMapper.selectVoById(id);
    }

    @Override
    @CacheEvict(cacheNames = CacheNames.BD_CATEGORY_TREE, allEntries = true)
    @Transactional(rollbackFor = Exception.class)
    public int insertByBo(BdMaterialCategoryBo bo) {
        BdMaterialCategory entity = MapstructUtils.convert(bo, BdMaterialCategory.class);
        // 预生成雪花ID：path_ids 包含自身，须在写入前拿到ID（否则 pathIds 会变成 "...,null"）
        entity.setCategoryId(IdGeneratorUtil.nextLongId());
        validateCategory(bo, entity);
        fillPathAndLevel(entity);
        return baseMapper.insert(entity);
    }

    @Override
    @CacheEvict(cacheNames = CacheNames.BD_CATEGORY_TREE, allEntries = true)
    @Transactional(rollbackFor = Exception.class)
    public int updateByBo(BdMaterialCategoryBo bo) {
        BdMaterialCategory entity = MapstructUtils.convert(bo, BdMaterialCategory.class);
        BdMaterialCategory old = baseMapper.selectById(entity.getCategoryId());
        if (old == null) {
            throw new ServiceException("分类不存在");
        }
        validateCategory(bo, entity);
        fillPathAndLevel(entity);
        int rows = baseMapper.updateById(entity);
        // 父级变化或重命名都需级联刷新子孙的 path_ids / path_names / level_num
        if (!Objects.equals(old.getParentId(), entity.getParentId())
            || !Objects.equals(old.getCategoryName(), entity.getCategoryName())) {
            updateCategoryChildren(old, entity);
        }
        return rows;
    }

    @Override
    @CacheEvict(cacheNames = CacheNames.BD_CATEGORY_TREE, allEntries = true)
    @Transactional(rollbackFor = Exception.class)
    public int deleteByIds(Long[] ids) {
        return baseMapper.deleteBatchIds(Arrays.asList(ids));
    }

    @Override
    public List<BdMaterialCategoryVo> selectAllChildrenIds(Long[] categoryIds) {
        if (categoryIds == null || categoryIds.length == 0) {
            return new ArrayList<>();
        }
        LambdaQueryWrapper<BdMaterialCategory> lqw = Wrappers.lambdaQuery();
        lqw.and(w -> {
            for (int i = 0; i < categoryIds.length; i++) {
                final Long id = categoryIds[i];
                if (i > 0) {
                    w.or();
                }
                w.nested(n -> n.apply(DataBaseHelper.findInSet(id, "path_ids"))
                    .or()
                    .eq(BdMaterialCategory::getCategoryId, id));
            }
        });
        return baseMapper.selectVoList(lqw);
    }

    @Override
    public boolean checkCategoryCodeUnique(BdMaterialCategoryBo bo) {
        boolean exist = baseMapper.exists(new LambdaQueryWrapper<BdMaterialCategory>()
            .eq(BdMaterialCategory::getCategoryCode, bo.getCategoryCode())
            .ne(ObjectUtil.isNotNull(bo.getCategoryId()), BdMaterialCategory::getCategoryId, bo.getCategoryId()));
        return !exist;
    }

    @Override
    public boolean hasChildByCategoryIds(Long[] categoryIds) {
        if (categoryIds == null || categoryIds.length == 0) {
            return false;
        }
        return baseMapper.exists(new LambdaQueryWrapper<BdMaterialCategory>()
            .in(BdMaterialCategory::getParentId, Arrays.asList(categoryIds)));
    }

    /**
     * 校验：编码唯一 / 不以自身为父 / 新父级不能是自身子孙（循环引用）
     */
    private void validateCategory(BdMaterialCategoryBo bo, BdMaterialCategory entity) {
        if (!checkCategoryCodeUnique(bo)) {
            throw new ServiceException("分类编码'" + bo.getCategoryCode() + "'已存在");
        }
        Long cid = entity.getCategoryId();
        Long pid = entity.getParentId();
        if (cid != null && cid.equals(pid)) {
            throw new ServiceException("上级分类不能是自己");
        }
        if (cid != null && pid != null && pid > 0L) {
            boolean cycle = baseMapper.exists(new LambdaQueryWrapper<BdMaterialCategory>()
                .eq(BdMaterialCategory::getCategoryId, pid)
                .apply(DataBaseHelper.findInSet(cid, "path_ids")));
            if (cycle) {
                throw new ServiceException("上级分类不能是其子分类，存在循环引用");
            }
        }
    }

    /**
     * 服务端权威计算 path_ids / path_names / level_num（覆盖客户端传入值）
     */
    private void fillPathAndLevel(BdMaterialCategory entity) {
        if (entity.getParentId() == null || entity.getParentId() <= 0L) {
            entity.setLevelNum(1L);
            entity.setPathIds(String.valueOf(entity.getCategoryId()));
            entity.setPathNames(entity.getCategoryName());
            return;
        }
        BdMaterialCategory parent = baseMapper.selectById(entity.getParentId());
        if (parent == null) {
            throw new ServiceException("上级分类不存在");
        }
        entity.setLevelNum(parent.getLevelNum() + 1);
        entity.setPathIds(parent.getPathIds() + StringUtils.SEPARATOR + entity.getCategoryId());
        entity.setPathNames(parent.getPathNames() + StringUtils.SEPARATOR + entity.getCategoryName());
    }

    /**
     * 移动或重命名节点后，按前缀替换刷新所有子孙的物化路径。
     * <p>
     * path_ids / path_names 以本节点路径为前缀，substring(oldLen) 截取后缀再拼接新前缀；
     * level_num 按层级差值平移。比 SQL REPLACE() 更精确（不会误替换路径中重复出现的子串）。
     */
    private void updateCategoryChildren(BdMaterialCategory oldX, BdMaterialCategory newX) {
        List<BdMaterialCategory> children = baseMapper.selectList(
            new LambdaQueryWrapper<BdMaterialCategory>()
                .apply(DataBaseHelper.findInSet(newX.getCategoryId(), "path_ids"))
                .ne(BdMaterialCategory::getCategoryId, newX.getCategoryId()));
        if (CollUtil.isEmpty(children)) {
            return;
        }
        int idsLen = oldX.getPathIds().length();
        int namesLen = oldX.getPathNames().length();
        long levelDelta = newX.getLevelNum() - oldX.getLevelNum();
        List<BdMaterialCategory> batch = new ArrayList<>();
        for (BdMaterialCategory d : children) {
            BdMaterialCategory u = new BdMaterialCategory();
            u.setCategoryId(d.getCategoryId());
            u.setPathIds(newX.getPathIds() + d.getPathIds().substring(idsLen));
            u.setPathNames(newX.getPathNames() + d.getPathNames().substring(namesLen));
            u.setLevelNum(d.getLevelNum() + levelDelta);
            batch.add(u);
        }
        baseMapper.updateBatchById(batch);
    }

    private LambdaQueryWrapper<BdMaterialCategory> buildQueryWrapper(BdMaterialCategoryBo bo) {
        LambdaQueryWrapper<BdMaterialCategory> lqw = Wrappers.lambdaQuery();
        lqw.eq(StringUtils.isNotBlank(bo.getCategoryCode()), BdMaterialCategory::getCategoryCode, bo.getCategoryCode());
        lqw.like(StringUtils.isNotBlank(bo.getCategoryName()), BdMaterialCategory::getCategoryName, bo.getCategoryName());
        lqw.eq(bo.getStatus() != null, BdMaterialCategory::getStatus, bo.getStatus());
        lqw.orderByAsc(BdMaterialCategory::getPathIds);
        lqw.orderByAsc(BdMaterialCategory::getSortOrder);
        return lqw;
    }
}
