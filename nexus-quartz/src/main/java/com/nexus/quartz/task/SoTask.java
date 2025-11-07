package com.nexus.quartz.task;

import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;
import com.nexus.core.so.domain.SoCustomerOrder;
import com.nexus.core.so.domain.SoSalesOrderHeader;
import com.nexus.core.so.service.ISoCustomerOrderService;
import com.nexus.core.so.service.ISoSalesOrderHeaderService;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

/**
 * 定时任务调度
 * 将so_customer_order表中数据转移到so_sales_order表中
 *
 * @author nexus
 */
@Component("soTask")
public class SoTask {
    
    @Autowired
    private ISoCustomerOrderService soCustomerOrderService;
    
    @Autowired
    private ISoSalesOrderHeaderService soSalesOrderHeaderService;

    /**
     * 将so_customer_order表中status字段值等于CONFIRMED的数据新增同步到so_sales_order_header表
     * 增量同步
     */
    public void customerToSalesOrder() {
        // 创建查询条件，只查询状态为CONFIRMED的客户订单
        SoCustomerOrder query = new SoCustomerOrder();
        query.setStatus("CONFIRMED");
        
        // 查询所有状态为CONFIRMED的客户订单
        List<SoCustomerOrder> confirmedOrders = soCustomerOrderService.selectSoCustomerOrderList(query);
        
        // 遍历处理每一条记录
        for (SoCustomerOrder customerOrder : confirmedOrders) {
            // 检查是否已经同步过（通过coId检查）
            SoSalesOrderHeader checkHeader = new SoSalesOrderHeader();
            checkHeader.setCoId(customerOrder.getCoId());
            List<SoSalesOrderHeader> existingHeaders = soSalesOrderHeaderService.selectSoSalesOrderHeaderList(checkHeader);
            
            // 如果还没有同步过，则执行同步操作
            if (existingHeaders.isEmpty()) {
                SoSalesOrderHeader salesOrderHeader = new SoSalesOrderHeader();
                
                // 基本字段映射
                salesOrderHeader.setCoId(customerOrder.getCoId());
                salesOrderHeader.setSoNo(customerOrder.getCoNo());
                salesOrderHeader.setClientId(customerOrder.getClientId());
                salesOrderHeader.setClientName(customerOrder.getClientName());
                salesOrderHeader.setSalesRepId(customerOrder.getSalesRepId());
                salesOrderHeader.setSalesRepName(customerOrder.getSalesRepName());
                salesOrderHeader.setOrderDate(customerOrder.getOrderDate());
                salesOrderHeader.setRequestedDate(customerOrder.getRequestedDate());
                salesOrderHeader.setCurrency(customerOrder.getCurrency());
                salesOrderHeader.setTotalAmount(customerOrder.getTotalAmount());
                salesOrderHeader.setRemark("从客户订单同步");
                
                // 设置默认值
                salesOrderHeader.setOrderStatus("DRAFT"); // 默认订单状态
                
                // 插入到销售订单表
                soSalesOrderHeaderService.insertSoSalesOrderHeader(salesOrderHeader);
            }
        }
        
        System.out.println("so_customer_order --> so_sales_order数据同步成功！共处理" + confirmedOrders.size() + "条记录。");
    }
}