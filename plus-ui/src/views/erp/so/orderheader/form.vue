<template>
  <div class="app-container">
    <el-card>
      <template #header>
        <div class="card-header">
          <span class="card-title">{{ title }}</span>
          <div class="card-actions">
            <el-button type="primary" @click="submitForm" v-if="isEditable">保存</el-button>
            <el-button type="success" @click="handleSubmitFlow" v-if="isEditable && form.orderStatus === 'DRAFT'">提交审批</el-button>
            <el-button type="info" @click="onViewFlow" v-if="form.orderStatus === 'APPROVING' || form.orderStatus === 'APPROVED'">查看流程</el-button>
            <el-button @click="cancel" v-if="!props.disabled">取消</el-button>
          </div>
        </div>
      </template>

      <div class="form-container">
        <el-form :model="form" :rules="rules" ref="orderheaderRef" label-width="120px" :disabled="formDisabled">
          <el-collapse v-model="activeSections">
            <!-- ==================== 基本信息 ==================== -->
            <el-collapse-item title="基本信息" name="basic">
              <el-row :gutter="20">
                <el-col :xs="24" :sm="12" :md="8" :lg="6">
                  <el-form-item label="销售订单编号" prop="soNo">
                    <!-- 已保存单据单号不可变更，自动生成开关仅新增时可用 -->
                    <AutoGenCodeField
                      ref="autoGenCodeRef"
                      v-model="form.soNo"
                      rule-code="SO_ORDER_CODE"
                      placeholder="请输入销售订单编号"
                      :disabled="formDisabled || form.soId != null"
                    />
                  </el-form-item>
                </el-col>
                <el-col :xs="24" :sm="12" :md="8" :lg="6">
                  <el-form-item label="客户订单" prop="coId">
                    <el-input v-model="customerOrderDisplay" placeholder="请选择客户订单" readonly>
                      <template #append>
                        <el-button icon="Search" @click.stop="openCustomerOrderSelect" :disabled="!isEditable" />
                      </template>
                      <template #suffix v-if="form.coId && isEditable">
                        <el-icon class="cell-clear" title="解除关联" @click.stop="clearCustomerOrder"><CircleClose /></el-icon>
                      </template>
                    </el-input>
                  </el-form-item>
                </el-col>
                <el-col :xs="24" :sm="12" :md="8" :lg="6">
                  <el-form-item label="来源客户" prop="clientName">
                    <el-input v-model="form.clientName" placeholder="请选择来源客户" readonly>
                      <template #append>
                        <el-button icon="Search" @click.stop="openClientSelect" :disabled="!isEditable" />
                      </template>
                      <template #suffix v-if="form.clientId && isEditable">
                        <el-icon class="cell-clear" title="清除客户" @click.stop="clearClient"><CircleClose /></el-icon>
                      </template>
                    </el-input>
                  </el-form-item>
                </el-col>
                <el-col :xs="24" :sm="12" :md="8" :lg="6">
                  <el-form-item label="销售代表" prop="salesRepName">
                    <el-input v-model="form.salesRepName" placeholder="请选择销售代表" readonly>
                      <template #append>
                        <el-button icon="Search" @click.stop="openUserSelect" :disabled="!isEditable" />
                      </template>
                    </el-input>
                  </el-form-item>
                </el-col>
                <el-col :xs="24" :sm="12" :md="8" :lg="6">
                  <el-form-item label="订单生成日期" prop="orderDate">
                    <el-date-picker
                      clearable
                      v-model="form.orderDate"
                      type="date"
                      value-format="YYYY-MM-DD"
                      placeholder="请选择订单生成日期"
                      style="width: 100%"
                    />
                  </el-form-item>
                </el-col>
                <el-col :xs="24" :sm="12" :md="8" :lg="6">
                  <el-form-item label="承诺交货日" prop="promiseDate">
                    <el-date-picker
                      clearable
                      v-model="form.promiseDate"
                      type="date"
                      value-format="YYYY-MM-DD"
                      placeholder="请选择承诺交货日"
                      style="width: 100%"
                    />
                  </el-form-item>
                </el-col>
                <el-col :xs="24" :sm="12" :md="8" :lg="6">
                  <el-form-item label="客户要求交货日" prop="requestedDate">
                    <el-date-picker
                      clearable
                      v-model="form.requestedDate"
                      type="date"
                      value-format="YYYY-MM-DD"
                      placeholder="请选择客户要求交货日"
                      style="width: 100%"
                    />
                  </el-form-item>
                </el-col>
                <el-col :xs="24" :sm="12" :md="8" :lg="6">
                  <el-form-item label="订单状态" prop="orderStatus">
                    <!-- 状态由服务端强制为草稿，之后只能经审批流 / 列表页动作流转，表单内始终只读 -->
                    <dict-tag v-if="form.orderStatus" :options="so_order_status" :value="form.orderStatus" />
                    <span v-else>-</span>
                  </el-form-item>
                </el-col>
              </el-row>

              <el-row :gutter="20">
                <el-col :xs="24" :sm="12" :md="8" :lg="6">
                  <el-form-item label="审批流程" prop="selectFlowId">
                    <el-select
                      v-model="form.selectFlowId"
                      clearable
                      filterable
                      :loading="loadingFlow"
                      :disabled="formDisabled || form.orderStatus !== 'DRAFT'"
                      placeholder="请选择审批流程"
                      style="width: 100%"
                    >
                      <el-option v-for="f in flowOptions" :key="f.id" :label="f.flowName || f.businessName || '流程#' + f.id" :value="f.id" />
                    </el-select>
                  </el-form-item>
                </el-col>
                <el-col :xs="24" :sm="12" :md="16" :lg="18">
                  <el-form-item v-if="showFlowWarning" label-width="0">
                    <el-alert
                      type="warning"
                      show-icon
                      :closable="false"
                      title="未配置可用审批流程，请先在 workflow 模块配置流程模板并绑定到 soSalesOrderHeaderService。"
                    />
                  </el-form-item>
                </el-col>
              </el-row>
            </el-collapse-item>

            <!-- ==================== 地址信息 ==================== -->
            <el-collapse-item title="地址信息" name="address">
              <el-row :gutter="20">
                <el-col :xs="24" :sm="12" :md="8" :lg="6">
                  <el-form-item label="客户档案地址">
                    <div class="inline-field">
                      <span class="text-hint">{{ clientAddress || '未选择客户或无地址' }}</span>
                      <el-button size="small" icon="MagicStick" :disabled="!isEditable || !clientAddress" @click="applyClientAddress">带出</el-button>
                    </div>
                  </el-form-item>
                </el-col>
                <el-col :xs="24" :sm="24" :md="16" :lg="12">
                  <el-form-item label="发货地址详情" prop="deliveryAddress">
                    <el-input v-model="form.deliveryAddress" type="textarea" placeholder="请输入发货地址详情" :rows="2" />
                  </el-form-item>
                </el-col>
                <el-col :xs="24" :sm="24" :md="16" :lg="12">
                  <el-form-item label="开票地址详情" prop="invoiceAddress">
                    <el-input v-model="form.invoiceAddress" type="textarea" placeholder="请输入开票地址详情" :rows="2" />
                  </el-form-item>
                </el-col>
              </el-row>
            </el-collapse-item>

            <!-- ==================== 财务信息 ==================== -->
            <el-collapse-item title="财务信息" name="finance">
              <el-row :gutter="20">
                <el-col :xs="24" :sm="12" :md="8" :lg="6">
                  <el-form-item label="付款条款" prop="paymentTerms">
                    <el-input v-model="form.paymentTerms" placeholder="请输入付款条款" />
                  </el-form-item>
                </el-col>
                <el-col :xs="24" :sm="12" :md="8" :lg="6">
                  <el-form-item label="货币" prop="currency">
                    <el-select v-model="form.currency" placeholder="请选择货币" style="width: 100%">
                      <el-option v-for="dict in so_currency_type" :key="dict.value" :label="dict.label" :value="dict.value" />
                    </el-select>
                  </el-form-item>
                </el-col>
                <el-col :xs="24" :sm="12" :md="8" :lg="6">
                  <el-form-item label="汇率" prop="exchangeRate">
                    <el-input-number v-model="form.exchangeRate" controls-position="right" :min="0" :precision="4" style="width: 100%" />
                  </el-form-item>
                </el-col>
                <el-col :xs="24" :sm="12" :md="8" :lg="6">
                  <el-form-item label="含税总金额" prop="totalAmount">
                    <el-input :model-value="formatAmount(form.totalAmount)" readonly />
                  </el-form-item>
                </el-col>
                <el-col :xs="24" :sm="12" :md="8" :lg="6">
                  <el-form-item label="税款" prop="taxAmount">
                    <el-input :model-value="formatAmount(form.taxAmount)" readonly />
                  </el-form-item>
                </el-col>
                <el-col :xs="24" :sm="12" :md="8" :lg="6">
                  <el-form-item label="整单折扣额" prop="discountAmount">
                    <el-input :model-value="formatAmount(form.discountAmount)" readonly />
                  </el-form-item>
                </el-col>
                <el-col :xs="24" :sm="12" :md="8" :lg="6">
                  <el-form-item label="整单折扣率%" prop="discountRate">
                    <el-input-number
                      v-model="form.discountRate"
                      controls-position="right"
                      :min="0"
                      :max="100"
                      :precision="2"
                      @change="calcHeader"
                      style="width: 100%"
                    />
                  </el-form-item>
                </el-col>
              </el-row>
            </el-collapse-item>

            <!-- ==================== 信用信息 ==================== -->
            <el-collapse-item title="信用信息" name="credit">
              <el-row :gutter="20">
                <el-col :xs="24" :sm="12" :md="8" :lg="6">
                  <el-form-item label="信用状态" prop="creditStatus">
                    <!-- 信用状态/检查时间/拦截人由信用检查服务在确认订单时写入，表单内只读 -->
                    <dict-tag v-if="form.creditStatus" :options="so_credit_status" :value="form.creditStatus" />
                    <span v-else>-</span>
                  </el-form-item>
                </el-col>
                <el-col :xs="24" :sm="12" :md="8" :lg="6">
                  <el-form-item label="信用检查时间" prop="creditCheckTime">
                    <span class="text-value">{{ formatDate(form.creditCheckTime) }}</span>
                  </el-form-item>
                </el-col>
                <el-col :xs="24" :sm="12" :md="8" :lg="6">
                  <el-form-item label="信用拦截人ID" prop="creditBlockedBy">
                    <span class="text-value">{{ form.creditBlockedBy ?? '-' }}</span>
                  </el-form-item>
                </el-col>
              </el-row>
            </el-collapse-item>

            <!-- ==================== 项目信息 ==================== -->
            <el-collapse-item title="项目信息" name="project">
              <el-row :gutter="20">
                <el-col :xs="24" :sm="12" :md="8" :lg="6">
                  <el-form-item label="项目ID" prop="projectId">
                    <el-input-number
                      v-model="form.projectId"
                      controls-position="right"
                      :min="0"
                      :precision="0"
                      :controls="false"
                      placeholder="请输入项目ID"
                      style="width: 100%"
                    />
                  </el-form-item>
                </el-col>
                <el-col :xs="24" :sm="12" :md="8" :lg="6">
                  <el-form-item label="是否启用项目配套发料" prop="isPaEnabled">
                    <el-radio-group v-model="form.isPaEnabled">
                      <el-radio v-for="dict in sys_true_or_false" :key="dict.value" :label="Number(dict.value)">{{ dict.label }}</el-radio>
                    </el-radio-group>
                  </el-form-item>
                </el-col>
              </el-row>
            </el-collapse-item>

            <!-- ==================== 时间信息 ==================== -->
            <el-collapse-item title="时间信息" name="time">
              <el-row :gutter="20">
                <el-col :xs="24" :sm="12" :md="8" :lg="6">
                  <el-form-item label="确认时间" prop="confirmedTime">
                    <span class="text-value">{{ formatDate(form.confirmedTime) }}</span>
                  </el-form-item>
                </el-col>
                <el-col :xs="24" :sm="12" :md="8" :lg="6">
                  <el-form-item label="完全履行时间" prop="fulfilledTime">
                    <span class="text-value">{{ formatDate(form.fulfilledTime) }}</span>
                  </el-form-item>
                </el-col>
                <el-col :xs="24" :sm="12" :md="8" :lg="6">
                  <el-form-item label="结束时间" prop="closedTime">
                    <span class="text-value">{{ formatDate(form.closedTime) }}</span>
                  </el-form-item>
                </el-col>
                <el-col :xs="24" :sm="12" :md="8" :lg="6">
                  <el-form-item label="取消时间" prop="cancelledTime">
                    <span class="text-value">{{ formatDate(form.cancelledTime) }}</span>
                  </el-form-item>
                </el-col>
              </el-row>
            </el-collapse-item>

            <!-- ==================== 其他信息 ==================== -->
            <el-collapse-item title="其他信息" name="other">
              <el-row :gutter="20">
                <el-col :xs="24" :sm="12" :md="8" :lg="6">
                  <el-form-item label="取消原因" prop="cancelledReason">
                    <el-input
                      v-model="form.cancelledReason"
                      :readonly="form.orderStatus !== 'CANCELLED'"
                      :placeholder="form.orderStatus === 'CANCELLED' ? '请输入取消原因' : '订单取消后由列表页动作写入'"
                    />
                  </el-form-item>
                </el-col>
                <el-col :xs="24" :sm="12" :md="8" :lg="6">
                  <el-form-item label="销售部门" prop="deptId">
                    <dept-select v-model="form.deptId" category="SALES" placeholder="请选择销售部门" @change="handleDeptChange" />
                  </el-form-item>
                </el-col>
                <el-col :xs="24" :sm="12" :md="8" :lg="6">
                  <el-form-item label="分销渠道" prop="distributionChannel">
                    <el-input v-model="form.distributionChannel" placeholder="请输入分销渠道" />
                  </el-form-item>
                </el-col>
                <el-col :xs="24" :sm="12" :md="8" :lg="6">
                  <el-form-item label="客户参考号" prop="customerRefNo">
                    <el-input v-model="form.customerRefNo" placeholder="请输入客户参考号" />
                  </el-form-item>
                </el-col>
                <el-col :xs="24">
                  <el-form-item label="备注" prop="remark">
                    <el-input v-model="form.remark" type="textarea" placeholder="请输入内容" :rows="3" />
                  </el-form-item>
                </el-col>
              </el-row>
            </el-collapse-item>

            <!-- ==================== 销售订单行信息 ==================== -->
            <el-collapse-item title="销售订单行信息" name="lines">
              <el-row :gutter="10" class="mb8">
                <el-col :span="1.5">
                  <el-button type="primary" icon="Plus" @click="handleAddSoSalesOrderLine" v-if="isEditable">添加</el-button>
                </el-col>
                <el-col :span="1.5">
                  <el-button type="danger" icon="Delete" @click="handleDeleteSoSalesOrderLine" v-if="isEditable">删除</el-button>
                </el-col>
                <el-col :span="1.5">
                  <el-button icon="Refresh" @click="recalcAll" v-if="isEditable">重算金额</el-button>
                </el-col>
              </el-row>
              <el-table
                :data="soSalesOrderLineList"
                @selection-change="handleSoSalesOrderLineSelectionChange"
                border
                show-summary
                :summary-method="getSummaries"
                class="line-table"
              >
                <el-table-column type="selection" width="46" align="center" v-if="isEditable" />
                <el-table-column label="序号" type="index" width="56" align="center" />
                <el-table-column label="行号" prop="lineNo" width="80" align="center">
                  <template #default="scope">
                    <el-input v-model="scope.row.lineNo" placeholder="自动" v-if="isEditable" />
                    <span v-else>{{ scope.row.lineNo || '-' }}</span>
                  </template>
                </el-table-column>
                <el-table-column label="物料" prop="materialName" min-width="260">
                  <template #default="scope">
                    <el-input v-model="scope.row.materialName" placeholder="请选择物料" readonly v-if="isEditable">
                      <template #append>
                        <el-button icon="Search" @click.stop="openMaterialSelect(scope.row)" />
                      </template>
                      <template #suffix v-if="scope.row.materialId">
                        <el-icon class="cell-clear" title="清除物料" @click.stop="clearMaterial(scope.row)"><CircleClose /></el-icon>
                      </template>
                    </el-input>
                    <span v-else>{{ scope.row.materialName || '-' }}</span>
                    <div class="line-sub">{{ materialSubText(scope.row) }}</div>
                  </template>
                </el-table-column>
                <el-table-column label="客户产品代号" prop="customerProductCode" width="150">
                  <template #default="scope">
                    <el-input v-model="scope.row.customerProductCode" placeholder="客户内部编号(可空)" v-if="isEditable" />
                    <span v-else>{{ scope.row.customerProductCode || '-' }}</span>
                  </template>
                </el-table-column>
                <el-table-column label="客户产品名称" prop="customerProductName" width="150">
                  <template #default="scope">
                    <el-input v-model="scope.row.customerProductName" placeholder="客户对该产品的称呼" v-if="isEditable" />
                    <span v-else>{{ scope.row.customerProductName || '-' }}</span>
                  </template>
                </el-table-column>
                <el-table-column label="订单数量" prop="orderedQty" width="140">
                  <template #default="scope">
                    <el-input-number
                      v-model="scope.row.orderedQty"
                      controls-position="right"
                      :min="0"
                      :precision="4"
                      @change="calcLine(scope.row)"
                      style="width: 100%"
                      v-if="isEditable"
                    />
                    <span v-else>{{ scope.row.orderedQty }}</span>
                  </template>
                </el-table-column>
                <el-table-column label="单位" prop="unit" width="120">
                  <template #default="scope">
                    <el-select v-model="scope.row.unit" placeholder="请选择单位" v-if="isEditable" filterable clearable style="width: 100%">
                      <el-option v-for="u in uomOptions" :key="u.uomId" :label="u.uomName" :value="u.uomName" />
                    </el-select>
                    <span v-else>{{ scope.row.unit || '-' }}</span>
                  </template>
                </el-table-column>
                <el-table-column label="单价" prop="unitPrice" width="140">
                  <template #default="scope">
                    <el-input-number
                      v-model="scope.row.unitPrice"
                      controls-position="right"
                      :min="0"
                      :precision="4"
                      @change="calcLine(scope.row)"
                      style="width: 100%"
                      v-if="isEditable"
                    />
                    <span v-else>{{ scope.row.unitPrice }}</span>
                  </template>
                </el-table-column>
                <el-table-column label="行金额" prop="lineAmount" width="120" align="right">
                  <template #default="scope">
                    <span>{{ formatAmount(scope.row.lineAmount) }}</span>
                  </template>
                </el-table-column>
                <el-table-column label="行折扣率%" prop="discountPercent" width="130">
                  <template #default="scope">
                    <el-input-number
                      v-model="scope.row.discountPercent"
                      controls-position="right"
                      :min="0"
                      :max="100"
                      :precision="2"
                      @change="calcLine(scope.row)"
                      style="width: 100%"
                      v-if="isEditable"
                    />
                    <span v-else>{{ scope.row.discountPercent }}</span>
                  </template>
                </el-table-column>
                <el-table-column label="行折扣额" prop="discountAmount" width="120" align="right">
                  <template #default="scope">
                    <span>{{ formatAmount(scope.row.discountAmount) }}</span>
                  </template>
                </el-table-column>
                <el-table-column label="税率%" prop="taxRate" width="120">
                  <template #default="scope">
                    <el-input-number
                      v-model="scope.row.taxRate"
                      controls-position="right"
                      :min="0"
                      :max="100"
                      :precision="2"
                      @change="calcLine(scope.row)"
                      style="width: 100%"
                      v-if="isEditable"
                    />
                    <span v-else>{{ scope.row.taxRate }}</span>
                  </template>
                </el-table-column>
                <el-table-column label="行税额" prop="taxAmount" width="120" align="right">
                  <template #default="scope">
                    <span>{{ formatAmount(scope.row.taxAmount) }}</span>
                  </template>
                </el-table-column>
                <el-table-column label="行要求交期" prop="requestedDate" width="150">
                  <template #default="scope">
                    <el-date-picker
                      clearable
                      v-model="scope.row.requestedDate"
                      type="date"
                      value-format="YYYY-MM-DD"
                      placeholder="请选择"
                      style="width: 100%"
                      v-if="isEditable"
                    />
                    <span v-else>{{ scope.row.requestedDate || '-' }}</span>
                  </template>
                </el-table-column>
                <el-table-column label="行承诺交期" prop="promiseDate" width="150">
                  <template #default="scope">
                    <el-date-picker
                      clearable
                      v-model="scope.row.promiseDate"
                      type="date"
                      value-format="YYYY-MM-DD"
                      placeholder="请选择"
                      style="width: 100%"
                      v-if="isEditable"
                    />
                    <span v-else>{{ scope.row.promiseDate || '-' }}</span>
                  </template>
                </el-table-column>
                <el-table-column label="默认仓库" prop="warehouseId" width="170">
                  <template #default="scope">
                    <el-select v-model="scope.row.warehouseId" placeholder="请选择仓库" filterable clearable style="width: 100%" v-if="isEditable">
                      <el-option v-for="w in warehouseOptions" :key="w.warehouseId" :label="w.warehouseName" :value="w.warehouseId" />
                    </el-select>
                    <span v-else>{{ scope.row.warehouseName || '-' }}</span>
                  </template>
                </el-table-column>
                <el-table-column label="交付优先级" prop="deliveryPriority" width="120">
                  <template #default="scope">
                    <el-select v-model="scope.row.deliveryPriority" placeholder="请选择" clearable style="width: 100%" v-if="isEditable">
                      <el-option v-for="dict in so_order_priority" :key="dict.value" :label="dict.label" :value="Number(dict.value)" />
                    </el-select>
                    <span v-else>{{ getDictLabel(so_order_priority, scope.row.deliveryPriority) }}</span>
                  </template>
                </el-table-column>
                <el-table-column label="客户行参考号" prop="customerLineRef" width="150">
                  <template #default="scope">
                    <el-input v-model="scope.row.customerLineRef" placeholder="请输入" v-if="isEditable" />
                    <span v-else>{{ scope.row.customerLineRef || '-' }}</span>
                  </template>
                </el-table-column>
                <!-- 以下为履约过程回写的只读列：编辑态与查看态保持一致，避免列数跳变 -->
                <el-table-column label="已发货数量" prop="deliveredQty" width="110" align="right">
                  <template #default="scope">
                    <span>{{ scope.row.deliveredQty ?? 0 }}</span>
                  </template>
                </el-table-column>
                <!-- 退货闭环（T3.3）：已退货量由退货单执行入库时回写，可退数量 = 已发货 − 已退货 -->
                <el-table-column label="已退货数量" prop="returnedQty" width="110" align="right">
                  <template #default="scope">
                    <span>{{ scope.row.returnedQty ?? 0 }}</span>
                  </template>
                </el-table-column>
                <el-table-column label="已投产数量" prop="productionQty" width="110" align="right">
                  <template #default="scope">
                    <span>{{ scope.row.productionQty ?? 0 }}</span>
                  </template>
                </el-table-column>
                <el-table-column label="已采购数量" prop="purchaseQty" width="110" align="right">
                  <template #default="scope">
                    <span>{{ scope.row.purchaseQty ?? 0 }}</span>
                  </template>
                </el-table-column>
                <el-table-column label="已预留库存数量" prop="reservedQty" width="130" align="right">
                  <template #default="scope">
                    <span>{{ scope.row.reservedQty ?? 0 }}</span>
                  </template>
                </el-table-column>
                <el-table-column label="订单明细状态" prop="lineStatus" width="120" align="center">
                  <template #default="scope">
                    <dict-tag v-if="scope.row.lineStatus" :options="so_order_line_status" :value="scope.row.lineStatus" />
                    <span v-else>-</span>
                  </template>
                </el-table-column>
                <el-table-column label="OQC检验状态" prop="oqcStatus" width="120" align="center">
                  <template #default="scope">
                    <dict-tag v-if="scope.row.oqcStatus" :options="bd_quality_status" :value="scope.row.oqcStatus" />
                    <span v-else>-</span>
                  </template>
                </el-table-column>
                <el-table-column label="关联生产计划" prop="linkedPlanNo" width="150" align="center">
                  <template #default="scope">
                    <el-link v-if="scope.row.linkedPlanNo" type="primary" @click="viewProductionPlan(scope.row)">{{
                      scope.row.linkedPlanNo
                    }}</el-link>
                    <span v-else>-</span>
                  </template>
                </el-table-column>
                <el-table-column label="关联生产工单" prop="linkedWoNo" width="160" align="center">
                  <template #default="scope">
                    <el-link v-if="scope.row.linkedWoId" type="primary" @click="viewWorkOrder(scope.row)">{{ scope.row.linkedWoNo || '-' }}</el-link>
                    <span v-else>-</span>
                  </template>
                </el-table-column>
                <el-table-column label="预留批次号" prop="reservedBatchNo" width="140">
                  <template #default="scope">
                    <span>{{ scope.row.reservedBatchNo || '-' }}</span>
                  </template>
                </el-table-column>
                <el-table-column label="行备注" prop="remark" min-width="150">
                  <template #default="scope">
                    <el-input v-model="scope.row.remark" placeholder="请输入" v-if="isEditable" />
                    <span v-else>{{ scope.row.remark || '-' }}</span>
                  </template>
                </el-table-column>
              </el-table>
            </el-collapse-item>
          </el-collapse>
        </el-form>
      </div>
    </el-card>

    <!-- 客户订单选择对话框 -->
    <CustomerOrderSelectDialog ref="customerOrderSelectRef" @confirm="handleCustomerOrderSelect" />
    <!-- 来源客户选择对话框 -->
    <ClientSelectDialog ref="clientSelectRef" @confirm="handleClientSelect" />
    <!-- 物料选择对话框 -->
    <MaterialSelectDialog ref="materialSelectDialogRef" :title="materialDialogTitle" @select="handleMaterialSelected" />

    <!-- 销售代表选择 -->
    <el-dialog title="选择销售代表" v-model="userDialog.visible" width="800px" append-to-body>
      <el-form :inline="true" :model="userDialog.queryParams">
        <el-form-item label="用户名称">
          <el-input v-model="userDialog.queryParams.userName" placeholder="请输入用户名称" clearable @keyup.enter="handleUserQuery" />
        </el-form-item>
        <el-form-item label="手机号码">
          <el-input v-model="userDialog.queryParams.phonenumber" placeholder="请输入手机号码" clearable @keyup.enter="handleUserQuery" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" icon="Search" @click="handleUserQuery">搜索</el-button>
          <el-button icon="Refresh" @click="resetUserQuery">重置</el-button>
        </el-form-item>
      </el-form>
      <el-table v-loading="userDialog.loading" :data="userDialog.list" height="400" @row-dblclick="selectUser">
        <el-table-column label="用户账号" align="center" prop="userName" />
        <el-table-column label="用户昵称" align="center" prop="nickName" />
        <el-table-column label="手机号码" align="center" prop="phonenumber" />
        <el-table-column label="操作" align="center" width="100">
          <template #default="scope">
            <el-button link type="primary" @click="selectUser(scope.row)">选择</el-button>
          </template>
        </el-table-column>
      </el-table>
      <pagination
        v-show="userDialog.total > 0"
        :total="userDialog.total"
        v-model:page="userDialog.queryParams.pageNum"
        v-model:limit="userDialog.queryParams.pageSize"
        @pagination="getUserList"
      />
    </el-dialog>
  </div>
</template>

<script setup lang="ts" name="OrderForm">
import { getOrderheader, addOrderheader, updateOrderheader, submitOrderFlow, getOrderProcId, resolveOrderPrice } from '@/api/erp/so/orderheader';
import { listMeasureAll } from '@/api/erp/bd/measure';
import { listWarehouse } from '@/api/erp/wm/warehouse';
import { getClient } from '@/api/erp/bd/client';
import { listUser } from '@/api/system/user';
import MaterialSelectDialog from '@/components/MaterialSelectDialog/index.vue';
import AutoGenCodeField from '@/components/AutoGenCodeField/index.vue';
import { getProcesss } from '@/api/workflow/process/index';
import { useRouter, useRoute } from 'vue-router';
import CustomerOrderSelectDialog from '../components/CustomerOrderSelectDialog.vue';
import ClientSelectDialog from '../components/ClientSelectDialog.vue';
import type { CustomerOrderVO } from '@/api/erp/so/customer-order/types';
import type { ClientVO } from '@/api/erp/bd/client/types';
import type { OrderheaderForm } from '@/api/erp/so/orderheader/types';

const { proxy } = getCurrentInstance() as ComponentInternalInstance;
const { so_credit_status, so_order_status, sys_true_or_false, so_currency_type, so_order_line_status, so_order_priority, bd_quality_status } =
  toRefs<any>(
    proxy.useDict(
      'so_credit_status',
      'so_order_status',
      'sys_true_or_false',
      'so_currency_type',
      'so_order_line_status',
      'so_order_priority',
      'bd_quality_status'
    )
  );

/** 流程详情 / HistoricDetail 嵌入时传入 */
const props = defineProps({
  disabled: { type: Boolean, default: false },
  model: { type: Object, default: () => ({}) },
  customFormData: { type: Object, default: () => ({}) },
  isNew: { type: Boolean, default: false }
});

const route = useRoute();
const router = useRouter();

const form = ref<OrderheaderForm>({});
const soSalesOrderLineList = ref<any[]>([]);
const checkedSoSalesOrderLine = ref<any[]>([]);
const isViewMode = ref(false);
const title = ref('');

// 流程相关状态
const loadingFlow = ref(false);
const flowOptions = ref([]);

/** 表单可编辑（查看模式 / 流程嵌入只读态均为 false） */
const isEditable = computed(() => !isViewMode.value && !props.disabled);
/** 表单整体禁用 */
const formDisabled = computed(() => !isEditable.value);

/** 审批流程缺失告警（仅新增/编辑且无可用流程时提示） */
const showFlowWarning = computed(() => isEditable.value && !loadingFlow.value && flowOptions.value.length === 0 && !form.value.selectFlowId);

// 分区折叠：默认展开核心分区，次要分区收起
const activeSections = ref<string[]>(['basic', 'address', 'finance', 'lines']);

const autoGenCodeRef = ref<any>(null);
const materialSelectDialogRef = ref<any>(null);
const customerOrderSelectRef = ref<InstanceType<typeof CustomerOrderSelectDialog> | null>(null);
const clientSelectRef = ref<InstanceType<typeof ClientSelectDialog> | null>(null);
const materialDialogTitle = ref('选择物料');
/** 当前正在选择物料的订单行（按行对象引用，避免删行后索引错位） */
const currentMaterialRow = ref<any>(null);

/** 客户档案地址（选中客户或加载已有单据时带出，用于一键填充发货/开票地址） */
const clientAddress = ref('');
/** 客户订单优先级，作为新增行的默认交付优先级 */
const coPriority = ref<number | null>(null);

// 计量单位下拉选项
const uomOptions = ref<any[]>([]);
function loadUomList() {
  listMeasureAll()
    .then((response: any) => {
      const list: any[] = response.data || response.rows || [];
      // 仅保留启用状态单位；若过滤后为空则回退全量，避免字典语义差异导致下拉为空
      const enabled = list.filter((u) => u.status == null || String(u.status) === '1');
      uomOptions.value = enabled.length ? enabled : list;
    })
    .catch(() => {
      uomOptions.value = [];
      proxy.$modal.msgError('计量单位加载失败');
    });
}

// 仓库下拉选项（销售行默认发货仓库）
const warehouseOptions = ref<any[]>([]);
function loadWarehouseList() {
  listWarehouse({ pageNum: 1, pageSize: 1000 })
    .then((response: any) => {
      warehouseOptions.value = response.rows || response.data || [];
    })
    .catch(() => {
      warehouseOptions.value = [];
      proxy.$modal.msgError('仓库列表加载失败');
    });
}

/** 客户订单回显文本：优先「客户订单号 - 客户名称」，缺号时退回 ID */
const customerOrderDisplay = computed(() => {
  if (!form.value.coId) return '';
  const parts = [form.value.coNo, form.value.clientName].filter((v) => v != null && v !== '');
  return parts.length ? parts.join(' - ') : `客户订单#${form.value.coId}`;
});

const rules = {
  soNo: [{ required: true, message: '销售订单编号不能为空', trigger: 'blur' }],
  clientName: [{ required: true, message: '来源客户不能为空', trigger: 'change' }],
  orderDate: [{ required: true, message: '订单生成日期不能为空', trigger: 'change' }]
};

// ==================== 表单默认值 ====================

/** 今日日期（YYYY-MM-DD，本地时区） */
function today(): string {
  const d = new Date();
  const p = (n: number) => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}`;
}

/** 新增单据的空白表单（含业务默认值：草稿、待检查、人民币、汇率 1、当日） */
function createEmptyForm(): OrderheaderForm {
  return {
    soId: null,
    soNo: null,
    coId: null,
    coNo: null,
    clientId: null,
    clientName: null,
    salesRepId: null,
    salesRepName: null,
    orderDate: today(),
    promiseDate: null,
    requestedDate: null,
    deliveryAddress: null,
    invoiceAddress: null,
    paymentTerms: null,
    currency: 'CNY',
    exchangeRate: 1,
    totalAmount: 0,
    taxAmount: 0,
    discountAmount: 0,
    discountRate: 0,
    creditStatus: 'NOT_CHECKED',
    creditCheckTime: null,
    creditBlockedBy: null,
    orderStatus: 'DRAFT',
    projectId: null,
    isPaEnabled: 0,
    confirmedTime: null,
    fulfilledTime: null,
    closedTime: null,
    cancelledTime: null,
    cancelledReason: null,
    deptId: null,
    deptName: null,
    distributionChannel: null,
    customerRefNo: null,
    remark: null,
    attr1: null,
    attr2: null,
    attr3: null,
    attr4: null,
    selectFlowId: null
  };
}

function reset() {
  form.value = createEmptyForm();
  soSalesOrderLineList.value = [];
  checkedSoSalesOrderLine.value = [];
  clientAddress.value = '';
  coPriority.value = null;
  autoFilled = {};
  autoGenCodeRef.value?.reset();
  proxy.resetForm('orderheaderRef');
}

// ==================== 自动赋值（可精确回滚） ====================

/**
 * 自动赋值记录：字段 -> 自动写入的值。
 * 取消关联/重新选择时只回滚「仍等于自动写入值」的字段，用户手工改过的内容不会被误清。
 */
let autoFilled: Record<string, any> = {};

/** 回滚上一次自动赋值（仅回滚未被用户修改的字段） */
function revertAutoFills() {
  Object.keys(autoFilled).forEach((key) => {
    if ((form.value as any)[key] === autoFilled[key]) {
      (form.value as any)[key] = null;
    }
  });
  autoFilled = {};
}

/** 写入自动赋值；overwrite=false 时仅填充空字段 */
function applyAutoFills(patch: Record<string, any>, overwrite = true) {
  Object.entries(patch).forEach(([key, value]) => {
    const current = (form.value as any)[key];
    const isEmpty = current === null || current === undefined || current === '';
    if (overwrite || isEmpty) {
      (form.value as any)[key] = value;
      autoFilled[key] = value;
    }
  });
}

/** 打开来源客户选择对话框 */
function openClientSelect() {
  clientSelectRef.value?.open();
}

/** 选中来源客户：带出客户主数据，并解除与之冲突的客户订单关联 */
function handleClientSelect(client: ClientVO) {
  const changed = form.value.clientId != null && String(form.value.clientId) !== String(client.clientId);
  if (changed && form.value.coId) {
    clearCustomerOrder();
  } else {
    revertAutoFills();
  }
  applyAutoFills({
    clientId: client.clientId,
    clientName: client.clientName,
    salesRepId: client.salesRepId ?? null,
    salesRepName: client.salesRepName ?? null,
    deptId: client.deptId ?? null,
    deptName: client.deptName ?? null
  });
  // 地址仅在为空时填充，避免覆盖已手工录入的收货地址
  applyAutoFills({ deliveryAddress: client.address || null, invoiceAddress: client.address || null }, false);
  clientAddress.value = client.address || '';
}

/** 清除已选择的来源客户（连带解除客户订单关联并回滚自动带出的字段） */
function clearClient() {
  clearCustomerOrder();
  form.value.clientId = null;
  form.value.clientName = null;
  clientAddress.value = '';
}

/** 打开客户订单选择对话框 */
function openCustomerOrderSelect() {
  customerOrderSelectRef.value?.open();
}

/** 选中客户订单：带出可映射字段（与客户订单保持一致，重新选择时先回滚上次自动写入） */
function handleCustomerOrderSelect(order: CustomerOrderVO) {
  revertAutoFills();
  form.value.coId = order.coId;
  form.value.coNo = order.coNo ?? null;
  applyAutoFills({
    clientId: order.clientId ?? null,
    clientName: order.clientName ?? null,
    orderDate: order.orderDate ?? null,
    requestedDate: order.requestedDate ?? null,
    currency: order.currency ?? null,
    salesRepId: order.salesRepId ?? null,
    salesRepName: order.salesRepName ?? null,
    distributionChannel: order.sourceChannel ?? null,
    customerRefNo: order.customerPoNo ?? null,
    remark: order.remark ?? null
  });
  coPriority.value = order.priority ?? null;
  if (order.clientId) {
    loadClientAddress(order.clientId);
  }
  // 含税总金额由订单行汇总计算，不沿用客户订单原值
  calcHeader();
}

/** 解除客户订单关联（连带回滚自动带出的字段） */
function clearCustomerOrder() {
  revertAutoFills();
  form.value.coId = null;
  form.value.coNo = null;
  coPriority.value = null;
}

/** 查询客户档案地址（选中客户 / 加载已有单据时调用） */
function loadClientAddress(clientId: any) {
  if (clientId == null) {
    clientAddress.value = '';
    return;
  }
  getClient(clientId)
    .then((res: any) => {
      clientAddress.value = res.data?.address || '';
    })
    .catch(() => {
      clientAddress.value = '';
    });
}

/** 将客户档案地址填充到发货/开票地址 */
function applyClientAddress() {
  if (!clientAddress.value) return;
  form.value.deliveryAddress = clientAddress.value;
  form.value.invoiceAddress = clientAddress.value;
}

/** 销售部门变更：回填部门名称并清空销售代表（人员归属已变） */
function handleDeptChange(dept: any) {
  form.value.deptName = dept ? dept.deptName : null;
  form.value.salesRepId = null;
  form.value.salesRepName = null;
}

// ==================== 销售代表选择 ====================

const userDialog = reactive({
  visible: false,
  loading: false,
  list: [] as any[],
  total: 0,
  queryParams: { pageNum: 1, pageSize: 10, userName: undefined as any, phonenumber: undefined as any, deptId: undefined as any }
});

function openUserSelect() {
  userDialog.visible = true;
  userDialog.queryParams.pageNum = 1;
  userDialog.queryParams.deptId = form.value.deptId ?? undefined;
  getUserList();
}

function getUserList() {
  userDialog.loading = true;
  listUser(userDialog.queryParams)
    .then((res: any) => {
      userDialog.list = res.rows || [];
      userDialog.total = res.total || 0;
    })
    .finally(() => {
      userDialog.loading = false;
    });
}

function handleUserQuery() {
  userDialog.queryParams.pageNum = 1;
  getUserList();
}

function resetUserQuery() {
  userDialog.queryParams.userName = undefined;
  userDialog.queryParams.phonenumber = undefined;
  userDialog.queryParams.deptId = form.value.deptId ?? undefined;
  handleUserQuery();
}

function selectUser(row: any) {
  form.value.salesRepId = row.userId;
  form.value.salesRepName = row.nickName || row.userName;
  userDialog.visible = false;
}

// ==================== 审批流程 ====================

/** 将流程下发的业务数据写入本地状态 */
function applyWorkflowFormPayload(payload: Record<string, any>) {
  if (!payload || typeof payload !== 'object') return;
  const merged = JSON.parse(JSON.stringify(payload)) as Record<string, any>;
  form.value = merged;
  soSalesOrderLineList.value = Array.isArray(merged.soSalesOrderLineList) ? merged.soSalesOrderLineList : [];
  if (merged.clientId) {
    loadClientAddress(merged.clientId);
  }
}

/** 加载审批流列表 */
function loadFlows() {
  loadingFlow.value = true;
  getProcesss({ serviceName: 'soSalesOrderHeaderService' })
    .then((response: any) => {
      flowOptions.value = response?.data || [];
      tryAutoSelect();
    })
    .catch(() => {
      flowOptions.value = [];
    })
    .finally(() => {
      loadingFlow.value = false;
    });
}

/** 尝试自动选择流程（仅一个选项时自动选中） */
function tryAutoSelect() {
  if (!form.value.selectFlowId && flowOptions.value.length === 1) {
    form.value.selectFlowId = flowOptions.value[0].id;
  }
}

watch(
  () => props.customFormData,
  (val) => {
    if (val && typeof val === 'object' && (val as any).soId != null) {
      applyWorkflowFormPayload(val as Record<string, any>);
      isViewMode.value = props.disabled;
      title.value = props.disabled ? '销售订单（流程）' : '销售订单';
      loadFlows();
    }
  },
  { deep: true, immediate: true }
);

onMounted(() => {
  loadUomList();
  loadWarehouseList();
  const id = route.query.id;
  const action = route.query.action as string | undefined;
  isViewMode.value = action === 'view';

  if (action === 'add') {
    title.value = '新增销售订单';
    reset();
    loadFlows();
    return;
  }
  if (id) {
    title.value = action === 'view' ? '查看销售订单' : '修改销售订单';
    getInfo(id);
    if (action === 'edit') {
      loadFlows();
    }
    return;
  }
  // 无 id 且非 add：按新增处理，避免空白页
  title.value = '新增销售订单';
  reset();
  loadFlows();
});

function getInfo(id: any) {
  getOrderheader(id)
    .then((response: any) => {
      const data = response.data || {};
      form.value = data;
      soSalesOrderLineList.value = data.soSalesOrderLineList || [];
      // 已有单据带出客户档案地址，供「带出」按钮使用
      loadClientAddress(data.clientId);
      tryAutoSelect();
      // 编辑态重算金额（含税总金额/税款/折扣等由行汇总自动带出）；查看态保留数据库原值
      if (isEditable.value) {
        recalcAll();
      }
    })
    .catch((error: any) => {
      proxy.$modal.msgError('获取数据失败: ' + error.message);
    });
}

// ==================== 明细行 ====================

/** 生成下一个行号（按 DDL 约定 0010/0020 步进） */
function nextLineNo(): string {
  const max = soSalesOrderLineList.value.reduce((acc, row) => {
    const n = parseInt(String(row.lineNo ?? '').replace(/\D/g, ''), 10);
    return Number.isFinite(n) ? Math.max(acc, n) : acc;
  }, 0);
  return String(max + 10).padStart(4, '0');
}

/** 重排排序号（删行后保持连续） */
function renumberSortOrder() {
  soSalesOrderLineList.value.forEach((row, index) => {
    row.sortOrder = index + 1;
  });
}

function handleAddSoSalesOrderLine() {
  soSalesOrderLineList.value.push({
    lineNo: nextLineNo(),
    materialId: null,
    materialCode: '',
    materialName: '',
    materialSpec: '',
    orderedQty: 1,
    deliveredQty: 0,
    invoicedQty: 0,
    unit: '',
    unitPrice: 0,
    lineAmount: 0,
    taxRate: 0,
    taxAmount: 0,
    discountPercent: 0,
    discountAmount: 0,
    // 交期默认取表头，用户仍可逐行调整
    requestedDate: form.value.requestedDate || '',
    promiseDate: form.value.promiseDate || '',
    fulfilledQty: 0,
    productionQty: 0,
    purchaseQty: 0,
    reservedQty: 0,
    lineStatus: 'PENDING',
    linkedMoId: null,
    reservedBatchNo: '',
    warehouseId: null,
    warehouseName: '',
    deliveryPriority: coPriority.value ?? 0,
    customerLineRef: '',
    customerProductCode: '',
    customerProductName: '',
    sortOrder: soSalesOrderLineList.value.length + 1,
    remark: ''
  });
}

function handleDeleteSoSalesOrderLine() {
  if (checkedSoSalesOrderLine.value.length === 0) {
    proxy.$modal.msgError('请先选择要删除的销售订单行数据');
    return;
  }
  soSalesOrderLineList.value = soSalesOrderLineList.value.filter((row) => !checkedSoSalesOrderLine.value.includes(row));
  checkedSoSalesOrderLine.value = [];
  renumberSortOrder();
  calcHeader();
}

function handleSoSalesOrderLineSelectionChange(selection: any[]) {
  checkedSoSalesOrderLine.value = selection;
}

function viewProductionPlan(row: any) {
  router.push({ path: '/erp/prd/prdplan/form', query: { id: row.linkedPlanId, action: 'view' } });
}

function viewWorkOrder(row: any) {
  if (row.linkedWoId) {
    router.push({ path: '/erp/prd/prdwork/form', query: { id: row.linkedWoId, action: 'view' } });
  }
}

// ==================== 展示辅助 ====================

/** 字典标签：缺失值统一显示 '-' */
function getDictLabel(dictOptions: any[], value: any): string {
  if (value === null || value === undefined || value === '') return '-';
  const dict = (dictOptions || []).find((item) => String(item.value) === String(value));
  return dict ? dict.label : String(value);
}

/** 日期格式化（兼容 'YYYY-MM-DD HH:mm:ss' 与 ISO 串） */
function formatDate(value: any): string {
  if (!value) return '-';
  const matched = String(value).match(/^(\d{4})-(\d{2})-(\d{2})/);
  return matched ? `${matched[1]}-${matched[2]}-${matched[3]}` : String(value);
}

/** 金额格式化（千分位 + 两位小数） */
function formatAmount(value: any): string {
  return num(value).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
}

/** 物料单元格副标题：编码 / 规格 */
function materialSubText(row: any): string {
  const parts = [row.materialCode, row.materialSpec].filter((v) => v != null && v !== '');
  return parts.length ? parts.join(' / ') : '未选择物料';
}

/** 明细合计行 */
function getSummaries(param: { columns: any[]; data: any[] }): string[] {
  const { columns, data } = param;
  const sum = (prop: string) => data.reduce((acc, row) => acc + num(row[prop]), 0);
  return columns.map((col, index) => {
    if (index === 0) return '合计';
    switch (col.property) {
      case 'orderedQty':
        return sum('orderedQty').toFixed(4);
      case 'deliveredQty':
      case 'productionQty':
      case 'purchaseQty':
      case 'reservedQty':
        return sum(col.property).toFixed(2);
      case 'lineAmount':
      case 'discountAmount':
      case 'taxAmount':
        return formatAmount(sum(col.property));
      default:
        return '';
    }
  });
}

// ==================== 金额计算 ====================

/** 数值安全转换（空串/非数字归零） */
function num(v: any): number {
  const n = parseFloat(v);
  return isNaN(n) ? 0 : n;
}

/** 保留 2 位小数（数字类型，便于 el-input-number 绑定） */
function round2(n: number): number {
  return Math.round(n * 100) / 100;
}

/**
 * 计算单行金额/折扣/税额（折扣税前扣，Model A）
 *   行金额   lineAmount      = 数量 × 单价
 *   行折扣额 discountAmount  = 行金额 × 行折扣率%
 *   行税额   taxAmount        = (行金额 − 行折扣额) × 税率%
 * 完成后联动重算表头。
 */
function calcLine(row: any) {
  const lineAmount = round2(num(row.orderedQty) * num(row.unitPrice));
  const discountAmount = round2((lineAmount * num(row.discountPercent)) / 100);
  const taxAmount = round2(((lineAmount - discountAmount) * num(row.taxRate)) / 100);
  row.lineAmount = lineAmount;
  row.discountAmount = discountAmount;
  row.taxAmount = taxAmount;
  calcHeader();
}

/**
 * 计算表头合计（由行汇总）
 *   净额   net      = Σ(行金额 − 行折扣额)
 *   税款   tax      = Σ 行税额
 *   价税合计 gross  = 净额 + 税款
 *   整单折扣额     = 价税合计 × 整单折扣率%
 *   含税总金额     = 价税合计 − 整单折扣额
 */
function calcHeader() {
  let net = 0;
  let tax = 0;
  soSalesOrderLineList.value.forEach((row: any) => {
    net += num(row.lineAmount) - num(row.discountAmount);
    tax += num(row.taxAmount);
  });
  const gross = round2(net + tax);
  const discountAmount = round2((gross * num(form.value.discountRate)) / 100);
  form.value.taxAmount = round2(tax);
  form.value.discountAmount = discountAmount;
  form.value.totalAmount = round2(gross - discountAmount);
}

/** 全量重算：逐行重算后汇总表头（数据加载/删行后调用） */
function recalcAll() {
  soSalesOrderLineList.value.forEach((row: any) => {
    row.lineAmount = round2(num(row.orderedQty) * num(row.unitPrice));
    row.discountAmount = round2((num(row.lineAmount) * num(row.discountPercent)) / 100);
    row.taxAmount = round2(((num(row.lineAmount) - num(row.discountAmount)) * num(row.taxRate)) / 100);
  });
  calcHeader();
}

// ==================== 物料选择 ====================

function openMaterialSelect(row: any) {
  currentMaterialRow.value = row;
  materialSelectDialogRef.value?.open();
}

async function handleMaterialSelected(material: any) {
  const line = currentMaterialRow.value;
  currentMaterialRow.value = null;
  if (!line) return;
  line.materialId = material.materialId;
  line.materialCode = material.materialCode;
  line.materialName = material.materialName;
  line.materialSpec = material.materialSpec;
  // 单价仅在用户未填写时预填，避免覆盖手工议价
  if (!(num(line.unitPrice) > 0)) {
    // 先落物料参考价兜底，再尝试走价目表取价（客户专属价 > 促销价 > 标准基价 > 参考价）
    line.unitPrice = num(material.referencePrice);
    if (form.value.clientId) {
      try {
        const res: any = await resolveOrderPrice({
          clientId: form.value.clientId,
          materialId: material.materialId,
          qty: num(line.orderedQty) > 0 ? num(line.orderedQty) : undefined,
          orderDate: form.value.orderDate || undefined
        });
        if (res?.data != null) {
          line.unitPrice = num(res.data);
        }
      } catch {
        // 取价失败静默回退到物料参考价，不阻断选料
      }
    }
  }
  // 单位仅在未选择时用物料基本单位预填
  if (!line.unit) {
    line.unit = material.baseUomName || '';
  }
  calcLine(line);
}

function clearMaterial(row: any) {
  row.materialId = null;
  row.materialCode = '';
  row.materialName = '';
  row.materialSpec = '';
}

// ==================== 提交 ====================

/** 明细校验：至少一行、每行必须选物料、数量大于 0 */
function validateLines(): boolean {
  if (soSalesOrderLineList.value.length === 0) {
    proxy.$modal.msgError('请至少添加一行销售订单行');
    return false;
  }
  for (let i = 0; i < soSalesOrderLineList.value.length; i++) {
    const row = soSalesOrderLineList.value[i];
    if (!row.materialId) {
      proxy.$modal.msgError(`第 ${i + 1} 行未选择物料`);
      return false;
    }
    if (!(num(row.orderedQty) > 0)) {
      proxy.$modal.msgError(`第 ${i + 1} 行订单数量必须大于 0`);
      return false;
    }
  }
  return true;
}

/** 新增/修改落库（提交与提交审批共用） */
function persist() {
  form.value.soSalesOrderLineList = soSalesOrderLineList.value;
  return form.value.soId != null ? updateOrderheader(form.value) : addOrderheader(form.value);
}

function submitForm() {
  (proxy.$refs['orderheaderRef'] as any).validate((valid: boolean) => {
    if (!valid) return;
    if (!validateLines()) return;
    // 自动生成开关打开时清空编码，由后端 isBlank 兜底生成，避免预览消耗流水号
    autoGenCodeRef.value?.applyAutoGen();
    persist()
      .then(() => {
        proxy.$modal.msgSuccess(form.value.soId != null ? '修改成功' : '新增成功');
        router.push('/erp/so/orderheader');
      })
      .catch(() => {
        // 失败提示由 request 拦截器统一给出，此处仅避免未捕获的 rejection
      });
  });
}

/** 提交审批（Flowable） */
function handleSubmitFlow() {
  (proxy.$refs['orderheaderRef'] as any).validate((valid: boolean) => {
    if (!valid) return;
    if (!validateLines()) return;
    if (!form.value.selectFlowId) {
      proxy.$modal.msgWarning('请先选择审批流程');
      return;
    }
    if (form.value.soId == null) {
      proxy.$modal.msgWarning('请先保存单据后再提交审批');
      return;
    }
    persist()
      .then(() => doSubmitFlow())
      .catch(() => {
        // 保存失败提示由 request 拦截器统一给出，此处不覆盖为「提交审批失败」
      });
  });
}

/** 调用审批流接口提交当前单据 */
function doSubmitFlow() {
  return submitOrderFlow(String(form.value.soId), String(form.value.selectFlowId))
    .then((res: any) => {
      if (res.code === 200) {
        proxy.$modal.msgSuccess('提交审批成功');
        router.push('/erp/so/orderheader');
      } else {
        proxy.$modal.msgError(res.msg || '提交审批失败');
      }
    })
    .catch((error: any) => {
      proxy.$modal.msgError('提交审批失败：' + (error?.message || error));
    });
}

/** 查看流程 */
function onViewFlow() {
  getOrderProcId(String(form.value.soId))
    .then((res: any) => {
      if (res.code === 200 && res.data) {
        router.push('/workflow/process/detail/' + res.data);
      } else {
        proxy.$modal.msgWarning('暂无流程信息');
      }
    })
    .catch(() => {
      proxy.$modal.msgWarning('获取流程信息失败');
    });
}

function cancel() {
  const obj = { path: '/erp/so/orderheader' };
  proxy.$tab.closeOpenPage(obj);
}
</script>

<style scoped>
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.card-title {
  font-size: 18px;
  font-weight: 600;
  color: #303133;
}
.card-actions {
  display: flex;
  gap: 10px;
}
.form-container {
  padding: 0 10px;
}
:deep(.el-form-item) {
  margin-bottom: 18px;
}
:deep(.el-form-item__label) {
  font-weight: 500;
}
.mb8 {
  margin-bottom: 8px;
}
.line-table {
  width: 100%;
}
.line-sub {
  margin-top: 2px;
  font-size: 12px;
  line-height: 16px;
  color: var(--el-text-color-secondary);
}
.cell-clear {
  cursor: pointer;
  color: var(--el-text-color-placeholder);
}
.cell-clear:hover {
  color: var(--el-color-danger);
}
.text-hint {
  color: var(--el-text-color-secondary);
  font-size: 13px;
  word-break: break-all;
}
.text-value {
  color: var(--el-text-color-regular);
  word-break: break-all;
}
.inline-field {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 100%;
}
@media (max-width: 768px) {
  .card-header {
    flex-direction: column;
    align-items: flex-start;
    gap: 10px;
  }
  .card-actions {
    width: 100%;
    justify-content: flex-end;
  }
  .form-container {
    padding: 0 5px;
  }
}
</style>
