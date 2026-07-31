package com.nexus.erp.fin.report.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.nexus.common.tenant.core.TenantEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * 报表项目-科目映射 report_item(报表取数引擎配置基座)
 * <p>每个报表项目绑定一组科目(account_code_match),支持前缀通配与取值符号。</p>
 *
 * @author nexus
 * @date 2026-07-31
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("report_item")
public class ReportItem extends TenantEntity {

    /** 项目ID */
    @TableId(value = "item_id")
    private Long itemId;

    /** 报表类型 BALANCE-资产负债表 INCOME-利润表 CASH-现金流量表 */
    private String reportType;

    /** 项目编码(如 BS_MONETARY) */
    private String itemCode;

    /** 项目名称(如 货币资金) */
    private String itemName;

    /** 显示顺序 */
    private Integer sortOrder;

    /** 父项目ID(分组行,0=顶级) */
    private Long parentItemId;

    /** 是否合计行 0-否 1-是(由子项汇总) */
    private String isSummary;

    /** 科目编码匹配(逗号分隔;100% 结尾通配) */
    private String accountCodeMatch;

    /** 取值符号 1-正 -1-负 */
    private String sign;

    /** 状态 0-正常 1-停用 */
    private String status;

    /** 乐观锁版本号 */
    @Version
    private Integer version;

    /** 逻辑删除标志 */
    @TableLogic
    private String delFlag;
}
