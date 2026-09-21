package com.nexus.erp.bd.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.core.utils.MapstructUtils;
import com.nexus.common.core.utils.StringUtils;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.erp.bd.domain.BdBomDetail;
import com.nexus.erp.bd.domain.BdMaterial;
import com.nexus.erp.bd.domain.bo.BdBomDetailBo;
import com.nexus.erp.bd.domain.vo.BdBomDetailVo;
import com.nexus.erp.bd.domain.vo.BdMaterialVo;
import com.nexus.erp.bd.mapper.BdBomDetailMapper;
import com.nexus.erp.bd.mapper.BdMaterialMapper;
import com.nexus.erp.bd.service.IBdBomDetailService;
import com.nexus.erp.prd.domain.PrdWorkOrder;
import com.nexus.erp.prd.mapper.PrdWorkOrderMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * BOM明细Service业务层处理
 *
 * @author nexus
 */
@RequiredArgsConstructor
@DS("erp")
@Service
public class BdBomDetailServiceImpl implements IBdBomDetailService {

    private final BdBomDetailMapper baseMapper;
    private final BdMaterialMapper materialMapper;
    private final PrdWorkOrderMapper workOrderMapper;

    /** 视为终态的工单状态（与 BdBomServiceImpl.WORK_ORDER_TERMINAL_STATUS 保持一致） */
    private static final List<String> WORK_ORDER_TERMINAL_STATUS = List.of("COMPLETED", "CLOSED", "CANCELLED");
    /**
     * 分页查询BOM明细列表
     *
     * @param bo 业务对象，包含筛选条件或待保存字段
     * @param pageQuery 分页参数，包含页码、每页大小及排序信息
     * @return 分页结果
     */

    @Override
    public TableDataInfo<BdBomDetailVo> selectPageList(BdBomDetailBo bo, PageQuery pageQuery) {
        Page<BdBomDetailVo> page = baseMapper.selectVoPage(pageQuery.build(), buildQueryWrapper(bo));
        page.setRecords(enrichMaterialName(page.getRecords()));
        return TableDataInfo.build(page);
    }
    /**
     * 查询BOM明细列表
     *
     * @param bo 业务对象，包含筛选条件或待保存字段
     * @return 结果列表
     */

    @Override
    public List<BdBomDetailVo> selectList(BdBomDetailBo bo) {
        return enrichMaterialName(baseMapper.selectVoList(buildQueryWrapper(bo)));
    }
    /**
     * 根据主键查询BOM明细详情
     *
     * @param id 主键ID
     * @return 结果
     */

    @Override
    public BdBomDetailVo selectById(Long id) {
        // ERP-BD-03: selectVoById 未命中返回 null，List.of(null) 会立刻抛 NPE（E11 同型残留）
        BdBomDetailVo vo = baseMapper.selectVoById(id);
        if (vo == null) {
            return null;
        }
        enrichMaterialName(new ArrayList<>(List.of(vo)));
        return vo;
    }
    /**
     * 新增BOM明细
     *
     * @param bo 业务对象，包含筛选条件或待保存字段
     * @return 操作结果/影响行数
     */

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int insertByBo(BdBomDetailBo bo) {
        assertChildMaterialsEnabled(List.of(bo));
        BdBomDetail entity = MapstructUtils.convert(bo, BdBomDetail.class);
        return baseMapper.insert(entity);
    }
    /**
     * 修改BOM明细
     *
     * @param bo 业务对象，包含筛选条件或待保存字段
     * @return 操作结果/影响行数
     */

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updateByBo(BdBomDetailBo bo) {
        assertChildMaterialsEnabled(List.of(bo));
        Long bomId = bo.getBomId();
        if (bomId == null && bo.getDetailId() != null) {
            BdBomDetail existing = baseMapper.selectById(bo.getDetailId());
            bomId = existing != null ? existing.getBomId() : null;
        }
        // ★ ERP-BD-08: 明细变更与整表删除等价地改变工单用料基准，必须有同样的工单守卫
        assertNoOpenWorkOrderReference(bomId == null ? List.of() : List.of(bomId));
        BdBomDetail entity = MapstructUtils.convert(bo, BdBomDetail.class);
        return baseMapper.updateById(entity);
    }
    /**
     * 批量删除BOM明细
     *
     * @param ids 主键ID数组（支持批量）
     * @return 操作结果/影响行数
     */

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteByIds(Long[] ids) {
        List<Long> idList = List.of(ids);
        List<BdBomDetail> details = baseMapper.selectList(
            Wrappers.<BdBomDetail>lambdaQuery().in(BdBomDetail::getDetailId, idList));
        // ★ ERP-BD-08: 明细删除同样需要工单守卫
        assertNoOpenWorkOrderReference(details.stream()
            .map(BdBomDetail::getBomId)
            .filter(Objects::nonNull)
            .distinct()
            .toList());
        return baseMapper.delete(Wrappers.<BdBomDetail>lambdaQuery().in(BdBomDetail::getDetailId, idList));
    }
    /**
     * 批量保存BOM明细（用于子表明细的批量新增/更新）
     *
     * @param list 批量数据列表
     * @return 操作结果/影响行数
     */

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int saveBatch(List<BdBomDetailBo> list) {
        if (list == null || list.isEmpty()) {
            return 0;
        }
        Long bomId = list.get(0).getBomId();
        // ★ ERP-BD-08: 「全删重插」与整表删除破坏力等价，必须补工单守卫（此前删除有守卫、改明细没有）
        assertNoOpenWorkOrderReference(bomId == null ? List.of() : List.of(bomId));
        assertChildMaterialsEnabled(list);
        baseMapper.delete(Wrappers.<BdBomDetail>lambdaQuery().eq(BdBomDetail::getBomId, bomId));
        List<BdBomDetail> entities = list.stream().map(bo -> {
            BdBomDetail entity = MapstructUtils.convert(bo, BdBomDetail.class);
            entity.setDetailId(null);
            return entity;
        }).toList();
        entities.forEach(baseMapper::insert);
        return entities.size();
    }

    /**
     * 校验 BOM 是否已被未完成工单引用（ERP-BD-08）。
     *
     * @param bomIds 待变更的 BOM ID 列表
     */
    private void assertNoOpenWorkOrderReference(List<Long> bomIds) {
        if (bomIds == null || bomIds.isEmpty()) {
            return;
        }
        Long referencingCount = workOrderMapper.selectCount(
            Wrappers.lambdaQuery(PrdWorkOrder.class)
                .in(PrdWorkOrder::getBomId, bomIds)
                .notIn(PrdWorkOrder::getWoStatus, WORK_ORDER_TERMINAL_STATUS));
        if (referencingCount != null && referencingCount > 0) {
            throw new ServiceException("该BOM已被" + referencingCount
                + "张未完成的生产工单引用，无法修改明细；请先关闭相关工单或复制新版本BOM");
        }
    }

    /**
     * 校验子项物料存在且为启用状态（ERP-BD-10）：停用物料不得进入 BOM/成本卷积。
     *
     * @param list 待保存的明细行
     */
    private void assertChildMaterialsEnabled(List<BdBomDetailBo> list) {
        if (list == null || list.isEmpty()) {
            return;
        }
        List<Long> materialIds = list.stream()
            .map(BdBomDetailBo::getChildMaterialId)
            .filter(Objects::nonNull)
            .distinct()
            .toList();
        if (materialIds.isEmpty()) {
            return;
        }
        Set<Long> enabledIds = materialMapper.selectList(Wrappers.<BdMaterial>lambdaQuery()
                .in(BdMaterial::getMaterialId, materialIds)
                .eq(BdMaterial::getStatus, 1L))
            .stream()
            .map(BdMaterial::getMaterialId)
            .collect(Collectors.toSet());
        for (BdBomDetailBo bo : list) {
            if (bo.getChildMaterialId() != null && !enabledIds.contains(bo.getChildMaterialId())) {
                throw new ServiceException("子项物料不存在或已停用，无法加入BOM: materialId=" + bo.getChildMaterialId());
            }
        }
    }

    private LambdaQueryWrapper<BdBomDetail> buildQueryWrapper(BdBomDetailBo bo) {
        LambdaQueryWrapper<BdBomDetail> lqw = Wrappers.lambdaQuery();
        lqw.eq(bo.getBomId() != null, BdBomDetail::getBomId, bo.getBomId());
        lqw.eq(bo.getChildMaterialId() != null, BdBomDetail::getChildMaterialId, bo.getChildMaterialId());
        lqw.eq(bo.getOperationStep() != null, BdBomDetail::getOperationStep, bo.getOperationStep());
        lqw.eq(bo.getStatus() != null, BdBomDetail::getStatus, bo.getStatus());
        // ERP-BD-01: supplyType 打通后支持按供应方筛选
        lqw.eq(StringUtils.isNotBlank(bo.getSupplyType()), BdBomDetail::getSupplyType, bo.getSupplyType());
        lqw.like(StringUtils.isNotBlank(bo.getRemark()), BdBomDetail::getRemark, bo.getRemark());
        return lqw;
    }

    private List<BdBomDetailVo> enrichMaterialName(List<BdBomDetailVo> list) {
        if (list == null || list.isEmpty()) {
            return list;
        }
        List<Long> materialIds = list.stream()
            .map(BdBomDetailVo::getChildMaterialId)
            .filter(Objects::nonNull)
            .distinct()
            .toList();
        if (materialIds.isEmpty()) {
            return list;
        }
        Map<Long, BdMaterialVo> materialMap = materialMapper.selectVoList(Wrappers.<BdMaterial>lambdaQuery()
                .in(BdMaterial::getMaterialId, materialIds))
            .stream()
            .collect(Collectors.toMap(BdMaterialVo::getMaterialId, m -> m, (a, b) -> a, HashMap::new));
        list.forEach(item -> {
            BdMaterialVo material = materialMap.get(item.getChildMaterialId());
            if (material != null) {
                item.setChildMaterialName(material.getMaterialName());
                item.setChildMaterialCode(material.getMaterialCode());
                item.setChildMaterialSpec(material.getMaterialSpec());
                item.setBaseUomId(material.getBaseUomId());
                item.setBaseUomName(material.getBaseUomName());
            }
        });
        return list;
    }
}
