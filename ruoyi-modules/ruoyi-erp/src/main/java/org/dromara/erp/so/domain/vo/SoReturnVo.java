package org.dromara.erp.so.domain.vo;

import cn.idev.excel.annotation.ExcelProperty;
import com.alibaba.fastjson.annotation.JSONField;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import org.dromara.erp.so.domain.SoReturn;
import org.dromara.erp.so.domain.SoReturnLine;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

@Data
@AutoMapper(target = SoReturn.class)
public class SoReturnVo implements Serializable {

    @ExcelProperty("退货ID")
    private Long returnId;

    @ExcelProperty("退货单号")
    private String returnNo;

    @ExcelProperty("销售订单号")
    private String soNo;

    @ExcelProperty("客户名称")
    private String clientName;

    @ExcelProperty("退货日期")
    private Date returnDate;

    @ExcelProperty("退货原因")
    private String returnReason;

    @ExcelProperty("退货类型")
    private String returnType;

    @ExcelProperty("总金额")
    private BigDecimal totalAmount;

    @ExcelProperty("退款金额")
    private BigDecimal refundAmount;

    @ExcelProperty("状态")
    private String status;

    private Long selectFlowId;

    @ExcelProperty("备注")
    private String remark;

    @JSONField(serialize = false)
    private List<SoReturnLine> soReturnLineList;
}
