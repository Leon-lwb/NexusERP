package com.nexus.erp.bd.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.core.utils.MapstructUtils;
import com.nexus.common.core.utils.StringUtils;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.erp.bd.domain.BdBom;
import com.nexus.erp.bd.domain.BdMaterial;
import com.nexus.erp.bd.domain.bo.BdBomBo;
import com.nexus.erp.bd.domain.vo.BdBomDetailVo;
import com.nexus.erp.bd.domain.vo.BdBomVo;
import com.nexus.erp.bd.domain.vo.BomComparisonVo;
import com.nexus.erp.bd.domain.vo.BomCostRollupVo;
import com.nexus.erp.bd.domain.vo.BomTreeNodeVo;
import com.nexus.erp.bd.domain.vo.IndentedBomLineVo;
import com.nexus.erp.bd.mapper.BdBomMapper;
import com.nexus.erp.bd.mapper.BdMaterialMapper;
import com.nexus.erp.bd.mapper.BdRoutingMapper;
import com.nexus.erp.bd.domain.BdRouting;
import com.nexus.erp.bd.domain.vo.BdRoutingVo;
import com.nexus.erp.bd.service.IBdBomService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.nexus.erp.bd.domain.BdBomAlternative;
import com.nexus.erp.bd.domain.BdBomDetail;
import com.nexus.erp.bd.domain.BdRoutingStep;
import com.nexus.erp.bd.mapper.BdBomAlternativeMapper;
import com.nexus.erp.bd.mapper.BdBomDetailMapper;
import com.nexus.erp.bd.mapper.BdRoutingStepMapper;
import com.nexus.erp.prd.domain.PrdWorkOrder;
import com.nexus.erp.prd.mapper.PrdWorkOrderMapper;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * BOMService业务层处理
 *
 * @author wanghuan
 */
@RequiredArgsConstructor
@DS("erp")
@Service
public class BdBomServiceImpl implements IBdBomService {

    /** BOM最大递归深度，防止异常数据导致栈溢出 */
    private static final int MAX_BOM_DEPTH = 20;

    private final BdBomMapper baseMapper;
    private final BdBomDetailMapper bomDetailMapper;
    private final BdBomAlternativeMapper bomAlternativeMapper;
    private final BdMaterialMapper materialMapper;
    private final BdRoutingMapper routingMapper;
    private final BdRoutingStepMapper routingStepMapper;
    private final PrdWorkOrderMapper workOrderMapper;

    /** 工单终态状态：处于这些状态的工单不再使用BOM，删除BOM时不阻断 */
    private static final List<String> WORK_ORDER_TERMINAL_STATUS =
        List.of("COMPLETED", "CLOSED", "CANCELLED");

    @Override
    public TableDataInfo<BdBomVo> selectPageList(BdBomBo bo, PageQuery pageQuery) {
        LambdaQueryWrapper<BdBom> lqw = buildQueryWrapper(bo);
        Page<BdBomVo> page = baseMapper.selectVoPage(pageQuery.build(), lqw);
        enrichBomVo(page.getRecords());
        return TableDataInfo.build(page);
    }

    @Override
    public List<BdBomVo> selectList(BdBomBo bo) {
        List<BdBomVo> list = baseMapper.selectVoList(buildQueryWrapper(bo));
        enrichBomVo(list);
        return list;
    }

    @Override
    public BdBomVo selectById(Long id) {
        BdBomVo vo = baseMapper.selectVoById(id);
        enrichBomVo(List.of(vo));
        return vo;
    }

    /**
     * 富集BOM VO: 填充父项物料编码/名称、工艺路线编码/名称
     * (这些字段在实体上不存在,需跨表查询,用于列表/详情展示及 MES 只读视图)
     */
    private void enrichBomVo(List<BdBomVo> list) {
        if (list == null || list.isEmpty()) {
            return;
        }
        // 批量加载父项物料
        Set<Long> materialIds = list.stream()
            .map(BdBomVo::getParentMaterialId)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());
        Map<Long, BdMaterial> materialMap = materialIds.isEmpty() ? Collections.emptyMap() :
            materialMapper.selectList(Wrappers.<BdMaterial>lambdaQuery()
                    .in(BdMaterial::getMaterialId, materialIds))
                .stream()
                .collect(Collectors.toMap(BdMaterial::getMaterialId, m -> m, (a, b) -> a));
        // 批量加载工艺路线
        Set<Long> routingIds = list.stream()
            .map(BdBomVo::getRoutingId)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());
        Map<Long, BdRoutingVo> routingMap = routingIds.isEmpty() ? Collections.emptyMap() :
            routingMapper.selectVoList(Wrappers.<BdRouting>lambdaQuery()
                    .in(BdRouting::getRoutingId, routingIds))
                .stream()
                .collect(Collectors.toMap(BdRoutingVo::getRoutingId, r -> r, (a, b) -> a));
        for (BdBomVo vo : list) {
            if (vo == null) {
                continue;
            }
            BdMaterial material = materialMap.get(vo.getParentMaterialId());
            if (material != null) {
                vo.setParentMaterialCode(material.getMaterialCode());
                vo.setParentMaterialName(material.getMaterialName());
            }
            BdRoutingVo routing = routingMap.get(vo.getRoutingId());
            if (routing != null) {
                vo.setRoutingCode(routing.getRoutingCode());
                vo.setRoutingName(routing.getRoutingName());
            }
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int insertByBo(BdBomBo bo) {
        // 校验同一父项物料下版本号唯一
        checkVersionUnique(bo.getParentMaterialId(), bo.getBomVersion(), null);
        // 防护多当前版本: 若新建BOM标记为当前版本,先取消同父项其他BOM的当前标记
        if (bo.getIsCurrent() != null && bo.getIsCurrent() == 1L) {
            clearCurrentFlag(bo.getParentMaterialId());
        }
        BdBom entity = MapstructUtils.convert(bo, BdBom.class);
        return baseMapper.insert(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updateByBo(BdBomBo bo) {
        // 校验同一父项物料下版本号唯一（排除自身）
        checkVersionUnique(bo.getParentMaterialId(), bo.getBomVersion(), bo.getBomId());
        // 防护多当前版本: 若更新后为当前版本,先取消同父项其他BOM的当前标记
        if (bo.getIsCurrent() != null && bo.getIsCurrent() == 1L) {
            clearCurrentFlag(bo.getParentMaterialId());
        }
        BdBom entity = MapstructUtils.convert(bo, BdBom.class);
        return baseMapper.updateById(entity);
    }

    /**
     * 取消同父项物料下所有BOM的当前版本标记
     */
    private void clearCurrentFlag(Long parentMaterialId) {
        if (parentMaterialId == null) {
            return;
        }
        baseMapper.update(Wrappers.lambdaUpdate(BdBom.class)
            .set(BdBom::getIsCurrent, 0L)
            .eq(BdBom::getParentMaterialId, parentMaterialId)
            .eq(BdBom::getIsCurrent, 1L));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteByIds(Long[] ids) {
        List<Long> idList = List.of(ids);
        // 1. 校验是否有非终态生产工单引用这些BOM
        Long referencingCount = workOrderMapper.selectCount(
            Wrappers.lambdaQuery(PrdWorkOrder.class)
                .in(PrdWorkOrder::getBomId, idList)
                .notIn(PrdWorkOrder::getWoStatus, WORK_ORDER_TERMINAL_STATUS));
        if (referencingCount != null && referencingCount > 0) {
            throw new ServiceException("所选BOM已被" + referencingCount + "张未完成的生产工单引用，无法删除");
        }
        // 2. 级联删除替代料（按bomId）
        bomAlternativeMapper.delete(
            Wrappers.lambdaQuery(BdBomAlternative.class)
                .in(BdBomAlternative::getBomId, idList));
        // 3. 级联删除BOM明细（按bomId）
        bomDetailMapper.delete(
            Wrappers.lambdaQuery(BdBomDetail.class)
                .in(BdBomDetail::getBomId, idList));
        // 4. 删除BOM主表
        return baseMapper.deleteBatchIds(idList);
    }

    /**
     * 校验同一父项物料下BOM版本号唯一
     *
     * @param parentMaterialId 父项物料ID
     * @param bomVersion       BOM版本号
     * @param excludeBomId     排除的BOM ID（更新时传自身ID，新增时传null）
     */
    private void checkVersionUnique(Long parentMaterialId, String bomVersion, Long excludeBomId) {
        if (parentMaterialId == null || StringUtils.isBlank(bomVersion)) {
            return;
        }
        Long count = baseMapper.selectCount(
            Wrappers.lambdaQuery(BdBom.class)
                .eq(BdBom::getParentMaterialId, parentMaterialId)
                .eq(BdBom::getBomVersion, bomVersion)
                .ne(excludeBomId != null, BdBom::getBomId, excludeBomId));
        if (count != null && count > 0) {
            throw new ServiceException("该父项物料下已存在版本号 " + bomVersion + " 的BOM，请使用其他版本号");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void activateVersion(Long bomId) {
        BdBom bom = baseMapper.selectById(bomId);
        if (bom == null) {
            throw new ServiceException("BOM不存在");
        }
        if (bom.getParentMaterialId() == null) {
            throw new ServiceException("BOM父项物料为空，无法激活");
        }
        // 将该物料所有BOM版本设为非当前
        List<BdBom> sameParentBoms = baseMapper.selectList(
            Wrappers.lambdaQuery(BdBom.class)
                .eq(BdBom::getParentMaterialId, bom.getParentMaterialId()));
        for (BdBom b : sameParentBoms) {
            b.setIsCurrent(0L);
            baseMapper.updateById(b);
        }
        // 激活当前版本
        bom.setIsCurrent(1L);
        bom.setStatus(1L);
        baseMapper.updateById(bom);
    }

    @Override
    public String verifyBom(Long bomId) {
        BdBom bom = baseMapper.selectById(bomId);
        if (bom == null) {
            return "BOM不存在";
        }
        // 检查是否有子项
        List<BdBomDetail> details = bomDetailMapper.selectList(
            Wrappers.lambdaQuery(BdBomDetail.class)
                .eq(BdBomDetail::getBomId, bomId));
        if (details == null || details.isEmpty()) {
            return "BOM明细为空，请添加子项物料";
        }
        // 检查循环引用
        Set<Long> visited = new HashSet<>();
        if (checkCircularRef(bom.getParentMaterialId(), details, visited)) {
            return "BOM存在循环引用";
        }
        // 检查无效子项物料
        for (BdBomDetail detail : details) {
            if (detail.getChildMaterialId() == null) {
                return "BOM明细存在空的子项物料";
            }
            if (detail.getQuantityPerParent() == null || detail.getQuantityPerParent().compareTo(java.math.BigDecimal.ZERO) <= 0) {
                return "BOM明细子项用量必须大于0";
            }
        }
        return "OK";
    }

    /**
     * 递归检查BOM是否存在循环引用
     */
    private boolean checkCircularRef(Long parentMaterialId, List<BdBomDetail> currentDetails, Set<Long> visited) {
        if (parentMaterialId == null) {
            return false;
        }
        visited.add(parentMaterialId);
        for (BdBomDetail detail : currentDetails) {
            Long childId = detail.getChildMaterialId();
            if (visited.contains(childId)) {
                return true;
            }
            // 检查子物料是否有自己的BOM，如果有则递归检查
            List<BdBom> childBoms = baseMapper.selectList(
                Wrappers.lambdaQuery(BdBom.class)
                    .eq(BdBom::getParentMaterialId, childId)
                    .eq(BdBom::getIsCurrent, 1L));
            for (BdBom childBom : childBoms) {
                List<BdBomDetail> childDetails = bomDetailMapper.selectList(
                    Wrappers.lambdaQuery(BdBomDetail.class)
                        .eq(BdBomDetail::getBomId, childBom.getBomId()));
                if (childDetails != null && !childDetails.isEmpty()) {
                    Set<Long> childVisited = new HashSet<>(visited);
                    if (checkCircularRef(childId, childDetails, childVisited)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * 获取BOM树形结构
     *
     * @param bomId BOM主键
     * @return BOM树形结构根节点
     */
    @Override
    public BomTreeNodeVo getBomTree(Long bomId) {
        BdBom bom = baseMapper.selectById(bomId);
        if (bom == null) {
            return null;
        }
        // 获取父项物料信息
        BdMaterial parentMaterial = materialMapper.selectById(bom.getParentMaterialId());
        BomTreeNodeVo rootNode = new BomTreeNodeVo();
        rootNode.setBomId(bomId);
        rootNode.setMaterialId(bom.getParentMaterialId());
        rootNode.setSourceType(bom.getSourceType());
        if (parentMaterial != null) {
            rootNode.setMaterialCode(parentMaterial.getMaterialCode());
            rootNode.setMaterialName(parentMaterial.getMaterialName());
            rootNode.setMaterialSpec(parentMaterial.getMaterialSpec());
            rootNode.setMaterialType(String.valueOf(parentMaterial.getMaterialType()));
        }
        rootNode.setLevel(0);
        rootNode.setNodeType("ASSEMBLY");
        rootNode.setHasChildren(true);
        rootNode.setQuantityPerParent(BigDecimal.ONE);
        rootNode.setLossRate(BigDecimal.ZERO);
        rootNode.setTotalQuantity(BigDecimal.ONE);
        // 初始化循环引用检测集合
        Set<Long> visited = new HashSet<>();
        visited.add(bom.getParentMaterialId());
        // 递归构建子树
        List<BdBomDetail> details = bomDetailMapper.selectList(
            Wrappers.lambdaQuery(BdBomDetail.class).eq(BdBomDetail::getBomId, bomId));
        List<BomTreeNodeVo> children = new ArrayList<>();
        for (BdBomDetail detail : details) {
            children.add(buildTreeNode(detail, bomId, 1, visited));
        }
        rootNode.setChildren(children);
        rootNode.setLeaf(children.isEmpty());
        return rootNode;
    }

    /**
     * 递归构建BOM树节点（含循环引用检测和深度保护）
     *
     * @param detail       BOM明细行
     * @param parentBomId  父BOM ID
     * @param level        当前层级
     * @param visited      已访问的物料ID集合（用于循环引用检测）
     * @return 树节点
     * @throws ServiceException 当检测到循环引用或超过最大深度时抛出
     */
    private BomTreeNodeVo buildTreeNode(BdBomDetail detail, Long parentBomId, int level, Set<Long> visited) {
        // 深度保护：超过最大递归深度时终止
        if (level > MAX_BOM_DEPTH) {
            throw new ServiceException("BOM层级超过最大深度限制(" + MAX_BOM_DEPTH + ")，物料ID: " + detail.getChildMaterialId() + "，请检查是否存在异常数据");
        }
        // 循环引用检测：子物料已在访问路径中出现过
        if (visited.contains(detail.getChildMaterialId())) {
            throw new ServiceException("BOM存在循环引用，物料ID: " + detail.getChildMaterialId() + " 已在上级路径中出现");
        }

        BomTreeNodeVo node = new BomTreeNodeVo();
        node.setDetailId(detail.getDetailId());
        node.setMaterialId(detail.getChildMaterialId());
        node.setParentBomId(parentBomId);
        node.setQuantityPerParent(detail.getQuantityPerParent());
        node.setLossRate(detail.getLossRate() != null ? detail.getLossRate() : BigDecimal.ZERO);
        node.setLevel(level);
        node.setSourceType(detail.getSourceType());
        node.setLeadTime(detail.getLeadTime());
        // 计算含损耗总用量
        BigDecimal qty = detail.getQuantityPerParent() != null ? detail.getQuantityPerParent() : BigDecimal.ZERO;
        BigDecimal loss = detail.getLossRate() != null ? detail.getLossRate() : BigDecimal.ZERO;
        node.setTotalQuantity(qty.multiply(BigDecimal.ONE.add(loss.divide(new BigDecimal("100"), 6, java.math.RoundingMode.HALF_UP))));
        // 获取物料信息
        BdMaterial material = materialMapper.selectById(detail.getChildMaterialId());
        if (material != null) {
            node.setMaterialCode(material.getMaterialCode());
            node.setMaterialName(material.getMaterialName());
            node.setMaterialSpec(material.getMaterialSpec());
            node.setMaterialType(String.valueOf(material.getMaterialType()));
        }
        // 检查子项物料是否有自己的BOM
        BdBom childBom = findCurrentBom(detail.getChildMaterialId());
        if (childBom != null) {
            node.setNodeType("ASSEMBLY");
            node.setBomId(childBom.getBomId());
            node.setHasChildren(true);
            node.setLeaf(false);
            // 将当前子物料加入已访问集合（传递副本，兄弟节点间不共享）
            Set<Long> childVisited = new HashSet<>(visited);
            childVisited.add(detail.getChildMaterialId());
            // 递归展开子BOM
            List<BdBomDetail> childDetails = bomDetailMapper.selectList(
                Wrappers.lambdaQuery(BdBomDetail.class).eq(BdBomDetail::getBomId, childBom.getBomId()));
            List<BomTreeNodeVo> grandchildren = new ArrayList<>();
            for (BdBomDetail childDetail : childDetails) {
                grandchildren.add(buildTreeNode(childDetail, childBom.getBomId(), level + 1, childVisited));
            }
            node.setChildren(grandchildren);
        } else {
            node.setNodeType("PART");
            node.setHasChildren(false);
            node.setLeaf(true);
            node.setChildren(Collections.emptyList());
        }
        return node;
    }

    /**
     * 获取BOM缩进式扁平列表
     *
     * @param bomId BOM主键
     * @return 缩进式BOM行列表
     */
    @Override
    public List<IndentedBomLineVo> getIndentedBom(Long bomId) {
        BdBom bom = baseMapper.selectById(bomId);
        if (bom == null) {
            return Collections.emptyList();
        }
        List<IndentedBomLineVo> result = new ArrayList<>();
        // 添加顶层节点
        BdMaterial parentMaterial = materialMapper.selectById(bom.getParentMaterialId());
        IndentedBomLineVo topLine = new IndentedBomLineVo();
        topLine.setLevel(0);
        topLine.setIndent("");
        topLine.setBomId(bomId);
        topLine.setMaterialId(bom.getParentMaterialId());
        topLine.setSourceType(bom.getSourceType());
        if (parentMaterial != null) {
            topLine.setMaterialCode(parentMaterial.getMaterialCode());
            topLine.setMaterialName(parentMaterial.getMaterialName());
            topLine.setMaterialSpec(parentMaterial.getMaterialSpec());
            topLine.setMaterialType(String.valueOf(parentMaterial.getMaterialType()));
        }
        topLine.setNodeType("ASSEMBLY");
        topLine.setQuantityPerParent(BigDecimal.ONE);
        topLine.setLossRate(BigDecimal.ZERO);
        topLine.setCumulativeQuantity(BigDecimal.ONE);
        topLine.setUnitCost(materialCost(parentMaterial));
        topLine.setLineCost(topLine.getUnitCost());
        topLine.setExpandable(true);
        result.add(topLine);
        // 初始化循环引用检测集合
        Set<Long> visited = new HashSet<>();
        visited.add(bom.getParentMaterialId());
        // 递归添加子节点
        buildIndentedLines(bomId, 1, BigDecimal.ONE, result, visited);
        return result;
    }

    /**
     * 递归构建BOM缩进式行列表（含循环引用检测和深度保护）
     *
     * @param bomId               当前BOM ID
     * @param level               当前层级
     * @param parentCumulativeQty 父级累计用量
     * @param result              结果列表
     * @param visited             已访问的物料ID集合（用于循环引用检测）
     * @throws ServiceException 当检测到循环引用或超过最大深度时抛出
     */
    private void buildIndentedLines(Long bomId, int level, BigDecimal parentCumulativeQty,
                                     List<IndentedBomLineVo> result, Set<Long> visited) {
        // 深度保护
        if (level > MAX_BOM_DEPTH) {
            throw new ServiceException("BOM层级超过最大深度限制(" + MAX_BOM_DEPTH + ")，BOM ID: " + bomId + "，请检查是否存在异常数据");
        }

        List<BdBomDetail> details = bomDetailMapper.selectList(
            Wrappers.lambdaQuery(BdBomDetail.class).eq(BdBomDetail::getBomId, bomId));
        for (BdBomDetail detail : details) {
            // 循环引用检测
            if (visited.contains(detail.getChildMaterialId())) {
                throw new ServiceException("BOM存在循环引用，物料ID: " + detail.getChildMaterialId() + " 已在上级路径中出现");
            }

            IndentedBomLineVo line = new IndentedBomLineVo();
            line.setLevel(level);
            line.setIndent("  ".repeat(level));
            line.setDetailId(detail.getDetailId());
            line.setParentBomId(bomId);
            line.setMaterialId(detail.getChildMaterialId());
            line.setQuantityPerParent(detail.getQuantityPerParent());
            line.setLossRate(detail.getLossRate() != null ? detail.getLossRate() : BigDecimal.ZERO);
            line.setSourceType(detail.getSourceType());
            line.setLeadTime(detail.getLeadTime());
            // 计算累计用量
            BigDecimal qty = detail.getQuantityPerParent() != null ? detail.getQuantityPerParent() : BigDecimal.ZERO;
            BigDecimal loss = detail.getLossRate() != null ? detail.getLossRate() : BigDecimal.ZERO;
            BigDecimal totalQty = qty.multiply(BigDecimal.ONE.add(loss.divide(new BigDecimal("100"), 6, java.math.RoundingMode.HALF_UP)));
            line.setCumulativeQuantity(parentCumulativeQty.multiply(totalQty));
            // 获取物料信息
            BdMaterial material = materialMapper.selectById(detail.getChildMaterialId());
            if (material != null) {
                line.setMaterialCode(material.getMaterialCode());
                line.setMaterialName(material.getMaterialName());
                line.setMaterialSpec(material.getMaterialSpec());
                line.setMaterialType(String.valueOf(material.getMaterialType()));
            }
            line.setUnitCost(materialCost(material));
            line.setLineCost(line.getUnitCost().multiply(line.getCumulativeQuantity()));
            // 检查是否有子BOM
            BdBom childBom = findCurrentBom(detail.getChildMaterialId());
            if (childBom != null) {
                line.setNodeType("ASSEMBLY");
                line.setBomId(childBom.getBomId());
                line.setExpandable(true);
                result.add(line);
                // 将当前子物料加入已访问集合（传递副本，兄弟节点间不共享）
                Set<Long> childVisited = new HashSet<>(visited);
                childVisited.add(detail.getChildMaterialId());
                // 递归添加子节点
                buildIndentedLines(childBom.getBomId(), level + 1, line.getCumulativeQuantity(), result, childVisited);
            } else {
                line.setNodeType("PART");
                line.setExpandable(false);
                result.add(line);
            }
        }
    }

    @Override
    public List<IndentedBomLineVo> explodeBom(Long bomId) {
        List<IndentedBomLineVo> allLines = getIndentedBom(bomId);
        // 只取叶子节点（PART类型），并按物料聚合(同一叶子在多分支出现时合并累计用量与行成本)
        Map<Long, IndentedBomLineVo> merged = new LinkedHashMap<>();
        for (IndentedBomLineVo line : allLines) {
            if (!"PART".equals(line.getNodeType()) || line.getMaterialId() == null) {
                continue;
            }
            merged.merge(line.getMaterialId(), line, (a, b) -> {
                a.setCumulativeQuantity(safeAdd(a.getCumulativeQuantity(), b.getCumulativeQuantity()));
                a.setLineCost(safeAdd(a.getLineCost(), b.getLineCost()));
                return a;
            });
        }
        return new ArrayList<>(merged.values());
    }

    /**
     * 取物料单位成本: 优先标准成本,其次参考价,均为空返回0
     */
    private BigDecimal materialCost(BdMaterial material) {
        if (material == null) {
            return BigDecimal.ZERO;
        }
        if (material.getStandardCost() != null) {
            return material.getStandardCost();
        }
        if (material.getReferencePrice() != null) {
            return material.getReferencePrice();
        }
        return BigDecimal.ZERO;
    }

    private BigDecimal safeAdd(BigDecimal a, BigDecimal b) {
        return (a == null ? BigDecimal.ZERO : a).add(b == null ? BigDecimal.ZERO : b);
    }

    /**
     * Where-Used反查：查询某物料被哪些BOM作为子项使用
     */
    @Override
    public List<BdBomVo> whereUsed(Long materialId) {
        if (materialId == null) {
            return Collections.emptyList();
        }
        // 查找所有 bom_detail 中引用了该物料的 bom_id
        List<BdBomDetail> details = bomDetailMapper.selectList(
            Wrappers.lambdaQuery(BdBomDetail.class)
                .eq(BdBomDetail::getChildMaterialId, materialId));
        if (details == null || details.isEmpty()) {
            return Collections.emptyList();
        }
        List<Long> bomIds = details.stream()
            .map(BdBomDetail::getBomId)
            .distinct()
            .collect(Collectors.toList());
        // 查询这些BOM的主表信息
        List<BdBom> boms = baseMapper.selectBatchIds(bomIds);
        if (boms == null || boms.isEmpty()) {
            return Collections.emptyList();
        }
        // 转换为VO并补充父项物料信息
        List<BdBomVo> result = new ArrayList<>();
        for (BdBom bom : boms) {
            BdBomVo vo = MapstructUtils.convert(bom, BdBomVo.class);
            if (bom.getParentMaterialId() != null) {
                BdMaterial parentMaterial = materialMapper.selectById(bom.getParentMaterialId());
                if (parentMaterial != null) {
                    vo.setParentMaterialCode(parentMaterial.getMaterialCode());
                    vo.setParentMaterialName(parentMaterial.getMaterialName());
                }
            }
            result.add(vo);
        }
        return result;
    }

    /**
     * BOM复制：从已有BOM复制创建新版本
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long copyBom(Long sourceBomId, String newVersion) {
        // 1. 查询源BOM
        BdBom sourceBom = baseMapper.selectById(sourceBomId);
        if (sourceBom == null) {
            throw new ServiceException("源BOM不存在");
        }
        // 2. 检查新版本号是否已存在
        checkVersionUnique(sourceBom.getParentMaterialId(), newVersion, null);
        // 3. 创建新BOM主表
        BdBom newBom = new BdBom();
        newBom.setParentMaterialId(sourceBom.getParentMaterialId());
        newBom.setBomVersion(newVersion);
        newBom.setBomName(sourceBom.getBomName() + "（复制）");
        newBom.setBomType(sourceBom.getBomType());
        newBom.setSourceType(sourceBom.getSourceType());
        newBom.setRoutingId(sourceBom.getRoutingId());
        newBom.setIsCurrent(0L);
        newBom.setStatus(0L);
        newBom.setRemark("从BOM " + sourceBom.getBomId() + " (v" + sourceBom.getBomVersion() + ") 复制");
        baseMapper.insert(newBom);
        // 4. 复制BOM明细，并建立 旧明细ID -> 新明细ID 的映射（用于复制替代料）
        List<BdBomDetail> sourceDetails = bomDetailMapper.selectList(
            Wrappers.lambdaQuery(BdBomDetail.class)
                .eq(BdBomDetail::getBomId, sourceBomId));
        Map<Long, Long> detailIdMapping = new HashMap<>();
        if (sourceDetails != null && !sourceDetails.isEmpty()) {
            for (BdBomDetail sourceDetail : sourceDetails) {
                BdBomDetail newDetail = new BdBomDetail();
                newDetail.setBomId(newBom.getBomId());
                newDetail.setChildMaterialId(sourceDetail.getChildMaterialId());
                newDetail.setQuantityPerParent(sourceDetail.getQuantityPerParent());
                newDetail.setLossRate(sourceDetail.getLossRate());
                newDetail.setOperationStep(sourceDetail.getOperationStep());
                newDetail.setLeadTime(sourceDetail.getLeadTime());
                newDetail.setSourceType(sourceDetail.getSourceType());
                newDetail.setEffectiveDate(sourceDetail.getEffectiveDate());
                newDetail.setExpiryDate(sourceDetail.getExpiryDate());
                newDetail.setSortOrder(sourceDetail.getSortOrder());
                newDetail.setStatus(sourceDetail.getStatus());
                newDetail.setRemark(sourceDetail.getRemark());
                bomDetailMapper.insert(newDetail);
                // 记录映射（插入后newDetail.getDetailId()已被回填）
                if (sourceDetail.getDetailId() != null) {
                    detailIdMapping.put(sourceDetail.getDetailId(), newDetail.getDetailId());
                }
            }
        }
        // 5. 复制BOM替代料（按源BOM的所有明细归集，重映射detailId和bomId）
        List<BdBomAlternative> sourceAlternatives = bomAlternativeMapper.selectList(
            Wrappers.lambdaQuery(BdBomAlternative.class)
                .eq(BdBomAlternative::getBomId, sourceBomId));
        if (sourceAlternatives != null && !sourceAlternatives.isEmpty() && !detailIdMapping.isEmpty()) {
            for (BdBomAlternative sourceAlt : sourceAlternatives) {
                Long newDetailId = detailIdMapping.get(sourceAlt.getDetailId());
                if (newDetailId == null) {
                    // 源明细已被删除等异常情况，跳过
                    continue;
                }
                BdBomAlternative newAlt = new BdBomAlternative();
                newAlt.setDetailId(newDetailId);
                newAlt.setBomId(newBom.getBomId());
                newAlt.setAltMaterialId(sourceAlt.getAltMaterialId());
                newAlt.setAltMaterialCode(sourceAlt.getAltMaterialCode());
                newAlt.setAltMaterialName(sourceAlt.getAltMaterialName());
                newAlt.setAltQuantity(sourceAlt.getAltQuantity());
                newAlt.setPriority(sourceAlt.getPriority());
                newAlt.setSubstitutionType(sourceAlt.getSubstitutionType());
                newAlt.setEffectiveDate(sourceAlt.getEffectiveDate());
                newAlt.setExpiryDate(sourceAlt.getExpiryDate());
                newAlt.setStatus(sourceAlt.getStatus());
                newAlt.setRemark(sourceAlt.getRemark());
                bomAlternativeMapper.insert(newAlt);
            }
        }
        return newBom.getBomId();
    }

    /**
     * BOM比较：对比两个版本的差异
     */
    @Override
    public BomComparisonVo compareBom(Long bomId1, Long bomId2) {
        BdBom bom1 = baseMapper.selectById(bomId1);
        BdBom bom2 = baseMapper.selectById(bomId2);
        if (bom1 == null || bom2 == null) {
            throw new ServiceException("BOM不存在");
        }

        BomComparisonVo result = new BomComparisonVo();

        // 填充摘要信息
        result.setBom1(buildBomSummary(bom1));
        result.setBom2(buildBomSummary(bom2));

        // 获取两个BOM的明细
        List<BdBomDetail> details1 = bomDetailMapper.selectList(
            Wrappers.lambdaQuery(BdBomDetail.class).eq(BdBomDetail::getBomId, bomId1));
        List<BdBomDetail> details2 = bomDetailMapper.selectList(
            Wrappers.lambdaQuery(BdBomDetail.class).eq(BdBomDetail::getBomId, bomId2));

        // 构建 materialId -> detail 的映射
        Map<Long, BdBomDetail> map1 = new HashMap<>();
        if (details1 != null) {
            details1.forEach(d -> map1.put(d.getChildMaterialId(), d));
        }
        Map<Long, BdBomDetail> map2 = new HashMap<>();
        if (details2 != null) {
            details2.forEach(d -> map2.put(d.getChildMaterialId(), d));
        }

        // 收集所有物料ID
        Set<Long> allMaterialIds = new HashSet<>();
        allMaterialIds.addAll(map1.keySet());
        allMaterialIds.addAll(map2.keySet());

        List<BomComparisonVo.ComparisonLine> onlyInBom1 = new ArrayList<>();
        List<BomComparisonVo.ComparisonLine> onlyInBom2 = new ArrayList<>();
        List<BomComparisonVo.ComparisonLine> changed = new ArrayList<>();
        List<BomComparisonVo.ComparisonLine> unchanged = new ArrayList<>();

        for (Long materialId : allMaterialIds) {
            BdBomDetail d1 = map1.get(materialId);
            BdBomDetail d2 = map2.get(materialId);

            BomComparisonVo.ComparisonLine line = new BomComparisonVo.ComparisonLine();
            line.setChildMaterialId(materialId);

            // 补充物料信息
            BdMaterial material = materialMapper.selectById(materialId);
            if (material != null) {
                line.setChildMaterialCode(material.getMaterialCode());
                line.setChildMaterialName(material.getMaterialName());
                line.setChildMaterialSpec(material.getMaterialSpec());
            }

            if (d1 != null && d2 == null) {
                // 仅在BOM1中
                line.setQuantity1(d1.getQuantityPerParent());
                line.setLossRate1(d1.getLossRate());
                line.setOperationStep1(d1.getOperationStep());
                line.setChangeType("REMOVED");
                line.setChangeDetail("该子项在新版本中被移除");
                onlyInBom1.add(line);
            } else if (d1 == null && d2 != null) {
                // 仅在BOM2中
                line.setQuantity2(d2.getQuantityPerParent());
                line.setLossRate2(d2.getLossRate());
                line.setOperationStep2(d2.getOperationStep());
                line.setChangeType("ADDED");
                line.setChangeDetail("该子项为新增");
                onlyInBom2.add(line);
            } else {
                // 两边都有，比较差异
                line.setQuantity1(d1.getQuantityPerParent());
                line.setQuantity2(d2.getQuantityPerParent());
                line.setLossRate1(d1.getLossRate());
                line.setLossRate2(d2.getLossRate());
                line.setOperationStep1(d1.getOperationStep());
                line.setOperationStep2(d2.getOperationStep());

                boolean isQtyChanged = compareDecimal(d1.getQuantityPerParent(), d2.getQuantityPerParent());
                boolean isLossChanged = compareDecimal(d1.getLossRate(), d2.getLossRate());
                boolean isStepChanged = !Objects.equals(d1.getOperationStep(), d2.getOperationStep());

                if (isQtyChanged || isLossChanged || isStepChanged) {
                    line.setChangeType("MODIFIED");
                    StringBuilder detail = new StringBuilder();
                    if (isQtyChanged) {
                        detail.append("用量: ").append(d1.getQuantityPerParent()).append(" → ").append(d2.getQuantityPerParent()).append("; ");
                    }
                    if (isLossChanged) {
                        detail.append("损耗率: ").append(d1.getLossRate()).append("% → ").append(d2.getLossRate()).append("%; ");
                    }
                    if (isStepChanged) {
                        detail.append("工序步骤: ").append(d1.getOperationStep()).append(" → ").append(d2.getOperationStep()).append("; ");
                    }
                    line.setChangeDetail(detail.toString().trim());
                    changed.add(line);
                } else {
                    line.setChangeType("UNCHANGED");
                    unchanged.add(line);
                }
            }
        }

        result.setOnlyInBom1(onlyInBom1);
        result.setOnlyInBom2(onlyInBom2);
        result.setChanged(changed);
        result.setUnchanged(unchanged);
        return result;
    }

    /**
     * BOM成本卷积
     * 物料成本: 复用 explodeBom(已按叶子聚合,含累计用量与行成本)
     * 工序成本: 遍历关联工艺路线步骤, stepCost = 标准工时 × (人工费率×人数 + 机器费率×机台数)
     */
    @Override
    public BomCostRollupVo costRollup(Long bomId) {
        BdBom bom = baseMapper.selectById(bomId);
        if (bom == null) {
            throw new ServiceException("BOM不存在");
        }
        BomCostRollupVo result = new BomCostRollupVo();
        result.setBomId(bomId);
        result.setBomVersion(bom.getBomVersion());
        result.setRoutingId(bom.getRoutingId());

        // 父项物料信息
        BdMaterial parent = materialMapper.selectById(bom.getParentMaterialId());
        if (parent != null) {
            result.setParentMaterialId(parent.getMaterialId());
            result.setParentMaterialCode(parent.getMaterialCode());
            result.setParentMaterialName(parent.getMaterialName());
        }

        // 物料成本(叶子聚合)
        List<IndentedBomLineVo> leaves = explodeBom(bomId);
        List<BomCostRollupVo.MaterialCostLine> materialLines = new ArrayList<>();
        BigDecimal totalMaterialCost = BigDecimal.ZERO;
        for (IndentedBomLineVo leaf : leaves) {
            BomCostRollupVo.MaterialCostLine ml = new BomCostRollupVo.MaterialCostLine();
            ml.setMaterialId(leaf.getMaterialId());
            ml.setMaterialCode(leaf.getMaterialCode());
            ml.setMaterialName(leaf.getMaterialName());
            ml.setMaterialSpec(leaf.getMaterialSpec());
            ml.setCumulativeQuantity(leaf.getCumulativeQuantity());
            ml.setUnitCost(leaf.getUnitCost());
            ml.setLineCost(leaf.getLineCost());
            materialLines.add(ml);
            totalMaterialCost = safeAdd(totalMaterialCost, leaf.getLineCost());
        }
        result.setMaterialLines(materialLines);
        result.setTotalMaterialCost(totalMaterialCost);

        // 工序成本
        BigDecimal totalRoutingCost = BigDecimal.ZERO;
        boolean routingAvailable = false;
        List<BomCostRollupVo.RoutingCostLine> routingLines = new ArrayList<>();
        if (bom.getRoutingId() != null) {
            BdRouting routing = routingMapper.selectById(bom.getRoutingId());
            if (routing != null) {
                result.setRoutingCode(routing.getRoutingCode());
            }
            List<BdRoutingStep> steps = routingStepMapper.selectList(
                Wrappers.lambdaQuery(BdRoutingStep.class)
                    .eq(BdRoutingStep::getRoutingId, bom.getRoutingId())
                    .orderByAsc(BdRoutingStep::getSequenceNum));
            boolean allStepsCostable = !steps.isEmpty();
            for (BdRoutingStep step : steps) {
                BomCostRollupVo.RoutingCostLine rl = new BomCostRollupVo.RoutingCostLine();
                rl.setStepId(step.getStepId());
                rl.setSequenceNum(step.getSequenceNum());
                rl.setProcessName(step.getProcessName());
                rl.setWorkstationName(step.getWorkstationName());
                rl.setStandardHours(step.getStandardHours());
                rl.setLaborCount(step.getLaborCount());
                rl.setLaborRate(step.getLaborRate());
                rl.setMachineCount(step.getMachineCount());
                rl.setMachineRate(step.getMachineRate());
                BigDecimal stepCost = computeStepCost(step);
                rl.setStepCost(stepCost);
                routingLines.add(rl);
                if (stepCost == null) {
                    allStepsCostable = false;
                } else {
                    totalRoutingCost = totalRoutingCost.add(stepCost);
                }
            }
            routingAvailable = allStepsCostable;
        }
        result.setRoutingLines(routingLines);
        result.setRoutingCostAvailable(routingAvailable);
        result.setTotalRoutingCost(routingAvailable ? totalRoutingCost : BigDecimal.ZERO);
        result.setTotalCostPerUnit(result.getTotalMaterialCost().add(result.getTotalRoutingCost()));
        return result;
    }

    /**
     * 计算单步工序成本: 标准工时 × (人工费率×人数 + 机器费率×机台数)
     * 任一费率/工时为空返回 null(表示该步不可计算)
     */
    private BigDecimal computeStepCost(BdRoutingStep step) {
        if (step.getStandardHours() == null) {
            return null;
        }
        BigDecimal laborPart = BigDecimal.ZERO;
        BigDecimal machinePart = BigDecimal.ZERO;
        boolean hasLabor = step.getLaborRate() != null && step.getLaborCount() != null;
        boolean hasMachine = step.getMachineRate() != null && step.getMachineCount() != null;
        if (!hasLabor && !hasMachine) {
            return null;
        }
        if (hasLabor) {
            laborPart = step.getLaborRate().multiply(BigDecimal.valueOf(step.getLaborCount()));
        }
        if (hasMachine) {
            machinePart = step.getMachineRate().multiply(BigDecimal.valueOf(step.getMachineCount()));
        }
        return step.getStandardHours().multiply(laborPart.add(machinePart));
    }

    /**
     * 构建BOM摘要信息
     */
    private BomComparisonVo.BomSummary buildBomSummary(BdBom bom) {
        BomComparisonVo.BomSummary summary = new BomComparisonVo.BomSummary();
        summary.setBomId(bom.getBomId());
        summary.setBomName(bom.getBomName());
        summary.setBomVersion(bom.getBomVersion());
        summary.setBomType(bom.getBomType());
        if (bom.getParentMaterialId() != null) {
            BdMaterial material = materialMapper.selectById(bom.getParentMaterialId());
            if (material != null) {
                summary.setMaterialCode(material.getMaterialCode());
                summary.setMaterialName(material.getMaterialName());
            }
        }
        // 统计明细行数
        Long count = bomDetailMapper.selectCount(
            Wrappers.lambdaQuery(BdBomDetail.class).eq(BdBomDetail::getBomId, bom.getBomId()));
        summary.setTotalItems(count != null ? count.intValue() : 0);
        return summary;
    }

    /**
     * 比较两个BigDecimal是否不同
     */
    private boolean compareDecimal(BigDecimal a, BigDecimal b) {
        if (a == null && b == null) return false;
        if (a == null || b == null) return true;
        return a.compareTo(b) != 0;
    }

    /**
     * 查找物料的当前有效BOM
     */
    private BdBom findCurrentBom(Long materialId) {
        if (materialId == null) {
            return null;
        }
        return baseMapper.selectOne(
            Wrappers.lambdaQuery(BdBom.class)
                .eq(BdBom::getParentMaterialId, materialId)
                .eq(BdBom::getIsCurrent, 1L)
                .eq(BdBom::getStatus, 1L)
        );
    }

    private LambdaQueryWrapper<BdBom> buildQueryWrapper(BdBomBo bo) {
        LambdaQueryWrapper<BdBom> lqw = Wrappers.lambdaQuery();
        lqw.eq(StringUtils.isNotBlank(bo.getBomVersion()), BdBom::getBomVersion, bo.getBomVersion());
        lqw.like(StringUtils.isNotBlank(bo.getBomName()), BdBom::getBomName, bo.getBomName());
        lqw.eq(bo.getParentMaterialId() != null, BdBom::getParentMaterialId, bo.getParentMaterialId());
        lqw.eq(bo.getIsCurrent() != null, BdBom::getIsCurrent, bo.getIsCurrent());
        lqw.eq(bo.getStatus() != null, BdBom::getStatus, bo.getStatus());
        lqw.eq(StringUtils.isNotBlank(bo.getBomType()), BdBom::getBomType, bo.getBomType());
        lqw.eq(bo.getRoutingId() != null, BdBom::getRoutingId, bo.getRoutingId());
        return lqw;
    }
}
