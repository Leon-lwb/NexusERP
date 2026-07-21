package com.nexus.erp.wm.domain.vo;

import cn.idev.excel.annotation.ExcelIgnoreUnannotated;
import cn.idev.excel.annotation.ExcelProperty;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import com.nexus.erp.wm.domain.WmMaterialReceive;

import java.io.Serial;
import java.io.Serializable;
import java.util.Date;
import java.util.List;

/**
 * 物料入库单视图对象 wm_material_receive
 *
 * @author nexus
 */
@Data
@ExcelIgnoreUnannotated
@AutoMapper(target = WmMaterialReceive.class)
public class WmMaterialReceiveVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 入库单ID
     */
    private Long receiveId;

    /**
     * 入库单编号
     */
    @ExcelProperty(value = "入库单编号")
    private String receiveCode;

    /**
     * 入库单名称
     */
    @ExcelProperty(value = "入库单名称")
    private String receiveName;

    /**
     * 来料检验单ID
     */
    private Long iqcId;

    /**
     * 来料检验单编号
     */
    @ExcelProperty(value = "来料检验单编号")
    private String iqcCode;

    /**
     * 到货通知ID
     */
    private Long noticeId;

    /**
     * 到货通知单编号
     */
    @ExcelProperty(value = "到货通知单编号")
    private String noticeCode;

    /**
     * 采购订单ID
     */
    private Long purId;

    /**
     * 采购订单编号
     */
    @ExcelProperty(value = "采购订单编号")
    private String purCode;

    /**
     * 供应商ID
     */
    private Long vendorId;

    /**
     * 供应商编码
     */
    @ExcelProperty(value = "供应商编码")
    private String vendorCode;

    /**
     * 供应商名称
     */
    @ExcelProperty(value = "供应商名称")
    private String vendorName;

    /**
     * 供应商简称
     */
    @ExcelProperty(value = "供应商简称")
    private String vendorNick;

    /**
     * 仓库ID
     */
    private Long warehouseId;

    /**
     * 仓库编码
     */
    @ExcelProperty(value = "仓库编码")
    private String warehouseCode;

    /**
     * 仓库名称
     */
    @ExcelProperty(value = "仓库名称")
    private String warehouseName;

    /**
     * 库区ID
     */
    private Long areaId;

    /**
     * 库区编码
     */
    @ExcelProperty(value = "库区编码")
    private String areaCode;

    /**
     * 库区名称
     */
    @ExcelProperty(value = "库区名称")
    private String areaName;

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
     * 入库日期
     */
    @ExcelProperty(value = "入库日期")
    private Date receiveDate;

    /**
     * 单据状态
     */
    @ExcelProperty(value = "单据状态")
    private String status;

    /**
     * 关联流程ID
     */
    private Long selectFlowId;

    /**
     * 上架人ID
     */
    @ExcelProperty(value = "上架人ID")
    private Long shelfBy;

    /**
     * 上架人
     */
    @ExcelProperty(value = "上架人")
    private String shelfByName;

    /**
     * 上架时间
     */
    @ExcelProperty(value = "上架时间")
    private Date shelfTime;

    /**
     * 备注
     */
    private String remark;

    /**
     * 物料入库明细列表
     */
    private List<WmMaterialReceiveLineVo> wmMaterialReceiveLineList;
}
