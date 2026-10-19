package com.nexus.erp.bd.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ArrayUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import com.nexus.common.core.utils.MapstructUtils;
import com.nexus.common.core.utils.StringUtils;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.erp.bd.domain.BdMaterial;
import com.nexus.erp.bd.domain.BdMaterialCategory;
import com.nexus.erp.bd.domain.bo.BdMaterialBo;
import com.nexus.erp.bd.domain.vo.BdMaterialVo;
import com.nexus.erp.bd.mapper.BdMaterialCategoryMapper;
import com.nexus.erp.bd.mapper.BdMaterialMapper;
import com.nexus.erp.bd.mapper.BdUnitOfMeasureMapper;
import com.nexus.erp.bd.service.IBdMaterialService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.dynamic.datasource.annotation.DS;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 物料产品主Service业务层处理
 *
 * @author wanghuan
 */
@RequiredArgsConstructor
@DS("erp")
@Service
public class BdMaterialServiceImpl implements IBdMaterialService {

    private final BdMaterialMapper baseMapper;
    private final BdUnitOfMeasureMapper uomMapper;
    private final BdMaterialCategoryMapper categoryMapper;
    private final BdServiceHelper bd;
    private final com.nexus.erp.wm.trace.MaterialTrackingPolicyGuard trackingGuard;


    /**
     * 查询物料产品主分页列表
     *
     * @param bo 查询参数
     * @param pageQuery 分页参数
     * @return 物料产品主分页列表
     */
    @Override
    public TableDataInfo<BdMaterialVo> selectPageList(BdMaterialBo bo, PageQuery pageQuery) {
        LambdaQueryWrapper<BdMaterial> lqw = buildQueryWrapper(bo);
        Page<BdMaterialVo> page = baseMapper.selectVoPage(pageQuery.build(), lqw);
        fillRefNames(page.getRecords());
        return TableDataInfo.build(page);
    }

    /**
     * 查询物料产品主列表
     *
     * @param bo 查询参数
     * @return 物料产品主列表
     */
    @Override
    public List<BdMaterialVo> selectList(BdMaterialBo bo) {
        List<BdMaterialVo> list = baseMapper.selectVoList(buildQueryWrapper(bo));
        fillRefNames(list);
        return list;
    }

    /**
     * 根据ID查询物料产品主
     *
     * @param id 物料产品主ID
     * @return 物料产品主信息
     */
    @Override
    public BdMaterialVo selectById(Long id) {
        BdMaterialVo vo = baseMapper.selectVoById(id);
        if (vo != null) {
            fillRefNames(List.of(vo));
        }
        return vo;
    }

    /**
     * 新增物料产品主
     *
     * @param bo 物料产品主信息
     * @return 影响行数
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int insertByBo(BdMaterialBo bo) {
        BdMaterial entity = MapstructUtils.convert(bo, BdMaterial.class);
        entity.setAllowMixedLot(bo.getAllowMixedLot()==null?1:bo.getAllowMixedLot());
        entity.setTrackingVersion(1L);
        entity.setTrackingStage("AUDIT");
        if (StringUtils.isBlank(entity.getMaterialCode())) {
            entity.setMaterialCode(bd.autoCodeUtil.genSerialCode("MATERIAL_CODE", null));
        }
        return baseMapper.insert(entity);
    }

    /**
     * 修改物料产品主
     *
     * @param bo 物料产品主信息
     * @return 影响行数
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updateByBo(BdMaterialBo bo) {
        String tenant=com.nexus.common.tenant.helper.TenantHelper.getTenantId();
        BdMaterial old=baseMapper.lockTracking(tenant,bo.getMaterialId());
        if(old==null) throw new com.nexus.common.core.exception.ServiceException("物料不存在或不在当前租户范围");
        BdMaterial entity = MapstructUtils.convert(bo, BdMaterial.class);
        trackingGuard.prepare(old,entity,bo.getTrackingVersion());
        return baseMapper.update(entity,Wrappers.<BdMaterial>lambdaUpdate().eq(BdMaterial::getMaterialId,old.getMaterialId())
            .eq(BdMaterial::getTrackingVersion,old.getTrackingVersion()==null?1L:old.getTrackingVersion()));
    }

    /**
     * 批量删除物料产品主
     *
     * @param ids 物料产品主ID数组
     * @return 影响行数
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteByIds(Long[] ids) {
        return baseMapper.deleteBatchIds(List.of(ids));
    }

    /**
     * 根据分类ID列表查询物料产品主
     *
     * @param categoryIds 分类ID列表
     * @return 物料产品主列表
     */
    @Override
    public List<BdMaterialVo> selectByCategoryIds(List<Long> categoryIds) {
        if (categoryIds == null || categoryIds.isEmpty()) {
            return new ArrayList<>();
        }
        // ★ ERP-BD-10: 这是「选择器」语义（分类树点选物料），必须只返回启用物料；
        // 物料列表页仍可查停用物料（由列表查询条件控制）。
        List<BdMaterialVo> list = baseMapper.selectVoList(
            Wrappers.lambdaQuery(BdMaterial.class)
                .in(BdMaterial::getCategoryId, categoryIds)
                .eq(BdMaterial::getStatus, 1L)
        );
        fillRefNames(list);
        return list;
    }

    /**
     * 判断指定分类下是否存在物料
     */
    @Override
    public boolean hasMaterialByCategoryIds(Collection<Long> categoryIds) {
        if (categoryIds == null || categoryIds.isEmpty()) {
            return false;
        }
        return baseMapper.exists(new LambdaQueryWrapper<BdMaterial>()
            .in(BdMaterial::getCategoryId, categoryIds));
    }

    /**
     * 统计各分类下的直系物料数量（按 category_id 分组）。
     * <p>
     * 仅 SELECT category_id 列（租户/逻辑删除条件由插件自动注入），Java 端分组，
     * 避免自定义 SQL 的租户拦截不确定性。
     */
    @Override
    public Map<Long, Long> countMaterialByCategory() {
        List<BdMaterial> list = baseMapper.selectList(
            Wrappers.lambdaQuery(BdMaterial.class).select(BdMaterial::getCategoryId)
        );
        Map<Long, Long> map = new HashMap<>();
        if (CollUtil.isEmpty(list)) {
            return map;
        }
        for (BdMaterial m : list) {
            if (m.getCategoryId() != null) {
                map.merge(m.getCategoryId(), 1L, Long::sum);
            }
        }
        return map;
    }

    /**
     * 构建查询条件
     *
     * @param bo 查询参数
     * @return 查询条件包装器
     */
    private LambdaQueryWrapper<BdMaterial> buildQueryWrapper(BdMaterialBo bo) {
        LambdaQueryWrapper<BdMaterial> lqw = Wrappers.<BdMaterial>lambdaQuery()
            .eq(StringUtils.isNotBlank(bo.getMaterialCode()), BdMaterial::getMaterialCode, bo.getMaterialCode())
            .like(StringUtils.isNotBlank(bo.getMaterialName()), BdMaterial::getMaterialName, bo.getMaterialName())
            .eq(StringUtils.isNotBlank(bo.getMaterialSpec()), BdMaterial::getMaterialSpec, bo.getMaterialSpec())
            .eq(bo.getMaterialType() != null, BdMaterial::getMaterialType, bo.getMaterialType())
            .eq(bo.getSourceType() != null, BdMaterial::getSourceType, bo.getSourceType())
            .eq(bo.getCategoryId() != null, BdMaterial::getCategoryId, bo.getCategoryId())
            .eq(bo.getStatus() != null, BdMaterial::getStatus, bo.getStatus())
            .eq(StringUtils.isNotBlank(bo.getQualityStatus()), BdMaterial::getQualityStatus, bo.getQualityStatus());
        // 按分类集合过滤（分类树点击时传入选中分类及其全部子孙ID）
        if (ArrayUtil.isNotEmpty(bo.getCategoryIds())) {
            lqw.in(BdMaterial::getCategoryId, Arrays.asList(bo.getCategoryIds()));
        }
        // 按仓库/库区/库位过滤：仅返回在 wm_material_stock 中存在库存记录的物料
        // (warehouseId/areaId/locationId 均为 Long 类型，拼接安全；wm_material_stock 同处 erp 数据源)
        if (bo.getWarehouseId() != null || bo.getAreaId() != null || bo.getLocationId() != null) {
            StringBuilder sub = new StringBuilder(
                "SELECT material_id FROM wm_material_stock WHERE del_flag = '0'");
            if (bo.getWarehouseId() != null) {
                sub.append(" AND warehouse_id = ").append(bo.getWarehouseId());
            }
            if (bo.getAreaId() != null) {
                sub.append(" AND area_id = ").append(bo.getAreaId());
            }
            if (bo.getLocationId() != null) {
                sub.append(" AND location_id = ").append(bo.getLocationId());
            }
            lqw.inSql(BdMaterial::getMaterialId, sub.toString());
        }
        return lqw;
    }

    /**
     * 批量回填展示用的关联名称（基本计量单位名称、物料分类名称）。
     * <p>
     * bd_material 只存外键 ID，不含名称列；此前仅回填了计量单位名称，导致
     * {@code categoryName} 恒为空 —— 前端「物料分类」列、Excel 导出的「分类名称」列
     * 都是空的，和「物料分类」菜单里的树看起来对不上。此处统一补齐。
     *
     * @param list 物料VO列表
     */
    private void fillRefNames(List<BdMaterialVo> list) {
        fillBaseUomName(list);
        fillCategoryName(list);
    }

    /**
     * 批量填充物料分类名称
     *
     * @param list 物料VO列表
     */
    private void fillCategoryName(List<BdMaterialVo> list) {
        if (CollUtil.isEmpty(list)) {
            return;
        }
        List<Long> categoryIds = list.stream()
            .map(BdMaterialVo::getCategoryId)
            .filter(Objects::nonNull)
            .distinct()
            .collect(Collectors.toList());
        if (CollUtil.isEmpty(categoryIds)) {
            return;
        }
        Map<Long, String> categoryNameMap = categoryMapper.selectBatchIds(categoryIds).stream()
            .filter(c -> c.getCategoryName() != null)
            .collect(Collectors.toMap(
                BdMaterialCategory::getCategoryId,
                BdMaterialCategory::getCategoryName,
                (v1, v2) -> v1
            ));
        for (BdMaterialVo vo : list) {
            if (vo.getCategoryId() != null) {
                vo.setCategoryName(categoryNameMap.get(vo.getCategoryId()));
            }
        }
    }

    /**
     * 批量填充基本计量单位名称
     *
     * @param list 物料VO列表
     */
    private void fillBaseUomName(List<BdMaterialVo> list) {
        if (CollUtil.isEmpty(list)) {
            return;
        }
        List<Long> uomIds = list.stream()
            .map(BdMaterialVo::getBaseUomId)
            .filter(Objects::nonNull)
            .distinct()
            .collect(Collectors.toList());
        if (CollUtil.isEmpty(uomIds)) {
            return;
        }
        Map<Long, String> uomNameMap = uomMapper.selectBatchIds(uomIds).stream()
            .collect(Collectors.toMap(
                com.nexus.erp.bd.domain.BdUnitOfMeasure::getUomId,
                com.nexus.erp.bd.domain.BdUnitOfMeasure::getUomName,
                (v1, v2) -> v1
            ));
        for (BdMaterialVo vo : list) {
            if (vo.getBaseUomId() != null) {
                vo.setBaseUomName(uomNameMap.get(vo.getBaseUomId()));
            }
        }
    }
}
