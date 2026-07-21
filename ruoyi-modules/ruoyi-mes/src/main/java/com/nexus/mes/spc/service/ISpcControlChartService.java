package com.nexus.mes.spc.service;

import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.mes.spc.domain.bo.SpcControlChartBo;
import com.nexus.mes.spc.domain.vo.SpcControlChartVo;

import java.util.Map;

/**
 * MES SPC控制图 Service 业务层接口
 * <p>
 * 图表类型: X_BAR_R/X_BAR_S/P/C/U
 * analyze: 拉采集点数据→按样本大小分组→计算UCL/CL/LCL→回写→重建数据点+OOC判定
 * OOC规则: RULE1(超限)/RULE2(连续9点同侧)/RULE3(连续6点递增递减)/RULE4(连续14点交替)
 *
 * @author Nexus
 */
public interface ISpcControlChartService {

    /** 图表类型: 均值-极差 */
    String TYPE_X_BAR_R = "X_BAR_R";
    /** 图表类型: 均值-标准差 */
    String TYPE_X_BAR_S = "X_BAR_S";
    /** 图表类型: 不良率 */
    String TYPE_P = "P";
    /** 图表类型: 缺陷数 */
    String TYPE_C = "C";
    /** 图表类型: 单位缺陷数 */
    String TYPE_U = "U";

    /** 状态: 启用 */
    String STATUS_ACTIVE = "ACTIVE";
    /** 状态: 停用 */
    String STATUS_INACTIVE = "INACTIVE";

    /** OOC 规则：1 点超出控制限（最严重） */
    String OOC_RULE_1 = "RULE1";
    /** OOC 规则：连续 9 点在 CL 同侧 */
    String OOC_RULE_2 = "RULE2";
    /** OOC 规则：连续 6 点严格递增或递减 */
    String OOC_RULE_3 = "RULE3";
    /** OOC 规则：连续 14 点交替上下 */
    String OOC_RULE_4 = "RULE4";

    /**
     * 分页查询 SPC 控制图列表。
     *
     * @param bo        控制图查询条件（名称/类型/状态/采集点/物料）
     * @param pageQuery 分页参数（页码、每页大小、排序）
     * @return 分页结果，包含控制图视图列表及分页元信息
     */
    TableDataInfo<SpcControlChartVo> selectPageList(SpcControlChartBo bo, PageQuery pageQuery);

    /**
     * 根据主键 ID 获取 SPC 控制图详情。
     *
     * @param chartId 控制图 ID
     * @return 控制图视图对象
     */
    SpcControlChartVo selectById(Long chartId);

    /**
     * 新增 SPC 控制图，默认样本大小为 5、状态为启用（ACTIVE）。
     *
     * @param bo 控制图业务对象
     * @return 新增后的控制图视图对象
     */
    SpcControlChartVo insertByBo(SpcControlChartBo bo);

    /**
     * 编辑 SPC 控制图基础信息（控制限由 analyze 接口计算回写，不在此更新）。
     *
     * @param bo 控制图业务对象（需包含 chartId）
     * @return 编辑后的控制图视图对象
     */
    SpcControlChartVo updateByBo(SpcControlChartBo bo);

    /**
     * 批量删除 SPC 控制图（级联删除其下数据点）。
     *
     * @param ids 待删除的控制图 ID 数组
     * @return 是否删除成功
     */
    Boolean deleteByIds(Long[] ids);

    /**
     * 分析控制图: 拉数据→分组→计算控制限→回写→重建数据点+OOC判定
     *
     * @param chartId 控制图ID
     * @return { chart, points, oocCount, ucl, cl, lcl }
     */
    Map<String, Object> analyze(Long chartId);
}
