package com.nexus.core.prd.mapper;

import java.util.List;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.nexus.core.prd.domain.PrdMaterialRequirement;
import com.nexus.core.prd.domain.PrdMaterialRequirementDetail;

/**
 * 物料清单Mapper接口
 *
 * @author nexus
 * @date 2025-11-21
 */
public interface PrdMaterialRequirementMapper extends BaseMapper<PrdMaterialRequirement> {
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
     * 查询带明细的物料清单列表
     *
     * @param prdMaterialRequirement 物料清单
     * @return 物料清单集合（包含明细）
     */
    public List<PrdMaterialRequirement> selectPrdMaterialRequirementListWithDetails(PrdMaterialRequirement prdMaterialRequirement);

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
     * 删除物料清单
     *
     * @param requirementId 物料清单主键
     * @return 结果
     */
    public int deletePrdMaterialRequirementByRequirementId(Long requirementId);

    /**
     * 批量删除物料清单
     *
     * @param requirementIds 需要删除的数据主键集合
     * @return 结果
     */
    public int deletePrdMaterialRequirementByRequirementIds(Long[] requirementIds);

        /**
         * 批量删除物料需求单明细
         *
         * @param requirementIds 需要删除的数据主键集合
         * @return 结果
         */
        public int deletePrdMaterialRequirementDetailByRequirementIds(Long[] requirementIds);

        /**
         * 批量新增物料需求单明细
         *
         * @param prdMaterialRequirementDetailList 物料需求单明细列表
         * @return 结果
         */
        public int batchPrdMaterialRequirementDetail(List<PrdMaterialRequirementDetail> prdMaterialRequirementDetailList);


        /**
         * 通过物料清单主键删除物料需求单明细信息
         *
         * @param requirementId 物料清单ID
         * @return 结果
         */
        public int deletePrdMaterialRequirementDetailByRequirementId(Long requirementId);
        
        /**
         * 新增物料需求单明细
         *
         * @param prdMaterialRequirementDetail 物料需求单明细
         * @return 结果
         */
        public int insertPrdMaterialRequirementDetail(PrdMaterialRequirementDetail prdMaterialRequirementDetail);
        
        /**
         * 根据需求单ID查询明细列表
         *
         * @param requirementId 需求单ID
         * @return 明细列表
         */
        public List<PrdMaterialRequirementDetail> selectPrdMaterialRequirementDetailList(Long requirementId);
}