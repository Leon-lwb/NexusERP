package com.nexus.mes.fw.domain.vo;

import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import com.nexus.mes.fw.domain.FwTestItem;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * MES测试项标准视图对象 fw_test_item
 * <p>M3 测试项标准页启用, M1 随 DDL-ORM 对齐一并建立。</p>
 *
 * @author Nexus
 */
@Data
@AutoMapper(target = FwTestItem.class)
public class FwTestItemVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 测试项ID */
    private Long testItemId;

    /** 测试项编码 */
    private String itemCode;

    /** 测试项名称 */
    private String itemName;

    /** 适用物料ID */
    private Long materialId;

    /** 固件ID(0=该物料所有版本通用) */
    private Long firmwareId;

    /** 数据类型: NUMERIC/TEXT/ENUM */
    private String dataType;

    /** 单位 */
    private String unit;

    /** 上限 */
    private BigDecimal upperLimit;

    /** 下限 */
    private BigDecimal lowerLimit;

    /** 目标值 */
    private BigDecimal targetValue;

    /** ENUM合格值(JSON数组) */
    private String passEnum;

    /** 是否必测(0否/1是) */
    private String isRequired;

    /** 排序 */
    private Integer sortOrder;

    /** 备注 */
    private String remark;

    /** 创建时间 */
    private java.util.Date createTime;
}