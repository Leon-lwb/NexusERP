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
     * 分页查询控制图列表
     */
    TableDataInfo<SpcControlChartVo> selectPageList(SpcControlChartBo bo, PageQuery pageQuery);

    /**
     * 根据ID获取控制图详情
     */
    SpcControlChartVo selectById(Long chartId);

    /**
     * 新增控制图
     */
    SpcControlChartVo insertByBo(SpcControlChartBo bo);

    /**
     * 编辑控制图
     */
    SpcControlChartVo updateByBo(SpcControlChartBo bo);

    /**
     * 批量删除控制图
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
