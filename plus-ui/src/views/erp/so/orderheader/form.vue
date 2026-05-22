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
                <el-form :model="form" :rules="rules" ref="orderheaderRef" label-width="120px">
                    <el-divider content-position="left">基本信息</el-divider>
                    <el-row :gutter="20">
                        <el-col :xs="24" :sm="12" :md="8" :lg="6">
                            <el-form-item label="销售订单编号" prop="soNo">
                                <el-input v-model="form.soNo" placeholder="请输入销售订单编号" />
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
                                <el-input v-model="scope.row.orderedQty" placeholder="请输入订单数量" v-if="!isViewMode" @input="calculateLineAmount(scope.row)" />
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
                                <el-input v-model="scope.row.unitPrice" placeholder="请输入单价" v-if="!isViewMode" @input="calculateLineAmount(scope.row)" />
                                <span v-else>{{ scope.row.unitPrice }}</span>
                            </template>
                        </el-table-column>
                        <el-table-column label="行金额" prop="lineAmount" width="150">
                            <template #default="scope">
                                <el-input v-model="scope.row.lineAmount" placeholder="请输入行金额"
                                    v-if="!isViewMode" readonly />
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
                        <el-table-column label="关联生产计划" prop="linkedPlanNo" width="180" v-if="isViewMode">
                            <template #default="scope">
                                <el-link v-if="scope.row.linkedPlanNo" type="primary" @click="viewProductionPlan(scope.row)">{{ scope.row.linkedPlanNo }}</el-link>
                                <span v-else>-</span>
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

        <el-dialog :title="materialDialogTitle" v-model="materialSelectOpen" width="1200px" append-to-body>
            <el-row :gutter="20">
                <el-col :span="6">
                    <div class="category-tree-container">
                        <el-input
                            v-model="categoryFilterText"
                            placeholder="请输入分类名称"
                            clearable
                            prefix-icon="Search"
                            class="category-search-input"
                            aria-label="分类搜索"
                            @input="debouncedHandleCategoryFilter"
                        />
                        <el-tree
                            ref="categoryTreeRef"
                            class="category-tree"
                            :data="categoryTree"
                            :props="categoryTreeProps"
                            :expand-on-click-node="false"
                            :highlight-current="true"
                            node-key="id"
                            default-expand-all
                            :filter-node-method="filterCategoryNode"
                            @node-click="handleCategoryNodeClick"
                        />
                    </div>
                </el-col>

                <el-col :span="18">
                    <el-form :model="materialQueryParams" ref="materialQueryRef" :inline="true" label-width="68px">
                        <el-form-item label="物料编码" prop="materialCode">
                            <el-input
                                v-model="materialQueryParams.materialCode"
                                placeholder="请输入物料编码"
                                clearable
                                @keyup.enter="handleMaterialQuery"
                            />
                        </el-form-item>
                        <el-form-item label="物料名称" prop="materialName">
                            <el-input
                                v-model="materialQueryParams.materialName"
                                placeholder="请输入物料名称"
                                clearable
                                @keyup.enter="handleMaterialQuery"
                            />
                        </el-form-item>
                        <el-form-item>
                            <el-button type="primary" icon="Search" @click="handleMaterialQuery">搜索</el-button>
                            <el-button icon="Refresh" @click="resetMaterialQuery">重置</el-button>
                        </el-form-item>
                    </el-form>

                    <el-table
                        v-loading="materialLoading"
                        :data="materialList"
                        @row-dblclick="selectMaterial"
                        highlight-current-row
                        style="width: 100%"
                        height="400"
                    >
                        <el-table-column type="index" width="50" align="center" />
                        <el-table-column label="物料编码" align="center" prop="materialCode" />
                        <el-table-column label="物料名称" align="center" prop="materialName" />
                        <el-table-column label="规格型号" align="center" prop="materialSpec" />
                        <el-table-column label="物料类型" align="center" prop="materialType">
                            <template #default="scope">
                                <dict-tag :options="bd_material_type" :value="scope.row.materialType"/>
                            </template>
                        </el-table-column>
                        <el-table-column label="状态" align="center" prop="status">
                            <template #default="scope">
                                <dict-tag :options="sys_true_or_false" :value="scope.row.status"/>
                            </template>
                        </el-table-column>
                        <el-table-column label="操作" align="center" width="100">
                            <template #default="scope">
                                <el-button link type="primary" @click="selectMaterial(scope.row)">选择</el-button>
                            </template>
                        </el-table-column>
                    </el-table>

                    <pagination
                        v-show="materialTotal > 0"
                        :total="materialTotal"
                        v-model:page="materialQueryParams.pageNum"
                        v-model:limit="materialQueryParams.pageSize"
                        @pagination="getMaterialList"
                    />
                </el-col>
            </el-row>

            <template #footer>
                <div class="dialog-footer">
                    <el-button @click="materialSelectOpen = false">取 消</el-button>
                </div>
            </template>
        </el-dialog>
    </div>
</template>

<script setup lang="ts" name="OrderForm">
import { getOrderheader, addOrderheader, updateOrderheader } from "@/api/erp/so/orderheader"
import { listMaterial, getMaterial } from "@/api/erp/bd/material"
import { listCategory, getAllChildrenIds } from "@/api/erp/bd/category"
import { debounce } from 'lodash'
import { useRouter, useRoute } from 'vue-router'

const { proxy } = getCurrentInstance()
const { so_credit_status, so_order_status, sys_true_or_false, so_currency_type, so_order_line_status, so_order_priority, bd_material_type } = toRefs<any>(proxy.useDict('so_credit_status', 'so_order_status', 'sys_true_or_false', 'so_currency_type', 'so_order_line_status', 'so_order_priority', 'bd_material_type'))

const route = useRoute()
const router = useRouter()

const form = ref({})
const soSalesOrderLineList = ref([])
const checkedSoSalesOrderLine = ref([])
const isViewMode = ref(false)
const title = ref("")

const materialSelectOpen = ref(false)
const materialList = ref([])
const materialLoading = ref(false)
const materialTotal = ref(0)
const materialDialogTitle = ref("选择物料")
const currentMaterialRowIndex = ref(-1)

const categoryTree = ref([])
const categoryTreeProps = {
    children: 'children',
    label: 'categoryName',
    value: 'categoryId'
}
const categoryTreeRef = ref(null)
const categoryFilterText = ref('')

const debouncedHandleCategoryFilter = debounce(function(val) {
    handleCategoryFilter(val)
}, 300)

const materialQueryParams = ref({
    pageNum: 1,
    pageSize: 10,
    materialCode: null,
    materialName: null,
    categoryId: null,
    categoryIds: []
})

const rules = {
    soNo: [
        { required: true, message: "销售订单编号不能为空", trigger: "blur" }
    ],
    clientName: [
        { required: true, message: "来源客户不能为空", trigger: "blur" }
    ],
    orderDate: [
        { required: true, message: "订单生成日期不能为空", trigger: "blur" }
    ],
}

onMounted(() => {
    const id = route.query.id
    const action = route.query.action
    isViewMode.value = action === 'view'

    if (id) {
        initPage(id)
    }

    getCategoryTree()
})

function initPage(id) {
    const action = route.query.action
    if (action === 'view') {
        isViewMode.value = true
        title.value = "查看销售订单"
        if (id) {
            getInfo(id)
        }
    } else if (action === 'edit') {
        isViewMode.value = false
        title.value = "修改销售订单"
        if (id) {
            getInfo(id)
        }
    } else if (action === 'add') {
        isViewMode.value = false
        title.value = "新增销售订单"
        reset()
    }
}

function getInfo(id) {
    getOrderheader(id).then(response => {
        form.value = response.data
        soSalesOrderLineList.value = response.data.soSalesOrderLineList || []
    }).catch(error => {
        proxy.$modal.msgError("获取数据失败: " + error.message)
    })
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
    }
    soSalesOrderLineList.value = []
    proxy.resetForm("orderheaderRef")
}

function submitForm() {
    proxy.$refs["orderheaderRef"].validate(valid => {
        if (valid) {
            form.value.soSalesOrderLineList = soSalesOrderLineList.value
            if (form.value.soId != null) {
                updateOrderheader(form.value).then(response => {
                    proxy.$modal.msgSuccess("修改成功")
                    router.push('/erp/so/orderheader')
                })
            } else {
                addOrderheader(form.value).then(response => {
                    proxy.$modal.msgSuccess("新增成功")
                    router.push('/erp/so/orderheader')
                })
            }
        }
    })
}

function cancel() {
    const obj = { path: '/erp/so/orderheader' }
    proxy.$tab.closeOpenPage(obj)
}

function rowSoSalesOrderLineIndex({ row, rowIndex }) {
    row.index = rowIndex + 1
}

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
        requestedDate: form.value.requestedDate || "",
        promiseDate: form.value.promiseDate || "",
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

function handleSoSalesOrderLineSelectionChange(selection) {
    checkedSoSalesOrderLine.value = selection.map(item => item.index)
}

function viewProductionPlan(row) {
    router.push({ path: '/erp/prd/prdplan/form', query: { id: row.linkedPlanId, action: 'view' } })
}

function getDictLabel(dictOptions, value) {
    const dict = dictOptions.find(item => item.value === value)
    return dict ? dict.label : value
}

function calculateLineAmount(row) {
    const qty = parseFloat(row.orderedQty) || 0
    const price = parseFloat(row.unitPrice) || 0
    row.lineAmount = (qty * price).toFixed(2)
}

function openMaterialSelect(rowIndex) {
    currentMaterialRowIndex.value = rowIndex
    materialSelectOpen.value = true
    materialDialogTitle.value = "选择物料"
    getMaterialList()
}

function getMaterialList() {
    materialLoading.value = true
    const params = {}
    if (materialQueryParams.value.pageNum) params.pageNum = materialQueryParams.value.pageNum
    if (materialQueryParams.value.pageSize) params.pageSize = materialQueryParams.value.pageSize
    if (materialQueryParams.value.materialCode) params.materialCode = materialQueryParams.value.materialCode
    if (materialQueryParams.value.materialName) params.materialName = materialQueryParams.value.materialName
    if (materialQueryParams.value.categoryId) params.categoryId = materialQueryParams.value.categoryId
    params.status = 1
    if (materialQueryParams.value.categoryIds &&
        Array.isArray(materialQueryParams.value.categoryIds) &&
        materialQueryParams.value.categoryIds.length > 0) {
        const validCategoryIds = materialQueryParams.value.categoryIds
            .map(id => Number(id))
            .filter(id => !isNaN(id) && id > 0)
        if (validCategoryIds.length > 0) {
            params.categoryIds = validCategoryIds
        }
    }
    listMaterial(params).then(response => {
        const enabledMaterials = response.rows.filter(material => {
            return String(material.status) === '1' || material.status === 1;
        });
        materialList.value = enabledMaterials
        materialTotal.value = enabledMaterials.length
        materialLoading.value = false
    }).catch((error) => {
        materialLoading.value = false
        proxy.$modal.msgError("获取物料列表失败")
    })
}

function handleMaterialQuery() {
    materialQueryParams.value.pageNum = 1
    getMaterialList()
}

function resetMaterialQuery() {
    proxy.resetForm("materialQueryRef")
    materialQueryParams.value.materialCode = null
    materialQueryParams.value.materialName = null
    materialQueryParams.value.categoryId = null
    materialQueryParams.value.categoryIds = []
    handleMaterialQuery()
}

function selectMaterial(row) {
    if (currentMaterialRowIndex.value >= 0) {
        soSalesOrderLineList.value[currentMaterialRowIndex.value].materialId = row.materialId
        soSalesOrderLineList.value[currentMaterialRowIndex.value].materialCode = row.materialCode
        soSalesOrderLineList.value[currentMaterialRowIndex.value].materialName = row.materialName
        soSalesOrderLineList.value[currentMaterialRowIndex.value].materialSpec = row.materialSpec
        soSalesOrderLineList.value[currentMaterialRowIndex.value].unitPrice = row.referencePrice
    }
    materialSelectOpen.value = false
    currentMaterialRowIndex.value = -1
}

function clearMaterial(rowIndex) {
    soSalesOrderLineList.value[rowIndex].materialId = null
    soSalesOrderLineList.value[rowIndex].materialCode = ''
    soSalesOrderLineList.value[rowIndex].materialName = ''
    soSalesOrderLineList.value[rowIndex].materialSpec = ''
}

function getCategoryTree() {
    listCategory({ status: 1 }).then(response => {
        const data = response.data || response.rows || []
        const filteredData = Array.isArray(data) ? data.filter(item => item !== null && item !== undefined) : []
        const treeData = proxy.handleTree(filteredData, "categoryId", "parentId");
        categoryTree.value = treeData;
    }).catch(error => {
        proxy.$modal.msgError("获取物料分类失败")
    })
}

function handleCategoryNodeClick(data) {
    const categoryId = Number(data.id || data.categoryId)
    if (isNaN(categoryId) || categoryId <= 0) {
        proxy.$modal.msgError("无效的分类ID")
        return
    }
    materialLoading.value = true
    getAllChildrenIds(categoryId).then(response => {
        if (response.code === 200) {
            let allMaterials = [];
            if (response.data && Array.isArray(response.data)) {
                allMaterials = response.data;
            } else if (response.data && response.data.materials && Array.isArray(response.data.materials)) {
                allMaterials = response.data.materials;
            }
            const enabledMaterials = allMaterials.filter(material => {
                return String(material.status) === '1' || material.status === 1;
            });
            materialList.value = enabledMaterials
            materialTotal.value = enabledMaterials.length
            materialLoading.value = false
        } else {
            proxy.$modal.msgError("获取分类信息失败: " + response.msg)
            materialLoading.value = false
        }
    }).catch(error => {
        materialLoading.value = false
        proxy.$modal.msgError("请求出现异常: " + error.message)
    })
}

function filterCategoryNode(value, data) {
    if (!value) return true
    return data.categoryName && data.categoryName.includes(value)
}

function handleCategoryFilter(val) {
    if (categoryTreeRef.value) {
        categoryTreeRef.value.filter(val)
    }
}

onUnmounted(() => {
    if (debouncedHandleCategoryFilter && typeof debouncedHandleCategoryFilter.cancel === 'function') {
        debouncedHandleCategoryFilter.cancel()
    }
})
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
