package org.dromara.erp.wm.domain.bo;

import io.github.linpeilie.annotations.AutoMapper;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.dromara.common.mybatis.core.domain.BaseEntity;
import org.dromara.erp.wm.domain.WmWarehouse;

import java.math.BigDecimal;

/**
 * 仓库设置业务对象 wm_warehouse
 *
 * @author ruoyi
 * @date 2025-09-30
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@AutoMapper(target = WmWarehouse.class, reverseConvertGenerate = false)
public class WmWarehouseBo extends BaseEntity {

    /**
     * 仓库ID
     */
    private Long warehouseId;

    /**
     * 仓库编码
     */
    @NotBlank(message = "仓库编码不能为空")
    private String warehouseCode;

    /**
     * 仓库名称
     */
    @NotBlank(message = "仓库名称不能为空")
    private String warehouseName;

    /**
     * 位置
     */
    private String location;

    /**
     * 面积
     */
    private BigDecimal area;

    /**
     * 负责人
     */
    private String manager;

    /**
     * user_id
     */
    private Long managerId;

    /**
     * 仓库状态
     */
    private Long status;

    /**
     * 预留字段1
     */
    private String attr1;

    /**
     * 预留字段2
     */
    private String attr2;

    /**
     * 预留字段3
     */
    private Long attr3;

    /**
     * 预留字段4
     */
    private Long attr4;

    /**
     * 省份行政区划ID
     */
    private Long provinceAreaId;

    /**
     * 城市行政区划ID
     */
    private Long cityAreaId;

    /**
     * 区县行政区划ID
     */
    private Long districtAreaId;

    /**
     * 详细地址(街道、门牌号等)
     */
    private String streetId;

    /**
     * 地址纬度
     */
    private String addressLat;

    /**
     * 地址经度
     */
    private String addressLng;

    /**
     * 备注
     */
    private String remark;

    /**
     * 删除标志
     */
    private String delFlag;

    /**
     * 批量删除ID集合
     */
    private Long[] ids;
}
