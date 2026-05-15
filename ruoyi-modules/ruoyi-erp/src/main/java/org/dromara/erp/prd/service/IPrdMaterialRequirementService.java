package org.dromara.erp.prd.service;

import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.erp.prd.domain.PrdMaterialRequirement;
import org.dromara.erp.prd.domain.PrdMaterialRequirementDetail;
import org.dromara.erp.prd.domain.bo.PrdMaterialRequirementBo;
import org.dromara.erp.prd.domain.vo.PrdMaterialRequirementVo;

import java.math.BigDecimal;
import java.util.List;

/**
 * 物料清单Service接口
 *
 * @author ruoyi
 */
public interface IPrdMaterialRequirementService {

    TableDataInfo<PrdMaterialRequirementVo> selectPageList(PrdMaterialRequirementBo bo, PageQuery pageQuery);

    List<PrdMaterialRequirementVo> selectList(PrdMaterialRequirementBo bo);

    PrdMaterialRequirementVo selectById(Long id);

    PrdMaterialRequirement selectPrdMaterialRequirementByRequirementId(Long requirementId);

    List<PrdMaterialRequirement> selectPrdMaterialRequirementList(PrdMaterialRequirement prdMaterialRequirement);

    List<PrdMaterialRequirement> selectPrdMaterialRequirementListWithDetails(PrdMaterialRequirement prdMaterialRequirement);

    int insertPrdMaterialRequirement(PrdMaterialRequirement prdMaterialRequirement);

    int insertByBo(PrdMaterialRequirementBo bo);

    int updateByBo(PrdMaterialRequirementBo bo);

    int updatePrdMaterialRequirement(PrdMaterialRequirement prdMaterialRequirement);

    int deleteByIds(Long[] ids);

    int deletePrdMaterialRequirementByRequirementIds(Long[] requirementIds);

    int deletePrdMaterialRequirementByRequirementId(Long requirementId);

    int insertPrdMaterialRequirementDetails(List<PrdMaterialRequirementDetail> detailList);

    /**
     * 根据BOM ID导入物料需求明细
     *
     * @param bomId BOM ID
     * @param totalRequiredQuantity 需求总量
     * @return 物料需求明细列表
     */
    List<PrdMaterialRequirementDetail> importBomDetails(Long bomId, BigDecimal totalRequiredQuantity);
}
