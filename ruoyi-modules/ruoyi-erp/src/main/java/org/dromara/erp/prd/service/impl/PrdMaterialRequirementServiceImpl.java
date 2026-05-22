package org.dromara.erp.prd.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.core.utils.MapstructUtils;
import org.dromara.common.core.utils.MapstructUtils;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.erp.prd.domain.PrdMaterialRequirement;
import org.dromara.erp.prd.domain.PrdMaterialRequirementDetail;
import org.dromara.erp.prd.domain.bo.PrdMaterialRequirementBo;
import org.dromara.erp.prd.domain.vo.PrdMaterialRequirementVo;
import org.dromara.erp.prd.mapper.PrdMaterialRequirementDetailMapper;
import org.dromara.erp.prd.mapper.PrdMaterialRequirementMapper;
import org.dromara.erp.prd.service.IPrdMaterialRequirementService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.dynamic.datasource.annotation.DS;
import org.dromara.erp.bd.domain.BdMaterial;
import org.dromara.erp.bd.domain.BdBom;
import org.dromara.erp.bd.domain.bo.BdBomDetailBo;
import org.dromara.erp.bd.domain.vo.BdMaterialVo;
import org.dromara.erp.bd.domain.vo.BdBomDetailVo;
import org.dromara.erp.bd.mapper.BdMaterialMapper;
import org.dromara.erp.bd.mapper.BdBomMapper;
import org.dromara.erp.bd.service.IBdBomDetailService;
import org.dromara.erp.wm.domain.WmMaterialStock;
import org.dromara.erp.wm.mapper.WmMaterialStockMapper;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 物料清单Service业务层处理
 *
 * @author ruoyi
 */
@Slf4j
@RequiredArgsConstructor
@DS("erp")
@Service
public class PrdMaterialRequirementServiceImpl implements IPrdMaterialRequirementService {

    private final PrdMaterialRequirementMapper baseMapper;
    private final PrdMaterialRequirementDetailMapper detailMapper;
    private final IBdBomDetailService bomDetailService;
    private final BdMaterialMapper materialMapper;
    private final BdBomMapper bdBomMapper;
    private final WmMaterialStockMapper wmMaterialStockMapper;

    @Override
    public TableDataInfo<PrdMaterialRequirementVo> selectPageList(PrdMaterialRequirementBo bo, PageQuery pageQuery) {
        LambdaQueryWrapper<PrdMaterialRequirement> lqw = buildQueryWrapper(bo);
        Page<PrdMaterialRequirementVo> page = baseMapper.selectVoPage(pageQuery.build(), lqw);
        return TableDataInfo.build(page);
    }

    @Override
    public List<PrdMaterialRequirementVo> selectList(PrdMaterialRequirementBo bo) {
        return baseMapper.selectVoList(buildQueryWrapper(bo));
    }

    @Override
    public PrdMaterialRequirementVo selectById(Long id) {
        PrdMaterialRequirementVo vo = baseMapper.selectVoById(id);
        if (vo != null && vo.getRequirementId() != null) {
            LambdaQueryWrapper<PrdMaterialRequirementDetail> detailLqw = Wrappers.lambdaQuery();
            detailLqw.eq(PrdMaterialRequirementDetail::getRequirementId, vo.getRequirementId());
            List<PrdMaterialRequirementDetail> details = detailMapper.selectList(detailLqw);
            vo.setPrdMaterialRequirementDetailList(details);
        }
        return vo;
    }

    @Override
    public PrdMaterialRequirement selectPrdMaterialRequirementByRequirementId(Long requirementId) {
        return baseMapper.selectById(requirementId);
    }

    @Override
    public List<PrdMaterialRequirement> selectPrdMaterialRequirementList(PrdMaterialRequirement prdMaterialRequirement) {
        LambdaQueryWrapper<PrdMaterialRequirement> lqw = buildEntityQueryWrapper(prdMaterialRequirement);
        return baseMapper.selectList(lqw);
    }

    @Override
    public List<PrdMaterialRequirement> selectPrdMaterialRequirementListWithDetails(PrdMaterialRequirement prdMaterialRequirement) {
        LambdaQueryWrapper<PrdMaterialRequirement> lqw = buildEntityQueryWrapper(prdMaterialRequirement);
        List<PrdMaterialRequirement> list = baseMapper.selectList(lqw);
        for (PrdMaterialRequirement main : list) {
            if (main.getRequirementId() != null) {
                LambdaQueryWrapper<PrdMaterialRequirementDetail> detailLqw = Wrappers.lambdaQuery();
                detailLqw.eq(PrdMaterialRequirementDetail::getRequirementId, main.getRequirementId());
                List<PrdMaterialRequirementDetail> details = detailMapper.selectList(detailLqw);
                main.setPrdMaterialRequirementDetailList(details);
            }
        }
        return list;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int insertPrdMaterialRequirement(PrdMaterialRequirement prdMaterialRequirement) {
        int rows = baseMapper.insert(prdMaterialRequirement);
        insertPrdMaterialRequirementDetail(prdMaterialRequirement);
        return rows;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int insertByBo(PrdMaterialRequirementBo bo) {
        PrdMaterialRequirement entity = MapstructUtils.convert(bo, PrdMaterialRequirement.class);
        int rows = baseMapper.insert(entity);
        if (bo.getPrdMaterialRequirementDetailList() != null && !bo.getPrdMaterialRequirementDetailList().isEmpty()) {
            for (PrdMaterialRequirementDetail detail : bo.getPrdMaterialRequirementDetailList()) {
                detail.setRequirementId(entity.getRequirementId());
            }
            insertPrdMaterialRequirementDetails(bo.getPrdMaterialRequirementDetailList());
        }
        return rows;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updateByBo(PrdMaterialRequirementBo bo) {
        PrdMaterialRequirement entity = MapstructUtils.convert(bo, PrdMaterialRequirement.class);
        // Delete existing details
        deleteDetailByRequirementId(entity.getRequirementId());
        // Insert new details
        if (bo.getPrdMaterialRequirementDetailList() != null && !bo.getPrdMaterialRequirementDetailList().isEmpty()) {
            for (PrdMaterialRequirementDetail detail : bo.getPrdMaterialRequirementDetailList()) {
                detail.setRequirementId(entity.getRequirementId());
            }
            insertPrdMaterialRequirementDetails(bo.getPrdMaterialRequirementDetailList());
        }
        return baseMapper.updateById(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updatePrdMaterialRequirement(PrdMaterialRequirement prdMaterialRequirement) {
        deleteDetailByRequirementId(prdMaterialRequirement.getRequirementId());
        insertPrdMaterialRequirementDetail(prdMaterialRequirement);
        return baseMapper.updateById(prdMaterialRequirement);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteByIds(Long[] ids) {
        for (Long id : ids) {
            deleteDetailByRequirementId(id);
        }
        return baseMapper.deleteBatchIds(List.of(ids));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deletePrdMaterialRequirementByRequirementIds(Long[] requirementIds) {
        for (Long id : requirementIds) {
            deleteDetailByRequirementId(id);
        }
        return baseMapper.deleteBatchIds(List.of(requirementIds));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deletePrdMaterialRequirementByRequirementId(Long requirementId) {
        deleteDetailByRequirementId(requirementId);
        return baseMapper.deleteById(requirementId);
    }

    @Override
    public List<PrdMaterialRequirementDetail> importBomDetails(Long bomId, BigDecimal totalRequiredQuantity) {
        if (bomId == null) {
            throw new RuntimeException("BOM ID不能为空");
        }
        if (totalRequiredQuantity == null || totalRequiredQuantity.compareTo(BigDecimal.ZERO) <= 0) {
            throw new RuntimeException("需求总量必须大于0");
        }

        // 1. Query BOM to get sourceType
        BdBom bom = bdBomMapper.selectById(bomId);
        String sourceType = bom != null ? bom.getSourceType() : null;

        // 2. Query BOM details by bomId
        BdBomDetailBo bo = new BdBomDetailBo();
        bo.setBomId(bomId);
        List<BdBomDetailVo> bomDetails = bomDetailService.selectList(bo);

        if (bomDetails == null || bomDetails.isEmpty()) {
            return new ArrayList<>();
        }

        // 3. Batch-query materials for code, spec, and UOM name
        List<Long> materialIds = bomDetails.stream()
            .map(BdBomDetailVo::getChildMaterialId)
            .filter(Objects::nonNull)
            .distinct()
            .toList();

        Map<Long, BdMaterialVo> materialMap = new HashMap<>();
        if (!materialIds.isEmpty()) {
            materialMap = materialMapper.selectVoList(
                Wrappers.<BdMaterial>lambdaQuery()
                    .in(BdMaterial::getMaterialId, materialIds)
            ).stream().collect(Collectors.toMap(
                BdMaterialVo::getMaterialId,
                v -> v,
                (a, b) -> a,
                HashMap::new
            ));
        }

        // 4. Map to PrdMaterialRequirementDetail with quantity calculation
        final Map<Long, BdMaterialVo> finalMaterialMap = materialMap;
        final String finalSourceType = sourceType;
        return bomDetails.stream().map(detail -> {
            PrdMaterialRequirementDetail reqDetail = new PrdMaterialRequirementDetail();
            BdMaterialVo mat = finalMaterialMap.get(detail.getChildMaterialId());

            reqDetail.setMaterialId(detail.getChildMaterialId());
            reqDetail.setMaterialCode(detail.getChildMaterialCode() != null ? detail.getChildMaterialCode() : (mat != null ? mat.getMaterialCode() : null));
            reqDetail.setMaterialName(detail.getChildMaterialName() != null ? detail.getChildMaterialName() : (mat != null ? mat.getMaterialName() : null));
            reqDetail.setMaterialSpec(detail.getChildMaterialSpec() != null ? detail.getChildMaterialSpec() : (mat != null ? mat.getMaterialSpec() : null));
            reqDetail.setUnit(mat != null ? mat.getBaseUomName() : null);
            reqDetail.setBomQuantity(detail.getQuantityPerParent());
            reqDetail.setLossRate(detail.getLossRate());
            if (detail.getQuantityPerParent() != null) {
                reqDetail.setRequiredQuantity(detail.getQuantityPerParent().multiply(totalRequiredQuantity));
            }
            reqDetail.setIssuedQuantity(BigDecimal.ZERO);
            reqDetail.setStockQuantity(BigDecimal.ZERO);
            reqDetail.setShortageQuantity(BigDecimal.ZERO);
            reqDetail.setOperationStep(detail.getOperationStep() != null ? Long.valueOf(detail.getOperationStep()) : null);
            reqDetail.setWorkstationId(detail.getWorkstationId());
            reqDetail.setWorkstationName(detail.getWorkstationName());
            reqDetail.setLeadTime(detail.getLeadTime() != null ? Long.valueOf(detail.getLeadTime()) : null);
            reqDetail.setRequiredDate(new Date());
            reqDetail.setStatus(1L);
            reqDetail.setSourceType(finalSourceType);

            return reqDetail;
        }).collect(Collectors.toList());
    }

    @Override
    public List<PrdMaterialRequirementVo> selectPrdMaterialRequirementTreeByPlanNo(String planNo) {
        PrdMaterialRequirement queryParam = new PrdMaterialRequirement();
        queryParam.setPlanNo(planNo);
        List<PrdMaterialRequirement> allList = selectPrdMaterialRequirementListWithDetails(queryParam);
        if (allList == null || allList.isEmpty()) {
            return new ArrayList<>();
        }

        // 按bomId排序
        allList.sort(Comparator.comparing(PrdMaterialRequirement::getBomId, Comparator.nullsLast(Comparator.naturalOrder())));

        List<PrdMaterialRequirementVo> treeRoots = new ArrayList<>();
        Map<Long, PrdMaterialRequirementVo> nodeMap = new HashMap<>();

        for (PrdMaterialRequirement req : allList) {
            PrdMaterialRequirementVo vo = MapstructUtils.convert(req, PrdMaterialRequirementVo.class);
            vo.setChildren(new ArrayList<>());
            nodeMap.put(vo.getRequirementId(), vo);
        }

        for (PrdMaterialRequirementVo vo : nodeMap.values()) {
            if (vo.getParentRequirementId() != null) {
                PrdMaterialRequirementVo parent = nodeMap.get(vo.getParentRequirementId());
                if (parent != null) {
                    parent.getChildren().add(vo);
                }
            } else {
                treeRoots.add(vo);
            }
        }

        // 每层子节点也按bomId排序
        for (PrdMaterialRequirementVo vo : nodeMap.values()) {
            if (vo.getChildren() != null && !vo.getChildren().isEmpty()) {
                vo.getChildren().sort(Comparator.comparing(PrdMaterialRequirementVo::getBomId, Comparator.nullsLast(Comparator.naturalOrder())));
            }
        }

        return treeRoots;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int insertPrdMaterialRequirementDetails(List<PrdMaterialRequirementDetail> detailList) {
        int count = 0;
        for (PrdMaterialRequirementDetail detail : detailList) {
            count += detailMapper.insert(detail);
        }
        return count;
    }

    @Override
    public void calculateNetRequirements(PrdMaterialRequirementDetail detail) {
        if (detail == null || detail.getMaterialId() == null) {
            return;
        }

        // 查询当前库存
        BigDecimal stockQty = queryTotalStock(detail.getMaterialId());
        detail.setStockQuantity(stockQty);

        // 查询物料安全库存
        BigDecimal safetyStock = BigDecimal.ZERO;
        try {
            BdMaterial material = materialMapper.selectById(detail.getMaterialId());
            if (material != null && material.getSafetyStock() != null) {
                safetyStock = material.getSafetyStock();
            }
        } catch (Exception e) {
            log.warn("查询物料安全库存失败, materialId={}", detail.getMaterialId(), e);
        }

        // 净需求 = 毛需求 - (现有库存 - 安全库存)，不小于0
        BigDecimal requiredQty = detail.getRequiredQuantity() != null ? detail.getRequiredQuantity() : BigDecimal.ZERO;
        BigDecimal availableStock = stockQty.subtract(safetyStock);

        if (availableStock.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal shortage = requiredQty.subtract(availableStock);
            detail.setShortageQuantity(shortage.compareTo(BigDecimal.ZERO) > 0 ? shortage : BigDecimal.ZERO);
        } else {
            detail.setShortageQuantity(requiredQty);
        }
    }

    /**
     * 查询物料的总库存量
     */
    private BigDecimal queryTotalStock(Long materialId) {
        if (materialId == null) {
            return BigDecimal.ZERO;
        }
        try {
            LambdaQueryWrapper<WmMaterialStock> lqw = Wrappers.lambdaQuery();
            lqw.eq(WmMaterialStock::getMaterialId, materialId);
            lqw.gt(WmMaterialStock::getStockQuantity, BigDecimal.ZERO);
            List<WmMaterialStock> stocks = wmMaterialStockMapper.selectList(lqw);
            return stocks.stream()
                    .map(WmMaterialStock::getStockQuantity)
                    .filter(Objects::nonNull)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
        } catch (Exception e) {
            log.warn("查询物料库存失败, materialId={}", materialId, e);
            return BigDecimal.ZERO;
        }
    }

    /**
     * Insert detail records for a requirement
     */
    private void insertPrdMaterialRequirementDetail(PrdMaterialRequirement prdMaterialRequirement) {
        List<PrdMaterialRequirementDetail> detailList = prdMaterialRequirement.getPrdMaterialRequirementDetailList();
        Long requirementId = prdMaterialRequirement.getRequirementId();
        if (detailList != null && !detailList.isEmpty()) {
            List<PrdMaterialRequirementDetail> list = new ArrayList<>();
            for (PrdMaterialRequirementDetail detail : detailList) {
                detail.setRequirementId(requirementId);
                list.add(detail);
            }
            if (!list.isEmpty()) {
                insertPrdMaterialRequirementDetails(list);
            }
        }
    }

    /**
     * Delete details by requirement ID
     */
    private void deleteDetailByRequirementId(Long requirementId) {
        if (requirementId != null) {
            LambdaQueryWrapper<PrdMaterialRequirementDetail> lqw = Wrappers.lambdaQuery();
            lqw.eq(PrdMaterialRequirementDetail::getRequirementId, requirementId);
            detailMapper.delete(lqw);
        }
    }

    private LambdaQueryWrapper<PrdMaterialRequirement> buildQueryWrapper(PrdMaterialRequirementBo bo) {
        LambdaQueryWrapper<PrdMaterialRequirement> lqw = Wrappers.lambdaQuery();
        lqw.eq(StringUtils.isNotBlank(bo.getRequirementNo()), PrdMaterialRequirement::getRequirementNo, bo.getRequirementNo());
        lqw.eq(bo.getPlanId() != null, PrdMaterialRequirement::getPlanId, bo.getPlanId());
        lqw.eq(StringUtils.isNotBlank(bo.getPlanNo()), PrdMaterialRequirement::getPlanNo, bo.getPlanNo());
        lqw.eq(bo.getBomId() != null, PrdMaterialRequirement::getBomId, bo.getBomId());
        lqw.eq(StringUtils.isNotBlank(bo.getStatus()), PrdMaterialRequirement::getStatus, bo.getStatus());
        lqw.eq(bo.getPlannerId() != null, PrdMaterialRequirement::getPlannerId, bo.getPlannerId());
        lqw.like(StringUtils.isNotBlank(bo.getPlannerName()), PrdMaterialRequirement::getPlannerName, bo.getPlannerName());
        lqw.eq(bo.getDepartmentId() != null, PrdMaterialRequirement::getDepartmentId, bo.getDepartmentId());
        lqw.like(StringUtils.isNotBlank(bo.getDepartmentName()), PrdMaterialRequirement::getDepartmentName, bo.getDepartmentName());
        return lqw;
    }

    private LambdaQueryWrapper<PrdMaterialRequirement> buildEntityQueryWrapper(PrdMaterialRequirement entity) {
        LambdaQueryWrapper<PrdMaterialRequirement> lqw = Wrappers.lambdaQuery();
        lqw.eq(StringUtils.isNotBlank(entity.getRequirementNo()), PrdMaterialRequirement::getRequirementNo, entity.getRequirementNo());
        lqw.eq(entity.getPlanId() != null, PrdMaterialRequirement::getPlanId, entity.getPlanId());
        lqw.eq(StringUtils.isNotBlank(entity.getPlanNo()), PrdMaterialRequirement::getPlanNo, entity.getPlanNo());
        lqw.eq(entity.getBomId() != null, PrdMaterialRequirement::getBomId, entity.getBomId());
        lqw.eq(StringUtils.isNotBlank(entity.getStatus()), PrdMaterialRequirement::getStatus, entity.getStatus());
        lqw.eq(entity.getPlannerId() != null, PrdMaterialRequirement::getPlannerId, entity.getPlannerId());
        lqw.like(StringUtils.isNotBlank(entity.getPlannerName()), PrdMaterialRequirement::getPlannerName, entity.getPlannerName());
        lqw.eq(entity.getDepartmentId() != null, PrdMaterialRequirement::getDepartmentId, entity.getDepartmentId());
        lqw.like(StringUtils.isNotBlank(entity.getDepartmentName()), PrdMaterialRequirement::getDepartmentName, entity.getDepartmentName());
        return lqw;
    }
}
