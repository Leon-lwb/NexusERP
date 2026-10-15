package com.nexus.common.report.catalog;

/** 业务模块提供代码批准且版本固定的ERP实体目录。 */
public interface ReportEntityCatalogProvider {
    /**
     * 返回不可变实体目录；同版本不能修改字段或策略。
     *
     * @return 当前受控处理的固定结果
     */
    ReportEntityCatalog catalog();
}
