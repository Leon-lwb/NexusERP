package com.nexus.erp.prd.service.impl;

import com.nexus.erp.prd.util.BomExplosionCalculator;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.nexus.common.core.utils.MapstructUtils;
import com.nexus.common.core.utils.StringUtils;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.system.strategy.AutoCodeUtil;
import com.nexus.erp.prd.domain.PrdMaterialRequirement;
import com.nexus.erp.prd.domain.PrdMaterialRequirementDetail;
import com.nexus.erp.prd.domain.bo.PrdMaterialRequirementBo;
import com.nexus.erp.prd.domain.vo.PrdMaterialRequirementVo;
import com.nexus.erp.prd.mapper.PrdMaterialRequirementDetailMapper;
import com.nexus.erp.prd.mapper.PrdMaterialRequirementMapper;
import com.nexus.erp.prd.service.IPrdMaterialRequirementService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.nexus.erp.bd.domain.BdMaterial;
import com.nexus.erp.bd.domain.BdBom;
import com.nexus.erp.bd.domain.bo.BdBomDetailBo;
import com.nexus.erp.bd.domain.vo.BdMaterialVo;
import com.nexus.erp.bd.domain.vo.BdBomDetailVo;
import com.nexus.erp.bd.mapper.BdMaterialMapper;
import com.nexus.erp.bd.mapper.BdBomMapper;
import com.nexus.erp.bd.service.IBdBomDetailService;
import com.nexus.erp.wm.domain.WmMaterialStock;
import com.nexus.erp.wm.mapper.WmMaterialStockMapper;

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
 * 物料清单 Service 业务层实现
 * <p>
 * 实现物料需求清单相关的业务逻辑，包括物料需求的查询、BOM 导入、净需求计算及树形展示等。
 *
 * @author nexus
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
    private final AutoCodeUtil autoCodeUtil;

    /**
     * 分页查询物料需求清单列表
     *
     * @param bo        查询参数
     * @param pageQuery 分页参数
     * @return 物料需求清单分页列表
     */
    @Override
    public TableDataInfo<PrdMaterialRequirementVo> selectPageList(PrdMaterialRequirementBo bo, PageQuery pageQuery) {
        LambdaQueryWrapper<PrdMaterialRequirement> lqw = buildQueryWrapper(bo);
        Page<PrdMaterialRequirementVo> page = baseMapper.selectVoPage(pageQuery.build(), lqw);
        return TableDataInfo.build(page);
    }

    /**
     * 查询物料需求清单列表（不分页）
     *
     * @param bo 查询参数
     * @return 物料需求清单列表
     */
    @Override
    public List<PrdMaterialRequirementVo> selectList(PrdMaterialRequirementBo bo) {
        return baseMapper.selectVoList(buildQueryWrapper(bo));
    }

    /**
     * 根据 ID 查询物料需求清单详情
     *
     * @param id 物料需求 ID
     * @return 物料需求清单详情
     */
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

    /**
     * 根据物料需求 ID 查询物料需求实体
     *
     * @param requirementId 物料需求 ID
     * @return 物料需求实体
     */
    @Override
    public PrdMaterialRequirement selectPrdMaterialRequirementByRequirementId(Long requirementId) {
        return baseMapper.selectById(requirementId);
    }

    /**
     * 查询物料需求清单列表（按实体条件）
     *
     * @param prdMaterialRequirement 物料需求查询实体
     * @return 物料需求清单列表
     */
    @Override
    public List<PrdMaterialRequirement> selectPrdMaterialRequirementList(PrdMaterialRequirement prdMaterialRequirement) {
        LambdaQueryWrapper<PrdMaterialRequirement> lqw = buildEntityQueryWrapper(prdMaterialRequirement);
        return baseMapper.selectList(lqw);
    }

    /**
     * 查询物料需求清单列表（含明细）
     *
     * @param prdMaterialRequirement 物料需求查询实体
     * @return 物料需求清单列表（每条记录含明细列表）
     */
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

    /**
     * 新增物料需求清单（含明细）
     *
     * @param prdMaterialRequirement 物料需求实体（含明细列表）
     * @return 影响的行数
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int insertPrdMaterialRequirement(PrdMaterialRequirement prdMaterialRequirement) {
        int rows = baseMapper.insert(prdMaterialRequirement);
        insertPrdMaterialRequirementDetail(prdMaterialRequirement);
        return rows;
    }

    /**
     * 新增物料需求清单（通过 BO，含明细）
     *
     * @param bo 物料需求业务对象（含明细列表）
     * @return 影响的行数
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int insertByBo(PrdMaterialRequirementBo bo) {
        PrdMaterialRequirement entity = MapstructUtils.convert(bo, PrdMaterialRequirement.class);
        if (StringUtils.isBlank(entity.getRequirementNo())) {
            entity.setRequirementNo(autoCodeUtil.genSerialCode("PRD_MATERIAL_MBOM", null));
        }
        int rows = baseMapper.insert(entity);
        if (bo.getPrdMaterialRequirementDetailList() != null && !bo.getPrdMaterialRequirementDetailList().isEmpty()) {
            for (PrdMaterialRequirementDetail detail : bo.getPrdMaterialRequirementDetailList()) {
                detail.setRequirementId(entity.getRequirementId());
            }
            insertPrdMaterialRequirementDetails(bo.getPrdMaterialRequirementDetailList());
        }
        return rows;
    }

    /**
     * 修改物料需求清单（通过 BO，含明细）
     * <p>
     * 先删除原有明细，再插入新的明细列表。
     *
     * @param bo 物料需求业务对象（含明细列表）
     * @return 影响的行数
     */
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

    /**
     * 修改物料需求清单（含明细）
     *
     * @param prdMaterialRequirement 物料需求实体（含明细列表）
     * @return 影响的行数
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updatePrdMaterialRequirement(PrdMaterialRequirement prdMaterialRequirement) {
        deleteDetailByRequirementId(prdMaterialRequirement.getRequirementId());
        insertPrdMaterialRequirementDetail(prdMaterialRequirement);
        return baseMapper.updateById(prdMaterialRequirement);
    }

    /**
     * 批量删除物料需求清单（含明细）
     *
     * @param ids 要删除的物料需求 ID 数组
     * @return 影响的行数
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteByIds(Long[] ids) {
        for (Long id : ids) {
            deleteDetailByRequirementId(id);
        }
        return baseMapper.deleteBatchIds(List.of(ids));
    }

    /**
     * 根据物料需求 ID 数组批量删除物料需求清单（含明细）
     *
     * @param requirementIds 物料需求 ID 数组
     * @return 影响的行数
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deletePrdMaterialRequirementByRequirementIds(Long[] requirementIds) {
        for (Long id : requirementIds) {
            deleteDetailByRequirementId(id);
        }
        return baseMapper.deleteBatchIds(List.of(requirementIds));
    }

    /**
     * 根据物料需求 ID 删除物料需求清单（含明细）
     *
     * @param requirementId 物料需求 ID
     * @return 影响的行数
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deletePrdMaterialRequirementByRequirementId(Long requirementId) {
        deleteDetailByRequirementId(requirementId);
        return baseMapper.deleteById(requirementId);
    }

    /**
     * 根据 BOM ID 导入物料需求明细
     * <p>
     * 根据 BOM 展开计算各子项物料的需求数量，并补充物料基础信息。
     *
     * @param bomId                 BOM ID
     * @param totalRequiredQuantity 需求总量
     * @return 物料需求明细列表
     */
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

        // 3. 批量读取物料编码、规格与基本单位身份
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
        //    sourceType 从子项物料本身取（而非父BOM），便于后续按来源分流：
        //    PROCESS→工单 / PROCUREMENT→采购申请 / SUBCONTRACT→委外订单 / PHANTOM→跳过（虚拟件不入库不产生需求）
        final Map<Long, BdMaterialVo> finalMaterialMap = materialMap;
        final String finalSourceType = sourceType;
        return bomDetails.stream()
            .filter(detail -> {
                // 虚拟件不产生需求（虚拟件展开时跳层，其下层物料已通过 BOM 展开独立计算需求）
                BdMaterialVo mat = finalMaterialMap.get(detail.getChildMaterialId());
                return mat == null || !Integer.valueOf(1).equals(mat.getIsPhantom());
            })
            .map(detail -> {
            PrdMaterialRequirementDetail reqDetail = new PrdMaterialRequirementDetail();
            BdMaterialVo mat = finalMaterialMap.get(detail.getChildMaterialId());

            reqDetail.setMaterialId(detail.getChildMaterialId());
            reqDetail.setMaterialCode(detail.getChildMaterialCode() != null ? detail.getChildMaterialCode() : (mat != null ? mat.getMaterialCode() : null));
            reqDetail.setMaterialName(detail.getChildMaterialName() != null ? detail.getChildMaterialName() : (mat != null ? mat.getMaterialName() : null));
            reqDetail.setMaterialSpec(detail.getChildMaterialSpec() != null ? detail.getChildMaterialSpec() : (mat != null ? mat.getMaterialSpec() : null));
            // 新生成明细只登记基本单位主键；名称仅供展示，不能推断认证身份。
            // 未知主键保持 NULL，数量与单位可信资格仍由来源认证独立复验。
            Long baseUomId = mat != null ? mat.getBaseUomId() : null;
            reqDetail.setUnit(baseUomId != null && baseUomId > 0 ? baseUomId.toString() : null);
            reqDetail.setBomQuantity(detail.getQuantityPerParent());
            reqDetail.setLossRate(detail.getLossRate());
            if (detail.getQuantityPerParent() != null) {
                // ★ ERP-PRD-01: 与计划驱动路径统一口径（含损耗率 + CEILING 取整）
                reqDetail.setRequiredQuantity(BomExplosionCalculator.calculateChildQuantity(
                    totalRequiredQuantity, detail.getQuantityPerParent(), detail.getLossRate()));
            }
            reqDetail.setIssuedQuantity(BigDecimal.ZERO);
            reqDetail.setStockQuantity(BigDecimal.ZERO);
            reqDetail.setShortageQuantity(BigDecimal.ZERO);
            reqDetail.setOperationStep(detail.getOperationStep() != null ? Long.valueOf(detail.getOperationStep()) : null);
            reqDetail.setLeadTime(detail.getLeadTime() != null ? Long.valueOf(detail.getLeadTime()) : null);
            // ★ ERP-PRD-01: leadTime 单位统一为小时，需求日期按「基准 - leadTime」倒排（此处无父层，基准取当前时间）
            reqDetail.setRequiredDate(BomExplosionCalculator.resolveRequiredDate(null, detail.getLeadTime()));
            reqDetail.setStatus(1L);
            // sourceType 优先取子项物料的（统一编码后），物料未配置时回退父BOM
            reqDetail.setSourceType(mat != null && mat.getSourceType() != null ? mat.getSourceType() : finalSourceType);
            calculateNetRequirements(reqDetail);
            return reqDetail;
        }).collect(Collectors.toList());
    }

    /**
     * 根据计划编号查询物料需求树（含父子关系嵌套）
     *
     * @param planNo 生产计划编号
     * @return 物料需求树形列表（顶层节点）
     */
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

    /**
     * 批量新增物料需求明细
     *
     * @param detailList 物料需求明细列表
     * @return 影响的行数
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int insertPrdMaterialRequirementDetails(List<PrdMaterialRequirementDetail> detailList) {
        int count = 0;
        for (PrdMaterialRequirementDetail detail : detailList) {
            count += detailMapper.insert(detail);
        }
        return count;
    }

    /**
     * 计算物料需求明细的净需求
     * <p>查询当前库存与安全库存，按「净需求 = 毛需求 - (现有库存 - 安全库存)」计算缺口（不小于 0）。</p>
     *
     * @param detail 物料需求明细，计算后回填库存量与短缺量
     */
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
            // ★ ERP-PRD-07: 冻结库存不计入可用量，与 PuPurchaseRequisitionServiceImpl 低库存口径一致
            lqw.eq(WmMaterialStock::getFrozenFlag, 0L);
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
     * 按提前期倒排需求日期。
     *
     * @deprecated ERP-PRD-01 起统一走 {@link BomExplosionCalculator#resolveRequiredDate(java.util.Date, Number)}，
     *             本方法仅为兼容历史调用保留，请勿在新代码中使用。
     */
    @Deprecated
    private Date resolveRequiredDate(Integer leadTime) {
        return BomExplosionCalculator.resolveRequiredDate(null, leadTime);
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
        lqw.eq(bo.getDeptId() != null, PrdMaterialRequirement::getDeptId, bo.getDeptId());
        lqw.like(StringUtils.isNotBlank(bo.getDeptName()), PrdMaterialRequirement::getDeptName, bo.getDeptName());
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
        lqw.eq(entity.getDeptId() != null, PrdMaterialRequirement::getDeptId, entity.getDeptId());
        lqw.like(StringUtils.isNotBlank(entity.getDeptName()), PrdMaterialRequirement::getDeptName, entity.getDeptName());
        return lqw;
    }
}
