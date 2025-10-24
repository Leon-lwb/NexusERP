<template>
  <div class="app-container">
    <el-form :model="queryParams" ref="queryRef" :inline="true" v-show="showSearch" label-width="100px">
      <el-form-item label="销售订单编号" prop="soNo">
        <el-input v-model="queryParams.soNo" placeholder="请输入销售订单编号" clearable @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="来源客户订单" prop="coId">
        <el-input v-model="queryParams.coId" placeholder="请输入来源客户订单" clearable @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="来源客户ID" prop="clientId">
        <el-input v-model="queryParams.clientId" placeholder="请输入来源客户ID" clearable @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="销售代表" prop="salesRepId">
        <el-input v-model="queryParams.salesRepId" placeholder="请输入销售代表" clearable @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="订单生成日期" prop="orderDate">
        <el-date-picker clearable v-model="queryParams.orderDate" type="date" value-format="YYYY-MM-DD"
          placeholder="请选择订单生成日期" style="width: 200px;">
        </el-date-picker>
      </el-form-item>
      <el-form-item label="承诺交货日" prop="promiseDate">
        <el-date-picker clearable v-model="queryParams.promiseDate" type="date" value-format="YYYY-MM-DD"
          placeholder="请选择承诺交货日" style="width: 200px;">
        </el-date-picker>
      </el-form-item>
      <el-form-item label="要求交货日" prop="requestedDate">
        <el-date-picker clearable v-model="queryParams.requestedDate" type="date" value-format="YYYY-MM-DD"
          placeholder="请选择客户要求交货日" style="width: 200px;">
        </el-date-picker>
      </el-form-item>
      <el-form-item label="信用状态" prop="creditStatus">
        <el-select v-model="queryParams.creditStatus" placeholder="请选择信用状态" clearable style="width: 200px;">
          <el-option v-for="dict in so_credit_status" :key="dict.value" :label="dict.label" :value="dict.value" />
        </el-select>
      </el-form-item>
      <el-form-item label="信用拦截人" prop="creditBlockedBy">
        <el-input v-model="queryParams.creditBlockedBy" placeholder="请输入信用拦截人" clearable @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="订单状态" prop="orderStatus">
        <el-select v-model="queryParams.orderStatus" placeholder="请选择订单状态" clearable style="width: 200px;">
          <el-option v-for="dict in so_order_status" :key="dict.value" :label="dict.label" :value="dict.value" />
        </el-select>
      </el-form-item>
      <el-form-item label="项目ID" prop="projectId">
        <el-input v-model="queryParams.projectId" placeholder="请输入项目ID" clearable @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
        <el-button icon="Refresh" @click="resetQuery">重置</el-button>
      </el-form-item>
    </el-form>

    <el-row :gutter="10" class="mb8">
      <el-col :span="1.5">
        <el-button type="primary" plain icon="Plus" @click="handleAdd"
          v-hasPermi="['so:orderheader:add']">新增</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="success" plain icon="Edit" :disabled="single" @click="handleUpdate"
          v-hasPermi="['so:orderheader:edit']">修改</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="danger" plain icon="Delete" :disabled="multiple" @click="handleDelete"
          v-hasPermi="['so:orderheader:remove']">删除</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="info" plain icon="View" :disabled="single" @click="handleView"
          v-hasPermi="['so:orderheader:query']">查看</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="warning" plain icon="Download" @click="handleExport"
          v-hasPermi="['so:orderheader:export']">导出</el-button>
      </el-col>
      <right-toolbar v-model:showSearch="showSearch" @queryTable="getList"></right-toolbar>
    </el-row>

    <el-table v-loading="loading" :data="orderheaderList" @selection-change="handleSelectionChange">
      <el-table-column type="selection" width="55" align="center" />
      <el-table-column label="销售订单ID" align="center" prop="soId" />
      <el-table-column label="销售订单编号" align="center" prop="soNo" />
      <el-table-column label="来源客户订单" align="center" prop="coId" />
      <el-table-column label="来源客户ID" align="center" prop="clientId" />
      <el-table-column label="销售代表" align="center" prop="salesRepId" />
      <el-table-column label="订单生成日期" align="center" prop="orderDate" width="180">
        <template #default="scope">
          <span>{{ parseTime(scope.row.orderDate, '{y}-{m}-{d}') }}</span>
        </template>
      </el-table-column>
      <el-table-column label="承诺交货日" align="center" prop="promiseDate" width="180">
        <template #default="scope">
          <span>{{ parseTime(scope.row.promiseDate, '{y}-{m}-{d}') }}</span>
        </template>
      </el-table-column>
      <el-table-column label="客户要求交货日" align="center" prop="requestedDate" width="180">
        <template #default="scope">
          <span>{{ parseTime(scope.row.requestedDate, '{y}-{m}-{d}') }}</span>
        </template>
      </el-table-column>
      <el-table-column label="发货地址" align="center" prop="deliveryAddressId" />
      <el-table-column label="开票地址ID" align="center" prop="invoiceAddressId" />
      <el-table-column label="付款条款" align="center" prop="paymentTerms" />
      <el-table-column label="货币" align="center" prop="currency">
        <template #default="scope">
          <dict-tag :options="so_currency_type" :value="scope.row.currency" />
        </template>
      </el-table-column>
      <el-table-column label="汇率" align="center" prop="exchangeRate" />
      <el-table-column label="含税总金额" align="center" prop="totalAmount" />
      <el-table-column label="税款" align="center" prop="taxAmount" />
      <el-table-column label="整单折扣" align="center" prop="discountAmount" />
      <el-table-column label="整单折扣率" align="center" prop="discountRate" />
      <el-table-column label="信用状态" align="center" prop="creditStatus">
        <template #default="scope">
          <dict-tag :options="so_credit_status" :value="scope.row.creditStatus" />
        </template>
      </el-table-column>
      <el-table-column label="信用检查时间" align="center" prop="creditCheckTime" width="180">
        <template #default="scope">
          <span>{{ parseTime(scope.row.creditCheckTime, '{y}-{m}-{d}') }}</span>
        </template>
      </el-table-column>
      <el-table-column label="信用拦截人" align="center" prop="creditBlockedBy" />
      <el-table-column label="订单状态" align="center" prop="orderStatus">
        <template #default="scope">
          <dict-tag :options="so_order_status" :value="scope.row.orderStatus" />
        </template>
      </el-table-column>
      <el-table-column label="项目ID" align="center" prop="projectId" />
      <el-table-column label="是否启用项目配套发料" align="center" prop="isPaEnabled">
        <template #default="scope">
          <dict-tag :options="sys_true_or_false" :value="scope.row.isPaEnabled" />
        </template>
      </el-table-column>
      <el-table-column label="确认时间" align="center" prop="confirmedTime" width="180">
        <template #default="scope">
          <span>{{ parseTime(scope.row.confirmedTime, '{y}-{m}-{d}') }}</span>
        </template>
      </el-table-column>
      <el-table-column label="完全履行时间" align="center" prop="fulfilledTime" width="180">
        <template #default="scope">
          <span>{{ parseTime(scope.row.fulfilledTime, '{y}-{m}-{d}') }}</span>
        </template>
      </el-table-column>
      <el-table-column label="结束时间" align="center" prop="closedTime" width="180">
        <template #default="scope">
          <span>{{ parseTime(scope.row.closedTime, '{y}-{m}-{d}') }}</span>
        </template>
      </el-table-column>
      <el-table-column label="取消时间" align="center" prop="cancelledTime" width="180">
        <template #default="scope">
          <span>{{ parseTime(scope.row.cancelledTime, '{y}-{m}-{d}') }}</span>
        </template>
      </el-table-column>
      <el-table-column label="取消原因" align="center" prop="cancelledReason" />
      <el-table-column label="销售组织ID" align="center" prop="salesOrgId" />
      <el-table-column label="分销渠道" align="center" prop="distributionChannel" />
      <el-table-column label="客户参考号" align="center" prop="customerRefNo" />
      <el-table-column label="操作" align="center" class-name="small-padding fixed-width">
        <template #default="scope">
          <el-button link type="primary" icon="View" @click="handleView(scope.row)">查看</el-button>
          <el-button link type="primary" icon="Edit" @click="handleUpdate(scope.row)"
            v-hasPermi="['so:orderheader:edit']">修改</el-button>
          <el-button link type="primary" icon="Delete" @click="handleDelete(scope.row)"
            v-hasPermi="['so:orderheader:remove']">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <pagination v-show="total > 0" :total="total" v-model:page="queryParams.pageNum" v-model:limit="queryParams.pageSize"
      @pagination="getList" />

    <!-- 添加或修改销售订单对话框 -->
    <el-dialog :title="title" v-model="open" width="960px" append-to-body>
      <el-form ref="orderheaderRef" :model="form" :rules="rules" label-width="80px" :disabled="isViewMode">
        <el-form-item label="销售订单编号" prop="soNo">
          <el-input v-model="form.soNo" placeholder="请输入销售订单编号" />
        </el-form-item>
        <el-form-item label="来源客户订单" prop="coId">
          <el-input v-model="form.coId" placeholder="请输入来源客户订单" />
        </el-form-item>
        <el-form-item label="来源客户ID" prop="clientId">
          <el-input v-model="form.clientId" placeholder="请输入来源客户ID" />
        </el-form-item>
        <el-form-item label="销售代表" prop="salesRepId">
          <el-input v-model="form.salesRepId" placeholder="请输入销售代表" />
        </el-form-item>
        <el-form-item label="订单生成日期" prop="orderDate">
          <el-date-picker clearable v-model="form.orderDate" type="date" value-format="YYYY-MM-DD"
            placeholder="请选择订单生成日期">
          </el-date-picker>
        </el-form-item>
        <el-form-item label="承诺交货日" prop="promiseDate">
          <el-date-picker clearable v-model="form.promiseDate" type="date" value-format="YYYY-MM-DD"
            placeholder="请选择承诺交货日">
          </el-date-picker>
        </el-form-item>
        <el-form-item label="客户要求交货日" prop="requestedDate">
          <el-date-picker clearable v-model="form.requestedDate" type="date" value-format="YYYY-MM-DD"
            placeholder="请选择客户要求交货日">
          </el-date-picker>
        </el-form-item>
        <el-form-item label="发货地址" prop="deliveryAddressId">
          <el-input v-model="form.deliveryAddressId" placeholder="请输入发货地址" />
        </el-form-item>
        <el-form-item label="开票地址ID" prop="invoiceAddressId">
          <el-input v-model="form.invoiceAddressId" placeholder="请输入开票地址ID" />
        </el-form-item>
        <el-form-item label="发货地址详情" prop="deliveryAddress">
          <el-input v-model="form.deliveryAddress" type="textarea" placeholder="请输入内容" />
        </el-form-item>
        <el-form-item label="开票地址详情" prop="invoiceAddress">
          <el-input v-model="form.invoiceAddress" type="textarea" placeholder="请输入内容" />
        </el-form-item>
        <el-form-item label="付款条款" prop="paymentTerms">
          <el-input v-model="form.paymentTerms" placeholder="请输入付款条款" />
        </el-form-item>
        <el-form-item label="货币" prop="currency">
          <el-select v-model="form.currency" placeholder="请选择货币">
            <el-option v-for="dict in so_currency_type" :key="dict.value" :label="dict.label"
              :value="dict.value"></el-option>
          </el-select>
        </el-form-item>
        <el-form-item label="汇率" prop="exchangeRate">
          <el-input v-model="form.exchangeRate" placeholder="请输入汇率" />
        </el-form-item>
        <el-form-item label="含税总金额" prop="totalAmount">
          <el-input v-model="form.totalAmount" placeholder="请输入含税总金额" />
        </el-form-item>
        <el-form-item label="税款" prop="taxAmount">
          <el-input v-model="form.taxAmount" placeholder="请输入税款" />
        </el-form-item>
        <el-form-item label="整单折扣" prop="discountAmount">
          <el-input v-model="form.discountAmount" placeholder="请输入整单折扣" />
        </el-form-item>
        <el-form-item label="整单折扣率" prop="discountRate">
          <el-input v-model="form.discountRate" placeholder="请输入整单折扣率" />
        </el-form-item>
        <el-form-item label="信用状态" prop="creditStatus">
          <el-radio-group v-model="form.creditStatus">
            <el-radio v-for="dict in so_credit_status" :key="dict.value" :label="dict.value">{{ dict.label }}</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="信用检查时间" prop="creditCheckTime">
          <el-date-picker clearable v-model="form.creditCheckTime" type="date" value-format="YYYY-MM-DD"
            placeholder="请选择信用检查时间">
          </el-date-picker>
        </el-form-item>
        <el-form-item label="信用拦截人" prop="creditBlockedBy">
          <el-input v-model="form.creditBlockedBy" placeholder="请输入信用拦截人" />
        </el-form-item>
        <el-form-item label="订单状态" prop="orderStatus">
          <el-select v-model="form.orderStatus" placeholder="请选择订单状态">
            <el-option v-for="dict in so_order_status" :key="dict.value" :label="dict.label"
              :value="dict.value"></el-option>
          </el-select>
        </el-form-item>
        <el-form-item label="项目ID" prop="projectId">
          <el-input v-model="form.projectId" placeholder="请输入项目ID" />
        </el-form-item>
        <el-form-item label="是否启用项目配套发料" prop="isPaEnabled">
          <el-radio-group v-model="form.isPaEnabled">
            <el-radio v-for="dict in sys_true_or_false" :key="dict.value"
              :label="parseInt(dict.value)">{{ dict.label }}</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="确认时间" prop="confirmedTime">
          <el-date-picker clearable v-model="form.confirmedTime" type="date" value-format="YYYY-MM-DD"
            placeholder="请选择确认时间">
          </el-date-picker>
        </el-form-item>
        <el-form-item label="完全履行时间" prop="fulfilledTime">
          <el-date-picker clearable v-model="form.fulfilledTime" type="date" value-format="YYYY-MM-DD"
            placeholder="请选择完全履行时间">
          </el-date-picker>
        </el-form-item>
        <el-form-item label="结束时间" prop="closedTime">
          <el-date-picker clearable v-model="form.closedTime" type="date" value-format="YYYY-MM-DD"
            placeholder="请选择结束时间">
          </el-date-picker>
        </el-form-item>
        <el-form-item label="取消时间" prop="cancelledTime">
          <el-date-picker clearable v-model="form.cancelledTime" type="date" value-format="YYYY-MM-DD"
            placeholder="请选择取消时间">
          </el-date-picker>
        </el-form-item>
        <el-form-item label="取消原因" prop="cancelledReason">
          <el-input v-model="form.cancelledReason" placeholder="请输入取消原因" />
        </el-form-item>
        <el-form-item label="销售组织ID" prop="salesOrgId">
          <el-input v-model="form.salesOrgId" placeholder="请输入销售组织ID" />
        </el-form-item>
        <el-form-item label="分销渠道" prop="distributionChannel">
          <el-input v-model="form.distributionChannel" placeholder="请输入分销渠道" />
        </el-form-item>
        <el-form-item label="客户参考号" prop="customerRefNo">
          <el-input v-model="form.customerRefNo" placeholder="请输入客户参考号" />
        </el-form-item>
        <el-form-item label="备注" prop="remark">
          <el-input v-model="form.remark" type="textarea" placeholder="请输入内容" />
        </el-form-item>
        <el-divider content-position="center">销售订单行信息</el-divider>
        <el-row :gutter="10" class="mb8">
          <el-col :span="1.5">
            <el-button type="primary" icon="Plus" @click="handleAddSoSalesOrderLine" v-if="!isViewMode">添加</el-button>
          </el-col>
          <el-col :span="1.5">
            <el-button type="danger" icon="Delete" @click="handleDeleteSoSalesOrderLine"
              v-if="!isViewMode">删除</el-button>
          </el-col>
        </el-row>
        <el-table :data="soSalesOrderLineList" :row-class-name="rowSoSalesOrderLineIndex"
          @selection-change="handleSoSalesOrderLineSelectionChange" ref="soSalesOrderLine">
          <el-table-column type="selection" width="50" align="center" v-if="!isViewMode" />
          <el-table-column label="序号" align="center" prop="index" width="50" />
          <el-table-column label="行号" prop="lineNo" width="150">
            <template #default="scope">
              <el-input v-model="scope.row.lineNo" placeholder="请输入行号" v-if="!isViewMode" />
              <span v-else>{{ scope.row.lineNo }}</span>
            </template>
          </el-table-column>
          <el-table-column label="物料ID" prop="materialId" width="150">
            <template #default="scope">
              <el-input v-model="scope.row.materialId" placeholder="请输入物料ID" v-if="!isViewMode" />
              <span v-else>{{ scope.row.materialId }}</span>
            </template>
          </el-table-column>
          <el-table-column label="物料编码" prop="materialCode" width="150">
            <template #default="scope">
              <el-input v-model="scope.row.materialCode" placeholder="请输入物料编码" v-if="!isViewMode" />
              <span v-else>{{ scope.row.materialCode }}</span>
            </template>
          </el-table-column>
          <el-table-column label="物料名称" prop="materialName" width="150">
            <template #default="scope">
              <el-input v-model="scope.row.materialName" placeholder="请输入物料名称" v-if="!isViewMode" />
              <span v-else>{{ scope.row.materialName }}</span>
            </template>
          </el-table-column>
          <el-table-column label="订单数量" prop="orderedQty" width="150">
            <template #default="scope">
              <el-input v-model="scope.row.orderedQty" placeholder="请输入订单数量" v-if="!isViewMode" />
              <span v-else>{{ scope.row.orderedQty }}</span>
            </template>
          </el-table-column>
          <el-table-column label="已发货数量" prop="deliveredQty" width="150">
            <template #default="scope">
              <el-input v-model="scope.row.deliveredQty" placeholder="请输入已发货数量" v-if="!isViewMode" />
              <span v-else>{{ scope.row.deliveredQty }}</span>
            </template>
          </el-table-column>
          <el-table-column label="单位" prop="unit" width="150">
            <template #default="scope">
              <el-input v-model="scope.row.unit" placeholder="请输入单位" v-if="!isViewMode" />
              <span v-else>{{ scope.row.unit }}</span>
            </template>
          </el-table-column>
          <el-table-column label="单价" prop="unitPrice" width="150">
            <template #default="scope">
              <el-input v-model="scope.row.unitPrice" placeholder="请输入单价" v-if="!isViewMode" />
              <span v-else>{{ scope.row.unitPrice }}</span>
            </template>
          </el-table-column>
          <el-table-column label="行金额 = qty * price" prop="lineAmount" width="150">
            <template #default="scope">
              <el-input v-model="scope.row.lineAmount" placeholder="请输入行金额 = qty * price" v-if="!isViewMode" />
              <span v-else>{{ scope.row.lineAmount }}</span>
            </template>
          </el-table-column>
          <el-table-column label="税率" prop="taxRate" width="150">
            <template #default="scope">
              <el-input v-model="scope.row.taxRate" placeholder="请输入税率" v-if="!isViewMode" />
              <span v-else>{{ scope.row.taxRate }}</span>
            </template>
          </el-table-column>
          <el-table-column label="行税额" prop="taxAmount" width="150">
            <template #default="scope">
              <el-input v-model="scope.row.taxAmount" placeholder="请输入行税额" v-if="!isViewMode" />
              <span v-else>{{ scope.row.taxAmount }}</span>
            </template>
          </el-table-column>
          <el-table-column label="行折扣率" prop="discountPercent" width="150">
            <template #default="scope">
              <el-input v-model="scope.row.discountPercent" placeholder="请输入行折扣率" v-if="!isViewMode" />
              <span v-else>{{ scope.row.discountPercent }}</span>
            </template>
          </el-table-column>
          <el-table-column label="行折扣额" prop="discountAmount" width="150">
            <template #default="scope">
              <el-input v-model="scope.row.discountAmount" placeholder="请输入行折扣额" v-if="!isViewMode" />
              <span v-else>{{ scope.row.discountAmount }}</span>
            </template>
          </el-table-column>
          <el-table-column label="行要求交期" prop="requestedDate" width="240">
            <template #default="scope">
              <el-date-picker clearable v-model="scope.row.requestedDate" type="date" value-format="YYYY-MM-DD"
                placeholder="请选择行要求交期" v-if="!isViewMode">
              </el-date-picker>
              <span v-else>{{ scope.row.requestedDate }}</span>
            </template>
          </el-table-column>
          <el-table-column label="行承诺交期" prop="promiseDate" width="240">
            <template #default="scope">
              <el-date-picker clearable v-model="scope.row.promiseDate" type="date" value-format="YYYY-MM-DD"
                placeholder="请选择行承诺交期" v-if="!isViewMode">
              </el-date-picker>
              <span v-else>{{ scope.row.promiseDate }}</span>
            </template>
          </el-table-column>
          <el-table-column label="已履行数量" prop="fulfilledQty" width="150">
            <template #default="scope">
              <el-input v-model="scope.row.fulfilledQty" placeholder="请输入已履行数量" v-if="!isViewMode" />
              <span v-else>{{ scope.row.fulfilledQty }}</span>
            </template>
          </el-table-column>
          <el-table-column label="已投产数量" prop="productionQty" width="150">
            <template #default="scope">
              <el-input v-model="scope.row.productionQty" placeholder="请输入已投产数量" v-if="!isViewMode" />
              <span v-else>{{ scope.row.productionQty }}</span>
            </template>
          </el-table-column>
          <el-table-column label="已采购数量" prop="purchaseQty" width="150">
            <template #default="scope">
              <el-input v-model="scope.row.purchaseQty" placeholder="请输入已采购数量" v-if="!isViewMode" />
              <span v-else>{{ scope.row.purchaseQty }}</span>
            </template>
          </el-table-column>
          <el-table-column label="已预留库存数量" prop="reservedQty" width="150">
            <template #default="scope">
              <el-input v-model="scope.row.reservedQty" placeholder="请输入已预留库存数量" v-if="!isViewMode" />
              <span v-else>{{ scope.row.reservedQty }}</span>
            </template>
          </el-table-column>
          <el-table-column label="PENDING-待处理,SCHEDULED-已排产/已采购,PARTIALLY_FULFILLED-部分履行,FULFILLED-已履行,CANCELLED-已取消"
            prop="lineStatus" width="150">
            <template #default="scope">
              <el-select v-model="scope.row.lineStatus"
                placeholder="请选择PENDING-待处理,SCHEDULED-已排产/已采购,PARTIALLY_FULFILLED-部分履行,FULFILLED-已履行,CANCELLED-已取消"
                v-if="!isViewMode">
                <el-option label="请选择字典生成" value="" />
              </el-select>
              <span v-else>{{ scope.row.lineStatus }}</span>
            </template>
          </el-table-column>
          <el-table-column label="关联生产工单ID" prop="linkedMoId" width="150">
            <template #default="scope">
              <el-input v-model="scope.row.linkedMoId" placeholder="请输入关联生产工单ID" v-if="!isViewMode" />
              <span v-else>{{ scope.row.linkedMoId }}</span>
            </template>
          </el-table-column>
          <el-table-column label="关联采购订单ID" prop="linkedPoId" width="150">
            <template #default="scope">
              <el-input v-model="scope.row.linkedPoId" placeholder="请输入关联采购订单ID" v-if="!isViewMode" />
              <span v-else>{{ scope.row.linkedPoId }}</span>
            </template>
          </el-table-column>
          <el-table-column label="预留批次号" prop="reservedBatchNo" width="150">
            <template #default="scope">
              <el-input v-model="scope.row.reservedBatchNo" placeholder="请输入预留批次号" v-if="!isViewMode" />
              <span v-else>{{ scope.row.reservedBatchNo }}</span>
            </template>
          </el-table-column>
          <el-table-column label="PA配套组ID" prop="paGroupId" width="150">
            <template #default="scope">
              <el-input v-model="scope.row.paGroupId" placeholder="请输入PA配套组ID" v-if="!isViewMode" />
              <span v-else>{{ scope.row.paGroupId }}</span>
            </template>
          </el-table-column>
          <el-table-column label="默认仓库ID" prop="warehouseId" width="150">
            <template #default="scope">
              <el-input v-model="scope.row.warehouseId" placeholder="请输入默认仓库ID" v-if="!isViewMode" />
              <span v-else>{{ scope.row.warehouseId }}</span>
            </template>
          </el-table-column>
          <el-table-column label="交付优先级" prop="deliveryPriority" width="150">
            <template #default="scope">
              <el-input v-model="scope.row.deliveryPriority" placeholder="请输入交付优先级" v-if="!isViewMode" />
              <span v-else>{{ scope.row.deliveryPriority }}</span>
            </template>
          </el-table-column>
          <el-table-column label="客户行参考号" prop="customerLineRef" width="150">
            <template #default="scope">
              <el-input v-model="scope.row.customerLineRef" placeholder="请输入客户行参考号" v-if="!isViewMode" />
              <span v-else>{{ scope.row.customerLineRef }}</span>
            </template>
          </el-table-column>
          <el-table-column label="" prop="sortOrder" width="150">
            <template #default="scope">
              <el-input v-model="scope.row.sortOrder" placeholder="请输入" v-if="!isViewMode" />
              <span v-else>{{ scope.row.sortOrder }}</span>
            </template>
          </el-table-column>
          <el-table-column label="预留字段1" prop="attr1" width="150">
            <template #default="scope">
              <el-input v-model="scope.row.attr1" placeholder="请输入预留字段1" v-if="!isViewMode" />
              <span v-else>{{ scope.row.attr1 }}</span>
            </template>
          </el-table-column>
          <el-table-column label="预留字段2" prop="attr2" width="150">
            <template #default="scope">
              <el-input v-model="scope.row.attr2" placeholder="请输入预留字段2" v-if="!isViewMode" />
              <span v-else>{{ scope.row.attr2 }}</span>
            </template>
          </el-table-column>
          <el-table-column label="预留字段3" prop="attr3" width="150">
            <template #default="scope">
              <el-input v-model="scope.row.attr3" placeholder="请输入预留字段3" v-if="!isViewMode" />
              <span v-else>{{ scope.row.attr3 }}</span>
            </template>
          </el-table-column>
          <el-table-column label="预留字段4" prop="attr4" width="150">
            <template #default="scope">
              <el-input v-model="scope.row.attr4" placeholder="请输入预留字段4" v-if="!isViewMode" />
              <span v-else>{{ scope.row.attr4 }}</span>
            </template>
          </el-table-column>
        </el-table>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button type="primary" @click="submitForm" v-if="!isViewMode">确 定</el-button>
          <el-button @click="cancel">{{ isViewMode ? '关 闭' : '取 消' }}</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup name="Orderheader">
import { listOrderheader, getOrderheader, delOrderheader, addOrderheader, updateOrderheader } from "@/api/core/so/orderheader"

const { proxy } = getCurrentInstance()
const { so_credit_status, so_order_status, sys_true_or_false, so_currency_type } = proxy.useDict('so_credit_status', 'so_order_status', 'sys_true_or_false', 'so_currency_type')

const orderheaderList = ref([])
const soSalesOrderLineList = ref([])
const open = ref(false)
const loading = ref(true)
const showSearch = ref(true)
const ids = ref([])
const checkedSoSalesOrderLine = ref([])
const single = ref(true)
const multiple = ref(true)
const total = ref(0)
const title = ref("")
const isViewMode = ref(false) // 新增：标识是否为查看模式

const data = reactive({
  form: {},
  queryParams: {
    pageNum: 1,
    pageSize: 10,
    soNo: null,
    coId: null,
    clientId: null,
    salesRepId: null,
    orderDate: null,
    promiseDate: null,
    requestedDate: null,
    creditStatus: null,
    creditBlockedBy: null,
    orderStatus: null,
    projectId: null,
  },
  rules: {
    soNo: [
      { required: true, message: "销售订单编号不能为空", trigger: "blur" }
    ],
    clientId: [
      { required: true, message: "来源客户ID不能为空", trigger: "blur" }
    ],
    orderDate: [
      { required: true, message: "订单生成日期不能为空", trigger: "blur" }
    ],
  }
})

const { queryParams, form, rules } = toRefs(data)

/** 查询销售订单列表 */
function getList() {
  loading.value = true
  listOrderheader(queryParams.value).then(response => {
    orderheaderList.value = response.rows
    total.value = response.total
    loading.value = false
  })
}

// 取消按钮
function cancel() {
  open.value = false
  reset()
}

// 表单重置
function reset() {
  form.value = {
    soId: null,
    soNo: null,
    coId: null,
    clientId: null,
    salesRepId: null,
    orderDate: null,
    promiseDate: null,
    requestedDate: null,
    deliveryAddressId: null,
    invoiceAddressId: null,
    deliveryAddress: null,
    invoiceAddress: null,
    paymentTerms: null,
    currency: null,
    exchangeRate: null,
    totalAmount: null,
    taxAmount: null,
    discountAmount: null,
    discountRate: null,
    creditStatus: null,
    creditCheckTime: null,
    creditBlockedBy: null,
    orderStatus: null,
    projectId: null,
    isPaEnabled: null,
    confirmedTime: null,
    fulfilledTime: null,
    closedTime: null,
    cancelledTime: null,
    cancelledReason: null,
    salesOrgId: null,
    distributionChannel: null,
    customerRefNo: null,
    remark: null,
    attr1: null,
    attr2: null,
    attr3: null,
    attr4: null,
    createBy: null,
    createTime: null,
    updateBy: null,
    updateTime: null
  }
  soSalesOrderLineList.value = []
  proxy.resetForm("orderheaderRef")
}

/** 搜索按钮操作 */
function handleQuery() {
  queryParams.value.pageNum = 1
  getList()
}

/** 重置按钮操作 */
function resetQuery() {
  proxy.resetForm("queryRef")
  handleQuery()
}

// 多选框选中数据
function handleSelectionChange(selection) {
  ids.value = selection.map(item => item.soId)
  single.value = selection.length != 1
  multiple.value = !selection.length
}

/** 新增按钮操作 */
function handleAdd() {
  reset()
  isViewMode.value = false // 设置为编辑模式
  open.value = true
  title.value = "添加销售订单"
}

/** 修改按钮操作 */
function handleUpdate(row) {
  reset()
  isViewMode.value = false // 设置为编辑模式
  const _soId = row.soId || ids.value
  getOrderheader(_soId).then(response => {
    form.value = response.data
    soSalesOrderLineList.value = response.data.soSalesOrderLineList
    open.value = true
    title.value = "修改销售订单"
  })
}

/** 查看按钮操作 */
function handleView(row) {
  reset()
  isViewMode.value = true // 设置为查看模式
  const _soId = row.soId || ids.value
  getOrderheader(_soId).then(response => {
    form.value = response.data
    soSalesOrderLineList.value = response.data.soSalesOrderLineList
    open.value = true
    title.value = "查看销售订单"
  })
}

/** 提交按钮 */
function submitForm() {
  proxy.$refs["orderheaderRef"].validate(valid => {
    if (valid) {
      form.value.soSalesOrderLineList = soSalesOrderLineList.value
      if (form.value.soId != null) {
        updateOrderheader(form.value).then(response => {
          proxy.$modal.msgSuccess("修改成功")
          open.value = false
          getList()
        })
      } else {
        addOrderheader(form.value).then(response => {
          proxy.$modal.msgSuccess("新增成功")
          open.value = false
          getList()
        })
      }
    }
  })
}

/** 删除按钮操作 */
function handleDelete(row) {
  const _soIds = row.soId || ids.value
  proxy.$modal.confirm('是否确认删除销售订单编号为"' + _soIds + '"的数据项？').then(function () {
    return delOrderheader(_soIds)
  }).then(() => {
    getList()
    proxy.$modal.msgSuccess("删除成功")
  }).catch(() => { })
}

/** 销售订单行序号 */
function rowSoSalesOrderLineIndex({ row, rowIndex }) {
  row.index = rowIndex + 1
}

/** 销售订单行添加按钮操作 */
function handleAddSoSalesOrderLine() {
  let obj = {}
  obj.lineNo = ""
  obj.materialId = ""
  obj.materialCode = ""
  obj.materialName = ""
  obj.materialSpec = ""
  obj.orderedQty = ""
  obj.deliveredQty = ""
  obj.unit = ""
  obj.unitPrice = ""
  obj.lineAmount = ""
  obj.taxRate = ""
  obj.taxAmount = ""
  obj.discountPercent = ""
  obj.discountAmount = ""
  obj.requestedDate = ""
  obj.promiseDate = ""
  obj.fulfilledQty = ""
  obj.productionQty = ""
  obj.purchaseQty = ""
  obj.reservedQty = ""
  obj.lineStatus = ""
  obj.linkedMoId = ""
  obj.linkedPoId = ""
  obj.reservedBatchNo = ""
  obj.reservedSerials = ""
  obj.paGroupId = ""
  obj.warehouseId = ""
  obj.deliveryPriority = ""
  obj.customerLineRef = ""
  obj.sortOrder = ""
  obj.remark = ""
  obj.attr1 = ""
  obj.attr2 = ""
  obj.attr3 = ""
  obj.attr4 = ""
  soSalesOrderLineList.value.push(obj)
}

/** 销售订单行删除按钮操作 */
function handleDeleteSoSalesOrderLine() {
  if (checkedSoSalesOrderLine.value.length == 0) {
    proxy.$modal.msgError("请先选择要删除的销售订单行数据")
  } else {
    const soSalesOrderLines = soSalesOrderLineList.value
    const checkedSoSalesOrderLines = checkedSoSalesOrderLine.value
    soSalesOrderLineList.value = soSalesOrderLines.filter(function (item) {
      return checkedSoSalesOrderLines.indexOf(item.index) == -1
    })
  }
}

/** 复选框选中数据 */
function handleSoSalesOrderLineSelectionChange(selection) {
  checkedSoSalesOrderLine.value = selection.map(item => item.index)
}

/** 获取字典标签 */
function getDictLabel(dictOptions, value) {
  const dict = dictOptions.find(item => item.value === value)
  return dict ? dict.label : value
}

/** 导出按钮操作 */
function handleExport() {
  proxy.download('so/orderheader/export', {
    ...queryParams.value
  }, `orderheader_${new Date().getTime()}.xlsx`)
}

getList()
</script>