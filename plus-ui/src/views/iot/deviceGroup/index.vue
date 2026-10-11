<template>
  <div class="app-container">
    <el-row :gutter="16">
      <!-- 左：分组树 -->
      <el-col :span="6">
        <el-card shadow="never" class="tree-card">
          <template #header>
            <div class="tree-header">
              <span>设备分组</span>
              <el-button link type="primary" icon="Plus" @click="handleAddRoot()" v-hasPermi="['iot:deviceGroup:add']">新增根分组</el-button>
            </div>
            <el-input v-model="filterText" placeholder="请输入分组名称" clearable prefix-icon="Search" @input="handleFilter" />
          </template>
          <el-tree
            ref="groupTreeRef"
            v-loading="groupLoading"
            :data="groupTree"
            node-key="groupId"
            :props="treeProps"
            :filter-node-method="filterGroupNode"
            highlight-current
            default-expand-all
            :expand-on-click-node="false"
            @node-click="handleNodeClick"
          >
            <template #default="{ data }">
              <span class="tree-node">
                <span class="tree-node-label">{{ data.groupName }}</span>
                <el-tag size="small" type="info">{{ data.deviceCount ?? 0 }}</el-tag>
                <span class="tree-node-actions">
                  <el-button link type="primary" icon="Plus" @click.stop="handleAddChild(data)" v-hasPermi="['iot:deviceGroup:add']" />
                  <el-button link type="primary" icon="Edit" @click.stop="handleUpdate(data)" v-hasPermi="['iot:deviceGroup:edit']" />
                  <el-button link type="danger" icon="Delete" @click.stop="handleDelete(data)" v-hasPermi="['iot:deviceGroup:remove']" />
                </span>
              </span>
            </template>
          </el-tree>
          <el-empty v-if="!groupLoading && groupTree.length === 0" description="暂无分组" :image-size="60" />
        </el-card>
      </el-col>

      <!-- 右：分组内设备 -->
      <el-col :span="18">
        <el-form :model="deviceQuery" ref="deviceQueryRef" label-width="100px" class="list-search">
          <el-row :gutter="16">
            <el-col :span="8">
              <el-form-item label="设备编码" prop="deviceCode">
                <el-input v-model="deviceQuery.deviceCode" placeholder="请输入设备编码" clearable @keyup.enter="handleDeviceQuery()" />
              </el-form-item>
            </el-col>
            <el-col :span="8">
              <el-form-item label="设备名称" prop="deviceName">
                <el-input v-model="deviceQuery.deviceName" placeholder="请输入设备名称" clearable @keyup.enter="handleDeviceQuery()" />
              </el-form-item>
            </el-col>
            <el-col :span="8" class="search-actions">
              <el-form-item>
                <el-button type="primary" icon="Search" @click="handleDeviceQuery()">搜索</el-button>
                <el-button icon="Refresh" @click="resetDeviceQuery()">重置</el-button>
              </el-form-item>
            </el-col>
          </el-row>
        </el-form>

        <el-row :gutter="10" class="mb8">
          <el-col :span="4">
            <el-checkbox v-model="deviceQuery.includeSubGroup" @change="handleDeviceQuery()">包含子分组</el-checkbox>
          </el-col>
          <el-col :span="20" class="device-actions">
            <el-button
              type="primary"
              plain
              icon="Plus"
              :disabled="deviceMultiple || !currentGroup"
              @click="openAssign(false)"
              v-hasPermi="['iot:deviceGroup:assign']"
              >挂入分组</el-button
            >
            <el-button
              type="danger"
              plain
              icon="Remove"
              :disabled="deviceMultiple || !currentGroup"
              @click="openAssign(true)"
              v-hasPermi="['iot:deviceGroup:assign']"
              >移出分组</el-button
            >
          </el-col>
        </el-row>

        <el-alert
          v-if="currentGroup"
          type="info"
          :closable="false"
          class="mb8"
          :title="`当前分组：${currentGroup.groupName}（已挂载 ${currentGroup.deviceCount ?? 0} 台设备）`"
        />
        <el-alert v-else type="warning" :closable="false" class="mb8" title="请先在左侧选择一个分组，再进行设备挂载/移出" />

        <el-table v-loading="deviceLoading" :data="deviceList" @selection-change="handleDeviceSelectionChange" show-overflow-tooltip>
          <el-table-column type="selection" width="55" align="center" />
          <el-table-column label="序号" type="index" width="60" align="center" />
          <el-table-column label="设备编码" align="center" prop="deviceCode" min-width="140" />
          <el-table-column label="设备名称" align="center" prop="deviceName" min-width="120" />
          <el-table-column label="所属产品" align="center" prop="productName" min-width="120" />
          <el-table-column label="协议类型" align="center" prop="protocolType" min-width="90">
            <template #default="scope">
              <dict-tag :options="iot_protocol_type" :value="scope.row.protocolType" />
            </template>
          </el-table-column>
          <el-table-column label="连接状态" align="center" prop="connectionStatus" min-width="90">
            <template #default="scope">
              <dict-tag :options="iot_connection_status" :value="scope.row.connectionStatus" />
            </template>
          </el-table-column>
          <el-table-column label="生命周期" align="center" prop="status" min-width="90">
            <template #default="scope">
              <dict-tag :options="iot_device_status" :value="scope.row.status" />
            </template>
          </el-table-column>
        </el-table>

        <pagination
          v-show="deviceTotal > 0"
          :total="deviceTotal"
          v-model:page="deviceQuery.pageNum"
          v-model:limit="deviceQuery.pageSize"
          @pagination="getDeviceList"
        />
      </el-col>
    </el-row>

    <!-- 分组新增/修改 -->
    <el-dialog :title="title" v-model="open" width="600px" append-to-body>
      <el-form ref="groupRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="父分组" prop="parentId">
          <el-tree-select
            v-model="form.parentId"
            :data="parentOptions"
            :props="treeSelectProps"
            placeholder="请选择父分组（根分组请选择「顶级分组」）"
            check-strictly
            clearable
            style="width: 100%"
          />
        </el-form-item>
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="分组编码" prop="groupCode">
              <el-input v-model="form.groupCode" placeholder="如 LINE_A" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="分组名称" prop="groupName">
              <el-input v-model="form.groupName" placeholder="请输入分组名称" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="显示顺序" prop="orderNum">
              <el-input-number v-model="form.orderNum" :min="0" controls-position="right" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="状态" prop="status">
              <el-radio-group v-model="form.status">
                <el-radio v-for="dict in sys_normal_disable" :key="dict.value" :value="dict.value">{{ dict.label }}</el-radio>
              </el-radio-group>
            </el-form-item>
          </el-col>
        </el-row>
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

    <!-- 批量挂入/移出分组 -->
    <el-dialog :title="assignForm.remove ? '移出分组' : '挂入分组'" v-model="assignOpen" width="560px" append-to-body>
      <el-form ref="assignRef" :model="assignForm" :rules="assignRules" label-width="100px">
        <el-form-item label="已选设备">
          <span>共 {{ selectedDeviceIds.length }} 台</span>
        </el-form-item>
        <el-form-item label="目标分组" prop="groupId">
          <el-tree-select
            v-model="assignForm.groupId"
            :data="groupTree"
            :props="treeSelectProps"
            placeholder="请选择目标分组"
            check-strictly
            clearable
            style="width: 100%"
          />
        </el-form-item>
        <el-alert
          :type="assignForm.remove ? 'warning' : 'info'"
          :closable="false"
          :title="assignForm.remove ? '移出后这些设备将不再属于目标分组（其它分组归属不受影响）。' : '挂入为幂等操作：设备已在该分组时不会重复挂载。'"
        />
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button type="primary" :loading="submitLoading" @click="submitAssign">确 定</el-button>
          <el-button @click="assignOpen = false">取 消</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts" name="IotDeviceGroup">
import { addDeviceGroup, assignDeviceGroup, delDeviceGroup, getDeviceGroup, listDeviceGroup, updateDeviceGroup } from '@/api/iot/deviceGroup';
import type { DeviceGroupForm, DeviceGroupQuery, DeviceGroupVO } from '@/api/iot/deviceGroup/types';
import { listDevice } from '@/api/iot/device';
import type { DeviceQuery, DeviceVO } from '@/api/iot/device/types';
import type { FormInstance } from 'element-plus';
import { useCrud } from '@/composables/useCrud';

const { proxy } = getCurrentInstance() as ComponentInternalInstance;
const { sys_normal_disable, iot_protocol_type, iot_connection_status, iot_device_status } = toRefs<any>(
  proxy.useDict('sys_normal_disable', 'iot_protocol_type', 'iot_connection_status', 'iot_device_status')
);

const groupRef = ref<FormInstance>();
const assignRef = ref<FormInstance>();
const groupTreeRef = ref<any>(null);
const filterText = ref('');
const currentGroup = ref<DeviceGroupVO | null>(null);
const assignOpen = ref(false);
const submitLoading = ref(false);

/** 分组树节点字段映射（el-tree-select 支持 value 字段，element-plus 的 TreeOptionProps 类型未收录） */
const treeProps = { label: 'groupName', children: 'children' };
const treeSelectProps = { value: 'groupId', label: 'groupName', children: 'children' };

const groupQuery = ref<DeviceGroupQuery>({ pageNum: 1, pageSize: 10, groupNameLike: null, status: null });

const deviceQuery = ref<DeviceQuery>({
  pageNum: 1,
  pageSize: 10,
  deviceCode: null,
  deviceName: null,
  groupId: null,
  includeSubGroup: true
});

const assignForm = ref<{ groupId: string | number | null; remove: boolean }>({ groupId: null, remove: false });

const rules = {
  groupCode: [{ required: true, message: '分组编码不能为空', trigger: 'blur' }],
  groupName: [{ required: true, message: '分组名称不能为空', trigger: 'blur' }]
};

const assignRules = {
  groupId: [{ required: true, message: '目标分组不能为空', trigger: 'change' }]
};

/** 分组 CRUD：后端返回平铺数组（非分页），包装为 useCrud 标准形状 */
const {
  loading: groupLoading,
  list: groupFlat,
  open,
  title,
  form,
  getList: getGroupList,
  submitForm,
  cancel,
  handleAdd: rawHandleAdd,
  handleUpdate: rawHandleUpdate,
  handleDelete
} = useCrud<DeviceGroupVO, DeviceGroupQuery>({
  apis: {
    list: (query) =>
      listDeviceGroup(query).then((res: any) => {
        const rows = (res.data || []) as DeviceGroupVO[];
        return { rows, total: rows.length };
      }),
    get: getDeviceGroup,
    add: addDeviceGroup,
    update: updateDeviceGroup,
    del: delDeviceGroup
  },
  idField: 'groupId',
  entityName: '设备分组',
  formRef: groupRef,
  queryParams: groupQuery,
  initForm: () => ({
    groupId: null,
    parentId: '0',
    groupCode: null,
    groupName: null,
    orderNum: 0,
    status: '0',
    remark: null
  })
});

/** 平铺分组 → 树（handleTree 由 RuoYi 全局挂载） */
const groupTree = computed<DeviceGroupVO[]>(() => proxy.handleTree(groupFlat.value, 'groupId', 'parentId') || []);

/** 父级下拉：新增时全量；修改时排除自身及其子树（避免形成环）。顶层「顶级分组」对应 parentId=0 */
const parentOptions = computed<DeviceGroupVO[]>(() => {
  const editingId = (form.value as DeviceGroupForm).groupId;
  const nodes = editingId ? excludeSubtree(groupTree.value, String(editingId)) : groupTree.value;
  const root = { groupId: '0', parentId: '-1', groupCode: 'ROOT', groupName: '顶级分组', children: nodes } as DeviceGroupVO;
  return [root];
});

/** 设备列表 CRUD（右侧分组内设备） */
const {
  loading: deviceLoading,
  list: deviceList,
  total: deviceTotal,
  ids: selectedDeviceIds,
  multiple: deviceMultiple,
  getList: getDeviceList,
  handleQuery: handleDeviceQuery,
  handleSelectionChange: handleDeviceSelectionChange
} = useCrud<DeviceVO, DeviceQuery>({
  apis: { list: listDevice },
  idField: 'deviceId',
  entityName: '设备',
  formRef: ref<FormInstance>(),
  queryParams: deviceQuery,
  initForm: () => ({})
});

/** 过滤分组树 */
function handleFilter(val: string) {
  groupTreeRef.value?.filter(val);
}

function filterGroupNode(value: string, data: DeviceGroupVO) {
  if (!value) return true;
  return data.groupName?.toLowerCase().includes(value.toLowerCase()) ?? false;
}

/** 递归剔除指定节点及其子树 */
function excludeSubtree(nodes: DeviceGroupVO[], excludeId: string): DeviceGroupVO[] {
  return nodes
    .filter((n) => String(n.groupId) !== excludeId)
    .map((n) => ({ ...n, children: n.children ? excludeSubtree(n.children, excludeId) : [] }));
}

/** 选中树节点 → 过滤右侧设备 */
function handleNodeClick(data: DeviceGroupVO) {
  currentGroup.value = data;
  deviceQuery.value.groupId = data.groupId;
  handleDeviceQuery();
}

function resetDeviceQuery() {
  proxy.resetForm('deviceQueryRef');
  deviceQuery.value.groupId = currentGroup.value?.groupId ?? null;
  handleDeviceQuery();
}

function handleAddRoot() {
  rawHandleAdd(() => {
    (form.value as DeviceGroupForm).parentId = '0';
  });
}

function handleAddChild(row: DeviceGroupVO) {
  rawHandleAdd(() => {
    (form.value as DeviceGroupForm).parentId = row.groupId;
  });
}

/** 修改分组（树节点入口；表单父级下拉已排除自身子树，避免形成环） */
function handleUpdate(row: DeviceGroupVO) {
  return rawHandleUpdate(row);
}

function openAssign(remove: boolean) {
  assignForm.value = { groupId: currentGroup.value?.groupId ?? null, remove };
  assignOpen.value = true;
}

function submitAssign() {
  assignRef.value?.validate((valid: boolean) => {
    if (!valid) return;
    submitLoading.value = true;
    assignDeviceGroup({
      deviceIds: selectedDeviceIds.value,
      groupId: assignForm.value.groupId as string,
      remove: assignForm.value.remove
    })
      .then(() => {
        proxy.$modal.msgSuccess(assignForm.value.remove ? '已移出分组' : '已挂入分组');
        assignOpen.value = false;
        // 分组设备数与设备列表都要刷新
        getGroupList();
        getDeviceList();
      })
      .finally(() => {
        submitLoading.value = false;
      });
  });
}

getGroupList();
</script>

<style scoped>
.tree-card :deep(.el-card__header) {
  padding: 12px;
}
.tree-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
  font-weight: 600;
}
.tree-node {
  display: flex;
  align-items: center;
  gap: 6px;
  width: 100%;
  padding-right: 4px;
}
.tree-node-label {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.tree-node-actions {
  margin-left: auto;
  display: none;
}
.tree-node:hover .tree-node-actions {
  display: inline-flex;
}
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
.device-actions {
  text-align: right;
}
</style>
