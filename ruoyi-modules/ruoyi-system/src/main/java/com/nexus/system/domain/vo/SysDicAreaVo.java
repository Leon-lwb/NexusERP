package com.nexus.system.domain.vo;

import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import com.nexus.system.domain.SysDicArea;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 行政区划视图对象 sys_dic_area
 * <p>用于前端展示与传输多级行政区划树信息，属于系统管理模块。</p>
 */
@Data
@AutoMapper(target = SysDicArea.class)
public class SysDicAreaVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 主键 */
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
    /** 子行政区划 */
    private List<SysDicAreaVo> children;
}
