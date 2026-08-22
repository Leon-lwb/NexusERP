package com.nexus.mes.release.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.nexus.common.tenant.core.TenantEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * MES发布包BOM选配对象 mes_release_pkg_bom_item
 * <p>在标准BOM/MBOM基础上做增量覆写: REPLACE(替换原物料)/ADD(新增行)/REMOVE(移除行)。
 * 定位方式: 按原物料 childMaterialId 定位(抗BOM版本变更), bomDetailId 可选精确指定。</p>
 *
 * @author Nexus
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("mes_release_pkg_bom_item")
public class ReleasePkgBomItem extends TenantEntity {

    /** 主键ID */
    @TableId(value = "item_id")
    private Long itemId;

    /** 发布包ID(mes_release_package.package_id) */
    private Long packageId;

    /** 操作类型: REPLACE/ADD/REMOVE */
    private String actionType;

    /** 原物料ID(REPLACE/REMOVE 定位键) */
    private Long childMaterialId;

    /** 原物料编码(冗余) */
    private String childMaterialCode;

    /** 原物料名称(冗余) */
    private String childMaterialName;

    /** 标准BOM明细行ID(可选精确定位, 空则按原物料定位) */
    private Long bomDetailId;

    /** 目标物料ID(REPLACE/ADD 必填) */
    private Long targetMaterialId;

    /** 目标物料编码(冗余) */
    private String targetMaterialCode;

    /** 目标物料名称(冗余) */
    private String targetMaterialName;

    /** 单位用量(空=REPLACE沿用原行/ADD必填) */
    private BigDecimal quantityPerParent;

    /** 排序 */
    private Integer sortOrder;

    /** 备注 */
    private String remark;

    /** 删除标志 */
    @TableLogic
    private String delFlag;
}
