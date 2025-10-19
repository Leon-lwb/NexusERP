package com.nexus.core.bd.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import java.util.List;
import java.math.BigDecimal;
import com.nexus.common.utils.DateUtils;
import com.nexus.core.bd.domain.dto.BomDetailDTO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.nexus.core.bd.mapper.BdBomMapper;
import com.nexus.core.bd.domain.BdBom;
import com.nexus.core.bd.service.IBdBomService;
import org.springframework.transaction.annotation.Transactional;

/**
 * 产品BOMService业务层处理
 *
 * @author nexus
 * @date 2025-10-18
 */
@Service
@Transactional
public class BdBomServiceImpl extends ServiceImpl<BdBomMapper, BdBom> implements IBdBomService {

    /**
     * 查询产品BOM
     *
     * @param bomId 产品BOM主键
     * @return 产品BOM
     */
    @Override
    public BdBom selectBdBomByBomId(Long bomId) {
        return getBaseMapper().selectBdBomByBomId(bomId);
    }

    /**
     * 查询产品BOM列表
     *
     * @param bdBom 产品BOM
     * @return 产品BOM
     */
    @Override
    public List<BdBom> selectBdBomList(BdBom bdBom) {
        return getBaseMapper().selectBdBomList(bdBom);
    }

    /**
     * 新增产品BOM
     *
     * @param bdBom 产品BOM
     * @return 结果
     */
    @Override
    public int insertBdBom(BdBom bdBom) {
        bdBom.setCreateTime(DateUtils.getNowDate());
        return getBaseMapper().insertBdBom(bdBom);
    }

    /**
     * 修改产品BOM
     *
     * @param bdBom 产品BOM
     * @return 结果
     */
    @Override
    public int updateBdBom(BdBom bdBom) {
        bdBom.setUpdateTime(DateUtils.getNowDate());
        return getBaseMapper().updateBdBom(bdBom);
    }

    /**
     * 批量删除产品BOM
     *
     * @param bomIds 需要删除的产品BOM主键
     * @return 结果
     */
    @Override
    public int deleteBdBomByBomIds(Long[] bomIds) {
        return getBaseMapper().deleteBdBomByBomIds(bomIds);
    }

    /**
     * 删除产品BOM信息
     *
     * @param bomId 产品BOM主键
     * @return 结果
     */
    @Override
    public int deleteBdBomByBomId(Long bomId) {
        return getBaseMapper().deleteBdBomByBomId(bomId);
    }

    /**
     * 创建BOM明细
     *
     * @param bomDetailDTO BOM明细数据传输对象
     * @return 插入结果影响行数
     */
    @Override
    public int insertBomDetail(BomDetailDTO bomDetailDTO) {
        // 在同一个事务中处理主表和明细表
        // 要么都成功，要么都失败
        if (bomDetailDTO == null) {
            throw new IllegalArgumentException("BOM明细数据不能为空");
        }
        
        // 检查必填字段
        if (bomDetailDTO.getBomId() == null) {
            throw new IllegalArgumentException("BOM主表ID不能为空");
        }
        
        if (bomDetailDTO.getChildMaterialId() == null) {
            throw new IllegalArgumentException("子项物料ID不能为空");
        }
        
        if (bomDetailDTO.getQuantityPerParent() == null) {
            throw new IllegalArgumentException("单位父项用量不能为空");
        }
        
        // 检查数量必须大于0
        if (bomDetailDTO.getQuantityPerParent().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("单位父项用量必须大于0");
        }
        
        // 检查损耗率范围
        if (bomDetailDTO.getLossRate() != null && 
            (bomDetailDTO.getLossRate().compareTo(BigDecimal.ZERO) < 0 || 
             bomDetailDTO.getLossRate().compareTo(new BigDecimal("100")) > 0)) {
            throw new IllegalArgumentException("损耗率必须在0-100之间");
        }

        return getBaseMapper().insertBomDetail(bomDetailDTO);
    }
}