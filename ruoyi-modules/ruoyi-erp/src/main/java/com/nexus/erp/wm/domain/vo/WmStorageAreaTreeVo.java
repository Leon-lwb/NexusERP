package com.nexus.erp.wm.domain.vo;

import lombok.Data;
import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 库区树形结构视图对象
 * <p>
 * 用于仓库-库区-库位三级树形展示，仅包含必要字段。
 *
 * @author nexus
 */
@Data
public class WmStorageAreaTreeVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 库区ID
     */
    private Long areaId;

    /**
     * 库区编码
     */
    private String areaCode;

    /**
     * 库区名称
     */
    private String areaName;

    /**
     * 所属仓库ID
     */
    private Long warehouseId;

    /**
     * 是否开启库位管理
     */
    private Long locationFlag;

    /**
     * 是否启用
     */
    private Long enableFlag;

    /**
     * 布局类型
     */
    private String layoutType;

    /**
     * 子级库位列表
     */
    private List<WmStorageLocationTreeVo> children;
}
