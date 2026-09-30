package com.nexus.erp.prd.service.impl;

import com.nexus.erp.prd.util.BomExplosionCalculator;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.nexus.common.core.utils.MapstructUtils;
import com.nexus.common.core.utils.StringUtils;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.common.satoken.utils.LoginHelper;
import com.nexus.erp.bd.domain.BdBom;
import com.nexus.erp.bd.domain.BdMaterial;
import com.nexus.erp.bd.domain.BdMaterialCategory;
import com.nexus.erp.bd.domain.BdBomDetail;
import com.nexus.erp.bd.domain.vo.BdBomDetailVo;
import com.nexus.erp.bd.mapper.BdBomDetailMapper;
import com.nexus.erp.bd.mapper.BdBomMapper;
import com.nexus.erp.bd.mapper.BdMaterialCategoryMapper;
import com.nexus.erp.bd.mapper.BdMaterialMapper;
import com.nexus.erp.prd.domain.PrdMaterialRequirement;
import com.nexus.erp.prd.domain.PrdMaterialRequirementDetail;
import com.nexus.erp.prd.domain.PrdProductionPlan;
import com.nexus.erp.prd.domain.PrdWorkOrder;
import com.nexus.erp.prd.domain.bo.MaterialRequirementCalcDTO;
import com.nexus.erp.prd.domain.bo.PrdProductionPlanBo;
import com.nexus.erp.prd.domain.vo.PrdProductionPlanVo;
import com.nexus.erp.prd.mapper.PrdProductionPlanMapper;
import com.nexus.erp.prd.mapper.PrdWorkOrderMapper;
import com.nexus.erp.prd.service.IPrdMaterialRequirementService;
import com.nexus.erp.prd.service.IPrdProductionPlanService;
import com.nexus.erp.prd.service.IPrdWorkOrderService;
import com.nexus.erp.so.domain.SoSalesOrderHeader;
import com.nexus.erp.so.domain.SoSalesOrderLine;
import com.nexus.erp.so.domain.vo.SoSalesOrderLineVo;
import com.nexus.erp.so.mapper.SoSalesOrderHeaderMapper;
import com.nexus.erp.so.mapper.SoSalesOrderLineMapper;
import com.nexus.erp.so.service.ISoSalesOrderLineService;
import com.nexus.system.strategy.AutoCodeUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.dynamic.datasource.annotation.DS;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;

/**
 * 生产计划服务业务层处理
 *
 * @author nexus
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
    private final ISoSalesOrderLineService soSalesOrderLineService;
    /** 销售订单行/头映射接口：生成工单时解析客户信息（直接调用映射接口，避免服务层循环依赖） */
    private final SoSalesOrderLineMapper soSalesOrderLineMapper;
    private final SoSalesOrderHeaderMapper soSalesOrderHeaderMapper;
    private final AutoCodeUtil autoCodeUtil;
    private final PrdWorkOrderMapper prdWorkOrderMapper;
    private final IPrdWorkOrderService prdWorkOrderService;

    /** 状态常量（从 NexusConstants 迁移） */
    private static final String STATUS_DRAFT = "DRAFT";
    private static final String STATUS_CONFIRMED = "CONFIRMED";
    private static final String STATUS_RELEASED = "RELEASED";
    private static final Long VERSION_INIT = 1L;
    private static final Long VERSION_NOT_CURRENT = 0L;
    private static final ZoneId BOM_BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");

    /**
     * 分页查询生产计划列表
     *
     * @param bo        查询参数，包含计划编号、名称、状态等筛选条件
     * @param pageQuery 分页参数，包含页码、每页条数及排序信息
     * @return 生产计划分页列表
     */
    @Override
    public TableDataInfo<PrdProductionPlanVo> selectPageList(PrdProductionPlanBo bo, PageQuery pageQuery) {
        LambdaQueryWrapper<PrdProductionPlan> lqw = buildQueryWrapper(bo);
        Page<PrdProductionPlanVo> page = baseMapper.selectVoPage(pageQuery.build(), lqw);
        return TableDataInfo.build(page);
    }

    /**
     * 查询生产计划列表（不分页）
     *
     * @param bo 查询参数
     * @return 生产计划列表
     */
    @Override
    public List<PrdProductionPlanVo> selectList(PrdProductionPlanBo bo) {
        return baseMapper.selectVoList(buildQueryWrapper(bo));
    }

    /**
     * 根据 ID 查询生产计划详情
     *
     * @param id 生产计划 ID
     * @return 生产计划详情
     */
    @Override
    public PrdProductionPlanVo selectById(Long id) {
        return baseMapper.selectVoById(id);
    }

    /**
     * 根据生产计划 ID 查询生产计划实体
     *
     * @param planId 生产计划 ID
     * @return 生产计划实体
     */
    @Override
    public PrdProductionPlan selectPrdProductionPlanByPlanId(Long planId) {
        return baseMapper.selectById(planId);
    }

    /**
     * 查询生产计划列表（按实体条件）
     *
     * @param prdProductionPlan 生产计划查询实体
     * @return 生产计划列表
     */
    @Override
    public List<PrdProductionPlan> selectPrdProductionPlanList(PrdProductionPlan prdProductionPlan) {
        LambdaQueryWrapper<PrdProductionPlan> lqw = buildEntityQueryWrapper(prdProductionPlan);
        return baseMapper.selectList(lqw);
    }

    /**
     * 新增生产计划
     *
     * @param prdProductionPlan 生产计划实体
     * @return 影响的行数
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int insertPrdProductionPlan(PrdProductionPlan prdProductionPlan) {
        return baseMapper.insert(prdProductionPlan);
    }

    /**
     * 新增生产计划（通过 BO）
     * <p>
     * 自动生成生产计划编号，默认版本号为 1，计划状态设为 DRAFT（草稿）。
     *
     * @param bo 生产计划业务对象
     * @return 影响的行数
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int insertByBo(PrdProductionPlanBo bo) {
        PrdProductionPlan entity = MapstructUtils.convert(bo, PrdProductionPlan.class);
        if (StringUtils.isBlank(entity.getPlanNo())) {
            entity.setPlanNo(autoCodeUtil.genSerialCode("PRD_PLAN_CODE", null));
        }
        return baseMapper.insert(entity);
    }

    /**
     * 修改生产计划（通过 BO）
     * <p>
     * 仅 DRAFT 状态的生产计划可编辑。
     *
     * @param bo 生产计划业务对象
     * @return 影响的行数
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updateByBo(PrdProductionPlanBo bo) {
        PrdProductionPlan entity = MapstructUtils.convert(bo, PrdProductionPlan.class);
        return baseMapper.updateById(entity);
    }

    /**
     * 修改生产计划
     *
     * @param prdProductionPlan 生产计划实体
     * @return 影响的行数
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updatePrdProductionPlan(PrdProductionPlan prdProductionPlan) {
        return baseMapper.updateById(prdProductionPlan);
    }

    /**
     * 批量删除生产计划
     *
     * @param ids 要删除的生产计划 ID 数组
     * @return 影响的行数
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteByIds(Long[] ids) {
        return baseMapper.deleteBatchIds(List.of(ids));
    }

    /**
     * 根据生产计划 ID 数组批量删除生产计划（含明细）
     *
     * @param planIds 生产计划 ID 数组
     * @return 影响的行数
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deletePrdProductionPlanByPlanIds(Long[] planIds) {
        return baseMapper.deleteBatchIds(List.of(planIds));
    }

    /**
     * 根据生产计划 ID 删除生产计划（含明细）
     *
     * @param planId 生产计划 ID
     * @return 影响的行数
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deletePrdProductionPlanByPlanId(Long planId) {
        return baseMapper.deleteById(planId);
    }

    /**
     * 更新生产计划版本
     */
    /**
     * 更新生产计划版本
     * <p>
     * 仅草稿状态的生产计划可更新版本，更新后版本号递增。
     *
     * @param prdProductionPlan 包含版本更新信息的生产计划实体
     * @return 影响的行数
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updatePrdProductionPlanVersion(PrdProductionPlan prdProductionPlan) {
        /** 查询原生产计划 */
        PrdProductionPlan originalPlan = baseMapper.selectById(prdProductionPlan.getPlanId());
        if (originalPlan == null) {
            throw new RuntimeException("生产计划不存在");
        }

        /** 检查状态是否为草稿(DRAFT) */
        if (!STATUS_DRAFT.equals(originalPlan.getStatus())) {
            throw new RuntimeException("该生产计划不为草稿，无法保存！");
        }

        /** 直接在原记录上更新全部表单字段，不新增记录 */
        Long userId = LoginHelper.getUserId();
        BeanUtil.copyProperties(prdProductionPlan, originalPlan);
        originalPlan.setPlanId(prdProductionPlan.getPlanId());
        originalPlan.setUpdateBy(userId);
        originalPlan.setVersion((originalPlan.getVersion() == null ? 0L : originalPlan.getVersion()) + 1L);
        originalPlan.setIsCurrent(VERSION_INIT);

        return baseMapper.updateById(originalPlan);
    }

    /**
     * 确认生产计划
     * <p>
     * 仅草稿（DRAFT）状态可确认，确认后状态变为已确认（CONFIRMED）并记录确认时间。
     *
     * @param planId 生产计划 ID
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void confirm(Long planId) {
        PrdProductionPlan plan = baseMapper.selectById(planId);
        if (plan == null) {
            throw new RuntimeException("生产计划不存在");
        }
        if (!STATUS_DRAFT.equals(plan.getStatus())) {
            throw new RuntimeException("仅草稿状态的生产计划可确认，当前状态：" + plan.getStatus());
        }
        plan.setStatus(STATUS_CONFIRMED);
        plan.setConfirmedTime(new Date());
        baseMapper.updateById(plan);
    }

    /**
     * 下达生产计划
     * <p>
     * 仅已确认（CONFIRMED）状态可下达，下达后状态变为已下达（RELEASED）并记录下达时间。
     *
     * @param planId 生产计划 ID
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void release(Long planId) {
        PrdProductionPlan plan = baseMapper.selectById(planId);
        if (plan == null) {
            throw new RuntimeException("生产计划不存在");
        }
        if (!STATUS_CONFIRMED.equals(plan.getStatus())) {
            throw new RuntimeException("仅已确认状态的生产计划可下达，当前状态：" + plan.getStatus());
        }
        plan.setStatus(STATUS_RELEASED);
        plan.setReleasedTime(new Date());
        baseMapper.updateById(plan);
    }

    /**
     * BOM计算 - 根据生产计划中的订单行物料计算BOM树
     */
    /**
     * 根据生产计划计算 BOM 树
     * <p>
     * 查询生产计划关联订单行的物料对应的有效 BOM，并返回 BOM 明细树。
     *
     * @param planId 生产计划 ID
     * @return BOM 详情树节点列表
     */
    @Override
    public List<BdBomDetailVo> calculateBomTree(Long planId) {
        if (planId == null) {
            throw new RuntimeException("生产计划ID不能为空");
        }

        /** 查询生产计划 */
        PrdProductionPlan plan = baseMapper.selectById(planId);
        if (plan == null) {
            throw new RuntimeException("生产计划不存在");
        }

        /** 检查是否关联订单行 */
        if (plan.getOrderLineId() == null) {
            throw new RuntimeException("该生产计划未关联订单行信息");
        }

        SoSalesOrderLineVo orderLine = soSalesOrderLineService.selectById(plan.getOrderLineId());
        if (orderLine == null) {
            throw new RuntimeException("订单行信息不存在");
        }
        if (orderLine.getMaterialId() == null) {
            throw new RuntimeException("订单行未指定物料信息");
        }

        /** 查询该物料的当前有效BOM */
        LambdaQueryWrapper<BdBom> bomQuery = Wrappers.lambdaQuery();
        bomQuery.eq(BdBom::getBomId, plan.getBomId());
        bomQuery.eq(BdBom::getIsCurrent, 1L);
        bomQuery.eq(BdBom::getStatus, 1L);
        List<BdBom> bomList = bdBomMapper.selectList(bomQuery);

        if (bomList == null || bomList.isEmpty()) {
            throw new RuntimeException("未找到该物料的有效BOM信息");
        }

        /** 获取第一个BOM（当前版本） */
        BdBom bom = bomList.get(0);

        /** 查询BOM明细 */
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
    /**
     * 根据生产计划 ID 生成物料需求清单及明细
     * <p>
     * 按节点拆分 BOM 树，每个有 BOM 的节点生成一条主表，其底层物料作为子表；
     * 子节点 BOM 递归拆分。
     *
     * @param planId 生产计划 ID
     * @return 生成的物料需求主表记录列表
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<PrdMaterialRequirement> generateMaterialRequirementsWithDetails(Long planId) {
        /** 1. 查询生产计划 */
        PrdProductionPlan plan = baseMapper.selectById(planId);
        if (plan == null) {
            throw new RuntimeException("生产计划不存在");
        }

        /** 2. 物料需求生成使用生产计划选定的 BOM；销售订单驱动的计划还须核对成品物料。 */
        if (plan.getBomId() == null) {
            throw new RuntimeException("该生产计划未选择BOM，请先在表单选择BOM后再计算");
        }

        /** 仅使用计划明确选中的 BOM，不能把 BOM 主键当作成品物料 ID，也不能回退到其他版本。 */
        BdBom rootBom = bdBomMapper.selectById(plan.getBomId());
        /** BOM 日期为 SQL DATE，以 Asia/Shanghai 业务日为准，失效日当天仍有效。 */
        LocalDate today = LocalDate.now(BOM_BUSINESS_ZONE);
        if (rootBom == null || !Objects.equals(rootBom.getBomId(), plan.getBomId())
            || !Long.valueOf(1L).equals(rootBom.getIsCurrent()) || !Long.valueOf(1L).equals(rootBom.getStatus())
            || (rootBom.getEffectiveDate() != null && bomBusinessDate(rootBom.getEffectiveDate()).isAfter(today))
            || (rootBom.getExpiryDate() != null && bomBusinessDate(rootBom.getExpiryDate()).isBefore(today))) {
            throw new RuntimeException("所选BOM无效或不在有效期");
        }
        if (rootBom.getParentMaterialId() == null || rootBom.getParentMaterialId() <= 0) {
            throw new RuntimeException("所选BOM缺少有效的父项物料");
        }
        if (StringUtils.isBlank(plan.getTenantId()) || !Objects.equals(plan.getTenantId(), rootBom.getTenantId())) {
            throw new RuntimeException("生产计划与所选BOM租户不一致");
        }
        if (StringUtils.isNotBlank(plan.getBomVersion()) && !Objects.equals(plan.getBomVersion(), rootBom.getBomVersion())) {
            throw new RuntimeException("所选BOM版本与生产计划不一致");
        }
        if (plan.getOrderLineId() != null) {
            SoSalesOrderLine orderLine = soSalesOrderLineMapper.selectById(plan.getOrderLineId());
            if (orderLine == null) {
                throw new RuntimeException("生产计划关联的销售订单行不存在");
            }
            if (!Objects.equals(plan.getTenantId(), orderLine.getTenantId())) {
                throw new RuntimeException("生产计划与销售订单行租户不一致");
            }
            if (!Objects.equals(rootBom.getParentMaterialId(), orderLine.getMaterialId())) {
                throw new RuntimeException("所选BOM父项物料与销售订单行物料不一致");
            }
        }

        /** 幂等返回前也必须核验历史根需求，避免错误物料被继续用于生成工单。 */
        PrdMaterialRequirement existingProbe = new PrdMaterialRequirement();
        existingProbe.setPlanId(planId);
        List<PrdMaterialRequirement> existing = prdMaterialRequirementService.selectPrdMaterialRequirementList(existingProbe);
        if (existing != null && !existing.isEmpty()) {
            List<PrdMaterialRequirement> roots = existing.stream()
                .filter(requirement -> Objects.equals(requirement.getPlanId(), planId)
                    && requirement.getParentRequirementId() == null
                    && Objects.equals(requirement.getBomId(), rootBom.getBomId()))
                .toList();
            if (roots.size() != 1) {
                throw new RuntimeException("既有物料需求缺少唯一可信根（可能为虚拟件跳层），请人工审计，不得自动覆盖");
            }
            if (!Objects.equals(roots.get(0).getMaterialId(), rootBom.getParentMaterialId())
                || !Objects.equals(roots.get(0).getTenantId(), plan.getTenantId())) {
                throw new RuntimeException("既有物料需求根物料或租户与所选BOM不一致，请人工审计，不得自动覆盖");
            }
            log.info("该生产计划已生成过物料需求, 核验根需求后返回既有记录: planId={}, count={}", planId, existing.size());
            return existing;
        }

        /** 3. 按节点拆分生成：顶层与每个子节点BOM分别生成主表与底层物料子表 */
        List<PrdMaterialRequirement> createdMainRequirements = processBomNode(
                rootBom.getParentMaterialId(),
                rootBom.getBomId(),
                plan.getTotalQuantity(),
                plan,
                null,
                /** 根节点需求日期 = 计划完成日期, 子层按各物料提前期倒排 */
                plan.getEndDate(),
                rootBom
        );

        log.info("成功生成物料需求，共生成 {} 条主表记录", createdMainRequirements.size());

        /** 4. 返回所有生成的主表记录 */
        return createdMainRequirements;
    }

    /** SQL DATE 保留库里的日历日；其他 Date 按业务时区解释，不按当前时分秒判断。 */
    private static LocalDate bomBusinessDate(Date date) {
        if (date instanceof java.sql.Date sqlDate) {
            return sqlDate.toLocalDate();
        }
        return date.toInstant().atZone(BOM_BUSINESS_ZONE).toLocalDate();
    }

    /**
     * 递归处理单个BOM节点：
     * - 当前节点（有BOM）生成一条主表，仅包含其BOM树下的底层物料作为子表
     * - 对每个具备BOM的子节点，递归生成独立的主表与其底层物料子表
     * - 虚拟件节点跳层：虚拟件本身不生成主表（不入库不产生需求），
     *   其子BOM递归产生的主表直接挂到祖父节点（parentRequirementId 沿用）
     * - 子项需求 sourceType 优先取子项物料本身（统一编码后），未配置时回退父BOM
     */
    private List<PrdMaterialRequirement> processBomNode(Long materialId, Long specifiedBomId,
                                                         BigDecimal parentQuantity, PrdProductionPlan plan,
                                                         Long parentRequirementId,
                                                         Date parentRequiredDate, BdBom selectedRootBom) {
        List<PrdMaterialRequirement> result = new ArrayList<>();

        /** 获取当前节点的BOM（顶层使用指定bomId，子节点优先查当前有效BOM） */
        BdBom currentBom = selectedRootBom != null ? selectedRootBom : getCurrentBom(materialId, specifiedBomId);
        if (currentBom == null) {
            /** 当前为底层物料（无BOM），不在此处生成主表，交由父节点主表收集 */
            return result;
        }

        /** 查询当前BOM的明细 */
        List<BdBomDetailVo> bomDetails = selectBomDetailVoList(currentBom.getBomId());
        if (bomDetails == null || bomDetails.isEmpty()) {
            /** 无明细则不生成主表 */
            return result;
        }

        /** 判断当前节点物料是否虚拟件：虚拟件跳层，本身不产生主表 */
        BdMaterial currentMaterial = getMaterialInfo(materialId);
        boolean isCurrentPhantom = currentMaterial != null
            && Integer.valueOf(1).equals(currentMaterial.getIsPhantom());

        /** 获取BOM的来源类型：PROCUREMENT-采购（不递归），PROCESS-加工（递归展开子BOM） */
        String sourceType = currentBom.getSourceType();
        boolean isProcurement = "PROCUREMENT".equals(sourceType);

        /** 当前节点用于主表的底层物料清单 */
        List<MaterialRequirementCalcDTO> leafRequirements = new ArrayList<>();
        /** 需要递归处理的子节点（具备BOM，且非采购类型） */
        List<BomCalculationNode> childAssemblyNodes = new ArrayList<>();

        for (BdBomDetailVo detail : bomDetails) {
            /** 计算该子项在当前节点需求数量（含损耗率） */
            BigDecimal childRequiredQuantity = calculateChildQuantity(
                    parentQuantity,
                    detail.getQuantityPerParent(),
                    detail.getLossRate()
            );

            /** 提前获取子项物料信息：用于虚拟件判断与 sourceType 分流 */
            BdMaterial childMaterial = getMaterialInfo(detail.getChildMaterialId());
            boolean isChildPhantom = childMaterial != null
                && Integer.valueOf(1).equals(childMaterial.getIsPhantom());
            /** 子项物料的 sourceType（优先），未配置时回退父BOM */
            String childSourceType = (childMaterial != null && childMaterial.getSourceType() != null)
                ? childMaterial.getSourceType() : sourceType;

            /**
             * 采购类型BOM：所有明细均作为底层物料，不递归展开子BOM
             * 加工类型BOM：判断子项是否有子BOM来决定是否递归
             * 虚拟件子项：本身不计入 leafRequirements（跳层），但其子BOM仍需递归展开
             */
            BdBom childBom = isProcurement ? null : getCurrentBom(detail.getChildMaterialId(), null);
            if (childBom != null) {
                /** 子项有子BOM：作为子节点递归（含虚拟件，虚拟件主表会在其入口处被跳过） */
                BomCalculationNode childNode = new BomCalculationNode();
                childNode.setMaterialId(detail.getChildMaterialId());
                childNode.setQuantity(childRequiredQuantity);
                childNode.setBomId(childBom.getBomId());
                childNode.setParentDetail(detail);
                childAssemblyNodes.add(childNode);
            } else if (!isChildPhantom) {
                /** 非虚拟件底层物料：进入当前主表的子表 */
                MaterialRequirementCalcDTO dto = new MaterialRequirementCalcDTO();
                dto.setMaterialId(detail.getChildMaterialId());
                dto.setTotalRequiredQuantity(childRequiredQuantity);
                dto.setQuantityPerParent(detail.getQuantityPerParent());
                dto.setLossRate(detail.getLossRate());
                dto.setOperationStep(detail.getOperationStep() != null ?
                        Long.valueOf(detail.getOperationStep()) : null);
                dto.setLeadTime(detail.getLeadTime() != null ? Long.valueOf(detail.getLeadTime()) : null);
                /** sourceType 优先取子项物料的，物料未配置时回退父BOM */
                dto.setSourceType(childSourceType);
                /** 补充物料基础信息 */
                if (childMaterial != null) {
                    dto.setMaterialCode(childMaterial.getMaterialCode());
                    dto.setMaterialName(childMaterial.getMaterialName());
                    dto.setMaterialSpec(childMaterial.getMaterialSpec());
                    BdMaterialCategory category = getMaterialCategory(childMaterial.getCategoryId());
                    if (category != null) {
                        dto.setCategoryName(category.getCategoryName());
                        dto.setCategoryPath(category.getPathNames());
                    }
                } else {
                    /** 如果获取不到物料信息，至少设置从BOM明细中可以获得的基本信息 */
                    dto.setMaterialCode(detail.getChildMaterialCode());
                    dto.setMaterialName(detail.getChildMaterialName());
                }
                leafRequirements.add(dto);
            }
            /** 虚拟件且无子BOM：跳过（虚拟件不应进入需求，正常情况下虚拟件必有子BOM） */
        }

        /** 合并相同底层物料用量，仅对当前节点的底层物料合并 */
        List<MaterialRequirementCalcDTO> mergedLeaves = mergeMaterialRequirements(leafRequirements);

        /**
         * 当前节点主表与子表生成（若存在底层物料，且当前节点非虚拟件）
         * 虚拟件本身不生成主表（跳层），其下层需求通过递归子BOM生成并挂到祖父
         */
        PrdMaterialRequirement mainRequirement = null;
        if (!isCurrentPhantom && !mergedLeaves.isEmpty()) {
            mainRequirement = createMaterialRequirement(plan, currentBom.getBomId(), mergedLeaves, parentRequirementId);
            prdMaterialRequirementService.insertPrdMaterialRequirement(mainRequirement);

            List<PrdMaterialRequirementDetail> detailList = new ArrayList<>();
            for (MaterialRequirementCalcDTO calcDto : mergedLeaves) {
                /** 底层物料需求日期按当前节点需求日期与物料提前期倒排(见 createMaterialRequirementDetail) */
                PrdMaterialRequirementDetail detailRecord = createMaterialRequirementDetail(
                        mainRequirement.getRequirementId(),
                        calcDto,
                        parentRequiredDate
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

        /**
         * 递归处理具备BOM的子节点：每个子节点生成独立的主表与其底层物料子表
         * 虚拟件节点：mainRequirement 为 null，子节点 parentRequirementId 沿用传入值（跳层挂到祖父）
         */
        Long nextParentRequirementId = mainRequirement != null
            ? mainRequirement.getRequirementId() : parentRequirementId;
        for (BomCalculationNode childNode : childAssemblyNodes) {
            /** 子节点需求日期倒排: 父节点需求日期 - 子项提前期(小时), 保证子项产出不晚于父项需求 */
            Date childRequiredDate = parentRequiredDate;
            if (parentRequiredDate != null && childNode.getParentDetail() != null
                && childNode.getParentDetail().getLeadTime() != null
                && childNode.getParentDetail().getLeadTime() > 0) {
                childRequiredDate = Date.from(parentRequiredDate.toInstant()
                    .minusSeconds(childNode.getParentDetail().getLeadTime() * 3600L));
            }
            result.addAll(processBomNode(
                    childNode.getMaterialId(),
                    childNode.getBomId(),
                    childNode.getQuantity(),
                    plan,
                    nextParentRequirementId,
                    childRequiredDate,
                    null
            ));
        }

        return result;
    }

    /**
     * 获取当前物料的BOM
     */
    /**
     * 从物料需求单生成生产工单
     * <p>
     * 选中物料需求记录，一键生成对应的生产工单（状态=CREATED）。
     *
     * @param planId         生产计划 ID
     * @param requirementIds 物料需求单 ID 列表
     * @return 生成的工单数量
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Integer generateWorkOrders(Long planId, List<Long> requirementIds) {
        if (planId == null) {
            throw new RuntimeException("生产计划ID不能为空");
        }
        if (requirementIds == null || requirementIds.isEmpty()) {
            throw new RuntimeException("请选择至少一个物料需求单");
        }

        PrdProductionPlan plan = baseMapper.selectById(planId);
        if (plan == null) {
            throw new RuntimeException("生产计划不存在");
        }

        /**
         * 解析销售订单客户信息(计划来源行 -> SO行 -> SO头), 落到工单并经MQ透传给MES匹配客户定制化发布包;
         * 非销售订单驱动的计划 orderLineId 为空, 客户信息保持空(MES走标准工艺路线不覆写)
         */
        Long soClientId = null;
        String soClientName = null;
        String soCustomerProductCode = null;
        if (plan.getOrderLineId() != null) {
            SoSalesOrderLine soLine = soSalesOrderLineMapper.selectById(plan.getOrderLineId());
            if (soLine != null) {
                soCustomerProductCode = soLine.getCustomerProductCode();
                if (soLine.getSoId() != null) {
                    SoSalesOrderHeader soHeader = soSalesOrderHeaderMapper.selectById(soLine.getSoId());
                    if (soHeader != null) {
                        soClientId = soHeader.getClientId();
                        soClientName = soHeader.getClientName();
                    }
                }
            }
        }

        /** 先校验整批来源再消耗编号/写工单，避免后项被拒绝时前项已生成或编号已提交。 */
        Map<Long, PrdMaterialRequirement> validatedRequirements = new LinkedHashMap<>();
        BdBom rootBom = null;
        for (Long requirementId : requirementIds) {
            PrdMaterialRequirement requirement = prdMaterialRequirementService
                    .selectPrdMaterialRequirementByRequirementId(requirementId);
            if (requirement == null) {
                log.warn("物料需求单不存在, requirementId={}", requirementId);
                continue;
            }
            if (!Objects.equals(plan.getPlanId(), requirement.getPlanId())) {
                throw new RuntimeException("所选物料需求单不属于当前生产计划，禁止跨计划生成工单");
            }
            if (StringUtils.isBlank(plan.getTenantId()) || !Objects.equals(plan.getTenantId(), requirement.getTenantId())) {
                throw new RuntimeException("所选物料需求单与生产计划租户不一致，禁止生成工单");
            }
            if (requirement.getMaterialId() == null) {
                log.warn("物料需求单未关联物料, requirementId={}", requirementId);
                continue;
            }
            if (requirement.getParentRequirementId() != null || plan.getBomId() == null
                || !Objects.equals(plan.getBomId(), requirement.getBomId())) {
                throw new RuntimeException("非根子装配缺少可信的父项需求数量，禁止使用子物料合计生成工单，请人工核验");
            }
            if (rootBom == null) {
                rootBom = bdBomMapper.selectById(plan.getBomId());
            }
            if (rootBom == null || !Objects.equals(rootBom.getBomId(), plan.getBomId())) {
                throw new RuntimeException("生产计划所选BOM不存在，无法核验根需求物料，请人工核验");
            }
            if (!Objects.equals(plan.getTenantId(), rootBom.getTenantId())) {
                throw new RuntimeException("生产计划与所选BOM租户不一致，禁止生成工单");
            }
            if (!Objects.equals(requirement.getMaterialId(), rootBom.getParentMaterialId())) {
                throw new RuntimeException("物料需求根物料与所选BOM父项物料不一致，请人工审计");
            }
            if (plan.getTotalQuantity() == null || plan.getTotalQuantity().compareTo(BigDecimal.ZERO) <= 0) {
                throw new RuntimeException("生产计划成品数量必须大于零，不能使用子物料合计替代");
            }
            validatedRequirements.put(requirementId, requirement);
        }

        int count = 0;
        for (Map.Entry<Long, PrdMaterialRequirement> entry : validatedRequirements.entrySet()) {
            Long requirementId = entry.getKey();
            PrdMaterialRequirement requirement = entry.getValue();
            /** 已生成过工单的跳过（既有工单不更新、不重建）。 */
            LambdaQueryWrapper<PrdWorkOrder> checkLqw = Wrappers.lambdaQuery();
            checkLqw.eq(PrdWorkOrder::getSourceId, requirementId);
            checkLqw.eq(PrdWorkOrder::getSourceType, "MATERIAL_REQUIREMENT");
            Long existing = prdWorkOrderMapper.selectCount(checkLqw);
            if (existing != null && existing > 0) {
                log.info("物料需求单已生成过工单, requirementId={}", requirementId);
                continue;
            }

            /** 生成工单号 */
            String woNo = autoCodeUtil.genSerialCode("PRD_WORK_ORDER_CODE", null);

            /** 创建工单 */
            PrdWorkOrder wo = new PrdWorkOrder();
            wo.setWoNo(woNo);
            wo.setPlanId(planId);
            wo.setPlanName(plan.getPlanName());
            wo.setSourceType("MATERIAL_REQUIREMENT");
            wo.setSourceId(requirementId);
            wo.setSourceNo(requirement.getRequirementNo());
            wo.setMaterialId(requirement.getMaterialId());
            wo.setMaterialCode(requirement.getMaterialCode());
            wo.setMaterialName(requirement.getMaterialName());
            wo.setMaterialSpec(requirement.getMaterialSpec());
            /** 仅允许已核验的计划根成品，组件合计不能代表根/子装配的应产数量。 */
            wo.setPlannedQty(plan.getTotalQuantity());
            wo.setCompletedQty(BigDecimal.ZERO);
            wo.setScrapQty(BigDecimal.ZERO);
            wo.setBomId(requirement.getBomId());
            wo.setClientId(soClientId);
            wo.setClientName(soClientName);
            wo.setCustomerProductCode(soCustomerProductCode);
            wo.setWoStatus(IPrdWorkOrderService.STATUS_CREATED);
            wo.setPlanStartDate(new Date());

            prdWorkOrderMapper.insert(wo);
            log.info("从物料需求单生成工单, requirementId={}, woNo={}, woId={}", requirementId, woNo, wo.getWoId());
            count++;
        }

        return count;
    }

    private BdBom getCurrentBom(Long materialId, Long specifiedBomId) {
        /** 如果指定了BOM ID，优先使用指定的BOM */
        if (specifiedBomId != null) {
            BdBom specifiedBom = bdBomMapper.selectById(specifiedBomId);
            if (specifiedBom != null && specifiedBom.getIsCurrent() == 1L && specifiedBom.getStatus() == 1L) {
                return specifiedBom;
            }
        }

        /** 否则查询该物料的当前有效BOM */
        LambdaQueryWrapper<BdBom> bomQuery = Wrappers.lambdaQuery();
        bomQuery.eq(BdBom::getParentMaterialId, materialId);
        bomQuery.eq(BdBom::getIsCurrent, 1L);
        bomQuery.eq(BdBom::getStatus, 1L);

        List<BdBom> bomList = bdBomMapper.selectList(bomQuery);
        return (bomList != null && !bomList.isEmpty()) ? bomList.get(0) : null;
    }

    /**
     * 计算子项需求数量（考虑损耗率）
     * <p>ERP-PRD-01：口径已抽到 {@link BomExplosionCalculator}，与「导入BOM明细」入口共用。</p>
     */
    private BigDecimal calculateChildQuantity(BigDecimal parentQuantity, BigDecimal quantityPerParent,
                                              BigDecimal lossRate) {
        return BomExplosionCalculator.calculateChildQuantity(parentQuantity, quantityPerParent, lossRate);
    }

    /**
     * 合并相同物料的用量
     */
    private List<MaterialRequirementCalcDTO> mergeMaterialRequirements(List<MaterialRequirementCalcDTO> requirements) {
        Map<Long, MaterialRequirementCalcDTO> mergedMap = new HashMap<>();

        for (MaterialRequirementCalcDTO item : requirements) {
            if (mergedMap.containsKey(item.getMaterialId())) {
                /** 合并相同物料的用量 */
                MaterialRequirementCalcDTO existing = mergedMap.get(item.getMaterialId());
                existing.setTotalRequiredQuantity(
                        existing.getTotalRequiredQuantity().add(item.getTotalRequiredQuantity())
                );
                /** 更新物料路径，显示多路径 */
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
                                                             List<MaterialRequirementCalcDTO> requirements,
                                                             Long parentRequirementId) {
        PrdMaterialRequirement mainRequirement = new PrdMaterialRequirement();

        /** 设置基本信息 */
        mainRequirement.setPlanId(plan.getPlanId());
        mainRequirement.setPlanNo(plan.getPlanNo());
        mainRequirement.setParentRequirementId(parentRequirementId);
        mainRequirement.setBomId(bomId);
        mainRequirement.setRequirementDate(new Date());
        mainRequirement.setStatus("DRAFT");

        /** 设置相关人员信息 */
        mainRequirement.setPlannerId(plan.getPlannerId());
        mainRequirement.setPlannerName(plan.getPlannerName());
        mainRequirement.setDeptId(plan.getDeptId());
        mainRequirement.setDeptName(plan.getDeptName());

        /** 设置物料信息 */
        BdBom bom = bdBomMapper.selectById(bomId);
        if (bom != null) {
            mainRequirement.setMaterialId(bom.getParentMaterialId());
            mainRequirement.setSourceType(bom.getSourceType());
            BdMaterial material = getMaterialInfo(bom.getParentMaterialId());
            if (material != null) {
                mainRequirement.setMaterialCode(material.getMaterialCode());
                mainRequirement.setMaterialName(material.getMaterialName());
                mainRequirement.setMaterialSpec(material.getMaterialSpec());
            }
        }

        String requirementNo = autoCodeUtil.genSerialCode("PRD_MATERIAL_MBOM", null);
        mainRequirement.setRequirementNo(requirementNo);

        /** 计算统计信息 */
        mainRequirement.setTotalMaterialTypes((long) requirements.size());

        BigDecimal totalRequiredQuantity = requirements.stream()
                .map(MaterialRequirementCalcDTO::getTotalRequiredQuantity)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        mainRequirement.setTotalRequiredQuantity(totalRequiredQuantity);

        return mainRequirement;
    }

    /**
     * 创建物料需求明细项（含净需求计算和提前期偏移）
     */
    private PrdMaterialRequirementDetail createMaterialRequirementDetail(Long requirementId,
                                                                          MaterialRequirementCalcDTO calcDto,
                                                                          Date parentRequiredDate) {
        PrdMaterialRequirementDetail detail = new PrdMaterialRequirementDetail();

        detail.setRequirementId(requirementId);
        detail.setMaterialId(calcDto.getMaterialId());
        detail.setMaterialCode(calcDto.getMaterialCode());
        detail.setMaterialName(calcDto.getMaterialName());
        detail.setMaterialSpec(calcDto.getMaterialSpec());

        /** 设置单位信息 - 从物料主数据获取 */
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
        detail.setIssuedQuantity(BigDecimal.ZERO);

        /** 设置工艺信息 */
        detail.setOperationStep(calcDto.getOperationStep());
        detail.setWorkstationId(calcDto.getWorkstationId());
        detail.setWorkstationName(calcDto.getWorkstationName());
        detail.setLeadTime(calcDto.getLeadTime());

        /** 净需求计算: 毛需求 - 现有库存 - 安全库存 = 净需求 */
        prdMaterialRequirementService.calculateNetRequirements(detail);

        /** 提前期偏移: 需求日期 = 父层需求日期 - 提前期(小时)，口径统一到 BomExplosionCalculator(ERP-PRD-01) */
        Date requiredDate = BomExplosionCalculator.resolveRequiredDate(parentRequiredDate, calcDto.getLeadTime());
        detail.setRequiredDate(requiredDate);

        detail.setStatus(1L);
        detail.setSourceType(calcDto.getSourceType());

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
        lqw.like(StringUtils.isNotBlank(bo.getDeptName()), PrdProductionPlan::getDeptName, bo.getDeptName());
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
        lqw.like(StringUtils.isNotBlank(entity.getDeptName()), PrdProductionPlan::getDeptName, entity.getDeptName());
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
