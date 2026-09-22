package com.nexus.system.domain.bo;

import io.github.linpeilie.annotations.AutoMapper;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;
import com.nexus.common.mybatis.core.domain.BaseEntity;
import com.nexus.system.domain.SysDicArea;

/**
 * 行政区划业务对象 sys_dic_area
 * <p>承接前端增改请求，避免直接暴露实体导致 tenantId/delFlag/createBy 等字段被越权写入。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@AutoMapper(target = SysDicArea.class, reverseConvertGenerate = false)
public class SysDicAreaBo extends BaseEntity {

    /** 主键（新增时为空） */
    private Long pkid;

    /** 行政区划代码（GB/T 2260） */
    @NotNull(message = "地区代码不能为空")
    private Long areaId;

    /** 父级行政区划代码（顶级为 0） */
    @NotNull(message = "上级地区不能为空")
    private Long parentAreaId;

    /** 行政区划名称 */
    @NotBlank(message = "地区名称不能为空")
    @Size(max = 255, message = "地区名称长度不能超过 255")
    private String areaName;

    /** 纬度 */
    @Pattern(regexp = "^-?\\d{1,3}(\\.\\d+)?$", message = "纬度格式不正确")
    private String lat;

    /** 经度 */
    @Pattern(regexp = "^-?\\d{1,3}(\\.\\d+)?$", message = "经度格式不正确")
    private String lng;

    /** 层级 */
    @NotNull(message = "层级不能为空")
    @Min(value = 1, message = "层级最小为 1")
    @Max(value = 5, message = "层级最大为 5")
    private Long layered;

    /** 排序 */
    @NotNull(message = "显示排序不能为空")
    @Min(value = 0, message = "显示排序不能为负数")
    private Long sort;

    /** 地址 */
    @Size(max = 500, message = "地址长度不能超过 500")
    private String address;
}
