package org.dromara.erp.bd.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.utils.MapstructUtils;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.erp.bd.domain.BdBom;
import org.dromara.erp.bd.domain.bo.BdBomBo;
import org.dromara.erp.bd.domain.vo.BdBomVo;
import org.dromara.erp.bd.mapper.BdBomMapper;
import org.dromara.erp.bd.service.IBdBomService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.dynamic.datasource.annotation.DS;
import org.dromara.erp.bd.domain.BdBomDetail;
import org.dromara.erp.bd.mapper.BdBomDetailMapper;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

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
