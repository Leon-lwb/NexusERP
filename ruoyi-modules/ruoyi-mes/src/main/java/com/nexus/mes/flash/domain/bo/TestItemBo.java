package com.nexus.mes.flash.domain.bo;

import com.nexus.common.core.validate.AddGroup;
import com.nexus.common.core.validate.EditGroup;
import com.nexus.common.mybatis.core.domain.BaseEntity;
import com.nexus.mes.flash.domain.TestItem;
import io.github.linpeilie.annotations.AutoMapper;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Null;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * MES测试项标准业务对象 mes_test_item
 * <p>按物料配置测试项; firmware_id=0 表示该物料所有版本通用, 特定版本项用真实固件ID覆盖。
 * 同物料+同固件+同编码唯一(软删除外)。</p>
 *
 * @author Nexus
 */
@Data
@EqualsAndHashCode(callSuper = true)
@AutoMapper(target = TestItem.class)
public class TestItemBo extends BaseEntity {

    /** 测试项ID */
    @Null(groups = {AddGroup.class})
    @NotNull(groups = {EditGroup.class})
    private Long testItemId;

    /** 测试项编码(如 RF-TXPWR) */
    @NotBlank(message = "测试项编码不能为空", groups = {AddGroup.class, EditGroup.class})
    private String itemCode;

    /** 测试项名称 */
    @NotBlank(message = "测试项名称不能为空", groups = {AddGroup.class, EditGroup.class})
    private String itemName;

    /** 适用物料ID */
    @NotNull(message = "适用物料不能为空", groups = {AddGroup.class, EditGroup.class})
    private Long materialId;

    /** 固件ID(0=该物料所有版本通用) */
    private Long contentRefId;

    /** 数据类型: NUMERIC/TEXT/ENUM */
    @NotBlank(message = "数据类型不能为空", groups = {AddGroup.class, EditGroup.class})
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
}