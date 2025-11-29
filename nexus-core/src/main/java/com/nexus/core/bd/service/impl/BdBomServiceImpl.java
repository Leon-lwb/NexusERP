package com.nexus.core.bd.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import java.util.List;
import java.math.BigDecimal;
import com.nexus.common.utils.DateUtils;
import com.nexus.core.bd.domain.dto.BomDetailDTO;
import com.nexus.core.bd.domain.vo.BomDetailVO;
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
    @Transactional
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

        if (bomDetailDTO.getOperationStep() == null) {
            throw new IllegalArgumentException("工序步骤不能为空");
        }

        return getBaseMapper().insertBomDetail(bomDetailDTO);
    }

    /**
     * 修改BOM明细
     * @param bomDetailDTO BOM明细DTO
     * @return 修改结果影响行数
     */
    @Override
    public int updateBomDetail(BomDetailDTO bomDetailDTO) {
        if (bomDetailDTO == null) {
            throw new IllegalArgumentException("BOM明细数据不能为空");
        }
        
        if (bomDetailDTO.getDetailId() == null) {
            throw new IllegalArgumentException("BOM明细ID不能为空");
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

        if (bomDetailDTO.getOperationStep() == null) {
            throw new IllegalArgumentException("工序步骤不能为空");
        }
        
        return getBaseMapper().updateBomDetail(bomDetailDTO);
    }

    @Override
    public int deleteBomDetails(Long[] detailIds) {
        return 0;
    }

    @Override
    public List<BomDetailVO> selectBomDetailList(Long bomId) {
        if (bomId == null) {
            throw new IllegalArgumentException("BOM ID不能为空");
        }
        return getBaseMapper().selectBomDetailList(bomId);
    }

    /**
     * 根据明细ID查询BOM明细
     *
     * @param detailId 明细ID
     * @return BOM明细
     */
    @Override
    public String selectBomDetailByDetailId(Long detailId) {
        if (detailId == null) {
            throw new IllegalArgumentException("明细ID不能为空");
        }
        return getBaseMapper().selectBomDetailByDetailId(detailId);
    }

    @Override
    @Transactional
    public int saveBomDetailsBatch(List<BomDetailDTO> detailList) {
        if (detailList == null || detailList.isEmpty()) {
            return 0;
        }
        
        int count = 0;
        for (BomDetailDTO detail : detailList) {
            if (detail.getDetailId() != null) {
                // 更新现有记录
                count += getBaseMapper().updateBomDetail(detail);
            } else {
                // 新增记录
                // 检查必填字段
                if (detail.getBomId() == null) {
                    throw new IllegalArgumentException("BOM主表ID不能为空");
                }
                
                if (detail.getChildMaterialId() == null) {
                    throw new IllegalArgumentException("子项物料ID不能为空");
                }
                
                if (detail.getQuantityPerParent() == null) {
                    throw new IllegalArgumentException("单位父项用量不能为空");
                }
                
                // 检查数量必须大于0
                if (detail.getQuantityPerParent().compareTo(BigDecimal.ZERO) <= 0) {
                    throw new IllegalArgumentException("单位父项用量必须大于0");
                }
                
                // 检查损耗率范围
                if (detail.getLossRate() != null && 
                    (detail.getLossRate().compareTo(BigDecimal.ZERO) < 0 || 
                     detail.getLossRate().compareTo(new BigDecimal("100")) > 0)) {
                    throw new IllegalArgumentException("损耗率必须在0-100之间");
                }

                if (detail.getOperationStep() == null) {
                    throw new IllegalArgumentException("工序步骤不能为空");
                }
                
                count += getBaseMapper().insertBomDetail(detail);
            }
        }
        
        return count;
    }

    @Override
    public String getBomStructure(Long parentId) {
        return null;
    }

    @Override
    public int copyBom(Long sourceBomId, Long targetParentId) {
        return 0;
    }

    @Override
    public String getCurrentBom(Long parentId) {
        return null;
    }
}