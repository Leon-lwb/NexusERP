package com.nexus.erp.prd.service;

import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.erp.prd.domain.PrdMaterialRequirement;
import com.nexus.erp.prd.domain.PrdProductionPlan;
import com.nexus.erp.prd.domain.bo.PrdProductionPlanBo;
import com.nexus.erp.bd.domain.vo.BdBomDetailVo;
import com.nexus.erp.prd.domain.vo.PrdProductionPlanVo;

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

    /**
     * 从物料需求单生成生产工单
     * 选中物料需求记录，一键生成对应的生产工单（状态=CREATED）
     *
     * @param planId         生产计划ID
     * @param requirementIds 物料需求单ID列表
     * @return 生成的工单数量
     */
    Integer generateWorkOrders(Long planId, List<Long> requirementIds);
}
