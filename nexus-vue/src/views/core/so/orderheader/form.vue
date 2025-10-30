form.vue
<template>
    <div class="app-container">
        <el-card>
            <template #header>
                <div class="card-header">
                    <span class="card-title">{{ title }}</span>
                    <div class="card-actions">
                        <el-button type="primary" @click="submitForm" v-if="!isViewMode">保存</el-button>
                        <el-button @click="cancel">取消</el-button>
                    </div>
                </div>
            </template>

            <div class="form-container">
                <!-- 基本信息 -->
                <el-form :model="form" :rules="rules" ref="orderheaderRef" label-width="120px">
                    <el-divider content-position="left">基本信息</el-divider>
                    <el-row :gutter="20">
                        <el-col :xs="24" :sm="12" :md="8" :lg="6">
                            <el-form-item label="销售订单编号" prop="soNo">
                                <el-input v-model="form.soNo" placeholder="请输入销售订单编号" />
                            </el-form-item>
                        </el-col>
                        <el-col :xs="24" :sm="12" :md="8" :lg="6">
                            <el-form-item label="来源客户订单" prop="coId">
                                <el-input v-model="form.coId" placeholder="请输入来源客户订单" />
                            </el-form-item>
                        </el-col>
                        <el-col :xs="24" :sm="12" :md="8" :lg="6">
                            <el-form-item label="来源客户ID" prop="clientId">
                                <el-input v-model="form.clientId" placeholder="请输入来源客户ID" />
                            </el-form-item>
                        </el-col>
                        <el-col :xs="24" :sm="12" :md="8" :lg="6">
                            <el-form-item label="销售代表" prop="salesRepId">
                                <el-input v-model="form.salesRepId" placeholder="请输入销售代表" />
                            </el-form-item>
                        </el-col>
                        <el-col :xs="24" :sm="12" :md="8" :lg="6">
                            <el-form-item label="订单生成日期" prop="orderDate">
                                <el-date-picker clearable v-model="form.orderDate" type="date" value-format="YYYY-MM-DD"
                                    placeholder="请选择订单生成日期" style="width: 100%" />
                            </el-form-item>
                        </el-col>
                        <el-col :xs="24" :sm="12" :md="8" :lg="6">
                            <el-form-item label="承诺交货日" prop="promiseDate">
                                <el-date-picker clearable v-model="form.promiseDate" type="date" value-format="YYYY-MM-DD"
                                    placeholder="请选择承诺交货日" style="width: 100%" />
                            </el-form-item>
                        </el-col>
                        <el-col :xs="24" :sm="12" :md="8" :lg="6">
                            <el-form-item label="客户要求交货日" prop="requestedDate">
                                <el-date-picker clearable v-model="form.requestedDate" type="date" value-format="YYYY-MM-DD"
                                    placeholder="请选择客户要求交货日" style="width: 100%" />
                            </el-form-item>
                        </el-col>
                        <el-col :xs="24" :sm="12" :md="8" :lg="6">
                            <el-form-item label="订单状态" prop="orderStatus">
                                <el-select v-model="form.orderStatus" placeholder="请选择订单状态" style="width: 100%">
                                    <el-option v-for="dict in so_order_status" :key="dict.value" :label="dict.label"
                                        :value="dict.value"></el-option>
                                </el-select>
                            </el-form-item>
                        </el-col>
                    </el-row>

                    <!-- 地址信息 -->
                    <el-divider content-position="left">地址信息</el-divider>
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

                    <!-- 财务信息 -->
                    <el-divider content-position="left">财务信息</el-divider>
                    <el-row :gutter="20">
                        <el-col :xs="24" :sm="12" :md="8" :lg="6">
                            <el-form-item label="付款条款" prop="paymentTerms">
                                <el-input v-model="form.paymentTerms" placeholder="请输入付款条款" />
                            </el-form-item>
                        </el-col>
                        <el-col :xs="24" :sm="12" :md="8" :lg="6">
                            <el-form-item label="货币" prop="currency">
                                <el-select v-model="form.currency" placeholder="请选择货币" style="width: 100%">
                                    <el-option v-for="dict in so_currency_type" :key="dict.value" :label="dict.label"
                                        :value="dict.value"></el-option>
                                </el-select>
                            </el-form-item>
                        </el-col>
                        <el-col :xs="24" :sm="12" :md="8" :lg="6">
                            <el-form-item label="汇率" prop="exchangeRate">
                                <el-input v-model="form.exchangeRate" placeholder="请输入汇率" type="number" />
                            </el-form-item>
                        </el-col>
                        <el-col :xs="24" :sm="12" :md="8" :lg="6">
                            <el-form-item label="含税总金额" prop="totalAmount">
                                <el-input v-model="form.totalAmount" placeholder="请输入含税总金额" type="number" />
                            </el-form-item>
                        </el-col>
                        <el-col :xs="24" :sm="12" :md="8" :lg="6">
                            <el-form-item label="税款" prop="taxAmount">
                                <el-input v-model="form.taxAmount" placeholder="请输入税款" type="number" />
                            </el-form-item>
                        </el-col>
                        <el-col :xs="24" :sm="12" :md="8" :lg="6">
                            <el-form-item label="整单折扣" prop="discountAmount">
                                <el-input v-model="form.discountAmount" placeholder="请输入整单折扣" type="number" />
                            </el-form-item>
                        </el-col>
                        <el-col :xs="24" :sm="12" :md="8" :lg="6">
                            <el-form-item label="整单折扣率" prop="discountRate">
                                <el-input v-model="form.discountRate" placeholder="请输入整单折扣率" type="number">
                                    <template #append>%</template>
                                </el-input>
                            </el-form-item>
                        </el-col>
                    </el-row>

                    <!-- 信用信息 -->
                    <el-divider content-position="left">信用信息</el-divider>
                    <el-row :gutter="20">
                        <el-col :xs="24" :sm="12" :md="8" :lg="6">
                            <el-form-item label="信用状态" prop="creditStatus">
                                <el-radio-group v-model="form.creditStatus">
                                    <el-radio v-for="dict in so_credit_status" :key="dict.value" :label="dict.value">{{
                                        dict.label
                                        }}</el-radio>
                                </el-radio-group>
                            </el-form-item>
                        </el-col>
                        <el-col :xs="24" :sm="12" :md="8" :lg="6">
                            <el-form-item label="信用检查时间" prop="creditCheckTime">
                                <el-date-picker clearable v-model="form.creditCheckTime" type="date"
                                    value-format="YYYY-MM-DD" placeholder="请选择信用检查时间" style="width: 100%" />
                            </el-form-item>
                        </el-col>
                        <el-col :xs="24" :sm="12" :md="8" :lg="6">
                            <el-form-item label="信用拦截人" prop="creditBlockedBy">
                                <el-input v-model="form.creditBlockedBy" placeholder="请输入信用拦截人" />
                            </el-form-item>
                        </el-col>
                    </el-row>

                    <!-- 项目信息 -->
                    <el-divider content-position="left">项目信息</el-divider>
                    <el-row :gutter="20">
                        <el-col :xs="24" :sm="12" :md="8" :lg="6">
                            <el-form-item label="项目ID" prop="projectId">
                                <el-input v-model="form.projectId" placeholder="请输入项目ID" />
                            </el-form-item>
                        </el-col>
                        <el-col :xs="24" :sm="12" :md="8" :lg="6">
                            <el-form-item label="是否启用项目配套发料" prop="isPaEnabled">
                                <el-radio-group v-model="form.isPaEnabled">
                                    <el-radio v-for="dict in sys_true_or_false" :key="dict.value"
                                        :label="parseInt(dict.value)">{{
                                            dict.label }}</el-radio>
                                </el-radio-group>
                            </el-form-item>
                        </el-col>
                    </el-row>

                    <!-- 时间信息 -->
                    <el-divider content-position="left">时间信息</el-divider>
                    <el-row :gutter="20">
                        <el-col :xs="24" :sm="12" :md="8" :lg="6">
                            <el-form-item label="确认时间" prop="confirmedTime">
                                <el-date-picker clearable v-model="form.confirmedTime" type="date" value-format="YYYY-MM-DD"
                                    placeholder="请选择确认时间" style="width: 100%" />
                            </el-form-item>
                        </el-col>
                        <el-col :xs="24" :sm="12" :md="8" :lg="6">
                            <el-form-item label="完全履行时间" prop="fulfilledTime">
                                <el-date-picker clearable v-model="form.fulfilledTime" type="date" value-format="YYYY-MM-DD"
                                    placeholder="请选择完全履行时间" style="width: 100%" />
                            </el-form-item>
                        </el-col>
                        <el-col :xs="24" :sm="12" :md="8" :lg="6">
                            <el-form-item label="结束时间" prop="closedTime">
                                <el-date-picker clearable v-model="form.closedTime" type="date" value-format="YYYY-MM-DD"
                                    placeholder="请选择结束时间" style="width: 100%" />
                            </el-form-item>
                        </el-col>
                        <el-col :xs="24" :sm="12" :md="8" :lg="6">
                            <el-form-item label="取消时间" prop="cancelledTime">
                                <el-date-picker clearable v-model="form.cancelledTime" type="date" value-format="YYYY-MM-DD"
                                    placeholder="请选择取消时间" style="width: 100%" />
                            </el-form-item>
                        </el-col>
                    </el-row>

                    <!-- 其他信息 -->
                    <el-divider content-position="left">其他信息</el-divider>
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

                    <!-- 销售订单行信息 -->
                    <el-divider content-position="center">销售订单行信息</el-divider>
                    <el-row :gutter="10" class="mb8">
                        <el-col :span="1.5">
                            <el-button type="primary" icon="Plus" @click="handleAddSoSalesOrderLine"
                                v-if="!isViewMode">添加</el-button>
                        </el-col>
                        <el-col :span="1.5">
                            <el-button type="danger" icon="Delete" @click="handleDeleteSoSalesOrderLine"
                                v-if="!isViewMode">删除</el-button>
                        </el-col>
                    </el-row>
                    <el-table :data="soSalesOrderLineList" :row-class-name="rowSoSalesOrderLineIndex"
                        @selection-change="handleSoSalesOrderLineSelectionChange" ref="soSalesOrderLine">
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
                                <el-input v-model="scope.row.lineAmount" placeholder="请输入行金额 = qty * price"
                                    v-if="!isViewMode" />
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
                                <el-date-picker clearable v-model="scope.row.requestedDate" type="date"
                                    value-format="YYYY-MM-DD" placeholder="请选择行要求交期" v-if="!isViewMode">
                                </el-date-picker>
                                <span v-else>{{ scope.row.requestedDate }}</span>
                            </template>
                        </el-table-column>
                        <el-table-column label="行承诺交期" prop="promiseDate" width="240">
                            <template #default="scope">
                                <el-date-picker clearable v-model="scope.row.promiseDate" type="date"
                                    value-format="YYYY-MM-DD" placeholder="请选择行承诺交期" v-if="!isViewMode">
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
                        <el-table-column label="订单明细状态" prop="lineStatus" width="150">
                            <template #default="scope">
                                <el-select v-model="scope.row.lineStatus" placeholder="请选择订单明细状态" v-if="!isViewMode">
                                    <el-option v-for="dict in so_order_line_status" :key="dict.value" :label="dict.label"
                                        :value="dict.value"></el-option>
                                </el-select>
                                <span v-else>{{ getDictLabel(so_order_line_status, scope.row.lineStatus) }}</span>
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
                                <el-select v-model="scope.row.deliveryPriority" placeholder="请选择交付优先级" v-if="!isViewMode">
                                    <el-option v-for="dict in so_order_priority" :key="dict.value" :label="dict.label"
                                        :value="dict.value"></el-option>
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
                </el-form>
            </div>
        </el-card>
    </div>
</template>

<script setup name="OrderForm">
import { getOrderheader, addOrderheader, updateOrderheader } from "@/api/core/so/orderheader"
import { useRouter, useRoute } from 'vue-router'

const { proxy } = getCurrentInstance()
const { so_credit_status, so_order_status, sys_true_or_false, so_currency_type } = proxy.useDict('so_credit_status', 'so_order_status', 'sys_true_or_false', 'so_currency_type')

const route = useRoute()
const router = useRouter()

const form = ref({})
const soSalesOrderLineList = ref([])
const checkedSoSalesOrderLine = ref([])
const isViewMode = ref(false)
const title = ref("")

const rules = {
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

onMounted(() => {
    const id = route.query.id
    const action = route.query.action
    // 设置查看模式
    isViewMode.value = action === 'view'
    console.log('id:', id);

    if (id) {
        initPage(id)
    }
})

function initPage(id) {
    const action = route.query.action
    if (action === 'view') {
        // 查看模式
        isViewMode.value = true
        title.value = "查看销售订单"
        if (id) {
            getInfo(id)
        }
    } else if (action === 'edit') {
        // 编辑模式
        isViewMode.value = false
        title.value = "修改销售订单"
        if (id) {
            getInfo(id)
        }
    } else if (action === 'add') {
        // 新增模式
        isViewMode.value = false
        title.value = "新增销售订单"
        reset()
    }
}

// 获取详细信息
function getInfo(id) {
    getOrderheader(id).then(response => {
        console.log('销售详情数据:', response.data);
        form.value = response.data
        soSalesOrderLineList.value = response.data.soSalesOrderLineList || []
    }).catch(error => {
        proxy.$modal.msgError("获取数据失败: " + error.message)
    })
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

/** 提交按钮 */
function submitForm() {
    proxy.$refs["orderheaderRef"].validate(valid => {
        if (valid) {
            form.value.soSalesOrderLineList = soSalesOrderLineList.value
            if (form.value.soId != null) {
                updateOrderheader(form.value).then(response => {
                    proxy.$modal.msgSuccess("修改成功")
                    router.push('/core/so/orderheader')
                })
            } else {
                addOrderheader(form.value).then(response => {
                    proxy.$modal.msgSuccess("新增成功")
                    router.push('/core/so/orderheader')
                })
            }
        }
    })
}

// 取消按钮
function cancel() {
    const obj = { path: '/core/so/orderheader' }
    proxy.$tab.closeOpenPage(obj)
}

/** 销售订单行序号 */
function rowSoSalesOrderLineIndex({ row, rowIndex }) {
    row.index = rowIndex + 1
}

/** 销售订单行添加按钮操作 */
function handleAddSoSalesOrderLine() {
    let obj = {
        lineNo: "",
        materialId: "",
        materialCode: "",
        materialName: "",
        materialSpec: "",
        orderedQty: "",
        deliveredQty: "",
        unit: "",
        unitPrice: "",
        lineAmount: "",
        taxRate: "",
        taxAmount: "",
        discountPercent: "",
        discountAmount: "",
        requestedDate: "",
        promiseDate: "",
        fulfilledQty: "",
        productionQty: "",
        purchaseQty: "",
        reservedQty: "",
        lineStatus: "PENDING",
        linkedMoId: "",
        linkedPoId: "",
        reservedBatchNo: "",
        reservedSerials: "",
        paGroupId: "",
        warehouseId: "",
        deliveryPriority: "",
        customerLineRef: "",
        sortOrder: soSalesOrderLineList.value.length + 1,
        remark: ""
    }
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

/** 获取行状态文本 */
function getLineStatusText(status) {
    const statusMap = {
        'PENDING': '待处理',
        'SCHEDULED': '已排产',
        'PARTIALLY_FULFILLED': '部分履行',
        'FULFILLED': '已履行',
        'CANCELLED': '已取消'
    }
    return statusMap[status] || status
}

/** 获取行状态标签类型 */
function getLineStatusTagType(status) {
    const typeMap = {
        'PENDING': 'info',
        'SCHEDULED': 'warning',
        'PARTIALLY_FULFILLED': 'primary',
        'FULFILLED': 'success',
        'CANCELLED': 'danger'
    }
    return typeMap[status] || 'info'
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

:deep(.el-divider__text) {
    background-color: #f5f7fa;
    font-weight: 600;
    color: #606266;
}

.table-actions {
    margin-bottom: 15px;
    display: flex;
    gap: 10px;
}

.table-container {
    border: 1px solid #e4e7ed;
    border-radius: 4px;
    overflow: hidden;
}

.order-line-table {
    width: 100%;
}

:deep(.order-line-table .el-table__header-wrapper) {
    background-color: #f5f7fa;
}

:deep(.order-line-table .el-table th) {
    background-color: #f5f7fa;
    color: #606266;
    font-weight: 600;
}

:deep(.el-form-item) {
    margin-bottom: 18px;
}

:deep(.el-form-item__label) {
    font-weight: 500;
}

:deep(.el-input .el-input-group__append) {
    padding: 0 8px;
}

/* 响应式调整 */
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

    .table-container {
        overflow-x: auto;
    }

    .order-line-table {
        min-width: 1200px;
    }
}
</style>