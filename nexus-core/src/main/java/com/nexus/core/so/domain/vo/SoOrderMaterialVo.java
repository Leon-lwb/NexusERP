package com.nexus.core.so.domain.vo;

import com.nexus.common.annotation.Excel;
import com.nexus.core.so.domain.SoSalesOrderHeader;


/**
 * @author wanghuan
 * @date 2025/11/20
 */
public class SoOrderMaterialVo extends SoSalesOrderHeader {

    //销售订单编号
    @Excel(name = "销售订单编号")
    private String soNo;

}
