package org.dromara.erp.wm.domain.vo;

import cn.idev.excel.annotation.ExcelIgnoreUnannotated;
import cn.idev.excel.annotation.ExcelProperty;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import org.dromara.erp.wm.domain.WmStorageLocation;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

/**
 * 库位视图对象 wm_storage_location
 *
 * @author ruoyi
 * @date 2025-12-02
 */
@Data
@ExcelIgnoreUnannotated
@AutoMapper(target = WmStorageLocation.class)
public class WmStorageLocationVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 库位ID
     */
    private Long locationId;

    /**
     * 库位编码
     */
    @ExcelProperty(value = "库位编码")
    private String locationCode;

    /**
     * 库位名称
     */
    @ExcelProperty(value = "库位名称")
    private String locationName;

    /**
     * 面积
     */
    @ExcelProperty(value = "面积")
    private BigDecimal area;

    /**
     * 最大载重量
     */
    @ExcelProperty(value = "最大载重量")
    private BigDecimal maxLoa;

    /**
     * 是否允许产品混放
     */
    @ExcelProperty(value = "是否允许产品混放")
    private Long productMixing;

    /**
     * 是否允许批次混放
     */
    @ExcelProperty(value = "是否允许批次混放")
    private Long batchMixing;

    /**
     * 是否启用
     */
    @ExcelProperty(value = "是否启用")
    private Long enableFlag;

    /**
     * 是否冻结
     */
    @ExcelProperty(value = "是否冻结")
    private Long frozenFlag;

    /**
     * 库位类型
     */
    @ExcelProperty(value = "库位类型")
    private String locationType;

    /**
     * 库位状态
     */
    @ExcelProperty(value = "库位状态")
    private Long status;

    /**
     * 占用率(0-100)
     */
    @ExcelProperty(value = "占用率")
    private BigDecimal occupancyRate;

    /**
     * 最后入库时间
     */
    @ExcelProperty(value = "最后入库时间")
    private Date lastInboundTime;

    /**
     * 最后出库时间
     */
    @ExcelProperty(value = "最后出库时间")
    private Date lastOutboundTime;
}
