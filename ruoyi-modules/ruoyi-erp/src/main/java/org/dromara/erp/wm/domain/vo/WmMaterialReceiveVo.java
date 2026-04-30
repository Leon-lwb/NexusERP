package org.dromara.erp.wm.domain.vo;

import cn.idev.excel.annotation.ExcelIgnoreUnannotated;
import cn.idev.excel.annotation.ExcelProperty;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import org.dromara.erp.wm.domain.WmMaterialReceive;

import java.io.Serial;
import java.io.Serializable;
import java.util.Date;
import java.util.List;

/**
 * 物料入库单视图对象 wm_material_receive
 *
 * @author ruoyi
 */
@Data
@ExcelIgnoreUnannotated
@AutoMapper(target = WmMaterialReceive.class)
public class WmMaterialReceiveVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long receiveId;

    @ExcelProperty(value = "入库单编号")
    private String receiveCode;

    @ExcelProperty(value = "入库单名称")
    private String receiveName;

    private Long iqcId;

    @ExcelProperty(value = "来料检验单编号")
    private String iqcCode;

    private Long noticeId;

    @ExcelProperty(value = "到货通知单编号")
    private String noticeCode;

    private Long purId;

    @ExcelProperty(value = "采购订单编号")
    private String purCode;

    private Long vendorId;

    @ExcelProperty(value = "供应商编码")
    private String vendorCode;

    @ExcelProperty(value = "供应商名称")
    private String vendorName;

    @ExcelProperty(value = "供应商简称")
    private String vendorNick;

    private Long warehouseId;

    @ExcelProperty(value = "仓库编码")
    private String warehouseCode;

    @ExcelProperty(value = "仓库名称")
    private String warehouseName;

    private Long areaId;

    @ExcelProperty(value = "库区编码")
    private String areaCode;

    @ExcelProperty(value = "库区名称")
    private String areaName;

    private Long locationId;

    @ExcelProperty(value = "库位编码")
    private String locationCode;

    @ExcelProperty(value = "库位名称")
    private String locationName;

    @ExcelProperty(value = "入库日期")
    private Date receiveDate;

    @ExcelProperty(value = "单据状态")
    private String status;

    private Long selectFlowId;

    private String remark;

    private List<WmMaterialReceiveLineVo> wmMaterialReceiveLineList;
}
