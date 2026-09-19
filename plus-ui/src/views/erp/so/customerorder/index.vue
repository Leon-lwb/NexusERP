<template>
  <div class="app-container">
    <el-form :model="queryParams" ref="queryRef" v-show="showSearch" label-width="100px" class="list-search">
      <el-row :gutter="16">
        <el-col :span="6">
          <el-form-item label="客户订单号" prop="coNo">
            <el-input v-model="queryParams.coNo" placeholder="请输入客户订单号" clearable @keyup.enter="handleQuery" />
          </el-form-item>
        </el-col>
        <el-col :span="6">
          <el-form-item label="客户名称" prop="clientName">
            <el-input v-model="queryParams.clientName" placeholder="请输入客户名称" clearable @keyup.enter="handleQuery" />
          </el-form-item>
        </el-col>
        <el-col :span="6">
          <el-form-item label="下单日期" prop="orderDate">
            <el-date-picker
              clearable
              v-model="queryParams.orderDate"
              type="date"
              value-format="YYYY-MM-DD"
              placeholder="请选择客户下单日期"
              style="width: 100%"
            >
            </el-date-picker>
          </el-form-item>
        </el-col>
        <el-col :span="6">
          <el-form-item label="订单状态" prop="status">
            <el-select v-model="queryParams.status" placeholder="请选择订单状态" clearable style="width: 100%">
              <el-option v-for="dict in so_customer_order_status" :key="dict.value" :label="dict.label" :value="dict.value" />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="6" :offset="18" class="search-actions">
          <el-form-item>
            <el-button type="primary" icon="Search" @click="handleQuery()">搜索</el-button>
            <el-button icon="Refresh" @click="resetQuery()">重置</el-button>
          </el-form-item>
        </el-col>
      </el-row>
    </el-form>

    <el-row :gutter="10" class="mb8">
      <el-col :span="1.5">
        <el-button type="primary" plain icon="Plus" @click="handleAddCustomerOrder()" v-hasPermi="['so:customerOrder:add']">新增</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="success" plain icon="Edit" :disabled="single" @click="handleUpdateCustomerOrder()" v-hasPermi="['so:customerOrder:edit']"
          >修改</el-button
        >
      </el-col>
      <el-col :span="1.5">
        <el-button type="danger" plain icon="Delete" :disabled="multiple" @click="handleDelete()" v-hasPermi="['so:customerOrder:remove']"
          >删除</el-button
        >
      </el-col>
      <el-col :span="1.5">
        <el-button type="info" plain icon="View" :disabled="single" @click="handleView()" v-hasPermi="['so:customerOrder:query']">查看</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="warning" plain icon="Download" @click="handleExport()" v-hasPermi="['so:customerOrder:export']">导出</el-button>
      </el-col>
      <right-toolbar v-model:showSearch="showSearch" @queryTable="getList"></right-toolbar>
    </el-row>

    <el-table v-loading="loading" :data="customerOrderList" @selection-change="handleSelectionChange" show-overflow-tooltip>
      <el-table-column type="selection" width="55" align="center" />
      <el-table-column label="客户订单号" align="center" prop="coNo" />
      <el-table-column label="客户名称" align="center" prop="clientName" min-width="120" show-overflow-tooltip />
      <el-table-column label="客户下单日期" align="center" prop="orderDate">
        <template #default="scope">
          <span>{{ parseTime(scope.row.orderDate, '{y}-{m}-{d}') }}</span>
        </template>
      </el-table-column>
      <el-table-column label="订单总金额" align="center" prop="totalAmount" />
      <el-table-column label="订单状态" align="center" prop="status">
        <template #default="scope">
          <dict-tag :options="so_customer_order_status" :value="scope.row.status" />
        </template>
      </el-table-column>
      <el-table-column label="订单优先级" align="center" prop="priority">
        <template #default="scope">
          <dict-tag :options="so_order_priority" :value="scope.row.priority" />
        </template>
      </el-table-column>
      <el-table-column label="客户采购订单号" align="center" prop="customerPoNo" min-width="120" show-overflow-tooltip />
      <el-table-column label="订单来源" align="center" prop="sourceChannel">
        <template #default="scope">
          <dict-tag :options="so_source_channel" :value="scope.row.sourceChannel" />
        </template>
      </el-table-column>
      <el-table-column label="操作" align="center" class-name="small-padding fixed-width" width="208" :show-overflow-tooltip="false">
        <template #default="scope">
          <el-button link type="primary" icon="View" @click="handleView(scope.row)">查看</el-button>
          <el-button link type="primary" icon="Edit" @click="handleUpdateCustomerOrder(scope.row)" v-hasPermi="['so:customerOrder:edit']"
            >修改</el-button
          >
          <el-button link type="primary" icon="Delete" @click="handleDelete(scope.row)" v-hasPermi="['so:customerOrder:remove']">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <pagination v-show="total > 0" :total="total" v-model:page="queryParams.pageNum" v-model:limit="queryParams.pageSize" @pagination="getList" />

    <!-- 添加或修改客户订单对话框 -->
    <el-dialog :title="title" v-model="open" width="960px" append-to-body>
      <el-form ref="customerOrderRef" :model="form" :rules="rules" label-width="100px" :disabled="isViewMode">
        <el-row :gutter="20">
          <el-col :span="16">
            <el-form-item label="客户订单号" prop="coNo">
              <div style="display: flex; align-items: center; gap: 10px">
                <el-input v-model="form.coNo" placeholder="请输入客户订单号" :disabled="autoGenFlag || isViewMode" style="flex: 1" />
                <el-switch v-model="autoGenFlag" active-color="#13ce66" active-text="自动生成" @change="handleAutoGenChange" :disabled="isViewMode">
                </el-switch>
              </div>
            </el-form-item>
          </el-col>
          <el-col :span="8"></el-col>
        </el-row>

        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="客户" prop="clientId">
              <el-row :gutter="10">
                <el-col :span="18">
                  <el-input v-model="form.clientName" placeholder="请选择客户" readonly>
                    <template #append>
                      <el-button v-if="!isViewMode" icon="Search" @click="handleOpenClientDialog" />
                    </template>
                  </el-input>
                </el-col>
                <el-col :span="6">
                  <el-button v-if="form.clientId && !isViewMode" type="danger" icon="Delete" @click="clearClient" />
                </el-col>
              </el-row>
              <el-input v-model="form.clientId" style="display: none" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="客户编码" prop="clientCode">
              <el-input v-model="form.clientCode" placeholder="请输入客户编码" readonly />
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="联系人" prop="contactName">
              <el-input v-model="form.contactName" placeholder="请输入联系人" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="联系人电话" prop="contactPhone">
              <el-input v-model="form.contactPhone" placeholder="请输入联系人电话" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="下单日期" prop="orderDate">
              <el-date-picker
                clearable
                v-model="form.orderDate"
                type="date"
                value-format="YYYY-MM-DD"
                placeholder="请选择客户下单日期"
                style="width: 100%"
              >
              </el-date-picker>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="要求交货日" prop="requestedDate">
              <el-date-picker
                clearable
                v-model="form.requestedDate"
                type="date"
                value-format="YYYY-MM-DD"
                placeholder="请选择客户要求交货日"
                style="width: 100%"
              >
              </el-date-picker>
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="货币" prop="currency">
              <el-select v-model="form.currency" placeholder="请选择货币" style="width: 100%">
                <el-option v-for="dict in so_currency_type" :key="dict.value" :label="dict.label" :value="dict.value"></el-option>
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="订单总金额" prop="totalAmount">
              <el-input v-model="form.totalAmount" placeholder="请输入订单总金额" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="订单优先级" prop="priority">
              <el-select v-model="form.priority" placeholder="请选择订单优先级" style="width: 100%">
                <el-option v-for="dict in so_order_priority" :key="dict.value" :label="dict.label" :value="parseInt(dict.value)"></el-option>
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="采购订单号" prop="customerPoNo">
              <el-input v-model="form.customerPoNo" placeholder="请输入客户采购订单号" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="订单来源" prop="sourceChannel">
              <el-select v-model="form.sourceChannel" placeholder="请选择订单来源" style="width: 100%">
                <el-option v-for="dict in so_source_channel" :key="dict.value" :label="dict.label" :value="dict.value"></el-option>
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="部门筛选">
              <!-- 仅用于筛选销售代表候选，不落库（so_customer_order 无部门字段） -->
              <dept-select
                v-model="salesRepDeptId"
                category="SALES"
                placeholder="不选则展示全部用户"
                :disabled="isViewMode"
                @change="handleDeptFilterChange"
              />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="销售代表" prop="salesRepId">
              <el-input v-model="form.salesRepName" placeholder="请选择负责人" readonly>
                <template #append>
                  <el-button icon="User" @click="openUserSelect" :disabled="isViewMode"></el-button>
                </template>
              </el-input>
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="20">
          <el-col :span="24">
            <el-form-item label="备注" prop="remark">
              <el-input v-model="form.remark" type="textarea" placeholder="请输入内容" :autosize="{ minRows: 2, maxRows: 4 }" />
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button type="primary" @click="submitForm" v-if="!isViewMode">确 定</el-button>
          <el-button @click="cancel">{{ isViewMode ? '关 闭' : '取 消' }}</el-button>
        </div>
      </template>
    </el-dialog>

    <!-- 客户选择对话框 -->
    <el-dialog title="选择客户" v-model="clientDialogVisible" width="800px" append-to-body>
      <el-form :model="clientQueryParams" :inline="true" label-width="80px">
        <el-form-item label="客户名称">
          <el-input v-model="clientQueryParams.clientName" placeholder="请输入客户名称" clearable @keyup.enter="handleClientQuery" />
        </el-form-item>
        <el-form-item label="客户编码">
          <el-input v-model="clientQueryParams.clientCode" placeholder="请输入客户编码" clearable @keyup.enter="handleClientQuery" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" icon="Search" @click="handleClientQuery()">搜索</el-button>
          <el-button icon="Refresh" @click="resetClientQuery()">重置</el-button>
        </el-form-item>
      </el-form>

      <el-table v-loading="clientLoading" :data="clientList" @row-click="handleClientSelect" show-overflow-tooltip>
        <el-table-column label="客户编码" align="center" prop="clientCode" />
        <el-table-column label="客户名称" align="center" prop="clientName" />
        <el-table-column label="客户类型" align="center" prop="clientType">
          <template #default="scope">
            <dict-tag :options="bd_client_type" :value="scope.row.clientType" />
          </template>
        </el-table-column>
        <el-table-column label="客户电话" align="center" prop="tel" />
        <el-table-column label="操作" align="center" class-name="small-padding fixed-width" width="208" :show-overflow-tooltip="false">
          <template #default="scope">
            <el-button link type="primary" @click="handleClientSelect(scope.row)">选择</el-button>
          </template>
        </el-table-column>
      </el-table>

      <pagination
        v-show="clientTotal > 0"
        :total="clientTotal"
        v-model:page="clientQueryParams.pageNum"
        v-model:limit="clientQueryParams.pageSize"
        @pagination="handleClientPagination"
      />
    </el-dialog>

    <!-- 用户选择对话框 -->
    <el-dialog title="选择负责人" v-model="userVisible" width="800px" append-to-body>
      <el-form :model="userQueryParams" ref="userQueryRef" :inline="true">
        <el-form-item label="用户名称" prop="userName">
          <el-input v-model="userQueryParams.userName" placeholder="请输入用户名称" clearable @keyup.enter="searchUser" />
        </el-form-item>
        <el-form-item label="手机号码" prop="phonenumber">
          <el-input v-model="userQueryParams.phonenumber" placeholder="请输入手机号码" clearable @keyup.enter="searchUser" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" icon="Search" @click="searchUser()">搜索</el-button>
          <el-button icon="Refresh" @click="resetUserQuery()">重置</el-button>
        </el-form-item>
      </el-form>

      <el-table v-loading="userLoading" :data="userList" @row-dblclick="selectUser" show-overflow-tooltip>
        <el-table-column label="用户编号" align="center" prop="userId" />
        <el-table-column label="用户名称" align="center" prop="userName" />
        <el-table-column label="用户昵称" align="center" prop="nickName" />
        <el-table-column label="手机号码" align="center" prop="phonenumber" />
        <el-table-column label="部门" align="center" prop="dept.deptName" />
        <el-table-column label="操作" align="center">
          <template #default="scope">
            <el-button link type="primary" @click="selectUser(scope.row)">选择</el-button>
          </template>
        </el-table-column>
      </el-table>

      <pagination
        v-show="userList.length > 0"
        :total="userTotal"
        v-model:page="userQueryParams.pageNum"
        v-model:limit="userQueryParams.pageSize"
        @pagination="getUserList"
      />

      <template #footer>
        <div class="dialog-footer">
          <el-button @click="userVisible = false">取 消</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts" name="CustomerOrder">
import { listCustomerOrder, getCustomerOrder, delCustomerOrder, addCustomerOrder, updateCustomerOrder } from '@/api/erp/so/customer-order';
import { listBd } from '@/api/erp/bd/client';
import { previewCode } from '@/api/system/autocode/rule';
import { listUser } from '@/api/system/user';
import type { FormInstance } from 'element-plus';
import { useCrud } from '@/composables/useCrud';

const { proxy } = getCurrentInstance() as ComponentInternalInstance;
const { so_customer_order_status, so_currency_type, so_order_priority, so_source_channel, bd_client_type } = toRefs<any>(
  proxy.useDict('so_customer_order_status', 'so_currency_type', 'so_order_priority', 'so_source_channel', 'bd_client_type')
);
const autoGenFlag = ref(false);
const clientDialogVisible = ref(false);
const clientList = ref([]);
const clientLoading = ref(false);
const clientTotal = ref(0);

const clientQueryParams = ref({
  pageNum: 1,
  pageSize: 10,
  clientName: null,
  clientCode: null
});

const userList = ref([]);
const userLoading = ref(false);
const userVisible = ref(false);
const userTotal = ref(0);
const userQueryParams = reactive({
  pageNum: 1,
  pageSize: 10,
  userName: undefined,
  phonenumber: undefined,
  deptId: undefined as any
});

/**
 * 销售代表候选的部门筛选。
 * <p>注意：{@code so_customer_order} 表没有部门字段（部门归属在客户档案 {@code bd_client.dept_id}），
 * 因此这里是一个**不落库**的本地筛选项，只用于缩小下方销售代表候选范围。
 * 原实现把它绑定到 {@code form.deptId}，而 VO/BO/实体都没有该字段 —— 选择被静默丢弃、
 * 列表「销售部门」列恒空。</p>
 */
const salesRepDeptId = ref<string | number | undefined>(undefined);

const customerOrderRef = ref<FormInstance>();

const queryParams = ref({
  pageNum: 1,
  pageSize: 10,
  coNo: null,
  clientName: null,
  orderDate: null,
  status: null
});

const rules = {
  coNo: [{ required: true, message: '客户订单号不能为空', trigger: 'blur' }],
  clientId: [{ required: true, message: '客户ID不能为空', trigger: 'blur' }],
  orderDate: [{ required: true, message: '客户下单日期不能为空', trigger: 'blur' }],
  currency: [{ required: true, message: '货币不能为空', trigger: 'change' }]
};

const {
  loading,
  list: customerOrderList,
  total,
  open,
  isViewMode,
  title,
  ids,
  single,
  multiple,
  showSearch,
  form,
  getList,
  handleQuery,
  handleAdd,
  handleUpdate,
  handleView,
  submitForm,
  cancel,
  handleDelete,
  handleExport,
  handleSelectionChange
} = useCrud({
  apis: { list: listCustomerOrder, get: getCustomerOrder, add: addCustomerOrder, update: updateCustomerOrder, del: delCustomerOrder },
  idField: 'coId',
  entityName: '客户订单',
  formRef: customerOrderRef,
  queryParams,
  initForm: () => ({
    coId: null,
    coNo: null,
    clientId: null,
    clientName: null,
    contactName: null,
    contactPhone: null,
    orderDate: null,
    requestedDate: null,
    currency: null,
    totalAmount: null,
    status: null,
    priority: null,
    customerPoNo: null,
    sourceChannel: null,
    remark: null,
    clientCode: null,
    attr2: null,
    salesRepId: null,
    salesRepName: null,
    createBy: null,
    createTime: null,
    updateBy: null,
    updateTime: null
  }),
  exportUrl: 'erp/so/customerOrder/export',
  exportFileName: 'customerOrder',
  onReset: () => {
    autoGenFlag.value = false;
  },
  beforeSubmit: () => {
    // 自动生成开关打开时清空编码，由后端 isBlank 兜底生成，避免预览消耗流水号
    if (autoGenFlag.value) {
      form.value.coNo = undefined;
    }
  },
  afterLoad: (data: any) => {
    // 回填客户名称/编码
    if (data.clientId) {
      listBd({ pageNum: 1, clientId: data.clientId, pageSize: 1000 }).then((clientResponse) => {
        if (clientResponse.rows && clientResponse.rows.length > 0) {
          const client = clientResponse.rows.find((item) => item.clientId == data.clientId);
          if (client) {
            form.value.clientName = client.clientName;
            form.value.clientCode = client.clientCode;
          }
        }
      });
    }
    if (data.salesRepId && !data.salesRepName) {
      listUser({ pageNum: 1, userIds: data.salesRepId, pageSize: 1 })
        .then((userResponse) => {
          if (userResponse.rows && userResponse.rows.length > 0) {
            const user = userResponse.rows[0];
            form.value.salesRepName = user.userName;
          }
        })
        .catch(() => {
          console.warn('获取销售代表名称失败');
        });
    }
  }
});

function resetQuery() {
  proxy.resetForm('queryRef');
  handleQuery();
}

/**
 * 新增客户订单：额外清空「部门筛选」。
 * 它是本地筛选项（不属于表单数据），若不清空会从上一次操作残留下来。
 */
function handleAddCustomerOrder() {
  handleAdd(() => {
    salesRepDeptId.value = undefined;
  });
}

/** 修改客户订单：同样清空部门筛选 */
function handleUpdateCustomerOrder(row?: any) {
  handleUpdate(row, () => {
    salesRepDeptId.value = undefined;
  });
}

function handleAutoGenChange(autoGenFlag) {
  if (autoGenFlag) {
    previewCode('SO_CO_NO').then((response: any) => {
      form.value.coNo = response;
    });
  } else {
    form.value.coNo = null;
  }
}

function openClientDialog() {
  clientDialogVisible.value = true;
  getClientList();
}

function handleOpenClientDialog() {
  openClientDialog();
}

function getClientList() {
  clientLoading.value = true;
  listBd(clientQueryParams.value).then((response) => {
    clientList.value = response.rows;
    clientTotal.value = response.total;
    clientLoading.value = false;
  });
}

function handleClientQuery() {
  clientQueryParams.value.pageNum = 1;
  getClientList();
}

function resetClientQuery() {
  clientQueryParams.value = {
    pageNum: 1,
    pageSize: 10,
    clientName: null,
    clientCode: null
  };
  handleClientQuery();
}

function handleClientSelect(row) {
  form.value.clientId = row.clientId;
  form.value.clientName = row.clientName;
  form.value.clientCode = row.clientCode;
  form.value.contactName = row.contact1;
  form.value.contactPhone = row.tel;
  clientDialogVisible.value = false;
}

function clearClient() {
  form.value.clientId = null;
  form.value.clientName = null;
}

function handleClientPagination(pagination) {
  clientQueryParams.value.pageNum = pagination.page;
  clientQueryParams.value.pageSize = pagination.limit;
  getClientList();
}

function getUserList() {
  userLoading.value = true;
  listUser({ ...userQueryParams }).then((response) => {
    userList.value = response.rows;
    userTotal.value = response.total;
    userLoading.value = false;
  });
}

function openUserSelect() {
  userVisible.value = true;
  // 按所选部门筛选销售代表；未选部门则展示全部用户
  userQueryParams.deptId = salesRepDeptId.value || undefined;
  getUserList();
}

function selectUser(row) {
  form.value.salesRepId = row.userId;
  form.value.salesRepName = row.userName;
  userVisible.value = false;
}

/** 部门筛选变更时清空已选销售代表（避免代表与筛选部门不匹配） */
function handleDeptFilterChange() {
  form.value.salesRepId = null;
  form.value.salesRepName = null;
}

function resetUserQuery() {
  userQueryParams.userName = undefined;
  userQueryParams.phonenumber = undefined;
  userQueryParams.deptId = salesRepDeptId.value || undefined;
  userQueryParams.pageNum = 1;
  getUserList();
}

function searchUser() {
  userQueryParams.pageNum = 1;
  getUserList();
}

getList();
</script>
