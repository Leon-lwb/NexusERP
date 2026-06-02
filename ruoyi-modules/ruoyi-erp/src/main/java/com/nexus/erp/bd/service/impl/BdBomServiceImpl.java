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
import com.nexus.erp.bd.domain.vo.BomTreeNodeVo;
import com.nexus.erp.bd.domain.vo.IndentedBomLineVo;
import com.nexus.erp.bd.mapper.BdBomMapper;
import com.nexus.erp.bd.mapper.BdMaterialMapper;
import com.nexus.erp.bd.service.IBdBomService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.nexus.erp.bd.domain.BdBomDetail;
import com.nexus.erp.bd.mapper.BdBomDetailMapper;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
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

    private final BdBomMapper baseMapper;
    private final BdBomDetailMapper bomDetailMapper;
    private final BdMaterialMapper materialMapper;

    @Override
    public TableDataInfo<BdBomVo> selectPageList(BdBomBo bo, PageQuery pageQuery) {
        LambdaQueryWrapper<BdBom> lqw = buildQueryWrapper(bo);
        Page<BdBomVo> page = baseMapper.selectVoPage(pageQuery.build(), lqw);
        return TableDataInfo.build(page);
    }

    @Override
    public List<BdBomVo> selectList(BdBomBo bo) {
        return baseMapper.selectVoList(buildQueryWrapper(bo));
    }

    @Override
    public BdBomVo selectById(Long id) {
        return baseMapper.selectVoById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int insertByBo(BdBomBo bo) {
        BdBom entity = MapstructUtils.convert(bo, BdBom.class);
        return baseMapper.insert(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updateByBo(BdBomBo bo) {
        BdBom entity = MapstructUtils.convert(bo, BdBom.class);
        return baseMapper.updateById(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteByIds(Long[] ids) {
        return baseMapper.deleteBatchIds(List.of(ids));
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
        // 递归构建子树
        List<BdBomDetail> details = bomDetailMapper.selectList(
            Wrappers.lambdaQuery(BdBomDetail.class).eq(BdBomDetail::getBomId, bomId));
        List<BomTreeNodeVo> children = new ArrayList<>();
        for (BdBomDetail detail : details) {
            BomTreeNodeVo childNode = buildTreeNode(detail, bomId, 1);
            children.add(childNode);
        }
        rootNode.setChildren(children);
        rootNode.setLeaf(children.isEmpty());
        return rootNode;
    }

    private BomTreeNodeVo buildTreeNode(BdBomDetail detail, Long parentBomId, int level) {
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
            // 递归展开子BOM
            List<BdBomDetail> childDetails = bomDetailMapper.selectList(
                Wrappers.lambdaQuery(BdBomDetail.class).eq(BdBomDetail::getBomId, childBom.getBomId()));
            List<BomTreeNodeVo> grandchildren = new ArrayList<>();
            for (BdBomDetail childDetail : childDetails) {
                grandchildren.add(buildTreeNode(childDetail, childBom.getBomId(), level + 1));
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
        topLine.setExpandable(true);
        result.add(topLine);
        // 递归添加子节点
        buildIndentedLines(bomId, 1, BigDecimal.ONE, result);
        return result;
    }

    private void buildIndentedLines(Long bomId, int level, BigDecimal parentCumulativeQty, List<IndentedBomLineVo> result) {
        List<BdBomDetail> details = bomDetailMapper.selectList(
            Wrappers.lambdaQuery(BdBomDetail.class).eq(BdBomDetail::getBomId, bomId));
        for (BdBomDetail detail : details) {
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
            // 检查是否有子BOM
            BdBom childBom = findCurrentBom(detail.getChildMaterialId());
            if (childBom != null) {
                line.setNodeType("ASSEMBLY");
                line.setBomId(childBom.getBomId());
                line.setExpandable(true);
                result.add(line);
                // 递归添加子节点
                buildIndentedLines(childBom.getBomId(), level + 1, line.getCumulativeQuantity(), result);
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
        // 只返回叶子节点（PART类型）
        return allLines.stream()
            .filter(line -> "PART".equals(line.getNodeType()))
            .collect(Collectors.toList());
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
        return lqw;
    }
}
