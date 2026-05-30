package com.nexus.erp.prd.service;

import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.erp.prd.domain.PrdMaterialRequirement;
import com.nexus.erp.prd.domain.PrdMaterialRequirementDetail;
import com.nexus.erp.prd.domain.bo.PrdMaterialRequirementBo;
import com.nexus.erp.prd.domain.vo.PrdMaterialRequirementVo;

import java.math.BigDecimal;
import java.util.List;

/**
 * 物料清单 Service 接口
 * <p>
 * 定义物料需求清单相关的业务逻辑操作，包括物料需求的查询、BOM 导入、净需求计算及树形展示等。
 *
 * @author ruoyi
 */
public interface IPrdMaterialRequirementService {

    /**
     * 分页查询物料需求清单列表
     *
     * @param bo        查询参数，包含物料编码、生产计划编号等筛选条件
     * @param pageQuery 分页参数，包含页码、每页条数及排序信息
     * @return 物料需求清单分页列表
     */
    TableDataInfo<PrdMaterialRequirementVo> selectPageList(PrdMaterialRequirementBo bo, PageQuery pageQuery);

    /**
     * 查询物料需求清单列表（不分页）
     *
     * @param bo 查询参数，包含物料编码、生产计划编号等筛选条件
     * @return 物料需求清单列表
     */
    List<PrdMaterialRequirementVo> selectList(PrdMaterialRequirementBo bo);

    /**
     * 根据 ID 查询物料需求清单详情（含明细）
     *
     * @param id 物料需求 ID
     * @return 物料需求清单详细信息（含明细列表）
     */
    PrdMaterialRequirementVo selectById(Long id);

    /**
     * 根据物料需求 ID 查询物料需求实体
     *
     * @param requirementId 物料需求 ID
     * @return 物料需求实体
     */
    PrdMaterialRequirement selectPrdMaterialRequirementByRequirementId(Long requirementId);

    /**
     * 查询物料需求清单列表（按实体条件）
     *
     * @param prdMaterialRequirement 物料需求查询实体
     * @return 物料需求清单列表
     */
    List<PrdMaterialRequirement> selectPrdMaterialRequirementList(PrdMaterialRequirement prdMaterialRequirement);

    /**
     * 查询物料需求清单列表（含明细）
     *
     * @param prdMaterialRequirement 物料需求查询实体
     * @return 物料需求清单列表（每条记录含明细列表）
     */
    List<PrdMaterialRequirement> selectPrdMaterialRequirementListWithDetails(PrdMaterialRequirement prdMaterialRequirement);

    /**
     * 新增物料需求清单（含明细）
     *
     * @param prdMaterialRequirement 物料需求实体（含明细列表）
     * @return 影响的行数
     */
    int insertPrdMaterialRequirement(PrdMaterialRequirement prdMaterialRequirement);

    /**
     * 新增物料需求清单（通过 BO，含明细）
     *
     * @param bo 物料需求业务对象（含明细列表）
     * @return 影响的行数
     */
    int insertByBo(PrdMaterialRequirementBo bo);

    /**
     * 修改物料需求清单（通过 BO，含明细）
     * <p>
     * 先删除原有明细，再插入新的明细列表。
     *
     * @param bo 物料需求业务对象（含明细列表）
     * @return 影响的行数
     */
    int updateByBo(PrdMaterialRequirementBo bo);

    /**
     * 修改物料需求清单（含明细）
     *
     * @param prdMaterialRequirement 物料需求实体（含明细列表）
     * @return 影响的行数
     */
    int updatePrdMaterialRequirement(PrdMaterialRequirement prdMaterialRequirement);

    /**
     * 批量删除物料需求清单（含明细）
     *
     * @param ids 要删除的物料需求 ID 数组
     * @return 影响的行数
     */
    int deleteByIds(Long[] ids);

    /**
     * 根据物料需求 ID 数组批量删除物料需求清单（含明细）
     *
     * @param requirementIds 物料需求 ID 数组
     * @return 影响的行数
     */
    int deletePrdMaterialRequirementByRequirementIds(Long[] requirementIds);

    /**
     * 根据物料需求 ID 删除物料需求清单（含明细）
     *
     * @param requirementId 物料需求 ID
     * @return 影响的行数
     */
    int deletePrdMaterialRequirementByRequirementId(Long requirementId);

    /**
     * 批量新增物料需求明细
     *
     * @param detailList 物料需求明细列表
     * @return 影响的行数
     */
    int insertPrdMaterialRequirementDetails(List<PrdMaterialRequirementDetail> detailList);

    /**
     * 根据 BOM ID 导入物料需求明细
     * <p>
     * 根据 BOM 展开计算各子项物料的需求数量，并补充物料基础信息。
     *
     * @param bomId                 BOM ID
     * @param totalRequiredQuantity 需求总量
     * @return 物料需求明细列表
     */
    List<PrdMaterialRequirementDetail> importBomDetails(Long bomId, BigDecimal totalRequiredQuantity);

    /**
     * 根据计划编号查询物料需求树（含父子关系嵌套）
     *
     * @param planNo 生产计划编号
     * @return 物料需求树形列表（顶层节点）
     */
    List<PrdMaterialRequirementVo> selectPrdMaterialRequirementTreeByPlanNo(String planNo);

    /**
     * 计算单个物料明细的净需求
     * <p>
     * 逻辑：毛需求 - 现有库存 - 安全库存 = 净需求（短缺量）。
     * 更新明细的 stockQuantity 和 shortageQuantity 字段。
     *
     * @param detail 物料需求明细（需已设置 materialId 和 requiredQuantity）
     */
    void calculateNetRequirements(PrdMaterialRequirementDetail detail);
}
