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
 * 生产计划 Service 接口
 * <p>
 * 定义生产计划相关的业务逻辑操作，包括生产计划的查询、版本管理、BOM 计算、物料需求生成及工单生成等。
 *
 * @author nexus
 */
public interface IPrdProductionPlanService {

    /**
     * 分页查询生产计划列表
     *
     * @param bo        查询参数，包含计划编号、产品、日期范围等筛选条件
     * @param pageQuery 分页参数，包含页码、每页条数及排序信息
     * @return 生产计划分页列表
     */
    TableDataInfo<PrdProductionPlanVo> selectPageList(PrdProductionPlanBo bo, PageQuery pageQuery);

    /**
     * 查询生产计划列表（不分页）
     *
     * @param bo 查询参数，包含计划编号、产品、日期范围等筛选条件
     * @return 生产计划列表
     */
    List<PrdProductionPlanVo> selectList(PrdProductionPlanBo bo);

    /**
     * 根据 ID 查询生产计划详情
     *
     * @param id 生产计划 ID
     * @return 生产计划详细信息
     */
    PrdProductionPlanVo selectById(Long id);

    /**
     * 根据生产计划 ID 查询生产计划实体
     *
     * @param planId 生产计划 ID
     * @return 生产计划实体
     */
    PrdProductionPlan selectPrdProductionPlanByPlanId(Long planId);

    /**
     * 查询生产计划列表（按实体条件）
     *
     * @param prdProductionPlan 生产计划查询实体
     * @return 生产计划列表
     */
    List<PrdProductionPlan> selectPrdProductionPlanList(PrdProductionPlan prdProductionPlan);

    /**
     * 新增生产计划
     *
     * @param prdProductionPlan 生产计划实体
     * @return 影响的行数
     */
    int insertPrdProductionPlan(PrdProductionPlan prdProductionPlan);

    /**
     * 新增生产计划（通过 BO）
     *
     * @param bo 生产计划业务对象
     * @return 影响的行数
     */
    int insertByBo(PrdProductionPlanBo bo);

    /**
     * 修改生产计划（通过 BO）
     *
     * @param bo 生产计划业务对象
     * @return 影响的行数
     */
    int updateByBo(PrdProductionPlanBo bo);

    /**
     * 修改生产计划
     *
     * @param prdProductionPlan 生产计划实体
     * @return 影响的行数
     */
    int updatePrdProductionPlan(PrdProductionPlan prdProductionPlan);

    /**
     * 批量删除生产计划
     *
     * @param ids 要删除的生产计划 ID 数组
     * @return 影响的行数
     */
    int deleteByIds(Long[] ids);

    /**
     * 根据生产计划 ID 数组批量删除生产计划
     *
     * @param planIds 生产计划 ID 数组
     * @return 影响的行数
     */
    int deletePrdProductionPlanByPlanIds(Long[] planIds);

    /**
     * 根据生产计划 ID 删除生产计划
     *
     * @param planId 生产计划 ID
     * @return 影响的行数
     */
    int deletePrdProductionPlanByPlanId(Long planId);

    /**
     * 更新生产计划版本
     * <p>
     * 仅草稿状态的生产计划可更新版本，更新后版本号递增。
     *
     * @param prdProductionPlan 包含版本更新信息的生产计划实体
     * @return 影响的行数
     */
    int updatePrdProductionPlanVersion(PrdProductionPlan prdProductionPlan);

    /**
     * 根据生产计划计算 BOM 树
     * <p>
     * 查询生产计划关联订单行的物料对应的有效 BOM，并返回 BOM 明细树。
     *
     * @param planId 生产计划 ID
     * @return BOM 详情树节点列表
     */
    List<BdBomDetailVo> calculateBomTree(Long planId);

    /**
     * 根据生产计划 ID 生成物料需求清单及明细
     * <p>
     * 按节点拆分 BOM 树，每个有 BOM 的节点生成一条主表，其底层物料作为子表；
     * 子节点 BOM 递归拆分。
     *
     * @param planId 生产计划 ID
     * @return 生成的物料需求主表记录列表
     */
    List<PrdMaterialRequirement> generateMaterialRequirementsWithDetails(Long planId);

    /**
     * 从物料需求单生成生产工单
     * <p>
     * 选中物料需求记录，一键生成对应的生产工单（状态=CREATED）。
     *
     * @param planId         生产计划 ID
     * @param requirementIds 物料需求单 ID 列表
     * @return 生成的工单数量
     */
    Integer generateWorkOrders(Long planId, List<Long> requirementIds);
}
