package org.dromara.erp.prd.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.core.utils.MapstructUtils;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.common.satoken.utils.LoginHelper;
import org.dromara.erp.bd.domain.BdBom;
import org.dromara.erp.bd.domain.BdMaterial;
import org.dromara.erp.bd.domain.BdMaterialCategory;
import org.dromara.erp.bd.domain.BdBomDetail;
import org.dromara.erp.bd.domain.vo.BdBomDetailVo;
import org.dromara.erp.bd.mapper.BdBomDetailMapper;
import org.dromara.erp.bd.mapper.BdBomMapper;
import org.dromara.erp.bd.mapper.BdMaterialCategoryMapper;
import org.dromara.erp.bd.mapper.BdMaterialMapper;
import org.dromara.erp.prd.domain.PrdMaterialRequirement;
import org.dromara.erp.prd.domain.PrdMaterialRequirementDetail;
import org.dromara.erp.prd.domain.PrdProductionPlan;
import org.dromara.erp.prd.domain.bo.MaterialRequirementCalcDTO;
import org.dromara.erp.prd.domain.bo.PrdProductionPlanBo;
import org.dromara.erp.prd.domain.vo.PrdProductionPlanVo;
import org.dromara.erp.prd.mapper.PrdProductionPlanMapper;
import org.dromara.erp.prd.service.IPrdMaterialRequirementService;
import org.dromara.erp.prd.service.IPrdProductionPlanService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.dynamic.datasource.annotation.DS;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

/**
 * 生产计划Service业务层处理
 *
 * @author ruoyi
 */
@Slf4j
@RequiredArgsConstructor
@DS("erp")
@Service
public class PrdProductionPlanServiceImpl implements IPrdProductionPlanService {

    private final PrdProductionPlanMapper baseMapper;
    private final BdBomMapper bdBomMapper;
    private final BdMaterialMapper bdMaterialMapper;
    private final BdMaterialCategoryMapper bdMaterialCategoryMapper;
    private final BdBomDetailMapper bdBomDetailMapper;
    private final IPrdMaterialRequirementService prdMaterialRequirementService;

    // ========== SO 模块依赖（需要迁移 so 模块后方可使用） ==========
    // TODO: 从 nexus-erp 迁移 SoSalesOrderLineService 后取消注释
    // private final SoSalesOrderLineService soSalesOrderLineService;

    // ========== 自动编码依赖（需要迁移 AutoCodeUtil 后方可使用） ==========
    // TODO: 从 nexus-erp 迁移 AutoCodeUtil 后取消注释
    // private final AutoCodeUtil autoCodeUtil;

    // 状态常量（从 NexusConstants 迁移）
    private static final String STATUS_DRAFT = "DRAFT";
    private static final Long VERSION_INIT = 1L;
    private static final Long VERSION_NOT_CURRENT = 0L;

    @Override
    public TableDataInfo<PrdProductionPlanVo> selectPageList(PrdProductionPlanBo bo, PageQuery pageQuery) {
        LambdaQueryWrapper<PrdProductionPlan> lqw = buildQueryWrapper(bo);
        Page<PrdProductionPlanVo> page = baseMapper.selectVoPage(pageQuery.build(), lqw);
        return TableDataInfo.build(page);
    }

    @Override
    public List<PrdProductionPlanVo> selectList(PrdProductionPlanBo bo) {
        return baseMapper.selectVoList(buildQueryWrapper(bo));
    }

    @Override
    public PrdProductionPlanVo selectById(Long id) {
        return baseMapper.selectVoById(id);
    }

    @Override
    public PrdProductionPlan selectPrdProductionPlanByPlanId(Long planId) {
        return baseMapper.selectById(planId);
    }

    @Override
    public List<PrdProductionPlan> selectPrdProductionPlanList(PrdProductionPlan prdProductionPlan) {
        LambdaQueryWrapper<PrdProductionPlan> lqw = buildEntityQueryWrapper(prdProductionPlan);
        return baseMapper.selectList(lqw);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int insertPrdProductionPlan(PrdProductionPlan prdProductionPlan) {
        return baseMapper.insert(prdProductionPlan);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int insertByBo(PrdProductionPlanBo bo) {
        PrdProductionPlan entity = MapstructUtils.convert(bo, PrdProductionPlan.class);
        return baseMapper.insert(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updateByBo(PrdProductionPlanBo bo) {
        PrdProductionPlan entity = MapstructUtils.convert(bo, PrdProductionPlan.class);
        return baseMapper.updateById(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updatePrdProductionPlan(PrdProductionPlan prdProductionPlan) {
        return baseMapper.updateById(prdProductionPlan);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteByIds(Long[] ids) {
        return baseMapper.deleteBatchIds(List.of(ids));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deletePrdProductionPlanByPlanIds(Long[] planIds) {
        return baseMapper.deleteBatchIds(List.of(planIds));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deletePrdProductionPlanByPlanId(Long planId) {
        return baseMapper.deleteById(planId);
    }

    /**
     * 更新生产计划版本
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updatePrdProductionPlanVersion(PrdProductionPlan prdProductionPlan) {
        // 查询原生产计划
        PrdProductionPlan originalPlan = baseMapper.selectById(prdProductionPlan.getPlanId());
        if (originalPlan == null) {
            throw new RuntimeException("生产计划不存在");
        }

        // 检查状态是否为草稿(DRAFT)
        if (!STATUS_DRAFT.equals(originalPlan.getStatus())) {
            throw new RuntimeException("该生产计划不为草稿，无法保存！");
        }

        // 创建新版本的生产计划
        PrdProductionPlan newPlan = new PrdProductionPlan();
        // 复制原计划的所有属性
        BeanUtil.copyProperties(prdProductionPlan, newPlan);
        // 版本号加1
        if (originalPlan.getVersion() != null) {
            newPlan.setVersion(originalPlan.getVersion() + 1);
        } else {
            newPlan.setVersion(1L);
        }
        // 设置更新人
        Long userId = LoginHelper.getUserId();
        newPlan.setCreateBy(userId);
        // 设置为当前版本
        newPlan.setIsCurrent(VERSION_INIT);
        // 修改原计划为非当前版本
        originalPlan.setIsCurrent(VERSION_NOT_CURRENT);
        baseMapper.updateById(originalPlan);

        // 插入新版本的生产计划
        return baseMapper.insert(newPlan);
    }

    /**
     * BOM计算 - 根据生产计划中的订单行物料计算BOM树
     */
    @Override
    public List<BdBomDetailVo> calculateBomTree(Long planId) {
        if (planId == null) {
            throw new RuntimeException("生产计划ID不能为空");
        }

        // 查询生产计划
        PrdProductionPlan plan = baseMapper.selectById(planId);
        if (plan == null) {
            throw new RuntimeException("生产计划不存在");
        }

        // 检查是否关联订单行
        if (plan.getOrderLineId() == null) {
            throw new RuntimeException("该生产计划未关联订单行信息");
        }

        // TODO: 从 nexus-erp 迁移 SoSalesOrderLine 后恢复下方逻辑
        // SoSalesOrderLine orderLine = soSalesOrderLineService.getById(plan.getOrderLineId());
        // if (orderLine == null) {
        //     throw new RuntimeException("订单行信息不存在");
        // }
        // if (orderLine.getMaterialId() == null) {
        //     throw new RuntimeException("订单行未指定物料信息");
        // }
        log.warn("SO模块尚未迁移，跳过订单行检查。planId={}, orderLineId={}", planId, plan.getOrderLineId());

        // 查询该物料的当前有效BOM
        LambdaQueryWrapper<BdBom> bomQuery = Wrappers.lambdaQuery();
        bomQuery.eq(BdBom::getBomId, plan.getBomId());
        bomQuery.eq(BdBom::getIsCurrent, 1L);
        bomQuery.eq(BdBom::getStatus, 1L);
        List<BdBom> bomList = bdBomMapper.selectList(bomQuery);

        if (bomList == null || bomList.isEmpty()) {
            throw new RuntimeException("未找到该物料的有效BOM信息");
        }

        // 获取第一个BOM（当前版本）
        BdBom bom = bomList.get(0);

        // 查询BOM明细
        List<BdBomDetailVo> bomDetails = selectBomDetailVoList(bom.getBomId());

        if (bomDetails == null || bomDetails.isEmpty()) {
            throw new RuntimeException("BOM明细为空");
        }

        return bomDetails;
    }

    /**
     * 根据生产计划ID生成物料需求清单及明细（按节点拆分BOM树）
     * 规则：每个有BOM的节点生成一条主表，其底层物料作为子表；子节点BOM递归拆分
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<PrdMaterialRequirement> generateMaterialRequirementsWithDetails(Long planId) {
        // 1. 查询生产计划
        PrdProductionPlan plan = baseMapper.selectById(planId);
        if (plan == null) {
            throw new RuntimeException("生产计划不存在");
        }

        // 2. 检查是否有关联订单行
        if (plan.getOrderLineId() == null) {
            throw new RuntimeException("该生产计划未关联订单行信息");
        }

        // TODO: 从 nexus-erp 迁移 SoSalesOrderLineService 后恢复
        // SoSalesOrderLine orderLine = soSalesOrderLineService.getById(plan.getOrderLineId());
        // if (orderLine == null) {
        //     throw new RuntimeException("订单行信息不存在");
        // }
        log.warn("SO模块尚未迁移，使用计划中的 bomId={} 进行BOM展开", plan.getBomId());

        // 4. 按节点拆分生成：顶层与每个子节点BOM分别生成主表与底层物料子表
        List<PrdMaterialRequirement> createdMainRequirements = processBomNode(
                plan.getBomId(),
                plan.getBomId(),
                plan.getTotalQuantity(),
                plan
        );

        log.info("成功生成物料需求，共生成 {} 条主表记录", createdMainRequirements.size());

        // 5. 返回所有生成的主表记录
        return createdMainRequirements;
    }

    /**
     * 递归处理单个BOM节点：
     * - 当前节点（有BOM）生成一条主表，仅包含其BOM树下的底层物料作为子表
     * - 对每个具备BOM的子节点，递归生成独立的主表与其底层物料子表
     */
    private List<PrdMaterialRequirement> processBomNode(Long materialId, Long specifiedBomId,
                                                         BigDecimal parentQuantity, PrdProductionPlan plan) {
        List<PrdMaterialRequirement> result = new ArrayList<>();

        // 获取当前节点的BOM（顶层使用指定bomId，子节点优先查当前有效BOM）
        BdBom currentBom = getCurrentBom(materialId, specifiedBomId);
        if (currentBom == null) {
            // 当前为底层物料（无BOM），不在此处生成主表，交由父节点主表收集
            return result;
        }

        // 查询当前BOM的明细
        List<BdBomDetailVo> bomDetails = selectBomDetailVoList(currentBom.getBomId());
        if (bomDetails == null || bomDetails.isEmpty()) {
            // 无明细则不生成主表
            return result;
        }

        // 当前节点用于主表的底层物料清单（仅收集无BOM的子项；有BOM的子项作为子节点递归处理）
        List<MaterialRequirementCalcDTO> leafRequirements = new ArrayList<>();
        // 需要递归处理的子节点（具备BOM）
        List<BomCalculationNode> childAssemblyNodes = new ArrayList<>();

        for (BdBomDetailVo detail : bomDetails) {
            // 计算该子项在当前节点需求数量（含损耗率）
            BigDecimal childRequiredQuantity = calculateChildQuantity(
                    parentQuantity,
                    detail.getQuantityPerParent(),
                    detail.getLossRate()
            );

            // 判断子项是否有BOM（子节点BOM）
            BdBom childBom = getCurrentBom(detail.getChildMaterialId(), null);
            if (childBom != null) {
                // 作为子节点，递归生成其主表与底层物料子表
                BomCalculationNode childNode = new BomCalculationNode();
                childNode.setMaterialId(detail.getChildMaterialId());
                childNode.setQuantity(childRequiredQuantity);
                childNode.setBomId(childBom.getBomId());
                childNode.setParentDetail(detail);
                childAssemblyNodes.add(childNode);
            } else {
                // 无BOM，为底层物料，归属于当前节点的主表子表
                MaterialRequirementCalcDTO dto = new MaterialRequirementCalcDTO();
                dto.setMaterialId(detail.getChildMaterialId());
                dto.setTotalRequiredQuantity(childRequiredQuantity);
                dto.setQuantityPerParent(detail.getQuantityPerParent());
                dto.setLossRate(detail.getLossRate());
                dto.setOperationStep(detail.getOperationStep() != null ?
                        Long.valueOf(detail.getOperationStep()) : null);
                dto.setWorkstationId(detail.getWorkstationId());
                dto.setWorkstationName(detail.getWorkstationName());
                // 尝试补充物料基础信息
                BdMaterial material = getMaterialInfo(detail.getChildMaterialId());
                if (material != null) {
                    dto.setMaterialCode(material.getMaterialCode());
                    dto.setMaterialName(material.getMaterialName());
                    dto.setMaterialSpec(material.getMaterialSpec());
                    BdMaterialCategory category = getMaterialCategory(material.getCategoryId());
                    if (category != null) {
                        dto.setCategoryName(category.getCategoryName());
                        dto.setCategoryPath(category.getPathNames());
                    }
                } else {
                    // 如果获取不到物料信息，至少设置从BOM明细中可以获得的基本信息
                    dto.setMaterialCode(detail.getChildMaterialCode());
                    dto.setMaterialName(detail.getChildMaterialName());
                }
                leafRequirements.add(dto);
            }
        }

        // 合并相同底层物料用量，仅对当前节点的底层物料合并
        List<MaterialRequirementCalcDTO> mergedLeaves = mergeMaterialRequirements(leafRequirements);

        // 当前节点主表与子表生成（若存在底层物料）
        if (!mergedLeaves.isEmpty()) {
            PrdMaterialRequirement mainRequirement = createMaterialRequirement(plan, currentBom.getBomId(), mergedLeaves);
            prdMaterialRequirementService.insertPrdMaterialRequirement(mainRequirement);

            List<PrdMaterialRequirementDetail> detailList = new ArrayList<>();
            for (MaterialRequirementCalcDTO calcDto : mergedLeaves) {
                PrdMaterialRequirementDetail detailRecord = createMaterialRequirementDetail(
                        mainRequirement.getRequirementId(),
                        calcDto
                );
                detailList.add(detailRecord);
            }
            if (!detailList.isEmpty()) {
                prdMaterialRequirementService.insertPrdMaterialRequirementDetails(detailList);
            }

            log.info("生成物料需求主表: BOM={}, 主表ID={}, 子表明细数={}",
                    currentBom.getBomId(), mainRequirement.getRequirementId(), detailList.size());

            result.add(mainRequirement);
        }

        // 递归处理具备BOM的子节点：每个子节点生成独立的主表与其底层物料子表
        for (BomCalculationNode childNode : childAssemblyNodes) {
            result.addAll(processBomNode(
                    childNode.getMaterialId(),
                    childNode.getBomId(),
                    childNode.getQuantity(),
                    plan
            ));
        }

        return result;
    }

    /**
     * 获取当前物料的BOM
     */
    private BdBom getCurrentBom(Long materialId, Long specifiedBomId) {
        // 如果指定了BOM ID，优先使用指定的BOM
        if (specifiedBomId != null) {
            BdBom specifiedBom = bdBomMapper.selectById(specifiedBomId);
            if (specifiedBom != null && specifiedBom.getIsCurrent() == 1L && specifiedBom.getStatus() == 1L) {
                return specifiedBom;
            }
        }

        // 否则查询该物料的当前有效BOM
        LambdaQueryWrapper<BdBom> bomQuery = Wrappers.lambdaQuery();
        bomQuery.eq(BdBom::getParentMaterialId, materialId);
        bomQuery.eq(BdBom::getIsCurrent, 1L);
        bomQuery.eq(BdBom::getStatus, 1L);

        List<BdBom> bomList = bdBomMapper.selectList(bomQuery);
        return (bomList != null && !bomList.isEmpty()) ? bomList.get(0) : null;
    }

    /**
     * 计算子项需求数量（考虑损耗率）
     */
    private BigDecimal calculateChildQuantity(BigDecimal parentQuantity, BigDecimal quantityPerParent,
                                              BigDecimal lossRate) {
        if (parentQuantity == null || quantityPerParent == null) {
            return BigDecimal.ZERO;
        }

        BigDecimal baseQuantity = parentQuantity.multiply(quantityPerParent);

        if (lossRate != null && lossRate.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal lossFactor = BigDecimal.ONE.add(
                    lossRate.divide(BigDecimal.valueOf(100), 6, RoundingMode.HALF_UP)
            );
            return baseQuantity.multiply(lossFactor);
        }

        return baseQuantity;
    }

    /**
     * 合并相同物料的用量
     */
    private List<MaterialRequirementCalcDTO> mergeMaterialRequirements(List<MaterialRequirementCalcDTO> requirements) {
        Map<Long, MaterialRequirementCalcDTO> mergedMap = new HashMap<>();

        for (MaterialRequirementCalcDTO item : requirements) {
            if (mergedMap.containsKey(item.getMaterialId())) {
                // 合并相同物料的用量
                MaterialRequirementCalcDTO existing = mergedMap.get(item.getMaterialId());
                existing.setTotalRequiredQuantity(
                        existing.getTotalRequiredQuantity().add(item.getTotalRequiredQuantity())
                );
                // 更新物料路径，显示多路径
                existing.setMaterialPath(existing.getMaterialPath() + "; " + item.getMaterialPath());
            } else {
                mergedMap.put(item.getMaterialId(), item);
            }
        }

        return new ArrayList<>(mergedMap.values());
    }

    /**
     * 获取物料信息
     */
    private BdMaterial getMaterialInfo(Long materialId) {
        if (materialId == null) {
            return null;
        }
        try {
            return bdMaterialMapper.selectById(materialId);
        } catch (Exception e) {
            log.warn("获取物料信息失败，物料ID: {}", materialId, e);
            return null;
        }
    }

    /**
     * 获取物料分类信息
     */
    private BdMaterialCategory getMaterialCategory(Long categoryId) {
        if (categoryId == null) {
            return null;
        }
        try {
            return bdMaterialCategoryMapper.selectById(categoryId);
        } catch (Exception e) {
            log.warn("获取物料分类信息失败，分类ID: {}", categoryId, e);
            return null;
        }
    }

    /**
     * 查询BOM明细VO列表
     */
    private List<BdBomDetailVo> selectBomDetailVoList(Long bomId) {
        LambdaQueryWrapper<BdBomDetail> lqw = Wrappers.lambdaQuery();
        lqw.eq(BdBomDetail::getBomId, bomId);
        return bdBomDetailMapper.selectVoList(lqw);
    }

    /**
     * 创建物料需求主表记录
     */
    private PrdMaterialRequirement createMaterialRequirement(PrdProductionPlan plan, Long bomId,
                                                             List<MaterialRequirementCalcDTO> requirements) {
        PrdMaterialRequirement mainRequirement = new PrdMaterialRequirement();

        // 设置基本信息
        mainRequirement.setPlanId(plan.getPlanId());
        mainRequirement.setPlanNo(plan.getPlanNo());
        mainRequirement.setBomId(bomId);
        mainRequirement.setRequirementDate(new Date());
        mainRequirement.setStatus("DRAFT");

        // 设置相关人员信息
        mainRequirement.setPlannerId(plan.getPlannerId());
        mainRequirement.setPlannerName(plan.getPlannerName());
        mainRequirement.setDepartmentId(plan.getDepartmentId());
        mainRequirement.setDepartmentName(plan.getDepartmentName());

        // 设置物料信息
        BdBom bom = bdBomMapper.selectById(bomId);
        if (bom != null) {
            mainRequirement.setMaterialId(bom.getParentMaterialId());
            BdMaterial material = getMaterialInfo(bom.getParentMaterialId());
            if (material != null) {
                mainRequirement.setMaterialCode(material.getMaterialCode());
                mainRequirement.setMaterialName(material.getMaterialName());
                mainRequirement.setMaterialSpec(material.getMaterialSpec());
            }
        }

        // 自动生成需求单号（TODO: 迁移 AutoCodeUtil 后恢复正式编码）
        // String requirementNo = autoCodeUtil.genSerialCode("PRD_MATERIAL_MBOM", null);
        String requirementNo = "MR-" + System.currentTimeMillis();
        mainRequirement.setRequirementNo(requirementNo);

        // 计算统计信息
        mainRequirement.setTotalMaterialTypes((long) requirements.size());

        BigDecimal totalRequiredQuantity = requirements.stream()
                .map(MaterialRequirementCalcDTO::getTotalRequiredQuantity)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        mainRequirement.setTotalRequiredQuantity(totalRequiredQuantity);

        return mainRequirement;
    }

    /**
     * 创建物料需求明细项
     */
    private PrdMaterialRequirementDetail createMaterialRequirementDetail(Long requirementId,
                                                                          MaterialRequirementCalcDTO calcDto) {
        PrdMaterialRequirementDetail detail = new PrdMaterialRequirementDetail();

        detail.setRequirementId(requirementId);
        detail.setMaterialId(calcDto.getMaterialId());
        detail.setMaterialCode(calcDto.getMaterialCode());
        detail.setMaterialName(calcDto.getMaterialName());
        detail.setMaterialSpec(calcDto.getMaterialSpec());

        // 设置单位信息 - 从物料主数据获取
        String unit = "";
        BdMaterial material = getMaterialInfo(calcDto.getMaterialId());
        if (material != null && material.getBaseUomId() != null) {
            unit = String.valueOf(material.getBaseUomId());
        }
        detail.setUnit(unit);

        detail.setBomQuantity(calcDto.getQuantityPerParent() != null ?
                calcDto.getQuantityPerParent() : BigDecimal.ZERO);
        detail.setLossRate(calcDto.getLossRate() != null ?
                calcDto.getLossRate() : BigDecimal.ZERO);
        detail.setRequiredQuantity(calcDto.getTotalRequiredQuantity());

        // 设置工艺信息
        detail.setOperationStep(calcDto.getOperationStep());
        detail.setWorkstationId(calcDto.getWorkstationId());
        detail.setWorkstationName(calcDto.getWorkstationName());

        detail.setRequiredDate(new Date());
        detail.setStatus(1L);

        return detail;
    }

    private LambdaQueryWrapper<PrdProductionPlan> buildQueryWrapper(PrdProductionPlanBo bo) {
        LambdaQueryWrapper<PrdProductionPlan> lqw = Wrappers.lambdaQuery();
        lqw.eq(StringUtils.isNotBlank(bo.getPlanNo()), PrdProductionPlan::getPlanNo, bo.getPlanNo());
        lqw.like(StringUtils.isNotBlank(bo.getPlanName()), PrdProductionPlan::getPlanName, bo.getPlanName());
        lqw.eq(StringUtils.isNotBlank(bo.getReferenceNo()), PrdProductionPlan::getReferenceNo, bo.getReferenceNo());
        lqw.eq(StringUtils.isNotBlank(bo.getPlanType()), PrdProductionPlan::getPlanType, bo.getPlanType());
        lqw.eq(StringUtils.isNotBlank(bo.getStatus()), PrdProductionPlan::getStatus, bo.getStatus());
        lqw.eq(bo.getPriority() != null, PrdProductionPlan::getPriority, bo.getPriority());
        lqw.like(StringUtils.isNotBlank(bo.getPlannerName()), PrdProductionPlan::getPlannerName, bo.getPlannerName());
        lqw.like(StringUtils.isNotBlank(bo.getDepartmentName()), PrdProductionPlan::getDepartmentName, bo.getDepartmentName());
        lqw.eq(bo.getIsCurrent() != null, PrdProductionPlan::getIsCurrent, bo.getIsCurrent());
        return lqw;
    }

    private LambdaQueryWrapper<PrdProductionPlan> buildEntityQueryWrapper(PrdProductionPlan entity) {
        LambdaQueryWrapper<PrdProductionPlan> lqw = Wrappers.lambdaQuery();
        lqw.eq(StringUtils.isNotBlank(entity.getPlanNo()), PrdProductionPlan::getPlanNo, entity.getPlanNo());
        lqw.like(StringUtils.isNotBlank(entity.getPlanName()), PrdProductionPlan::getPlanName, entity.getPlanName());
        lqw.eq(StringUtils.isNotBlank(entity.getReferenceNo()), PrdProductionPlan::getReferenceNo, entity.getReferenceNo());
        lqw.eq(StringUtils.isNotBlank(entity.getPlanType()), PrdProductionPlan::getPlanType, entity.getPlanType());
        lqw.eq(StringUtils.isNotBlank(entity.getStatus()), PrdProductionPlan::getStatus, entity.getStatus());
        lqw.eq(entity.getPriority() != null, PrdProductionPlan::getPriority, entity.getPriority());
        lqw.like(StringUtils.isNotBlank(entity.getPlannerName()), PrdProductionPlan::getPlannerName, entity.getPlannerName());
        lqw.like(StringUtils.isNotBlank(entity.getDepartmentName()), PrdProductionPlan::getDepartmentName, entity.getDepartmentName());
        lqw.eq(entity.getIsCurrent() != null, PrdProductionPlan::getIsCurrent, entity.getIsCurrent());
        return lqw;
    }

    /**
     * BOM计算节点类（内部类）
     */
    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class BomCalculationNode {
        private Long materialId;
        private BigDecimal quantity;
        private Long bomId;
        private Integer level;
        private String materialPath;
        private BdBomDetailVo parentDetail;
    }
}
