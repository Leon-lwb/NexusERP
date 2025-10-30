
package com.nexus.system.domain.vo;

import lombok.Data;
import com.nexus.common.core.plus.dto.BasePageDtoRequest;

/**
 * 行政区划对象 sys_dic_area vo
 *
 * @author nexus
 * @date 2025-10-29
 */
@Data
public class SysDicAreaVo extends BasePageDtoRequest {

    /**
     * ${column.columnComment}
     */
    private Long pkid;
    /**
     * 行政区划代码
     */
    private Long areaId;
    /**
     * 父级行政区划代码
     */
    private Long parentAreaId;
    /**
     * 行政区划名称
     */
    private String areaName;
    /**
     * 纬度
     */
    private String lat;
    /**
     * 经度
     */
    private String lng;
    /**
     * 地址
     */
    private String address;

}