export interface RecentOrderVO {
  soNo: string;
  clientName: string;
  totalAmount: number;
  orderStatus: string;
  createTime: string;
}

export interface StockAlertItemVO {
  alertId: number;
  materialCode: string;
  materialName: string;
  warehouseName: string;
  currentQty: number;
  safetyStock: number;
  alertLevel: string;
}

export interface MonthlyPointVO {
  month: string;
  amount: number;
  orderCount: number;
}

export interface DashboardStatsVO {
  monthSalesAmount: number;
  monthSalesCount: number;
  openSalesOrderCount: number;
  clientCount: number;
  materialCount: number;
  stockAlertCount: number;
  recentOrders: RecentOrderVO[];
  stockAlerts: StockAlertItemVO[];
  salesTrend: MonthlyPointVO[];
}
