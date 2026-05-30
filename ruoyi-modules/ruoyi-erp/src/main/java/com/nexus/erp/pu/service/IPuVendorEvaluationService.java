package com.nexus.erp.pu.service;

import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.erp.pu.domain.bo.PuVendorEvaluationBo;
import com.nexus.erp.pu.domain.vo.PuVendorEvaluationVo;

import java.util.List;
import java.util.Map;

/**
 * 供应商评估服务接口
 * <p>
 * 定义供应商绩效评估的业务操作，包括 CRUD 和评估计算功能。
 * 评估计算基于采购订单、退货单等历史数据，从交货及时率、质量合格率、
 * 价格竞争力三个维度综合计算供应商得分和等级。
 *
 * @author ruoyi
 */
public interface IPuVendorEvaluationService {

    /**
     * 分页查询供应商评估列表
     *
     * @param bo        查询条件
     * @param pageQuery 分页参数
     * @return 分页结果
     */
    TableDataInfo<PuVendorEvaluationVo> selectPageList(PuVendorEvaluationBo bo, PageQuery pageQuery);

    /**
     * 查询供应商评估列表（不分页）
     *
     * @param bo 查询条件
     * @return 供应商评估列表
     */
    List<PuVendorEvaluationVo> selectList(PuVendorEvaluationBo bo);

    /**
     * 根据主键查询供应商评估详情
     *
     * @param evaluationId 评估记录ID
     * @return 供应商评估视图对象
     */
    PuVendorEvaluationVo selectById(Long evaluationId);

    /**
     * 新增供应商评估记录
     *
     * @param bo 供应商评估业务对象
     * @return 影响行数
     */
    int insertByBo(PuVendorEvaluationBo bo);

    /**
     * 修改供应商评估记录
     *
     * @param bo 供应商评估业务对象
     * @return 影响行数
     */
    int updateByBo(PuVendorEvaluationBo bo);

    /**
     * 批量删除供应商评估记录
     *
     * @param evaluationIds 评估记录ID数组
     * @return 影响行数
     */
    int deleteByIds(Long[] evaluationIds);

    /**
     * 按供应商计算绩效评估
     * <p>
     * 统计指定供应商在当前评估周期内的交货及时率、质量合格率、价格竞争力，
     * 计算综合评分并保存或更新评估记录。
     *
     * @param vendorId 供应商ID
     */
    void calculateByVendor(Long vendorId);

    /**
     * 批量计算所有供应商绩效评估
     * <p>
     * 扫描所有状态为 ACTIVE 的供应商，逐条触发绩效评估计算。
     * 通常由定时任务调用。
     */
    void batchCalculateAll();

    /**
     * 按供应商查询评估趋势
     * <p>
     * 查询指定供应商的所有评估记录，按评估周期升序排列，用于趋势图展示。
     *
     * @param vendorId 供应商ID
     * @return 供应商评估趋势列表
     */
    List<PuVendorEvaluationVo> selectTrendByVendorId(Long vendorId);

    /**
     * 查询供应商评估等级分布
     * <p>
     * 统计所有评估记录中 A/B/C/D 四个等级的供应商数量。
     *
     * @return 等级分布统计，key 为等级，value 为数量
     */
    Map<String, Integer> selectLevelDistribution();
}
