<template>
  <div class="app-container">
    <el-card>
      <template #header>
        <div class="card-header">
          <span class="card-title">{{ title }}</span>
          <div class="card-actions">
            <el-button type="primary" @click="submitForm" v-if="!isViewMode">保存</el-button>
            <el-button type="success" @click="handleSubmitFlow" v-if="!isViewMode && form.orderStatus === 'DRAFT'">提交审批</el-button>
            <el-button type="info" @click="onViewFlow" v-if="form.orderStatus === 'APPROVING' || form.orderStatus === 'APPROVED'">查看流程</el-button>
            <el-button @click="cancel">取消</el-button>
          </div>
        </div>
      </template>

      <div class="form-container">
        <el-form :model="form" :rules="rules" ref="orderheaderRef" label-width="120px" :disabled="formDisabled">
          <el-collapse v-model="activeSections">
            <el-collapse-item title="基本信息" name="basic">
              <el-row :gutter="20">
                <el-col :xs="24" :sm="12" :md="8" :lg="6">
                  <el-form-item label="销售订单编号" prop="soNo">
                    <div style="display: flex; align-items: center; gap: 10px">
                      <el-input v-model="form.soNo" placeholder="请输入销售订单编号" :disabled="autoGenFlag || isViewMode" style="flex: 1" />
                      <el-switch v-model="autoGenFlag" active-color="#13ce66" active-text="生成" @change="handleAutoGenChange" :disabled="isViewMode">
                      </el-switch>
                    </div>
                  </el-form-item>
                </el-col>
                <el-col :xs="24" :sm="12" :md="8" :lg="6">
                  <el-form-item label="客户订单" prop="coId">
                    <el-row :gutter="10">
                      <el-col :span="18">
                        <el-input v-model="customerOrderDisplay" placeholder="请选择客户订单" readonly :disabled="isViewMode">
                          <template #append>
                            <el-button icon="Search" @click.stop="openCustomerOrderSelect" :disabled="isViewMode" />
                          </template>
                        </el-input>
                      </el-col>
                      <el-col :span="6">
                        <el-button v-if="form.coId && !isViewMode" type="danger" icon="Delete" @click.stop="clearCustomerOrder" />
                      </el-col>
                    </el-row>
                  </el-form-item>
                </el-col>
                <el-col :xs="24" :sm="12" :md="8" :lg="6">
                  <el-form-item label="来源客户" prop="clientName">
                    <el-input v-model="form.clientName" placeholder="请输入来源客户" />
                  </el-form-item>
                </el-col>
                <el-col :xs="24" :sm="12" :md="8" :lg="6">
                  <el-form-item label="销售代表" prop="salesRepName">
                    <el-input v-model="form.salesRepName" placeholder="请输入销售代表" />
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
                    <el-select v-model="form.orderStatus" placeholder="请选择订单状态" style="width: 100%">
                      <el-option v-for="dict in so_order_status" :key="dict.value" :label="dict.label" :value="dict.value"></el-option>
                    </el-select>
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
                      :disabled="isViewMode || form.orderStatus !== 'DRAFT'"
                      placeholder="请选择审批流程"
                      style="width: 100%"
                    >
                      <el-option v-for="f in flowOptions" :key="f.id" :label="f.flowName || f.businessName || '流程#' + f.id" :value="f.id" />
                    </el-select>
                  </el-form-item>
                </el-col>
                <el-col :xs="24" :sm="12" :md="8" :lg="6">
                  <el-form-item
                    label="审批流程提示"
                    v-if="!isViewMode && !loadingFlow && flowOptions.length === 0 && (!form.selectFlowId || form.selectFlowId === '')"
                  >
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
            <el-collapse-item title="地址信息" name="address">
              <el-row :gutter="20">
                <el-col :xs="24" :sm="12" :md="8" :lg="6">
                  <el-form-item label="发货地址" prop="deliveryAddressId">
                    <el-input v-model="form.deliveryAddressId" placeholder="请输入发货地址" />
                  </el-form-item>
                </el-col>
                <el-col :xs="24" :sm="12" :md="8" :lg="6">
                  <el-form-item label="开票地址ID" prop="invoiceAddressId">
                    <el-input v-model="form.invoiceAddressId" placeholder="请输入开票地址ID" />
                  </el-form-item>
                </el-col>
                <el-col :xs="24" :sm="24" :md="16" :lg="12">
                  <el-form-item label="发货地址详情" prop="deliveryAddress">
                    <el-input v-model="form.deliveryAddress" type="textarea" placeholder="请输入内容" :rows="2" />
                  </el-form-item>
                </el-col>
                <el-col :xs="24" :sm="24" :md="16" :lg="12">
                  <el-form-item label="开票地址详情" prop="invoiceAddress">
                    <el-input v-model="form.invoiceAddress" type="textarea" placeholder="请输入内容" :rows="2" />
                  </el-form-item>
                </el-col>
              </el-row>
            </el-collapse-item>
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
                      <el-option v-for="dict in so_currency_type" :key="dict.value" :label="dict.label" :value="dict.value"></el-option>
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
                    <el-input-number v-model="form.totalAmount" controls-position="right" :precision="2" readonly style="width: 100%" />
                  </el-form-item>
                </el-col>
                <el-col :xs="24" :sm="12" :md="8" :lg="6">
                  <el-form-item label="税款" prop="taxAmount">
                    <el-input-number v-model="form.taxAmount" controls-position="right" :precision="2" readonly style="width: 100%" />
                  </el-form-item>
                </el-col>
                <el-col :xs="24" :sm="12" :md="8" :lg="6">
                  <el-form-item label="整单折扣额" prop="discountAmount">
                    <el-input-number v-model="form.discountAmount" controls-position="right" :precision="2" readonly style="width: 100%" />
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
            <el-collapse-item title="信用信息" name="credit">
              <el-row :gutter="20">
                <el-col :xs="24" :sm="12" :md="8" :lg="6">
                  <el-form-item label="信用状态" prop="creditStatus">
                    <el-radio-group v-model="form.creditStatus">
                      <el-radio v-for="dict in so_credit_status" :key="dict.value" :label="dict.value">{{ dict.label }}</el-radio>
                    </el-radio-group>
                  </el-form-item>
                </el-col>
                <el-col :xs="24" :sm="12" :md="8" :lg="6">
                  <el-form-item label="信用检查时间" prop="creditCheckTime">
                    <el-date-picker
                      clearable
                      v-model="form.creditCheckTime"
                      type="date"
                      value-format="YYYY-MM-DD"
                      placeholder="请选择信用检查时间"
                      style="width: 100%"
                    />
                  </el-form-item>
                </el-col>
                <el-col :xs="24" :sm="12" :md="8" :lg="6">
                  <el-form-item label="信用拦截人" prop="creditBlockedBy">
                    <el-input v-model="form.creditBlockedBy" placeholder="请输入信用拦截人" />
                  </el-form-item>
                </el-col>
              </el-row>
            </el-collapse-item>
            <el-collapse-item title="项目信息" name="project">
              <el-row :gutter="20">
                <el-col :xs="24" :sm="12" :md="8" :lg="6">
                  <el-form-item label="项目ID" prop="projectId">
                    <el-input v-model="form.projectId" placeholder="请输入项目ID" />
                  </el-form-item>
                </el-col>
                <el-col :xs="24" :sm="12" :md="8" :lg="6">
                  <el-form-item label="是否启用项目配套发料" prop="isPaEnabled">
                    <el-radio-group v-model="form.isPaEnabled">
                      <el-radio v-for="dict in sys_true_or_false" :key="dict.value" :label="parseInt(dict.value)">{{ dict.label }}</el-radio>
                    </el-radio-group>
                  </el-form-item>
                </el-col>
              </el-row>
            </el-collapse-item>
            <el-collapse-item title="时间信息" name="time">
              <el-row :gutter="20">
                <el-col :xs="24" :sm="12" :md="8" :lg="6">
                  <el-form-item label="确认时间" prop="confirmedTime">
                    <el-date-picker
                      clearable
                      v-model="form.confirmedTime"
                      type="date"
                      value-format="YYYY-MM-DD"
                      placeholder="请选择确认时间"
                      style="width: 100%"
                    />
                  </el-form-item>
                </el-col>
                <el-col :xs="24" :sm="12" :md="8" :lg="6">
                  <el-form-item label="完全履行时间" prop="fulfilledTime">
                    <el-date-picker
                      clearable
                      v-model="form.fulfilledTime"
                      type="date"
                      value-format="YYYY-MM-DD"
                      placeholder="请选择完全履行时间"
                      style="width: 100%"
                    />
                  </el-form-item>
                </el-col>
                <el-col :xs="24" :sm="12" :md="8" :lg="6">
                  <el-form-item label="结束时间" prop="closedTime">
                    <el-date-picker
                      clearable
                      v-model="form.closedTime"
                      type="date"
                      value-format="YYYY-MM-DD"
                      placeholder="请选择结束时间"
                      style="width: 100%"
                    />
                  </el-form-item>
                </el-col>
                <el-col :xs="24" :sm="12" :md="8" :lg="6">
                  <el-form-item label="取消时间" prop="cancelledTime">
                    <el-date-picker
                      clearable
                      v-model="form.cancelledTime"
                      type="date"
                      value-format="YYYY-MM-DD"
                      placeholder="请选择取消时间"
                      style="width: 100%"
                    />
                  </el-form-item>
                </el-col>
              </el-row>
            </el-collapse-item>
            <el-collapse-item title="其他信息" name="other">
              <el-row :gutter="20">
                <el-col :xs="24" :sm="12" :md="8" :lg="6">
                  <el-form-item label="取消原因" prop="cancelledReason">
                    <el-input v-model="form.cancelledReason" placeholder="请输入取消原因" />
                  </el-form-item>
                </el-col>
                <el-col :xs="24" :sm="12" :md="8" :lg="6">
                  <el-form-item label="销售组织ID" prop="salesOrgId">
                    <el-input v-model="form.salesOrgId" placeholder="请输入销售组织ID" />
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
            <el-collapse-item title="销售订单行信息" name="lines">
              <el-row :gutter="10" class="mb8">
                <el-col :span="1.5">
                  <el-button type="primary" icon="Plus" @click="handleAddSoSalesOrderLine" v-if="!isViewMode">添加</el-button>
                </el-col>
                <el-col :span="1.5">
                  <el-button type="danger" icon="Delete" @click="handleDeleteSoSalesOrderLine" v-if="!isViewMode">删除</el-button>
                </el-col>
              </el-row>
              <el-table
                :data="soSalesOrderLineList"
                :row-class-name="rowSoSalesOrderLineIndex"
                @selection-change="handleSoSalesOrderLineSelectionChange"
                ref="soSalesOrderLine"
              >
                <el-table-column type="selection" width="50" align="center" v-if="!isViewMode" />
                <el-table-column label="序号" prop="sortOrder" width="60">
                  <template #default="scope">
                    <el-input v-model="scope.row.sortOrder" placeholder="请输入" v-if="!isViewMode" />
                    <span v-else>{{ scope.row.sortOrder }}</span>
                  </template>
                </el-table-column>
                <el-table-column label="行号" prop="lineNo" width="150">
                  <template #default="scope">
                    <el-input v-model="scope.row.lineNo" placeholder="请输入行号" v-if="!isViewMode" />
                    <span v-else>{{ scope.row.lineNo }}</span>
                  </template>
                </el-table-column>
                <el-table-column label="物料" align="center" prop="materialName" min-width="200">
                  <template #default="scope">
                    <el-input v-model="scope.row.materialName" placeholder="请选择物料" readonly v-show="isViewMode" />
                    <el-input v-model="scope.row.materialId" style="display: none" />
                    <el-row :gutter="10" v-show="!isViewMode">
                      <el-col :span="18">
                        <el-input v-model="scope.row.materialName" placeholder="请选择物料" readonly>
                          <template #append>
                            <el-button icon="Search" @click.stop="openMaterialSelect(scope.$index)" />
                          </template>
                        </el-input>
                      </el-col>
                      <el-col :span="6">
                        <el-button v-if="scope.row.materialId" type="danger" icon="Delete" @click.stop="clearMaterial(scope.$index)" />
                      </el-col>
                    </el-row>
                  </template>
                </el-table-column>
                <el-table-column label="订单数量" prop="orderedQty" width="150">
                  <template #default="scope">
                    <el-input-number
                      v-model="scope.row.orderedQty"
                      controls-position="right"
                      :min="0"
                      :precision="4"
                      @change="calcLine(scope.row)"
                      style="width: 100%"
                      v-if="!isViewMode"
                    />
                    <span v-else>{{ scope.row.orderedQty }}</span>
                  </template>
                </el-table-column>
                <el-table-column label="已发货数量" prop="deliveredQty" width="150" v-if="isViewMode">
                  <template #default="scope">
                    <span>{{ scope.row.deliveredQty }}</span>
                  </template>
                </el-table-column>
                <el-table-column label="单位" prop="unit" width="150">
                  <template #default="scope">
                    <el-select v-model="scope.row.unit" placeholder="请选择单位" v-if="!isViewMode" filterable clearable style="width: 100%">
                      <el-option v-for="u in uomOptions" :key="u.uomId" :label="u.uomName" :value="u.uomName" />
                    </el-select>
                    <span v-else>{{ scope.row.unit }}</span>
                  </template>
                </el-table-column>
                <el-table-column label="单价" prop="unitPrice" width="150">
                  <template #default="scope">
                    <el-input-number
                      v-model="scope.row.unitPrice"
                      controls-position="right"
                      :min="0"
                      :precision="4"
                      @change="calcLine(scope.row)"
                      style="width: 100%"
                      v-if="!isViewMode"
                    />
                    <span v-else>{{ scope.row.unitPrice }}</span>
                  </template>
                </el-table-column>
                <el-table-column label="行金额" prop="lineAmount" width="140">
                  <template #default="scope">
                    <el-input-number
                      v-model="scope.row.lineAmount"
                      controls-position="right"
                      :precision="2"
                      readonly
                      style="width: 100%"
                      v-if="!isViewMode"
                    />
                    <span v-else>{{ scope.row.lineAmount }}</span>
                  </template>
                </el-table-column>
                <el-table-column label="行折扣率%" prop="discountPercent" width="140">
                  <template #default="scope">
                    <el-input-number
                      v-model="scope.row.discountPercent"
                      controls-position="right"
                      :min="0"
                      :max="100"
                      :precision="2"
                      @change="calcLine(scope.row)"
                      style="width: 100%"
                      v-if="!isViewMode"
                    />
                    <span v-else>{{ scope.row.discountPercent }}</span>
                  </template>
                </el-table-column>
                <el-table-column label="行折扣额" prop="discountAmount" width="140">
                  <template #default="scope">
                    <el-input-number
                      v-model="scope.row.discountAmount"
                      controls-position="right"
                      :precision="2"
                      readonly
                      style="width: 100%"
                      v-if="!isViewMode"
                    />
                    <span v-else>{{ scope.row.discountAmount }}</span>
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
                      v-if="!isViewMode"
                    />
                    <span v-else>{{ scope.row.taxRate }}</span>
                  </template>
                </el-table-column>
                <el-table-column label="行税额" prop="taxAmount" width="140">
                  <template #default="scope">
                    <el-input-number
                      v-model="scope.row.taxAmount"
                      controls-position="right"
                      :precision="2"
                      readonly
                      style="width: 100%"
                      v-if="!isViewMode"
                    />
                    <span v-else>{{ scope.row.taxAmount }}</span>
                  </template>
                </el-table-column>
                <el-table-column label="行要求交期" prop="requestedDate" width="240">
                  <template #default="scope">
                    <el-date-picker
                      clearable
                      v-model="scope.row.requestedDate"
                      type="date"
                      value-format="YYYY-MM-DD"
                      placeholder="请选择行要求交期"
                      v-if="!isViewMode"
                    >
                    </el-date-picker>
                    <span v-else>{{ scope.row.requestedDate }}</span>
                  </template>
                </el-table-column>
                <el-table-column label="行承诺交期" prop="promiseDate" width="240">
                  <template #default="scope">
                    <el-date-picker
                      clearable
                      v-model="scope.row.promiseDate"
                      type="date"
                      value-format="YYYY-MM-DD"
                      placeholder="请选择行承诺交期"
                      v-if="!isViewMode"
                    >
                    </el-date-picker>
                    <span v-else>{{ scope.row.promiseDate }}</span>
                  </template>
                </el-table-column>
                <el-table-column label="已履行数量" prop="fulfilledQty" width="150" v-if="isViewMode">
                  <template #default="scope">
                    <span>{{ scope.row.fulfilledQty }}</span>
                  </template>
                </el-table-column>
                <el-table-column label="已投产数量" prop="productionQty" width="150" v-if="isViewMode">
                  <template #default="scope">
                    <span>{{ scope.row.productionQty }}</span>
                  </template>
                </el-table-column>
                <el-table-column label="已采购数量" prop="purchaseQty" width="150" v-if="isViewMode">
                  <template #default="scope">
                    <span>{{ scope.row.purchaseQty }}</span>
                  </template>
                </el-table-column>
                <el-table-column label="关联生产计划" prop="linkedPlanNo" width="180" v-if="isViewMode">
                  <template #default="scope">
                    <el-link v-if="scope.row.linkedPlanNo" type="primary" @click="viewProductionPlan(scope.row)">{{
                      scope.row.linkedPlanNo
                    }}</el-link>
                    <span v-else>-</span>
                  </template>
                </el-table-column>
                <el-table-column label="已预留库存数量" prop="reservedQty" width="150" v-if="isViewMode">
                  <template #default="scope">
                    <span>{{ scope.row.reservedQty }}</span>
                  </template>
                </el-table-column>
                <el-table-column label="订单明细状态" prop="lineStatus" width="150">
                  <template #default="scope">
                    <el-select v-model="scope.row.lineStatus" placeholder="请选择订单明细状态" v-if="!isViewMode">
                      <el-option v-for="dict in so_order_line_status" :key="dict.value" :label="dict.label" :value="dict.value"></el-option>
                    </el-select>
                    <span v-else>{{ getDictLabel(so_order_line_status, scope.row.lineStatus) }}</span>
                  </template>
                </el-table-column>
                <el-table-column label="关联生产工单ID" prop="linkedMoId" width="150" v-if="isViewMode">
                  <template #default="scope">
                    <span>{{ scope.row.linkedMoId }}</span>
                  </template>
                </el-table-column>
                <el-table-column label="关联采购订单ID" prop="linkedPoId" width="150" v-if="isViewMode">
                  <template #default="scope">
                    <span>{{ scope.row.linkedPoId }}</span>
                  </template>
                </el-table-column>
                <el-table-column label="预留批次号" prop="reservedBatchNo" width="150" v-if="isViewMode">
                  <template #default="scope">
                    <span>{{ scope.row.reservedBatchNo }}</span>
                  </template>
                </el-table-column>
                <el-table-column label="PA配套组ID" prop="paGroupId" width="150" v-if="isViewMode">
                  <template #default="scope">
                    <span>{{ scope.row.paGroupId }}</span>
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
                    <el-select v-model="scope.row.deliveryPriority" placeholder="请选择交付优先级" v-if="!isViewMode">
                      <el-option v-for="dict in so_order_priority" :key="dict.value" :label="dict.label" :value="dict.value"></el-option>
                    </el-select>
                    <span v-else>{{ getDictLabel(so_order_priority, scope.row.deliveryPriority) }}</span>
                  </template>
                </el-table-column>
                <el-table-column label="客户行参考号" prop="customerLineRef" width="150">
                  <template #default="scope">
                    <el-input v-model="scope.row.customerLineRef" placeholder="请输入客户行参考号" v-if="!isViewMode" />
                    <span v-else>{{ scope.row.customerLineRef }}</span>
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

    <MaterialSelectDialog ref="materialSelectDialogRef" :title="materialDialogTitle" @select="handleMaterialSelected" />
  </div>
</template>

<script setup lang="ts" name="OrderForm">
import { getOrderheader, addOrderheader, updateOrderheader, submitOrderFlow, getOrderProcId } from '@/api/erp/so/orderheader';
import { getMaterial } from '@/api/erp/bd/material';
import { listMeasureAll } from '@/api/erp/bd/measure';
import MaterialSelectDialog from '@/components/MaterialSelectDialog';
import { getProcesss } from '@/api/workflow/process';

import { previewCode } from '@/api/system/autocode/rule';
import { useRouter, useRoute } from 'vue-router';
import CustomerOrderSelectDialog from '../components/CustomerOrderSelectDialog.vue';
import type { CustomerOrderVO } from '@/api/erp/so/customer-order/types';
import type { OrderheaderForm } from '@/api/erp/so/orderheader/types';

const { proxy } = getCurrentInstance() as any;
const { so_credit_status, so_order_status, sys_true_or_false, so_currency_type, so_order_line_status, so_order_priority, bd_material_type } =
  toRefs<any>(
    proxy.useDict(
      'so_credit_status',
      'so_order_status',
      'sys_true_or_false',
      'so_currency_type',
      'so_order_line_status',
      'so_order_priority',
      'bd_material_type'
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
const autoGenFlag = ref(false);
const soSalesOrderLineList = ref([]);
const checkedSoSalesOrderLine = ref([]);
const isViewMode = ref(false);
const title = ref('');

// 流程相关状态
const loadingFlow = ref(false);
const flowOptions = ref([]);

// 表单禁用状态（考虑 workflow 嵌入和查看模式）
const formDisabled = computed(() => isViewMode.value || props.disabled);

// 分区折叠：默认展开核心分区，次要分区收起
const activeSections = ref<string[]>(['basic', 'address', 'finance', 'lines']);

const materialSelectDialogRef = ref<any>(null);
const materialDialogTitle = ref('选择物料');
const currentMaterialRowIndex = ref(-1);

// 计量单位下拉选项
const uomOptions = ref<any[]>([]);
function loadUomList() {
  listMeasureAll()
    .then((response: any) => {
      uomOptions.value = response.data || response.rows || [];
    })
    .catch(() => {
      uomOptions.value = [];
    });
}

// 客户订单选择相关
const customerOrderSelectRef = ref<InstanceType<typeof CustomerOrderSelectDialog> | null>(null);
const customerOrderDisplay = ref('');

const rules = {
  soNo: [{ required: true, message: '销售订单编号不能为空', trigger: 'blur' }],
  clientName: [{ required: true, message: '来源客户不能为空', trigger: 'blur' }],
  orderDate: [{ required: true, message: '订单生成日期不能为空', trigger: 'blur' }]
};

/** 将流程下发的业务数据写入本地状态 */
function applyWorkflowFormPayload(payload: Record<string, any>) {
  if (!payload || typeof payload !== 'object') return;
  const merged = JSON.parse(JSON.stringify(payload)) as Record<string, any>;
  form.value = merged;
  soSalesOrderLineList.value = Array.isArray(merged.soSalesOrderLineList) ? merged.soSalesOrderLineList : [];
}

/**
 * 加载审批流列表
 */
function loadFlows() {
  loadingFlow.value = true;
  getProcesss({ serviceName: 'soSalesOrderHeaderService' })
    .then((response: any) => {
      flowOptions.value = response?.data || [];
      tryAutoSelect();
      loadingFlow.value = false;
    })
    .catch(() => {
      loadingFlow.value = false;
    });
}

/**
 * 尝试自动选择流程（仅一个选项时自动选中）
 */
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
  const id = route.query.id;
  const action = route.query.action;
  isViewMode.value = action === 'view';
  loadUomList();

  if (id) {
    initPage(id);
  } else if (action === 'add') {
    loadFlows();
  }
});

function initPage(id) {
  const action = route.query.action;
  if (action === 'view') {
    isViewMode.value = true;
    title.value = '查看销售订单';
    if (id) {
      getInfo(id);
    }
  } else if (action === 'edit') {
    isViewMode.value = false;
    title.value = '修改销售订单';
    if (id) {
      getInfo(id);
    }
    loadFlows();
  } else if (action === 'add') {
    isViewMode.value = false;
    title.value = '新增销售订单';
    reset();
    loadFlows();
  }
}

function getInfo(id) {
  getOrderheader(id)
    .then((response) => {
      form.value = response.data;
      soSalesOrderLineList.value = response.data.soSalesOrderLineList || [];
      // 如果有客户订单ID，设置显示文本
      if (form.value.coId) {
        customerOrderDisplay.value = form.value.customerRefNo || `客户订单#${form.value.coId}`;
      } else {
        customerOrderDisplay.value = '';
      }
      tryAutoSelect();
      // 编辑态重算金额（含税总金额/税款/折扣等由行汇总自动带出）；查看态保留数据库原值
      if (!isViewMode.value) {
        recalcAll();
      }
    })
    .catch((error) => {
      proxy.$modal.msgError('获取数据失败: ' + error.message);
    });
}

function reset() {
  form.value = {
    soId: null,
    soNo: null,
    coId: null,
    clientName: null,
    salesRepName: null,
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
    selectFlowId: null
  };
  soSalesOrderLineList.value = [];
  proxy.resetForm('orderheaderRef');
}

function submitForm() {
  proxy.$refs['orderheaderRef'].validate((valid) => {
    if (valid) {
      // 自动生成开关打开时清空编码，由后端 isBlank 兜底生成，避免预览消耗流水号
      if (autoGenFlag.value) {
        form.value.soNo = undefined;
      }
      form.value.soSalesOrderLineList = soSalesOrderLineList.value;
      if (form.value.soId != null) {
        updateOrderheader(form.value).then((response) => {
          proxy.$modal.msgSuccess('修改成功');
          router.push('/erp/so/orderheader');
        });
      } else {
        addOrderheader(form.value).then((response) => {
          proxy.$modal.msgSuccess('新增成功');
          router.push('/erp/so/orderheader');
        });
      }
    }
  });
}

/**
 * 提交审批（Flowable）
 */
function handleSubmitFlow() {
  proxy.$refs['orderheaderRef'].validate((valid: boolean) => {
    if (valid) {
      if (!form.value.selectFlowId) {
        proxy.$modal.msgWarning('请先选择审批流程');
        return;
      }
      form.value.soSalesOrderLineList = soSalesOrderLineList.value;
      if (form.value.soId != null) {
        updateOrderheader(form.value).then(() => {
          submitOrderFlow(String(form.value.soId), String(form.value.selectFlowId))
            .then((res: any) => {
              if (res.code === 200) {
                proxy.$modal.msgSuccess('提交审批成功');
                router.push('/erp/so/orderheader');
              } else {
                proxy.$modal.msgError(res.msg || '提交审批失败');
              }
            })
            .catch((error: any) => {
              proxy.$modal.msgError('提交审批失败：' + error);
            });
        });
      } else {
        proxy.$modal.msgWarning('请先保存单据后再提交审批');
      }
    }
  });
}

/**
 * 查看流程
 */
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

//自动生成编号
function handleAutoGenChange(autoGenFlag) {
  if (autoGenFlag) {
    previewCode('SO_ORDER_CODE').then((response) => {
      form.value.soNo = response;
    });
  } else {
    form.value.soNo = null;
  }
}

function cancel() {
  const obj = { path: '/erp/so/orderheader' };
  proxy.$tab.closeOpenPage(obj);
}

/** 打开客户订单选择对话框 */
function openCustomerOrderSelect() {
  customerOrderSelectRef.value?.open();
}

/** 处理客户订单选择回调 */
function handleCustomerOrderSelect(order: CustomerOrderVO) {
  // 设置关联ID
  form.value.coId = order.coId;
  // 设置显示文本
  customerOrderDisplay.value = order.coNo ? `${order.coNo} - ${order.clientName}` : order.clientName;
  // 映射字段到销售订单表单
  form.value.clientId = order.clientId;
  form.value.clientName = order.clientName;
  form.value.orderDate = order.orderDate;
  form.value.requestedDate = order.requestedDate;
  form.value.currency = order.currency;
  form.value.salesRepId = order.salesRepId;
  form.value.salesRepName = order.salesRepName;
  form.value.customerRefNo = order.customerPoNo;
  form.value.remark = order.remark;
  // 含税总金额现由订单行汇总自动计算，不再沿用客户订单原值；重算表头
  calcHeader();
}

/** 清除已选择的客户订单 */
function clearCustomerOrder() {
  form.value.coId = null;
  customerOrderDisplay.value = '';
}

function rowSoSalesOrderLineIndex({ row, rowIndex }) {
  row.index = rowIndex + 1;
}

function handleAddSoSalesOrderLine() {
  const obj = {
    lineNo: '',
    materialId: '',
    materialCode: '',
    materialName: '',
    materialSpec: '',
    orderedQty: 0,
    deliveredQty: 0,
    unit: '',
    unitPrice: 0,
    lineAmount: 0,
    taxRate: 0,
    taxAmount: 0,
    discountPercent: 0,
    discountAmount: 0,
    requestedDate: form.value.requestedDate || '',
    promiseDate: form.value.promiseDate || '',
    fulfilledQty: 0,
    productionQty: 0,
    purchaseQty: 0,
    reservedQty: 0,
    lineStatus: 'PENDING',
    linkedMoId: '',
    linkedPoId: '',
    reservedBatchNo: '',
    reservedSerials: '',
    paGroupId: '',
    warehouseId: '',
    deliveryPriority: '',
    customerLineRef: '',
    sortOrder: soSalesOrderLineList.value.length + 1,
    remark: ''
  };
  soSalesOrderLineList.value.push(obj);
}

function handleDeleteSoSalesOrderLine() {
  if (checkedSoSalesOrderLine.value.length == 0) {
    proxy.$modal.msgError('请先选择要删除的销售订单行数据');
  } else {
    const soSalesOrderLines = soSalesOrderLineList.value;
    const checkedSoSalesOrderLines = checkedSoSalesOrderLine.value;
    soSalesOrderLineList.value = soSalesOrderLines.filter(function (item) {
      return checkedSoSalesOrderLines.indexOf(item.index) == -1;
    });
    // 行变化后重算表头合计
    calcHeader();
  }
}

function handleSoSalesOrderLineSelectionChange(selection) {
  checkedSoSalesOrderLine.value = selection.map((item) => item.index);
}

function viewProductionPlan(row) {
  router.push({ path: '/erp/prd/prdplan/form', query: { id: row.linkedPlanId, action: 'view' } });
}

function getDictLabel(dictOptions, value) {
  const dict = dictOptions.find((item) => item.value === value);
  return dict ? dict.label : value;
}

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
  const totalAmount = round2(gross - discountAmount);
  form.value.taxAmount = round2(tax);
  form.value.discountAmount = discountAmount;
  form.value.totalAmount = totalAmount;
}

/** 全量重算：逐行 calcLine 后汇总表头（数据加载/删行后调用） */
function recalcAll() {
  soSalesOrderLineList.value.forEach((row: any) => {
    const lineAmount = round2(num(row.orderedQty) * num(row.unitPrice));
    const discountAmount = round2((lineAmount * num(row.discountPercent)) / 100);
    const taxAmount = round2(((lineAmount - discountAmount) * num(row.taxRate)) / 100);
    row.lineAmount = lineAmount;
    row.discountAmount = discountAmount;
    row.taxAmount = taxAmount;
  });
  calcHeader();
}

function openMaterialSelect(rowIndex) {
  currentMaterialRowIndex.value = rowIndex;
  materialSelectDialogRef.value?.open();
}

function handleMaterialSelected(row) {
  if (currentMaterialRowIndex.value >= 0) {
    const line = soSalesOrderLineList.value[currentMaterialRowIndex.value];
    line.materialId = row.materialId;
    line.materialCode = row.materialCode;
    line.materialName = row.materialName;
    line.materialSpec = row.materialSpec;
    line.unitPrice = row.referencePrice;
    // 默认以物料基本单位预填充，用户仍可通过下拉框修改
    if (row.baseUomName) {
      line.unit = row.baseUomName;
    }
    // 单价/单位变化后重算该行金额及表头合计
    calcLine(line);
  }
  currentMaterialRowIndex.value = -1;
}

function clearMaterial(rowIndex) {
  soSalesOrderLineList.value[rowIndex].materialId = null;
  soSalesOrderLineList.value[rowIndex].materialCode = '';
  soSalesOrderLineList.value[rowIndex].materialName = '';
  soSalesOrderLineList.value[rowIndex].materialSpec = '';
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
.category-tree-container {
  border: 1px solid #dcdfe6;
  border-radius: 4px;
  padding: 10px;
  height: 500px;
  overflow-y: auto;
}
.category-tree {
  height: 100%;
}
.category-search-input {
  margin-bottom: 10px;
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
