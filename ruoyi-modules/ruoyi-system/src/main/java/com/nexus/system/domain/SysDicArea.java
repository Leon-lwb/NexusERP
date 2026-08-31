package com.nexus.system.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import com.nexus.common.mybatis.core.domain.BaseEntity;

import java.util.List;

/**
 * 行政区划对象 sys_dic_area
 * <p>维护省/市/区等多级行政区划树及其经纬度坐标，属于系统管理模块。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_dic_area")
public class SysDicArea extends BaseEntity {

    /** 序列化版本号 */
    private static final long serialVersionUID = 1L;

    /** 主键 */
    @TableId(value = "pkid", type = IdType.AUTO)
    private Long pkid;

    /** 行政区划ID */
    private Long areaId;

    /** 父级行政区划ID */
    private Long parentAreaId;

    /** 行政区划名称 */
    private String areaName;

    /** 纬度 */
    private String lat;

    /** 经度 */
    private String lng;

    /** 层级 */
    private Long layered;

    /** 排序 */
    private Long sort;

    /** 地址 */
    private String address;

    /** 删除标志（0代表存在 1代表删除） */
    private String delFlag;

    /** 租户ID */
    private String tenantId;

    /** 子行政区划 */
    @TableField(exist = false)
    private List<SysDicArea> children;
}
