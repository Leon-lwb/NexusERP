<template>
  <div class="app-container">
    <el-form :model="queryParams" ref="queryRef" v-show="showSearch" label-width="100px" class="list-search">
      <el-row :gutter="16">
        <el-col :span="6">
          <el-form-item label="客户编码" prop="clientCode">
            <el-input v-model="queryParams.clientCode" placeholder="请输入客户编码" clearable @keyup.enter="handleQuery" />
          </el-form-item>
        </el-col>
        <el-col :span="6">
          <el-form-item label="客户名称" prop="clientName">
            <el-input v-model="queryParams.clientName" placeholder="请输入客户名称" clearable @keyup.enter="handleQuery" />
          </el-form-item>
        </el-col>
        <el-col :span="6">
          <el-form-item label="客户类型" prop="clientType">
            <el-select v-model="queryParams.clientType" placeholder="请选择" clearable style="width: 100%">
              <el-option v-for="dict in bd_client_type" :key="dict.value" :label="dict.label" :value="dict.value" />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="6">
          <el-form-item label="客户电话" prop="tel">
            <el-input v-model="queryParams.tel" placeholder="请输入客户电话" clearable @keyup.enter="handleQuery" />
          </el-form-item>
        </el-col>
        <el-col :span="6">
          <el-form-item label="统一代码" prop="creditCode">
            <el-input v-model="queryParams.creditCode" placeholder="请输入统一社会信用代码" clearable @keyup.enter="handleQuery" />
          </el-form-item>
        </el-col>
        <el-col :span="6" :offset="12" class="search-actions">
          <el-form-item>
            <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
            <el-button icon="Refresh" @click="resetQuery">重置</el-button>
          </el-form-item>
        </el-col>
      </el-row>
    </el-form>

    <el-row :gutter="10" class="mb8">
      <el-col :span="1.5">
        <el-button type="primary" plain icon="Plus" @click="handleAdd" v-hasPermi="['core:bd:add']">新增</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="success" plain icon="Edit" :disabled="single" @click="handleUpdate" v-hasPermi="['core:bd:edit']">修改</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="danger" plain icon="Delete" :disabled="multiple" @click="handleDelete" v-hasPermi="['core:bd:remove']">删除</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="info" plain icon="View" :disabled="single" @click="handleView" v-hasPermi="['core:bd:query']">查看</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="warning" plain icon="Download" @click="handleExport" v-hasPermi="['core:bd:export']">导出</el-button>
      </el-col>
      <right-toolbar v-model:showSearch="showSearch" @queryTable="getList"></right-toolbar>
    </el-row>

    <el-table v-loading="loading" :data="bdList" @selection-change="handleSelectionChange" show-overflow-tooltip>
      <el-table-column type="selection" width="55" align="center" />
      <el-table-column label="序号" type="index" align="center" />
      <el-table-column label="客户编码" align="center" prop="clientCode" min-width="120" show-overflow-tooltip />
      <el-table-column label="客户名称" align="center" prop="clientName" min-width="120" show-overflow-tooltip />
      <el-table-column label="客户类型" align="center" prop="clientType">
        <template #default="scope">
          <dict-tag :options="bd_client_type" :value="scope.row.clientType" />
        </template>
      </el-table-column>
      <el-table-column label="客户电话" align="center" prop="tel" min-width="120" show-overflow-tooltip />
      <el-table-column label="客户LOGO" align="center" prop="clientLogo">
        <template #default="scope">
          <el-image
            v-if="scope.row.clientLogoUrl"
            :src="scope.row.clientLogoUrl"
            :preview-src-list="[scope.row.clientLogoUrl]"
            preview-teleported
            fit="cover"
            class="logo-image"
          />
          <span v-else>-</span>
        </template>
      </el-table-column>
      <el-table-column label="操作" align="center" class-name="small-padding fixed-width" width="208" :show-overflow-tooltip="false">
        <template #default="scope">
          <el-button link type="primary" icon="View" @click="handleView(scope.row)" v-hasPermi="['core:bd:query']">查看</el-button>
          <el-button link type="primary" icon="Edit" @click="handleUpdate(scope.row)" v-hasPermi="['core:bd:edit']">修改</el-button>
          <el-button link type="primary" icon="Delete" @click="handleDelete(scope.row)" v-hasPermi="['core:bd:remove']">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <pagination v-show="total > 0" :total="total" v-model:page="queryParams.pageNum" v-model:limit="queryParams.pageSize" @pagination="getList" />

    <!-- 添加或修改客户信息对话框 -->
    <el-dialog :title="title" v-model="open" @close="cancel" width="960px" append-to-body>
      <el-form ref="bdRef" :model="form" :rules="rules" label-width="120px">
        <el-tabs v-model="activeTab">
          <el-tab-pane label="基本信息" name="basic">
            <el-row>
              <el-col :span="8">
                <el-form-item label="客户编码" prop="clientCode">
                  <div style="display: flex; align-items: center; gap: 10px">
                    <el-input v-model="form.clientCode" placeholder="请输入客户编码" :disabled="autoGenFlag || isViewMode" style="flex: 1" />
                    <el-switch
                      v-model="autoGenFlag"
                      active-color="#13ce66"
                      active-text="自动生成"
                      @change="handleAutoGenChange"
                      :disabled="isViewMode"
                    >
                    </el-switch>
                  </div>
                </el-form-item>
              </el-col>
              <el-col :span="16">
                <el-form-item label="客户名称" prop="clientName">
                  <el-input v-model="form.clientName" placeholder="请输入客户名称" :disabled="isViewMode" />
                </el-form-item>
              </el-col>
            </el-row>
            <el-row>
              <el-col :span="12">
                <el-form-item label="客户简称" prop="clientNick">
                  <el-input v-model="form.clientNick" placeholder="请输入客户简称" :disabled="isViewMode" />
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item label="客户英文名称" prop="clientEn">
                  <el-input v-model="form.clientEn" placeholder="请输入客户英文名称" :disabled="isViewMode" />
                </el-form-item>
              </el-col>
            </el-row>
            <el-row>
              <el-col :span="12">
                <el-form-item label="客户类型" prop="clientType">
                  <el-select v-model="form.clientType" placeholder="请选择客户类型" style="width: 100%" :disabled="isViewMode">
                    <el-option v-for="dict in bd_client_type" :key="dict.value" :label="dict.label" :value="dict.value"> </el-option>
                  </el-select>
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item label="社会信用代码" prop="creditCode">
                  <el-input v-model="form.creditCode" placeholder="请输入统一社会信用代码" :disabled="isViewMode" />
                </el-form-item>
              </el-col>
            </el-row>
            <!-- 新增地址信息字段 -->
            <el-row>
              <el-col :span="6">
                <el-form-item label="省份" prop="provinceAreaId">
                  <el-select
                    v-model="form.provinceAreaId"
                    placeholder="请选择省份"
                    :disabled="isViewMode"
                    @change="handleProvinceChange"
                    style="width: 100%"
                  >
                    <el-option v-for="item in provinceOptions" :key="item.areaId" :label="item.areaName" :value="item.areaId"> </el-option>
                  </el-select>
                </el-form-item>
              </el-col>
              <el-col :span="6">
                <el-form-item label="城市" prop="cityAreaId">
                  <el-select
                    v-model="form.cityAreaId"
                    placeholder="请选择城市"
                    :disabled="isViewMode || !cityOptions.length"
                    @change="handleCityChange"
                    style="width: 100%"
                  >
                    <el-option v-for="item in cityOptions" :key="item.areaId" :label="item.areaName" :value="item.areaId"> </el-option>
                  </el-select>
                </el-form-item>
              </el-col>
              <el-col :span="6">
                <el-form-item label="区县" prop="districtAreaId">
                  <el-select
                    v-model="form.districtAreaId"
                    placeholder="请选择区县"
                    :disabled="isViewMode || !districtOptions.length"
                    @change="handleDistrictChange"
                    style="width: 100%"
                  >
                    <el-option v-for="item in districtOptions" :key="item.areaId" :label="item.areaName" :value="item.areaId"> </el-option>
                  </el-select>
                </el-form-item>
              </el-col>
              <el-col :span="6">
                <el-form-item label="街道" prop="streetId">
                  <el-select
                    v-model="form.streetId"
                    placeholder="请选择街道"
                    :disabled="isViewMode || !streetOptions.length"
                    style="width: 100%"
                    @change="handleStreetChange"
                  >
                    <el-option v-for="item in streetOptions" :key="item.areaId" :label="item.areaName" :value="item.areaId"> </el-option>
                  </el-select>
                </el-form-item>
              </el-col>
            </el-row>
            <el-row>
              <el-col :span="12">
                <el-form-item label="经度" prop="addressLng">
                  <el-input v-model="form.addressLng" placeholder="请输入地址经度" :disabled="isViewMode" />
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item label="纬度" prop="addressLat">
                  <el-input v-model="form.addressLat" placeholder="请输入地址纬度" :disabled="isViewMode" />
                </el-form-item>
              </el-col>
            </el-row>
            <el-row>
              <el-col :span="24">
                <el-form-item label="送货地点" prop="address">
                  <el-input v-model="form.address" type="textarea" placeholder="请输入内容" :disabled="isViewMode" />
                </el-form-item>
              </el-col>
            </el-row>
            <el-row>
              <el-col :span="24">
                <el-form-item label="客户简介" prop="clientDesc">
                  <el-input v-model="form.clientDesc" type="textarea" placeholder="请输入内容" :disabled="isViewMode" />
                </el-form-item>
              </el-col>
            </el-row>
            <el-row :gutter="20">
              <el-col :span="12">
                <el-form-item label="业务员" prop="salesRepName">
                  <el-input v-model="form.salesRepName" placeholder="请选择业务员" readonly>
                    <template #append>
                      <el-button icon="User" @click="openUserSelect" :disabled="isViewMode" />
                    </template>
                  </el-input>
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item label="归属部门" prop="deptId">
                  <dept-select
                    v-model="form.deptId"
                    category="SALES"
                    placeholder="请选择归属部门"
                    :disabled="isViewMode"
                    @change="handleDeptChange"
                  />
                  <el-input v-model="form.deptName" style="display: none" />
                </el-form-item>
              </el-col>
            </el-row>
          </el-tab-pane>

          <el-tab-pane label="联系方式" name="contact">
            <el-row>
              <el-col :span="12">
                <el-form-item label="客户官网地址" prop="webSite">
                  <el-input v-model="form.webSite" placeholder="请输入客户官网地址" :disabled="isViewMode" />
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item label="客户邮箱地址" prop="email">
                  <el-input v-model="form.email" placeholder="请输入客户邮箱地址" :disabled="isViewMode" />
                </el-form-item>
              </el-col>
            </el-row>
            <el-row>
              <el-col :span="12">
                <el-form-item label="客户电话" prop="tel">
                  <el-input v-model="form.tel" placeholder="请输入客户电话" :disabled="isViewMode" />
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item label="客户LOGO" prop="clientLogo">
                  <client-logo-upload v-model="form.clientLogo" :url="form.clientLogoUrl" :disabled="isViewMode" />
                </el-form-item>
              </el-col>
            </el-row>
            <el-row>
              <el-col :span="8">
                <el-form-item label="联系人1" prop="contact1">
                  <el-input v-model="form.contact1" placeholder="请输入联系人1" :disabled="isViewMode" />
                </el-form-item>
              </el-col>
              <el-col :span="8">
                <el-form-item label="联系人1-电话" prop="contact1Tel">
                  <el-input v-model="form.contact1Tel" placeholder="请输入联系人1-电话" :disabled="isViewMode" />
                </el-form-item>
              </el-col>
              <el-col :span="8">
                <el-form-item label="联系人1-邮箱" prop="contact1Emal">
                  <el-input v-model="form.contact1Emal" placeholder="请输入联系人1-邮箱" :disabled="isViewMode" />
                </el-form-item>
              </el-col>
            </el-row>
            <el-row>
              <el-col :span="8">
                <el-form-item label="联系人2" prop="contact2">
                  <el-input v-model="form.contact2" placeholder="请输入联系人2" :disabled="isViewMode" />
                </el-form-item>
              </el-col>
              <el-col :span="8">
                <el-form-item label="联系人2-电话" prop="contact2Tel">
                  <el-input v-model="form.contact2Tel" placeholder="请输入联系人2-电话" :disabled="isViewMode" />
                </el-form-item>
              </el-col>
              <el-col :span="8">
                <el-form-item label="联系人2-邮箱" prop="contact2Emal">
                  <el-input v-model="form.contact2Emal" placeholder="请输入联系人2-邮箱" :disabled="isViewMode" />
                </el-form-item>
              </el-col>
            </el-row>
            <el-row>
              <el-col :span="24">
                <el-form-item label="备注" prop="remark">
                  <el-input v-model="form.remark" type="textarea" placeholder="请输入内容" :disabled="isViewMode" />
                </el-form-item>
              </el-col>
            </el-row>
          </el-tab-pane>

          <el-tab-pane label="开票信息" name="invoice">
            <el-row>
              <el-col :span="12">
                <el-form-item label="纳税人识别号" prop="taxId">
                  <el-input v-model="form.taxId" placeholder="请输入纳税人识别号" :disabled="isViewMode" />
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item label="发票抬头" prop="invoiceTitle">
                  <el-input v-model="form.invoiceTitle" placeholder="请输入发票抬头" :disabled="isViewMode" />
                </el-form-item>
              </el-col>
            </el-row>
            <el-row>
              <el-col :span="12">
                <el-form-item label="开户行" prop="invoiceBankName">
                  <el-input v-model="form.invoiceBankName" placeholder="请输入开户行" :disabled="isViewMode" />
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item label="银行账号" prop="invoiceBankAccount">
                  <el-input v-model="form.invoiceBankAccount" placeholder="请输入银行账号" :disabled="isViewMode" />
                </el-form-item>
              </el-col>
            </el-row>
            <el-row>
              <el-col :span="12">
                <el-form-item label="开票地址电话" prop="invoiceAddressPhone">
                  <el-input v-model="form.invoiceAddressPhone" placeholder="请输入开票地址、电话" :disabled="isViewMode" />
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item label="默认发票类型" prop="defaultInvoiceType">
                  <el-select v-model="form.defaultInvoiceType" placeholder="请选择默认发票类型" style="width: 100%" :disabled="isViewMode">
                    <el-option v-for="dict in so_invoice_type" :key="dict.value" :label="dict.label" :value="dict.value" />
                  </el-select>
                </el-form-item>
              </el-col>
            </el-row>
          </el-tab-pane>
        </el-tabs>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button v-if="!isViewMode" type="primary" @click="submitForm">确 定</el-button>
          <el-button @click="cancel">取 消</el-button>
        </div>
      </template>
    </el-dialog>

    <!-- 业务员选择 -->
    <el-dialog title="选择业务员" v-model="userDialog.visible" width="800px" append-to-body>
      <el-form :inline="true" :model="userDialog.queryParams">
        <el-form-item label="用户名称" prop="userName">
          <el-input v-model="userDialog.queryParams.userName" placeholder="请输入用户名称" clearable @keyup.enter="searchUser" />
        </el-form-item>
        <el-form-item label="手机号码" prop="phonenumber">
          <el-input v-model="userDialog.queryParams.phonenumber" placeholder="请输入手机号码" clearable @keyup.enter="searchUser" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" icon="Search" @click="searchUser">搜索</el-button>
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

<script setup lang="ts" name="Bd">
import { listClient, getClient, addClient, updateClient, delClient } from '@/api/erp/bd/client';
import type { ClientForm, ClientQuery } from '@/api/erp/bd/client/types';
import { listUser } from '@/api/system/user';
import { previewCode } from '@/api/system/autocode/rule';
import { getProvinces, getNextLevel } from '@/api/system/area';
import type { FormInstance } from 'element-plus';
import ClientLogoUpload from './ClientLogoUpload.vue';

const { proxy } = getCurrentInstance() as any;
const { bd_client_type, so_invoice_type } = toRefs<any>(proxy.useDict('bd_client_type', 'so_invoice_type'));

const bdList = ref([]);
const open = ref(false);
const loading = ref(true);
const showSearch = ref(true);
const ids = ref([]);
const single = ref(true);
const multiple = ref(true);
const total = ref(0);
const title = ref('');
const autoGenFlag = ref(false);
const activeTab = ref('basic');
const isViewMode = ref(false); // 标识是否为查看模式

const bdRef = ref<FormInstance>();
const data = reactive<PageData<ClientForm, ClientQuery>>({
  form: {},
  queryParams: {
    pageNum: 1,
    pageSize: 10,
    clientCode: null,
    clientName: null,
    clientEn: null,
    clientType: null,
    tel: null,
    creditCode: null
  },
  rules: {
    clientCode: [{ required: true, message: '客户编码不能为空', trigger: 'blur' }],
    clientName: [{ required: true, message: '客户名称不能为空', trigger: 'blur' }],
    clientDesc: [{ required: true, message: '客户简介不能为空', trigger: 'blur' }],
    clientType: [{ required: true, message: '客户类型不能为空', trigger: 'change' }],
    address: [{ required: true, message: '客户默认送货地点不能为空', trigger: 'blur' }],
    email: [{ required: true, message: '客户邮箱不能为空', trigger: 'blur' }],
    tel: [{ required: true, message: '客户电话不能为空', trigger: 'blur' }],
    contact1: [{ required: true, message: '联系人1不能为空', trigger: 'blur' }],
    contact1Tel: [{ required: true, message: '联系人1电话不能为空', trigger: 'blur' }]
  }
});

// 新增地址相关数据
const provinceOptions = ref([]);
const cityOptions = ref([]);
const districtOptions = ref([]);
const streetOptions = ref([]);

const { queryParams, form, rules } = toRefs(data);

/** 查询客户信息列表 */
function getList() {
  loading.value = true;
  listClient(queryParams.value).then((response) => {
    bdList.value = response.rows;
    total.value = response.total;
    loading.value = false;
  });
}

// 取消按钮
function cancel() {
  open.value = false;
  reset();
}

// 表单重置
function reset() {
  form.value = {
    clientId: null,
    clientCode: null,
    clientName: null,
    clientNick: null,
    clientEn: null,
    clientDesc: null,
    clientType: null,
    clientLogo: null,
    clientLogoUrl: null,
    address: null,
    webSite: null,
    email: null,
    tel: null,
    contact1: null,
    contact1Tel: null,
    contact1Emal: null,
    contact2: null,
    contact2Tel: null,
    contact2Emal: null,
    creditCode: null,
    taxId: null,
    invoiceTitle: null,
    invoiceBankName: null,
    invoiceBankAccount: null,
    invoiceAddressPhone: null,
    defaultInvoiceType: null,
    remark: null,
    // 新增地址字段
    provinceAreaId: null,
    cityAreaId: null,
    districtAreaId: null,
    streetId: null,
    addressLat: null,
    addressLng: null,
    attr1: null,
    attr2: null,
    attr3: null,
    attr4: null,
    salesRepId: null,
    salesRepName: null,
    deptId: null,
    deptName: null,
    createBy: null,
    createTime: null,
    updateBy: null,
    updateTime: null
  };
  proxy.resetForm('bdRef');
  activeTab.value = 'basic';
  // 重置查看模式标识
  isViewMode.value = false;
  // 重置地址选项
  cityOptions.value = [];
  districtOptions.value = [];
  streetOptions.value = [];
}

/** 搜索按钮操作 */
function handleQuery() {
  queryParams.value.pageNum = 1;
  getList();
}

/** 重置按钮操作 */
function resetQuery() {
  proxy.resetForm('queryRef');
  handleQuery();
}

// 多选框选中数据
function handleSelectionChange(selection) {
  ids.value = selection.map((item) => item.clientId);
  single.value = selection.length != 1;
  multiple.value = !selection.length;
}

/** 新增按钮操作 */
function handleAdd() {
  reset();
  isViewMode.value = false;
  loadProvinceOptions(); // 加载省份数据
  open.value = true;
  title.value = '添加客户信息';
}

/** 修改按钮操作 */
function handleUpdate(row) {
  reset();
  isViewMode.value = false;
  const _clientId = row.clientId || ids.value;
  getClient(_clientId).then((response) => {
    form.value = response.data;
    // 加载地址级联数据
    loadProvinceOptions().then(() => {
      if (form.value.provinceAreaId) {
        loadCityOptions(form.value.provinceAreaId).then(() => {
          if (form.value.cityAreaId) {
            loadDistrictOptions(form.value.cityAreaId).then(() => {
              // 如果有街道ID，加载街道数据
              if (form.value.streetId && form.value.districtAreaId) {
                loadStreetOptions(form.value.districtAreaId).then(() => {
                  // 确保街道选项中包含当前选中的街道
                  const selectedStreet = streetOptions.value.find((option) => option.areaId === form.value.streetId);
                  if (!selectedStreet && form.value.streetId) {
                    // 如果街道选项中没有当前选中的街道，添加该街道到选项中
                    // 创建一个新的街道选项对象
                    const newStreetOption = {
                      areaId: form.value.streetId,
                      areaName: form.value.streetName || form.value.streetId,
                      parentAreaId: form.value.districtAreaId
                    };
                    streetOptions.value.push(newStreetOption);
                  }
                });
              }
            });
          }
        });
      }
    });
    open.value = true;
    title.value = '修改客户信息';
  });
}

/** 查看按钮操作 */
function handleView(row) {
  reset();
  isViewMode.value = true; // 设置为查看模式
  const _clientId = row.clientId || ids.value[0];
  getClient(_clientId).then((response) => {
    form.value = response.data;
    // 加载地址级联数据
    loadProvinceOptions().then(() => {
      if (form.value.provinceAreaId) {
        loadCityOptions(form.value.provinceAreaId).then(() => {
          if (form.value.cityAreaId) {
            loadDistrictOptions(form.value.cityAreaId).then(() => {
              // 如果有街道ID，加载街道数据
              if (form.value.streetId && form.value.districtAreaId) {
                loadStreetOptions(form.value.districtAreaId).then(() => {
                  // 确保街道选项中包含当前选中的街道
                  const selectedStreet = streetOptions.value.find((option) => option.areaId === form.value.streetId);
                  if (!selectedStreet && form.value.streetId) {
                    // 如果街道选项中没有当前选中的街道，添加该街道到选项中
                    // 创建一个新的街道选项对象
                    const newStreetOption = {
                      areaId: form.value.streetId,
                      areaName: form.value.streetName || form.value.streetId,
                      parentAreaId: form.value.districtAreaId
                    };
                    streetOptions.value.push(newStreetOption);
                  }
                });
              }
            });
          }
        });
      }
    });
    open.value = true;
    title.value = '查看客户信息';
  });
}

/** 提交按钮 */
function submitForm() {
  bdRef.value?.validate((valid) => {
    if (valid) {
      // 自动生成开关打开时清空编码，由后端 isBlank 兜底生成，避免预览消耗流水号
      if (autoGenFlag.value) {
        form.value.clientCode = undefined;
      }
      if (form.value.clientId != null) {
        updateClient(form.value).then((response) => {
          proxy.$modal.msgSuccess('修改成功');
          open.value = false;
          getList();
        });
      } else {
        addClient(form.value).then((response) => {
          proxy.$modal.msgSuccess('新增成功');
          open.value = false;
          getList();
        });
      }
    }
  });
}

/** 删除按钮操作 */
function handleDelete(row) {
  const _clientIds = row.clientId || ids.value;
  proxy.$modal
    .confirm('是否确认删除客户信息编号为"' + _clientIds + '"的数据项？')
    .then(function () {
      return delClient(_clientIds);
    })
    .then(() => {
      getList();
      proxy.$modal.msgSuccess('删除成功');
    })
    .catch(() => {});
}

/** 导出按钮操作 */
function handleExport() {
  proxy.download(
    'erp/bd/client/export',
    {
      ...queryParams.value
    },
    `bd_${new Date().getTime()}.xlsx`
  );
}

//自动生成编码
function handleAutoGenChange(autoGenFlag) {
  if (autoGenFlag) {
    previewCode('CLIENT_CODE').then((response: any) => {
      form.value.clientCode = response;
    });
  } else {
    form.value.clientCode = null;
  }
}

// 加载省份数据
function loadProvinceOptions() {
  return getProvinces().then((response) => {
    provinceOptions.value = response.data;
  });
}

// 省份变化处理
function handleProvinceChange(value) {
  form.value.cityAreaId = null;
  form.value.districtAreaId = null;
  form.value.streetId = null;
  cityOptions.value = [];
  districtOptions.value = [];
  streetOptions.value = [];

  if (value) {
    loadCityOptions(value);
  }

  // 更新地址字段
  nextTick(() => {
    updateAddressField();
  });
}

// 加载城市数据
function loadCityOptions(parentId) {
  return getNextLevel(parentId).then((response) => {
    cityOptions.value = response.data;
  });
}

// 城市变化处理
function handleCityChange(value) {
  form.value.districtAreaId = null;
  form.value.streetId = null;
  districtOptions.value = [];
  streetOptions.value = [];

  if (value) {
    loadDistrictOptions(value);
  }

  // 更新地址字段
  nextTick(() => {
    updateAddressField();
  });
}

// 区县变化处理
function handleDistrictChange(value) {
  form.value.streetId = null;
  streetOptions.value = [];

  if (value) {
    loadStreetOptions(value);
  }

  // 更新地址字段
  nextTick(() => {
    updateAddressField();
  });
}

// 街道变化处理
function handleStreetChange(value) {
  // 更新地址字段
  updateAddressField();
}

// 加载区县数据
function loadDistrictOptions(parentId) {
  return getNextLevel(parentId).then((response) => {
    districtOptions.value = response.data;
  });
}

// 加载街道数据
function loadStreetOptions(parentId) {
  return getNextLevel(parentId).then((response) => {
    streetOptions.value = response.data;
  });
}

// 更新地址字段
function updateAddressField() {
  // 获取各级区域的名称
  const provinceName = provinceOptions.value.find((p) => p.areaId === form.value.provinceAreaId)?.areaName || '';
  const cityName = cityOptions.value.find((c) => c.areaId === form.value.cityAreaId)?.areaName || '';
  const districtName = districtOptions.value.find((d) => d.areaId === form.value.districtAreaId)?.areaName || '';
  const streetName = streetOptions.value.find((s) => s.areaId === form.value.streetId)?.areaName || '';

  // 拼接地址
  const addressParts = [provinceName, cityName, districtName, streetName].filter((part) => part !== '');
  form.value.address = addressParts.join('');
}

// ============ 业务员 / 部门选择 ============
const userDialog = reactive({
  visible: false,
  loading: false,
  list: [] as any[],
  total: 0,
  queryParams: { pageNum: 1, pageSize: 10, userName: undefined, phonenumber: undefined, deptId: undefined as any }
});

/** 部门变更：回填部门名称并清空业务员（避免业务员与部门不匹配） */
function handleDeptChange(dept) {
  form.value.deptName = dept ? dept.deptName : null;
  form.value.salesRepId = null;
  form.value.salesRepName = null;
}

function openUserSelect() {
  if (!form.value.deptId) {
    proxy.$modal.msgWarning('请先选择归属部门');
    return;
  }
  userDialog.visible = true;
  userDialog.queryParams.deptId = form.value.deptId;
  userDialog.queryParams.pageNum = 1;
  getUserList();
}

function getUserList() {
  userDialog.loading = true;
  listUser(userDialog.queryParams).then((response) => {
    userDialog.list = response.rows;
    userDialog.total = response.total;
    userDialog.loading = false;
  });
}

function selectUser(row) {
  form.value.salesRepId = row.userId;
  form.value.salesRepName = row.nickName || row.userName;
  userDialog.visible = false;
}

function searchUser() {
  userDialog.queryParams.pageNum = 1;
  getUserList();
}

function resetUserQuery() {
  userDialog.queryParams.userName = undefined;
  userDialog.queryParams.phonenumber = undefined;
  userDialog.queryParams.deptId = form.value.deptId;
  userDialog.queryParams.pageNum = 1;
  getUserList();
}

getList();
</script>

<style scoped lang="scss">
.logo-image {
  width: 40px;
  height: 40px;
  border-radius: 4px;
}
</style>
