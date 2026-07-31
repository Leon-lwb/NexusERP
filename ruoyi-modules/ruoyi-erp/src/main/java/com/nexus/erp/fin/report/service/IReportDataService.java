package com.nexus.erp.fin.report.service;

import com.nexus.erp.fin.report.domain.bo.ReportItemBo;
import com.nexus.erp.fin.report.domain.vo.ReportItemVo;
import com.nexus.erp.fin.report.domain.vo.ReportLineVo;

import java.util.List;

/**
 * 报表取数引擎 Service(三大报表统一取数)
 *
 * @author nexus
 * @date 2026-07-31
 */
public interface IReportDataService {

    /**
     * 资产负债表:按期间取期末余额,按 report_item(BALANCE) 映射
     * <p>金额=期末净额×sign;资产类借方向科目取 closing_debit-closing_credit,贷方科目(累计折旧)取反;负债/权益类取 closing_credit-closing_debit。</p>
     *
     * @param periodId    会计期间ID
     * @param includeZero 是否包含零金额项目
     * @return 报表行列表(按 sortOrder 排序)
     */
    List<ReportLineVo> balanceSheet(Long periodId, boolean includeZero);

    /**
     * 利润表:本期或本年累计发生额,按 report_item(INCOME) 映射
     * <p>收入科目(CREDIT 方向)取 period_credit-period_debit;费用科目(DEBIT 方向)取 period_debit-period_credit。本年累计=1月~本期各期发生合计。</p>
     *
     * @param periodId   会计期间ID
     * @param yearToDate true-本年累计 false-本期
     * @return 报表行列表
     */
    List<ReportLineVo> incomeStatement(Long periodId, boolean yearToDate);

    /**
     * 现金流量表(直接法基础版):科目区间发生额归集
     * <p>sign=1 取贷方发生(收现),sign=-1 取借方发生(付现)。</p>
     *
     * @param periodId   会计期间ID
     * @param yearToDate true-本年累计 false-本期
     * @return 报表行列表
     */
    List<ReportLineVo> cashFlow(Long periodId, boolean yearToDate);

    /**
     * 查询报表项目映射配置
     *
     * @param reportType 报表类型
     * @return 映射列表
     */
    List<ReportItemVo> itemConfig(String reportType);

    /**
     * 保存报表项目映射(新增/修改)
     *
     * @param bo 映射业务对象
     * @return 成功/失败
     */
    Boolean saveItemConfig(ReportItemBo bo);

    /**
     * 删除报表项目映射
     *
     * @param itemIds 项目ID数组
     * @return 成功/失败
     */
    Boolean deleteItemConfig(Long[] itemIds);
}
