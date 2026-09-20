package com.nexus.erp.so.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import com.nexus.common.tenant.core.TenantEntity;
import com.nexus.erp.so.domain.vo.SoPriceListVo;

import java.time.LocalDate;
import java.util.List;

/**
 * 销售价格表对象 so_price_list
 *
 * @author nexus
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("so_price_list")
@AutoMapper(target = SoPriceListVo.class)
public class SoPriceList extends TenantEntity {

    /** 价格表ID */
    @TableId(value = "price_list_id", type = IdType.ASSIGN_ID)
    private Long priceListId;

    /** 价格表编码 */
    private String listCode;
    /** 价格表名称 */
    private String listName;
    /** 价格表类型 */
    private String listType;
    /** 客户ID */
    private Long clientId;
    /** 币种 */
    private String currency;
    /** 优先级 */
    private Integer priority;
    /** 生效日期 */
    private LocalDate validFrom;
    /** 失效日期 */
    private LocalDate validTo;
    /** 状态 */
    private String status;

    /** 价格表行列表（非数据库字段） */
    @TableField(exist = false)
    private List<SoPriceListLine> soPriceListLineList;

    /**
     * 删除标志（ERP-SO-05：由物理删除改为逻辑删除，历史报价/订单可追溯当时的价目表行）
     */
    @TableLogic
    private String delFlag;
}
