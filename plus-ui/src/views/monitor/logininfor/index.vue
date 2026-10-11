<template>
  <div class="app-container">
    <el-form :model="queryParams" ref="queryRef" v-show="showSearch" label-width="80px" class="list-search">
      <el-row :gutter="16">
        <el-col :span="6">
          <el-form-item label="登录地址" prop="ipaddr">
            <el-input v-model="queryParams.ipaddr" placeholder="请输入登录地址" clearable @keyup.enter="handleQuery" />
          </el-form-item>
        </el-col>
        <el-col :span="6">
          <el-form-item label="用户名称" prop="userName">
            <el-input v-model="queryParams.userName" placeholder="请输入用户名称" clearable @keyup.enter="handleQuery" />
          </el-form-item>
        </el-col>
        <el-col :span="6">
          <el-form-item label="状态" prop="status">
            <el-select v-model="queryParams.status" placeholder="登录状态" clearable>
              <el-option v-for="dict in sys_common_status" :key="dict.value" :label="dict.label" :value="dict.value" />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="6">
          <el-form-item label="登录时间">
            <el-date-picker
              v-model="dateRange"
              value-format="YYYY-MM-DD HH:mm:ss"
              type="daterange"
              range-separator="-"
              start-placeholder="开始日期"
              end-placeholder="结束日期"
              :default-time="[new Date(2000, 1, 1, 0, 0, 0), new Date(2000, 1, 1, 23, 59, 59)]"
            ></el-date-picker>
          </el-form-item>
        </el-col>
        <el-col :span="6" :offset="18" class="search-actions">
          <el-form-item>
            <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
            <el-button icon="Refresh" @click="resetQuery">重置</el-button>
          </el-form-item>
        </el-col>
      </el-row>
    </el-form>

    <el-row :gutter="10" class="mb8">
      <el-col :span="1.5">
        <el-button v-hasPermi="['monitor:logininfor:remove']" type="danger" plain icon="Delete" :disabled="multiple" @click="handleDelete()">
          删除
        </el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button v-hasPermi="['monitor:logininfor:remove']" type="danger" plain icon="Delete" @click="handleClean()">清空</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button v-hasPermi="['monitor:logininfor:unlock']" type="primary" plain icon="Unlock" :disabled="single" @click="handleUnlock()">
          解锁
        </el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button v-hasPermi="['monitor:logininfor:export']" type="warning" plain icon="Download" @click="handleExport">导出</el-button>
      </el-col>
      <right-toolbar v-model:showSearch="showSearch" @queryTable="getList"></right-toolbar>
    </el-row>

    <el-table
      ref="loginInfoTableRef"
      v-loading="loading"
      :data="list"
      :default-sort="defaultSort"
      border
      @selection-change="handleSelectionChange"
      @sort-change="handleSortChange"
    >
      <el-table-column type="selection" width="55" align="center" />
      <el-table-column label="访问编号" align="center" prop="infoId" min-width="140" />
      <el-table-column
        label="用户名称"
        align="center"
        prop="userName"
        min-width="120"
        :show-overflow-tooltip="true"
        sortable="custom"
        :sort-orders="['descending', 'ascending']"
      />
      <el-table-column label="客户端" align="center" prop="clientKey" min-width="100" :show-overflow-tooltip="true" />
      <el-table-column label="设备类型" align="center" min-width="100">
        <template #default="scope">
          <dict-tag :options="sys_device_type" :value="scope.row.deviceType" />
        </template>
      </el-table-column>
      <el-table-column label="地址" align="center" prop="ipaddr" min-width="120" :show-overflow-tooltip="true" />
      <el-table-column label="登录地点" align="center" prop="loginLocation" min-width="120" :show-overflow-tooltip="true" />
      <el-table-column label="操作系统" align="center" prop="os" min-width="120" :show-overflow-tooltip="true" />
      <el-table-column label="浏览器" align="center" prop="browser" min-width="100" :show-overflow-tooltip="true" />
      <el-table-column label="登录状态" align="center" prop="status" min-width="90">
        <template #default="scope">
          <dict-tag :options="sys_common_status" :value="scope.row.status" />
        </template>
      </el-table-column>
      <el-table-column label="描述" align="center" prop="msg" min-width="120" :show-overflow-tooltip="true" />
      <el-table-column label="访问时间" align="center" prop="loginTime" sortable="custom" :sort-orders="['descending', 'ascending']" width="180">
        <template #default="scope">
          <span>{{ proxy.parseTime(scope.row.loginTime) }}</span>
        </template>
      </el-table-column>
    </el-table>

    <pagination v-show="total > 0" :total="total" v-model:page="queryParams.pageNum" v-model:limit="queryParams.pageSize" @pagination="getList" />
  </div>
</template>

<script setup name="Logininfor" lang="ts">
import { list as listLoginInfo, delLoginInfo, cleanLoginInfo, unlockLoginInfo } from '@/api/monitor/login-info';
import { LoginInfoQuery, LoginInfoVO } from '@/api/monitor/login-info/types';
import { useCrud } from '@/composables/useCrud';

const { proxy } = getCurrentInstance() as ComponentInternalInstance;
const { sys_device_type } = toRefs<any>(proxy?.useDict('sys_device_type'));
const { sys_common_status } = toRefs<any>(proxy?.useDict('sys_common_status'));

/** 选中的用户名（用于解锁） */
const selectName = ref<Array<string>>([]);
const dateRange = ref<[string, string]>(['', '']);
const defaultSort = ref<any>({ prop: 'loginTime', order: 'descending' });

const loginInfoTableRef = ref<ElTableInstance>();
// 登录日志为只读页面（无详情/新增/修改弹窗），以下占位仅为满足 useCrud 的 API 签名，不会被调用
const loginInfoFormRef = ref<ElFormInstance>();
const readonlyApi = () => Promise.resolve({ data: {} as any });

// 查询参数
const queryParams = ref<LoginInfoQuery>({
  pageNum: 1,
  pageSize: 10,
  ipaddr: '',
  userName: '',
  status: '',
  orderByColumn: defaultSort.value.prop,
  isAsc: defaultSort.value.order
});

const {
  loading,
  list,
  total,
  single,
  multiple,
  showSearch,
  getList,
  handleQuery,
  handleDelete,
  handleExport,
  handleSelectionChange: rawHandleSelectionChange
} = useCrud<LoginInfoVO, LoginInfoQuery>({
  apis: { list: listLoginInfo, get: readonlyApi, add: readonlyApi, update: readonlyApi, del: delLoginInfo },
  idField: 'infoId',
  entityName: '登录日志',
  formRef: loginInfoFormRef,
  queryParams,
  initForm: () => ({}),
  exportUrl: 'monitor/logininfor/export',
  exportFileName: 'logininfor',
  beforeList: (query) => {
    proxy?.addDateRange(query, dateRange.value);
  }
});

/** 多选框选中数据（额外记录用户名用于解锁） */
function handleSelectionChange(selection: LoginInfoVO[]) {
  rawHandleSelectionChange(selection);
  selectName.value = selection.map((item) => item.userName);
}

/** 重置按钮操作（重置排序会触发 sort-change 从而刷新列表） */
function resetQuery() {
  dateRange.value = ['', ''];
  proxy?.resetForm('queryRef');
  queryParams.value.pageNum = 1;
  loginInfoTableRef.value?.sort(defaultSort.value.prop, defaultSort.value.order);
}

/** 排序触发事件 */
const handleSortChange = (column: any) => {
  queryParams.value.orderByColumn = column.prop;
  queryParams.value.isAsc = column.order;
  getList();
};

/** 清空按钮操作 */
const handleClean = async () => {
  await proxy?.$modal.confirm('是否确认清空所有登录日志数据项?');
  await cleanLoginInfo();
  await getList();
  proxy?.$modal.msgSuccess('清空成功');
};

/** 解锁按钮操作 */
const handleUnlock = async () => {
  const username = selectName.value;
  await proxy?.$modal.confirm('是否确认解锁用户"' + username + '"数据项?');
  await unlockLoginInfo(username);
  proxy?.$modal.msgSuccess('用户' + username + '解锁成功');
};

getList();
</script>
