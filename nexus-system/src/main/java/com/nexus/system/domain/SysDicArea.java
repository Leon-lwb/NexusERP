package com.nexus.system.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.nexus.common.annotation.Excel;
import com.nexus.common.core.domain.TreeEntity;
import java.util.List;

/**
 * 行政区划对象 sys_dic_area
 *
 * @author nexus
 * @date 2025-10-29
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@TableName(resultMap = "com.nexus.system.mapper.SysDicAreaMapper.SysDicAreaResult")
public class SysDicArea{
private static final long serialVersionUID = 1L;

    /** $column.columnComment */
    @TableId(value = "pkid",type = IdType.AUTO)
    private Long pkid;
    /** 行政区划代码 */
    @Excel(name = "行政区划代码")
    private Long areaId;
    /** 父级行政区划代码 */
    @Excel(name = "父级行政区划代码")
    private Long parentAreaId;
    /** 行政区划名称 */
    @Excel(name = "行政区划名称")
    private String areaName;
    /** 纬度 */
    @Excel(name = "纬度")
    private String lat;
    /** 经度 */
    @Excel(name = "经度")
    private String lng;
    /** 区划层级 */
    private Long layered;
    /** 序号 */
    private Long sort;
    /** 地址 */
    @Excel(name = "地址")
    private String address;
    
    /** 子节点 */
    private List<SysDicArea> children;
}