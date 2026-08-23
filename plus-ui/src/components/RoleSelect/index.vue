<template>
  <div>
    <el-dialog v-model="roleDialog.visible.value" :title="roleDialog.title.value" width="80%" append-to-body>
      <transition :enter-active-class="proxy?.animate.searchAnimate.enter" :leave-active-class="proxy?.animate.searchAnimate.leave">
        <div v-show="showSearch" class="mb-[10px]">
          <el-card shadow="hover">
            <el-form ref="queryFormRef" :model="queryParams" :inline="true">
              <el-form-item label="角色名称" prop="roleName">
                <el-input v-model="queryParams.roleName" placeholder="请输入角色名称" clearable @keyup.enter="handleQuery" />
              </el-form-item>
              <el-form-item label="权限字符" prop="roleKey">
                <el-input v-model="queryParams.roleKey" placeholder="请输入权限字符" clearable @keyup.enter="handleQuery" />
              </el-form-item>

              <el-form-item>
                <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
                <el-button icon="Refresh" @click="resetQuery">重置</el-button>
              </el-form-item>
            </el-form>
          </el-card>
        </div>
      </transition>

      <el-card shadow="hover">
        <template #header>
          <el-tag v-for="role in selectRoleList" :key="role.roleId" closable style="margin: 2px" @close="handleCloseTag(role)">
            {{ role.roleName }}
          </el-tag>
        </template>

        <el-table
          ref="tableRef"
          height="400px"
          border
          v-loading="loading"
          :data="roleList"
          row-key="roleId"
          highlight-current-row
          @select="handleSelect"
          @select-all="handleSelectAll"
        >
          <el-table-column type="selection" width="50" align="center" />
          <el-table-column prop="roleName" label="角色名称" show-overflow-tooltip />
          <el-table-column prop="roleKey" label="权限字符" show-overflow-tooltip />
          <el-table-column prop="roleSort" label="显示顺序" width="100" />
          <el-table-column label="状态" align="center" width="100">
            <template #default="scope">
              <dict-tag :options="sys_normal_disable" :value="scope.row.status"></dict-tag>
            </template>
          </el-table-column>
          <el-table-column prop="createTime" label="创建时间" align="center">
            <template #default="scope">
              <span>{{ proxy.parseTime(scope.row.createTime) }}</span>
            </template>
          </el-table-column>
        </el-table>

        <pagination
          v-if="total > 0"
          v-model:total="total"
          v-model:page="queryParams.pageNum"
          v-model:limit="queryParams.pageSize"
          @pagination="pageList"
        />
      </el-card>
      <template #footer>
        <el-button @click="close">取消</el-button>
        <el-button type="primary" @click="confirm">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { RoleVO, RoleQuery } from '@/api/system/role/types';
import useDialog from '@/composables/useDialog';
import api from '@/api/system/role';
interface PropType {
  modelValue?: RoleVO[] | RoleVO | undefined;
  multiple?: boolean;
  data?: string | number | (string | number)[];
}
const prop = withDefaults(defineProps<PropType>(), {
  multiple: true,
  modelValue: undefined,
  data: undefined
});
const emit = defineEmits(['update:modelValue', 'confirmCallBack']);

const router = useRouter();
const { proxy } = getCurrentInstance() as ComponentInternalInstance;
const { sys_normal_disable } = toRefs<any>(proxy?.useDict('sys_normal_disable'));

const roleList = ref<RoleVO[]>();
const loading = ref(true);
const showSearch = ref(true);
const total = ref(0);
const dateRange = ref<[DateModelType, DateModelType]>(['', '']);
const selectRoleList = ref<RoleVO[]>([]);

const roleDialog = useDialog({
  title: '角色选择'
});

const queryFormRef = ref<ElFormInstance>();
const tableRef = ref<ElTableInstance>();

const queryParams = ref<RoleQuery>({
  pageNum: 1,
  pageSize: 10,
  roleName: '',
  roleKey: '',
  status: ''
});

const defaultSelectRoleIds = computed(() => computedIds(prop.data));

const confirm = () => {
  emit('update:modelValue', selectRoleList.value);
  emit('confirmCallBack', selectRoleList.value);
  roleDialog.closeDialog();
};

const computedIds = (data) => {
  if (data instanceof Array) {
    return [...data];
  } else if (typeof data === 'string') {
    return data.split(',');
  } else if (typeof data === 'number') {
    return [data];
  } else {
    console.warn('<RoleSelect> The data type of data should be array or string or number, but I received other');
    return [];
  }
};

/**
 * 查询角色列表
 */
const getList = () => {
  loading.value = true;
  api.listRole(proxy?.addDateRange(queryParams.value, dateRange.value)).then((res) => {
    roleList.value = res.rows;
    total.value = res.total;
    loading.value = false;
  });
};
/** 当前页中已选中(跨页保持)的行恢复勾选态 */
const syncRowSelection = () => {
  nextTick(() => {
    roleList.value?.forEach((row) => {
      if (selectRoleList.value.some((role) => role.roleId === row.roleId)) {
        tableRef.value?.toggleRowSelection(row, true);
      }
    });
  });
};

const pageList = async () => {
  await getList();
  syncRowSelection();
};
/**
 * 搜索按钮操作
 */
const handleQuery = () => {
  queryParams.value.pageNum = 1;
  getList();
};

/** 重置 */
const resetQuery = () => {
  dateRange.value = ['', ''];
  queryFormRef.value?.resetFields();
  handleQuery();
};

/** 单行勾选/取消（维护跨页选中集合） */
const handleSelect = (selection: RoleVO[], row: RoleVO) => {
  const checked = selection.some((item) => item.roleId === row.roleId);
  if (checked) {
    if (!prop.multiple) {
      // 单选模式：取消其余勾选并清空已选集合
      roleList.value?.forEach((item) => {
        if (item.roleId !== row.roleId) tableRef.value?.toggleRowSelection(item, false);
      });
      selectRoleList.value = [];
    }
    selectRoleList.value.push(row);
  } else {
    selectRoleList.value = selectRoleList.value.filter((item) => {
      return item.roleId !== row.roleId;
    });
  }
};
/** 当前页全选/全不选 */
const handleSelectAll = (selection: RoleVO[]) => {
  const rows = roleList.value ?? [];
  if (selection.length > 0) {
    rows.forEach((row) => {
      if (!selectRoleList.value.some((item) => item.roleId === row.roleId)) {
        selectRoleList.value.push(row);
      }
    });
  } else {
    selectRoleList.value = selectRoleList.value.filter((item) => {
      return !rows.some((row) => row.roleId === item.roleId);
    });
  }
};

const handleCloseTag = (user: RoleVO) => {
  const roleId = user.roleId;
  // 使用split删除用户
  const index = selectRoleList.value.findIndex((item) => item.roleId === roleId);
  const row = selectRoleList.value[index];
  tableRef.value?.toggleRowSelection(row, false);
  selectRoleList.value.splice(index, 1);
};
/**
 * 初始化选中数据
 */
const initSelectRole = async () => {
  if (defaultSelectRoleIds.value.length > 0) {
    const { data } = await api.optionSelect(defaultSelectRoleIds.value);
    selectRoleList.value = data;
    const users = roleList.value?.filter((item) => {
      return defaultSelectRoleIds.value.includes(String(item.roleId));
    });
    await nextTick(() => {
      users?.forEach((row) => tableRef.value?.toggleRowSelection(row, true));
    });
  }
};
const close = () => {
  roleDialog.closeDialog();
};
watch(
  () => roleDialog.visible.value,
  (newValue: boolean) => {
    if (newValue) {
      initSelectRole();
    } else {
      tableRef.value?.clearSelection();
      resetQuery();
      selectRoleList.value = [];
    }
  }
);
onMounted(() => {
  getList(); // 初始化列表数据
});

defineExpose({
  open: roleDialog.openDialog,
  close: roleDialog.closeDialog
});
</script>
