package com.nexus.system.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import com.nexus.common.mybatis.core.domain.BaseEntity;

import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_dic_area")
public class SysDicArea extends BaseEntity {

    private static final long serialVersionUID = 1L;

    @TableId(value = "pkid", type = IdType.AUTO)
    private Long pkid;

    private Long areaId;

    private Long parentAreaId;

    private String areaName;

    private String lat;

    private String lng;

    private Long layered;

    private Long sort;

    private String address;

    private String delFlag;

    private String tenantId;

    @TableField(exist = false)
    private List<SysDicArea> children;
}
