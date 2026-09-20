<template>
  <div class="app-container">
    <!-- 搜索栏：按列表页标准 §2（el-row/el-col 网格，每行 4 项，label-width 100px） -->
    <el-form :model="queryParams" ref="queryRef" v-show="showSearch" label-width="100px" class="list-search">
      <el-row :gutter="16">
        <el-col :span="6">
          <el-form-item label="标签名称" prop="tagName">
            <el-input v-model="queryParams.tagName" placeholder="请输入标签名称" clearable @keyup.enter="handleQuery()" />
          </el-form-item>
        </el-col>
        <!-- 搜索项 1 个 → 1 % 4 = 1，按钮补当前行第 4 格（offset=12） -->
        <el-col :span="6" :offset="12" class="search-actions">
          <el-form-item>
            <el-button type="primary" icon="Search" @click="handleQuery()">搜索</el-button>
            <el-button icon="Refresh" @click="resetQuery()">重置</el-button>
          </el-form-item>
        </el-col>
      </el-row>
    </el-form>

    <el-row :gutter="10" class="mb8">
      <el-col :span="1.5">
        <el-button type="primary" plain icon="Plus" @click="handleAdd()" v-hasPermi="['iot:deviceTag:add']">新增</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="success" plain icon="CollectionTag" :disabled="multiple" @click="openBind(false)" v-hasPermi="['iot:deviceTag:bind']"
          >批量打标签</el-button
        >
      </el-col>
      <el-col :span="1.5">
        <el-button type="warning" plain icon="Remove" :disabled="multiple" @click="openBind(true)" v-hasPermi="['iot:deviceTag:bind']"
          >批量解绑</el-button
        >
      </el-col>
      <el-col :span="1.5">
        <el-button type="danger" plain icon="Delete" :disabled="multiple" @click="handleDelete()" v-hasPermi="['iot:deviceTag:remove']"
          >删除</el-button
        >
      </el-col>
      <right-toolbar v-model:showSearch="showSearch" @queryTable="getList"></right-toolbar>
    </el-row>

    <el-table v-loading="loading" :data="tagList" @selection-change="handleSelectionChange" show-overflow-tooltip>
      <el-table-column type="selection" width="55" align="center" />
      <el-table-column label="序号" type="index" align="center" />
      <el-table-column label="标签名称" align="center" prop="tagName" min-width="120">
        <template #default="scope">
          <span class="tag-name">
            <span class="tag-dot" :style="{ backgroundColor: scope.row.tagColor || '#409EFF' }"></span>
            {{ scope.row.tagName }}
          </span>
        </template>
      </el-table-column>
      <el-table-column label="颜色值" align="center" prop="tagColor" />
      <el-table-column label="已绑定设备数" align="center" prop="deviceCount" />
      <el-table-column label="备注" align="center" prop="remark" min-width="120" />
      <el-table-column label="创建时间" align="center" prop="createTime">
        <template #default="scope">{{ parseTime(scope.row.createTime) }}</template>
      </el-table-column>
      <el-table-column label="操作" align="center" class-name="small-padding fixed-width" width="208" :show-overflow-tooltip="false">
        <template #default="scope">
          <el-button link type="primary" icon="Edit" @click="handleUpdate(scope.row)" v-hasPermi="['iot:deviceTag:edit']">修改</el-button>
          <el-button link type="danger" icon="Delete" @click="handleDelete(scope.row)" v-hasPermi="['iot:deviceTag:remove']">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <!-- 后端标签列表接口为非分页全量返回（见报告「后端需求」），故不设分页器 -->

    <!-- 新增/修改标签 -->
    <el-dialog :title="title" v-model="open" width="520px" append-to-body>
      <el-form ref="tagRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="标签名称" prop="tagName">
          <el-input v-model="form.tagName" placeholder="请输入标签名称" />
        </el-form-item>
        <el-form-item label="标签颜色" prop="tagColor">
          <el-color-picker v-model="form.tagColor" />
          <span class="color-tip">用于列表色块展示，缺省为 #409EFF</span>
        </el-form-item>
        <el-form-item label="备注" prop="remark">
          <el-input v-model="form.remark" type="textarea" placeholder="请输入备注" />
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button type="primary" @click="submitForm()">确 定</el-button>
          <el-button @click="cancel()">取 消</el-button>
        </div>
      </template>
    </el-dialog>

    <!-- 批量打标签 / 解绑标签 -->
    <el-dialog :title="bindForm.remove ? '批量解绑标签' : '批量打标签'" v-model="bindOpen" width="640px" append-to-body>
      <el-form ref="bindRef" :model="bindForm" :rules="bindRules" label-width="100px">
        <el-form-item label="标签" prop="tagIds">
          <el-select v-model="bindForm.tagIds" multiple filterable placeholder="请选择标签" style="width: 100%">
            <el-option v-for="item in tagOptions" :key="item.tagId" :label="item.tagName" :value="item.tagId" />
          </el-select>
        </el-form-item>
        <el-form-item label="设备" prop="deviceIds">
          <el-select v-model="bindForm.deviceIds" multiple filterable placeholder="请选择设备（可按编码/名称过滤）" style="width: 100%">
            <el-option v-for="item in deviceOptions" :key="item.deviceId" :label="deviceLabel(item)" :value="item.deviceId" />
          </el-select>
        </el-form-item>
        <el-alert
          :type="bindForm.remove ? 'warning' : 'info'"
          :closable="false"
          :title="bindForm.remove ? '解绑为幂等操作：设备未绑定该标签时不会报错。' : '绑定为幂等操作：设备已绑定该标签时不会重复插入。'"
        />
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button type="primary" :loading="submitLoading" @click="submitBind">确 定</el-button>
          <el-button @click="bindOpen = false">取 消</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts" name="IotDeviceTag">
import { addDeviceTag, bindDeviceTag, delDeviceTag, getDeviceTag, listDeviceTag, optionDeviceTag, updateDeviceTag } from '@/api/iot/deviceTag';
import type { DeviceTagForm, DeviceTagQuery, DeviceTagVO } from '@/api/iot/deviceTag/types';
import { listDevice } from '@/api/iot/device';
import type { DeviceVO } from '@/api/iot/device/types';
import type { FormInstance } from 'element-plus';
import { useCrud } from '@/composables/useCrud';

const { proxy } = getCurrentInstance() as ComponentInternalInstance;

const tagRef = ref<FormInstance>();
const bindRef = ref<FormInstance>();
const bindOpen = ref(false);
const submitLoading = ref(false);
const tagOptions = ref<DeviceTagVO[]>([]);
const deviceOptions = ref<DeviceVO[]>([]);

const queryParams = ref<DeviceTagQuery>({
  pageNum: 1,
  pageSize: 10,
  tagName: null
});

const bindForm = ref<{ tagIds: Array<string | number>; deviceIds: Array<string | number>; remove: boolean }>({
  tagIds: [],
  deviceIds: [],
  remove: false
});

const rules = {
  tagName: [{ required: true, message: '标签名称不能为空', trigger: 'blur' }]
};

const bindRules = {
  tagIds: [{ required: true, message: '请至少选择一个标签', trigger: 'change' }],
  deviceIds: [{ required: true, message: '请至少选择一台设备', trigger: 'change' }]
};

/** 标签列表：后端返回全量数组（非分页），包装为 useCrud 标准形状 */
const {
  loading,
  list: tagList,
  open,
  title,
  ids: selectedTagIds,
  multiple,
  showSearch,
  form,
  getList,
  handleQuery,
  handleDelete,
  handleSelectionChange,
  submitForm,
  cancel,
  handleAdd,
  handleUpdate
} = useCrud<DeviceTagVO, DeviceTagQuery>({
  apis: {
    list: (query) =>
      listDeviceTag(query).then((res: any) => {
        const rows = (res.data || []) as DeviceTagVO[];
        return { rows, total: rows.length };
      }),
    get: getDeviceTag,
    add: addDeviceTag,
    update: updateDeviceTag,
    del: delDeviceTag
  },
  idField: 'tagId',
  entityName: '设备标签',
  formRef: tagRef,
  queryParams,
  initForm: () => ({
    tagId: null,
    tagName: null,
    tagColor: '#409EFF',
    remark: null
  })
});

function resetQuery() {
  proxy.resetForm('queryRef');
  handleQuery();
}

function deviceLabel(item: DeviceVO) {
  return `${item.deviceCode} - ${item.deviceName}`;
}

/** 标签下拉（供批量打标签复用） */
function loadTagOptions() {
  optionDeviceTag()
    .then((res: any) => {
      tagOptions.value = res.data || [];
    })
    .catch(() => {
      tagOptions.value = [];
    });
}

/**
 * 设备下拉：一次性加载（IoT 设备量级较小）。
 * 设备量很大时需改为远端搜索（后端 list 支持 deviceCode/deviceName 模糊），已登记后端需求。
 */
function loadDeviceOptions() {
  listDevice({ pageNum: 1, pageSize: 500 })
    .then((res) => {
      deviceOptions.value = res.rows || [];
    })
    .catch(() => {
      deviceOptions.value = [];
    });
}

function openBind(remove: boolean) {
  bindForm.value = { tagIds: remove ? [...selectedTagIds.value] : [], deviceIds: [], remove };
  bindOpen.value = true;
}

function submitBind() {
  bindRef.value?.validate((valid: boolean) => {
    if (!valid) return;
    submitLoading.value = true;
    bindDeviceTag({
      tagIds: bindForm.value.tagIds,
      deviceIds: bindForm.value.deviceIds,
      remove: bindForm.value.remove
    })
      .then(() => {
        proxy.$modal.msgSuccess(bindForm.value.remove ? '解绑成功' : '打标签成功');
        bindOpen.value = false;
        getList();
      })
      .finally(() => {
        submitLoading.value = false;
      });
  });
}

loadTagOptions();
loadDeviceOptions();
getList();
</script>

<style scoped>
.list-search .el-form-item {
  margin-bottom: 12px;
  width: 100%;
}
.list-search .el-form-item :deep(.el-input),
.list-search .el-form-item :deep(.el-select),
.list-search .el-form-item :deep(.el-date-editor) {
  width: 100%;
}
.list-search .search-actions :deep(.el-form-item__content) {
  justify-content: flex-end;
}
.tag-name {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}
.tag-dot {
  display: inline-block;
  width: 10px;
  height: 10px;
  border-radius: 2px;
}
.color-tip {
  margin-left: 10px;
  color: var(--el-text-color-secondary);
  font-size: 12px;
}
</style>
