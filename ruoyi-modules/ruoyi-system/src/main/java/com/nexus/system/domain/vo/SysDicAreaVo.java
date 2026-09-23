package com.nexus.system.domain.vo;

import cn.idev.excel.annotation.ExcelIgnore;
import cn.idev.excel.annotation.ExcelProperty;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import com.nexus.system.domain.SysDicArea;

import java.io.Serial;
import java.io.Serializable;
import java.util.Date;
import java.util.List;

/**
 * 行政区划视图对象 sys_dic_area
 * <p>用于前端展示与传输多级行政区划树信息，属于系统管理模块。</p>
 *
 * <p>导出说明：字段用 {@link ExcelProperty} 显式命名表头，{@code children} 用 {@link ExcelIgnore} 排除
 * ——它是树形传输用的集合，FastExcel 无法可靠写出，且对导出无意义。</p>
 */
@Data
@AutoMapper(target = SysDicArea.class)
public class SysDicAreaVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 主键（内部主键，不导出） */
    @ExcelIgnore
    private Long pkid;

    /** 行政区划代码 */
    @ExcelProperty("行政区划代码")
    private Long areaId;

    /** 上级行政区划代码 */
    @ExcelProperty("上级代码")
    private Long parentAreaId;

    /** 行政区划名称 */
    @ExcelProperty("行政区划名称")
    private String areaName;

    /** 层级 */
    @ExcelProperty("层级")
    private Long layered;

    /** 排序 */
    @ExcelProperty("排序")
    private Long sort;

    /** 全路径地址 */
    @ExcelProperty("地址")
    private String address;

    /** 纬度 */
    @ExcelProperty("纬度")
    private String lat;

    /** 经度 */
    @ExcelProperty("经度")
    private String lng;

    /** 创建时间 */
    @ExcelProperty("创建时间")
    private Date createTime;

    /** 更新时间 */
    @ExcelProperty("更新时间")
    private Date updateTime;

    /** 子行政区划（仅用于树形传输，不参与导出） */
    @ExcelIgnore
    private List<SysDicAreaVo> children;

    /**
     * 是否存在下级行政区划（仅用于树形传输，不参与导出）。
     * <p>懒加载表格与 {@code el-tree-select} 都靠它决定是否渲染展开箭头：没有该字段时前端只能按
     * "层级 &lt; 5" 猜，而库里 5112 行中只有 470 行真有下级 —— 会多出 4640 个点了返回空列表的假箭头。</p>
     * <p>由 {@code SysDicAreaServiceImpl} 回填：扁平（懒加载每一级）与过滤结果集都以库为准；
     * 全量树用内存中的 children 推导。为 {@code null} 时前端回退到层级启发式。</p>
     */
    @ExcelIgnore
    private Boolean hasChildren;
}
