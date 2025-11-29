package com.nexus.core.prd.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;

import java.util.*;

import com.nexus.common.constant.NexusConstants;
import com.nexus.common.utils.DateUtils;
import com.nexus.core.bd.domain.BdBom;
import com.nexus.core.bd.domain.BdMaterial;
import com.nexus.core.bd.domain.BdMaterialCategory;
import com.nexus.core.bd.domain.vo.BomDetailVO;
import com.nexus.core.bd.service.IBdBomService;
import com.nexus.core.prd.domain.dto.MaterialRequirementCalcDTO;
import com.nexus.core.so.domain.SoSalesOrderLine;
import com.nexus.core.so.service.SoSalesOrderLineService;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.nexus.core.prd.mapper.PrdProductionPlanMapper;
import com.nexus.core.prd.domain.PrdProductionPlan;
import com.nexus.core.prd.service.IPrdProductionPlanService;
import com.nexus.common.exception.ServiceException;
import com.nexus.common.utils.SecurityUtils;
import cn.hutool.core.bean.BeanUtil;
import com.nexus.core.prd.domain.PrdMaterialRequirement;
import com.nexus.core.prd.domain.PrdMaterialRequirementDetail;
import com.nexus.core.prd.service.IPrdMaterialRequirementService;
import com.nexus.system.strategy.AutoCodeUtil;
import org.springframework.transaction.annotation.Transactional;
import java.util.Deque;
import java.util.ArrayDeque;

import java.math.BigDecimal;

import static com.nexus.common.constant.NexusConstants.*;

/**
 * 生产计划Service业务层处理
 *
 * @author nexus
 * @date 2025-11-13
 */
@Service
@Slf4j
public class PrdProductionPlanServiceImpl extends ServiceImpl<PrdProductionPlanMapper, PrdProductionPlan> implements IPrdProductionPlanService {

    @Autowired
    private PrdProductionPlanMapper prdProductionPlanMapper;

    @Autowired
    private SoSalesOrderLineService soSalesOrderLineService;

    @Autowired
    private IBdBomService bdBomService;
    
    @Autowired
    private IPrdMaterialRequirementService prdMaterialRequirementService;
    
    @Autowired
    private AutoCodeUtil autoCodeUtil;

    /**
     * 查询生产计划
     *
     * @param planId 生产计划主键
     * @return 生产计划
     */
    @Override
    public PrdProductionPlan selectPrdProductionPlanByPlanId(Long planId) {
        return prdProductionPlanMapper.selectPrdProductionPlanByPlanId(planId);
    }

    /**
     * 查询生产计划列表
     *
     * @param prdProductionPlan 生产计划
     * @return 生产计划
     */
    @Override
    public List<PrdProductionPlan> selectPrdProductionPlanList(PrdProductionPlan prdProductionPlan) {
        return prdProductionPlanMapper.selectPrdProductionPlanList(prdProductionPlan);
    }

    /**
     * 新增生产计划
     *
     * @param prdProductionPlan 生产计划
     * @return 结果
     */
    @Override
    public int insertPrdProductionPlan(PrdProductionPlan prdProductionPlan) {
                prdProductionPlan.setCreateTime(DateUtils.getNowDate());
            return prdProductionPlanMapper.insertPrdProductionPlan(prdProductionPlan);
    }

    /**
     * 修改生产计划
     *
     * @param prdProductionPlan 生产计划
     * @return 结果
     */
    @Override
    public int updatePrdProductionPlan(PrdProductionPlan prdProductionPlan) {
                prdProductionPlan.setUpdateTime(DateUtils.getNowDate());
        return prdProductionPlanMapper.updatePrdProductionPlan(prdProductionPlan);
    }

    /**
     * 批量删除生产计划
     *
     * @param planIds 需要删除的生产计划主键
     * @return 结果
     */
    @Override
    public int deletePrdProductionPlanByPlanIds(Long[] planIds) {
        return prdProductionPlanMapper.deletePrdProductionPlanByPlanIds(planIds);
    }

    /**
     * 删除生产计划信息
     *
     * @param planId 生产计划主键
     * @return 结果
     */
    @Override
    public int deletePrdProductionPlanByPlanId(Long planId) {
        return prdProductionPlanMapper.deletePrdProductionPlanByPlanId(planId);
    }

    /**
     * 更新生产计划版本
     *
     * @param prdProductionPlan
     * @return
     */
    @Override
    public int updatePrdProductionPlanVersion(PrdProductionPlan prdProductionPlan) {

        // 查询原生产计划
        PrdProductionPlan originalPlan = baseMapper.selectPrdProductionPlanByPlanId(prdProductionPlan.getPlanId());
        if (originalPlan == null) {
            throw new ServiceException("生产计划不存在");
        }

        // 检查状态是否为草稿(DRAFT)
        if (!NexusConstants.STATUS_DRAFT.equals(originalPlan.getStatus())) {
            throw new ServiceException("该生产计划不为草稿，无法保存！");
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
        // 设置更新时间和更新人
        newPlan.setUpdateTime(DateUtils.getNowDate());
        String username = SecurityUtils.getUsername();
        newPlan.setUpdateBy(username);
        // 设置为当前版本
        newPlan.setIsCurrent(NexusConstants.VERSION_INIT);
        // 修改原计划为非当前版本
        originalPlan.setIsCurrent(NexusConstants.VERSION_NOT_CURRENT);
        //updateById(originalPlan);
        baseMapper.updatePrdProductionPlan(originalPlan);

        // 插入新版本的生产计划
        return prdProductionPlanMapper.insertPrdProductionPlan(newPlan);
    }

    /**
     * BOM计算 - 根据生产计划中的订单行物料计算BOM树
     *
     * @param planId 生产计划ID
     * @return BOM树结构列表
     */
    @Override
    public List<BomDetailVO> calculateBomTree(Long planId) {
        if (planId == null) {
            throw new ServiceException("生产计划ID不能为空");
        }

        // 查询生产计划
        PrdProductionPlan plan = prdProductionPlanMapper.selectPrdProductionPlanByPlanId(planId);
        if (plan == null) {
            throw new ServiceException("生产计划不存在");
        }

        // 检查是否关联订单行
        if (plan.getOrderLineId() == null) {
            throw new ServiceException("该生产计划未关联订单行信息");
        }

        // 查询订单行信息
        SoSalesOrderLine orderLine = soSalesOrderLineService.getById(plan.getOrderLineId());
        if (orderLine == null) {
            throw new ServiceException("订单行信息不存在");
        }

        // 检查物料信息
        if (orderLine.getMaterialId() == null) {
            throw new ServiceException("订单行未指定物料信息");
        }

        // 查询该物料的当前有效BOM
        BdBom bomQuery = new BdBom();
        bomQuery.setBomId(plan.getBomId());
        bomQuery.setIsCurrent(1L);  // 当前版本
        bomQuery.setStatus(1L);      // 启用状态
        List<BdBom> bomList = bdBomService.selectBdBomList(bomQuery);

        if (bomList == null || bomList.isEmpty()) {
            throw new ServiceException("未找到该物料的有效BOM信息，物料编码: " + orderLine.getMaterialCode());
        }

        // 获取第一个BOM（当前版本）
        BdBom bom = bomList.get(0);

        // 查询BOM明细
        List<BomDetailVO> bomDetails = bdBomService.selectBomDetailList(bom.getBomId());

        if (bomDetails == null || bomDetails.isEmpty()) {
            throw new ServiceException("BOM明细为空");
        }

        return bomDetails;
    }

    /**
     * 根据生产计划ID生成物料需求清单及明细（按节点拆分BOM树）
     * 规则：每个有BOM的节点生成一条主表，其底层物料作为子表；子节点BOM递归拆分
     *
     * @param planId 生产计划ID
     * @return 物料需求清单列表
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<PrdMaterialRequirement> generateMaterialRequirementsWithDetails(Long planId) {
        // 1. 查询生产计划
        PrdProductionPlan plan = prdProductionPlanMapper.selectPrdProductionPlanByPlanId(planId);
        if (plan == null) {
            throw new ServiceException("生产计划不存在");
        }

        // 2. 检查是否有关联订单行
        if (plan.getOrderLineId() == null) {
            throw new ServiceException("该生产计划未关联订单行信息");
        }

        // 3. 查询订单行信息
        SoSalesOrderLine orderLine = soSalesOrderLineService.getById(plan.getOrderLineId());
        if (orderLine == null) {
            throw new ServiceException("订单行信息不存在");
        }

        // 4. 按节点拆分生成：顶层与每个子节点BOM分别生成主表与底层物料子表
        List<PrdMaterialRequirement> createdMainRequirements = processBomNode(
                orderLine.getMaterialId(),
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
    private List<PrdMaterialRequirement> processBomNode(Long materialId, Long specifiedBomId, BigDecimal parentQuantity, PrdProductionPlan plan) {
        List<PrdMaterialRequirement> result = new ArrayList<>();

        // 获取当前节点的BOM（顶层使用指定bomId，子节点优先查当前有效BOM）
        BdBom currentBom = getCurrentBom(materialId, specifiedBomId);
        if (currentBom == null) {
            // 当前为底层物料（无BOM），不在此处生成主表，交由父节点主表收集
            return result;
        }

        // 查询当前BOM的明细
        List<BomDetailVO> bomDetails = bdBomService.selectBomDetailList(currentBom.getBomId());
        if (bomDetails == null || bomDetails.isEmpty()) {
            // 无明细则不生成主表
            return result;
        }

        // 当前节点用于主表的底层物料清单（仅收集无BOM的子项；有BOM的子项作为子节点递归处理）
        List<MaterialRequirementCalcDTO> leafRequirements = new ArrayList<>();
        // 需要递归处理的子节点（具备BOM）
        List<BomCalculationNode> childAssemblyNodes = new ArrayList<>();

        for (BomDetailVO detail : bomDetails) {
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
                dto.setOperationStep(Long.valueOf(detail.getOperationStep()));
                dto.setWorkstationId(detail.getWorkstationId());
                dto.setWorkstationName(detail.getWorkstationName());
                // 尝试补充物料基础信息（当前实现返回null，按现状允许为空）
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
            BdBom specifiedBom = bdBomService.getById(specifiedBomId);
            if (specifiedBom != null && specifiedBom.getIsCurrent() == 1L && specifiedBom.getStatus() == 1L) {
                return specifiedBom;
            }
        }

        // 否则查询该物料的当前有效BOM
        BdBom bomQuery = new BdBom();
        bomQuery.setParentMaterialId(materialId);
        bomQuery.setIsCurrent(1L);
        bomQuery.setStatus(1L);

        List<BdBom> bomList = bdBomService.selectBdBomList(bomQuery);
        return (bomList != null && !bomList.isEmpty()) ? bomList.get(0) : null;
    }

    /**
     * 计算子项需求数量（考虑损耗率）
     */
    private BigDecimal calculateChildQuantity(BigDecimal parentQuantity, BigDecimal quantityPerParent, BigDecimal lossRate) {
        if (parentQuantity == null || quantityPerParent == null) {
            return BigDecimal.ZERO;
        }

        BigDecimal baseQuantity = parentQuantity.multiply(quantityPerParent);

        if (lossRate != null && lossRate.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal lossFactor = BigDecimal.ONE.add(
                    lossRate.divide(BigDecimal.valueOf(100), 6, BigDecimal.ROUND_HALF_UP)
            );
            return baseQuantity.multiply(lossFactor);
        }

        return baseQuantity;
    }

    /**
     * 创建计算DTO
     */
    private MaterialRequirementCalcDTO createCalcDTO(BomCalculationNode node) {
        MaterialRequirementCalcDTO dto = new MaterialRequirementCalcDTO();
        dto.setMaterialId(node.getMaterialId());
        dto.setTotalRequiredQuantity(node.getQuantity());
        dto.setBomLevel(node.getLevel());
        dto.setMaterialPath(node.getMaterialPath());

        // 设置物料基本信息
        BdMaterial material = getMaterialInfo(node.getMaterialId());
        if (material != null) {
            dto.setMaterialCode(material.getMaterialCode());
            dto.setMaterialName(material.getMaterialName());
            dto.setMaterialSpec(material.getMaterialSpec());

            // 设置分类信息
            BdMaterialCategory category = getMaterialCategory(material.getCategoryId());
            if (category != null) {
                dto.setCategoryName(category.getCategoryName());
                dto.setCategoryPath(category.getPathNames());
            }
        }

        // 设置BOM明细信息
        if (node.getParentDetail() != null) {
            dto.setQuantityPerParent(node.getParentDetail().getQuantityPerParent());
            dto.setLossRate(node.getParentDetail().getLossRate());
            dto.setOperationStep(Long.valueOf(node.getParentDetail().getOperationStep()));
            dto.setWorkstationId(node.getParentDetail().getWorkstationId());
            dto.setWorkstationName(node.getParentDetail().getWorkstationName());
        }

        return dto;
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
        // 这里需要调用物料服务的查询方法
        // 假设有 bdMaterialService.getById(materialId)
        // 暂时返回null，您需要根据实际情况实现
        return null;
    }

    /**
     * 获取物料分类信息
     */
    private BdMaterialCategory getMaterialCategory(Long categoryId) {
        // 这里需要调用分类服务的查询方法
        // 暂时返回null，您需要根据实际情况实现
        return null;
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
        private BomDetailVO parentDetail; // 父级BOM明细信息
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
        mainRequirement.setStatus("DRAFT"); // 使用字符串状态

        // 设置相关人员信息
        mainRequirement.setPlannerId(plan.getPlannerId());
        mainRequirement.setPlannerName(plan.getPlannerName());
        mainRequirement.setDepartmentId(plan.getDepartmentId());
        mainRequirement.setDepartmentName(plan.getDepartmentName());

        // 自动生成需求单号
        String requirementNo = autoCodeUtil.genSerialCode("PRD_MATERIAL_MBOM", null);
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

        // 设置单位信息 - 需要根据实际情况获取
        detail.setUnit(""); // 这里需要从物料主数据获取单位

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
        detail.setStatus(1L); // 启用状态

        return detail;
    }
}