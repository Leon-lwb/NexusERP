package com.nexus.core.prd.service;

import java.util.List;
import com.nexus.core.prd.domain.PrdMaterialRequirement;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * 物料清单Service接口
 *
 * @author nexus
 * @date 2025-11-21
 */
public interface IPrdMaterialRequirementService extends IService<PrdMaterialRequirement> {
    /**
     * 查询物料清单
     *
     * @param requirementId 物料清单主键
     * @return 物料清单
     */
    public PrdMaterialRequirement selectPrdMaterialRequirementByRequirementId(Long requirementId);

    /**
     * 查询物料清单列表
     *
     * @param prdMaterialRequirement 物料清单
     * @return 物料清单集合
     */
    public List<PrdMaterialRequirement> selectPrdMaterialRequirementList(PrdMaterialRequirement prdMaterialRequirement);

    /**
     * 新增物料清单
     *
     * @param prdMaterialRequirement 物料清单
     * @return 结果
     */
    public int insertPrdMaterialRequirement(PrdMaterialRequirement prdMaterialRequirement);

    /**
     * 修改物料清单
     *
     * @param prdMaterialRequirement 物料清单
     * @return 结果
     */
    public int updatePrdMaterialRequirement(PrdMaterialRequirement prdMaterialRequirement);

    /**
     * 批量删除物料清单
     *
     * @param requirementIds 需要删除的物料清单主键集合
     * @return 结果
     */
    public int deletePrdMaterialRequirementByRequirementIds(Long[] requirementIds);

    /**
     * 删除物料清单信息
     *
     * @param requirementId 物料清单主键
     * @return 结果
     */
    public int deletePrdMaterialRequirementByRequirementId(Long requirementId);
}
