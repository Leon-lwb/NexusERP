package com.nexus.erp.bd.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ObjectUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import com.nexus.common.core.constant.CacheNames;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.core.utils.MapstructUtils;
import com.nexus.common.core.utils.StringUtils;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.common.mybatis.helper.DataBaseHelper;
import com.nexus.common.mybatis.utils.IdGeneratorUtil;
import com.nexus.erp.bd.domain.BdMaterialCategory;
import com.nexus.erp.bd.domain.bo.BdMaterialCategoryBo;
import com.nexus.erp.bd.domain.vo.BdMaterialCategoryVo;
import com.nexus.erp.bd.mapper.BdMaterialCategoryMapper;
import com.nexus.erp.bd.service.IBdMaterialCategoryService;
import com.nexus.erp.bd.service.IBdMaterialService;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.dynamic.datasource.annotation.DS;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
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
    private final IBdMaterialService bdMaterialService;
    /**
     * 分页查询物料类别列表
     *
     * @param bo 业务对象，包含筛选条件或待保存字段
     * @param pageQuery 分页参数，包含页码、每页大小及排序信息
     * @return 分页结果
     */

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
    /**
     * 查询物料类别列表
     *
     * @param bo 业务对象，包含筛选条件或待保存字段
     * @return 结果列表
     */
    public List<BdMaterialCategoryVo> selectList(BdMaterialCategoryBo bo) {
        return baseMapper.selectVoList(buildQueryWrapper(bo));
    }
    /**
     * 查询排除指定节点及其子节点后的物料类别列表
     *
     * @param excludeIds 需排除的类别ID数组
     * @return 结果列表
     */

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
    /**
     * 根据主键查询物料类别详情
     *
     * @param id 主键ID
     * @return 结果
     */

    @Override
    public BdMaterialCategoryVo selectById(Long id) {
        return baseMapper.selectVoById(id);
    }
    /**
     * 新增物料类别
     *
     * @param bo 业务对象，包含筛选条件或待保存字段
     * @return 操作结果/影响行数
     */

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
    /**
     * 修改物料类别
     *
     * @param bo 业务对象，包含筛选条件或待保存字段
     * @return 操作结果/影响行数
     */

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
    /**
     * 批量删除物料类别
     *
     * @param ids 主键ID数组（支持批量）
     * @return 操作结果/影响行数
     */

    @Override
    @CacheEvict(cacheNames = CacheNames.BD_CATEGORY_TREE, allEntries = true)
    @Transactional(rollbackFor = Exception.class)
    public int deleteByIds(Long[] ids) {
        return baseMapper.deleteBatchIds(Arrays.asList(ids));
    }
    /**
     * 查询指定类别的所有子孙类别ID
     *
     * @param categoryIds 类别ID数组
     * @return 结果列表
     */

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
    /**
     * 校验物料类别编码是否唯一
     *
     * @param bo 业务对象，包含筛选条件或待保存字段
     * @return 操作结果/影响行数
     */

    @Override
    public boolean checkCategoryCodeUnique(BdMaterialCategoryBo bo) {
        boolean exist = baseMapper.exists(new LambdaQueryWrapper<BdMaterialCategory>()
            .eq(BdMaterialCategory::getCategoryCode, bo.getCategoryCode())
            .ne(ObjectUtil.isNotNull(bo.getCategoryId()), BdMaterialCategory::getCategoryId, bo.getCategoryId()));
        return !exist;
    }
    /**
     * 判断指定类别是否包含子节点
     *
     * @param categoryIds 类别ID数组
     * @return 操作结果/影响行数
     */

    @Override
    public boolean hasChildByCategoryIds(Long[] categoryIds) {
        if (categoryIds == null || categoryIds.length == 0) {
            return false;
        }
        return baseMapper.exists(new LambdaQueryWrapper<BdMaterialCategory>()
            .in(BdMaterialCategory::getParentId, Arrays.asList(categoryIds)));
    }

    /**
     * 实时填充直系物料数量。物料增删不触发分类缓存失效，故此处每次实时查询，
     * 不进分类缓存，避免物料数陈旧。
     */
    @Override
    public void fillMaterialCount(List<BdMaterialCategoryVo> list) {
        if (CollUtil.isEmpty(list)) {
            return;
        }
        Map<Long, Long> countMap = bdMaterialService.countMaterialByCategory();
        for (BdMaterialCategoryVo vo : list) {
            vo.setMaterialCount(countMap.getOrDefault(vo.getCategoryId(), 0L));
        }
    }

    /**
     * 轻量状态变更：仅更新 status，不触发 path 级联。
     * <p>停用(1->0)时级联停用所有子孙；启用(0->1)仅自身（不级联启用祖先链，避免误启用）。
     */
    @Override
    @CacheEvict(cacheNames = CacheNames.BD_CATEGORY_TREE, allEntries = true)
    @Transactional(rollbackFor = Exception.class)
    public int updateStatus(Long categoryId, Long status) {
        if (baseMapper.selectById(categoryId) == null) {
            throw new ServiceException("分类不存在");
        }
        BdMaterialCategory update = new BdMaterialCategory();
        update.setCategoryId(categoryId);
        update.setStatus(status);
        int rows = baseMapper.updateById(update);
        // 停用时级联停用所有子孙（path_ids 含自身，find_in_set 命中自身+子孙，排除自身）
        if (Long.valueOf(0L).equals(status)) {
            List<BdMaterialCategory> descendants = baseMapper.selectList(
                new LambdaQueryWrapper<BdMaterialCategory>()
                    .apply(DataBaseHelper.findInSet(categoryId, "path_ids"))
                    .ne(BdMaterialCategory::getCategoryId, categoryId));
            if (CollUtil.isNotEmpty(descendants)) {
                List<Long> ids = new ArrayList<>();
                for (BdMaterialCategory d : descendants) {
                    ids.add(d.getCategoryId());
                }
                BdMaterialCategory cascade = new BdMaterialCategory();
                cascade.setStatus(0L);
                baseMapper.update(cascade, new LambdaQueryWrapper<BdMaterialCategory>()
                    .in(BdMaterialCategory::getCategoryId, ids));
            }
        }
        return rows;
    }

    /**
     * 移动分类到新父级（拖拽改父级）。复用循环校验 + path 级联刷新，
     * sortOrder 自动追加到新父级现有子级末尾。
     */
    @Override
    @CacheEvict(cacheNames = CacheNames.BD_CATEGORY_TREE, allEntries = true)
    @Transactional(rollbackFor = Exception.class)
    public int moveCategory(Long categoryId, Long newParentId) {
        BdMaterialCategory old = baseMapper.selectById(categoryId);
        if (old == null) {
            throw new ServiceException("分类不存在");
        }
        long parentId = (newParentId == null || newParentId <= 0L) ? 0L : newParentId;
        BdMaterialCategory entity = new BdMaterialCategory();
        entity.setCategoryId(categoryId);
        entity.setParentId(parentId);
        entity.setCategoryCode(old.getCategoryCode());
        entity.setCategoryName(old.getCategoryName());
        // sortOrder 追加到新父级现有子级末尾
        entity.setSortOrder(maxSiblingSortOrder(parentId, categoryId) + 1);
        // 循环引用 / 自身校验（编码不变，唯一性校验为冗余安全网）
        BdMaterialCategoryBo bo = new BdMaterialCategoryBo();
        bo.setCategoryId(categoryId);
        bo.setCategoryCode(old.getCategoryCode());
        bo.setParentId(parentId);
        validateCategory(bo, entity);
        fillPathAndLevel(entity);
        int rows = baseMapper.updateById(entity);
        if (!Objects.equals(old.getParentId(), entity.getParentId())) {
            updateCategoryChildren(old, entity);
        }
        return rows;
    }

    /**
     * 查询某父级下现有子级的最大 sortOrder（排除指定分类）。
     */
    private long maxSiblingSortOrder(long parentId, Long excludeId) {
        List<BdMaterialCategory> siblings = baseMapper.selectList(
            new LambdaQueryWrapper<BdMaterialCategory>()
                .eq(BdMaterialCategory::getParentId, parentId)
                .ne(BdMaterialCategory::getCategoryId, excludeId)
                .select(BdMaterialCategory::getSortOrder));
        long maxSort = 0L;
        for (BdMaterialCategory s : siblings) {
            if (s.getSortOrder() != null && s.getSortOrder() > maxSort) {
                maxSort = s.getSortOrder();
            }
        }
        return maxSort;
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
