package org.dromara.erp.prd.service;

import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.erp.prd.domain.PrdMaterialRequirement;
import org.dromara.erp.prd.domain.PrdProductionPlan;
import org.dromara.erp.prd.domain.bo.PrdProductionPlanBo;
import org.dromara.erp.bd.domain.vo.BdBomDetailVo;
import org.dromara.erp.prd.domain.vo.PrdProductionPlanVo;

import java.util.List;

/**
 * 生产计划Service接口
 *
 * @author ruoyi
 */
public interface IPrdProductionPlanService {

    TableDataInfo<PrdProductionPlanVo> selectPageList(PrdProductionPlanBo bo, PageQuery pageQuery);

    List<PrdProductionPlanVo> selectList(PrdProductionPlanBo bo);

    PrdProductionPlanVo selectById(Long id);

    PrdProductionPlan selectPrdProductionPlanByPlanId(Long planId);

    List<PrdProductionPlan> selectPrdProductionPlanList(PrdProductionPlan prdProductionPlan);

    int insertPrdProductionPlan(PrdProductionPlan prdProductionPlan);

    int insertByBo(PrdProductionPlanBo bo);

    int updateByBo(PrdProductionPlanBo bo);

    int updatePrdProductionPlan(PrdProductionPlan prdProductionPlan);

    int deleteByIds(Long[] ids);

    int deletePrdProductionPlanByPlanIds(Long[] planIds);

    int deletePrdProductionPlanByPlanId(Long planId);

    int updatePrdProductionPlanVersion(PrdProductionPlan prdProductionPlan);

    List<BdBomDetailVo> calculateBomTree(Long planId);

    List<PrdMaterialRequirement> generateMaterialRequirementsWithDetails(Long planId);
}
