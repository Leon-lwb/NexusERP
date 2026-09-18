<template>
  <div class="p-2">
    <el-card shadow="never">
      <!-- 治理说明：授权只针对公司章 -->
      <el-alert
        class="mb8"
        type="info"
        :closable="false"
        show-icon
        title="仅公司章需要在此配置授权"
        description="个人章仅归属人本人可用，无需也无法在此授权。一条授权的实际效力由「被授权用户/部门 + 业务场景 + 有效期 + 状态」共同决定，服务端在每次用章时实时判定。"
      />

      <!-- 搜索 -->
      <el-form v-show="showSearch" ref="queryRef" :model="queryParams" :inline="true">
        <el-form-item label="印章" prop="sealId">
          <el-select v-model="queryParams.sealId" placeholder="全部" clearable filterable style="width: 200px">
            <el-option v-for="item in sealOptions" :key="item.sealId" :label="sealOptionLabel(item)" :value="item.sealId" />
          </el-select>
        </el-form-item>
        <el-form-item label="被授权用户" prop="granteeUserId">
          <el-input
            v-model="queryGranteeUserName"
            placeholder="请选择被授权用户"
            readonly
            clearable
            style="width: 200px"
            @clear="onClearQueryGrantee"
          >
            <template #append>
              <el-button icon="Search" @click="queryUserSelectRef?.open()" />
            </template>
          </el-input>
        </el-form-item>
        <el-form-item label="被授权部门" prop="granteeDeptId">
          <el-tree-select
            v-model="queryParams.granteeDeptId"
            :data="deptOptions"
            :props="{ value: 'id', label: 'label', children: 'children' } as any"
            value-key="id"
            placeholder="请选择部门"
            check-strictly
            clearable
            filterable
            style="width: 200px"
          />
        </el-form-item>
        <el-form-item label="状态" prop="status">
          <el-select v-model="queryParams.status" placeholder="全部" clearable style="width: 160px">
            <el-option v-for="item in GRANT_STATUS_OPTIONS" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
          <el-button icon="Refresh" @click="resetQuery">重置</el-button>
        </el-form-item>
      </el-form>

      <el-row :gutter="10" class="mb8">
        <el-col :span="1.5">
          <el-button v-hasPermi="['system:sign:grant:add']" type="primary" plain icon="Plus" @click="handleAdd()">新增授权</el-button>
        </el-col>
        <right-toolbar v-model:showSearch="showSearch" @queryTable="getList"></right-toolbar>
      </el-row>

      <el-table v-loading="loading" :data="list">
        <el-table-column label="印章名称" align="center" prop="sealName" show-overflow-tooltip />
        <el-table-column label="授权对象" align="center" min-width="160" :show-overflow-tooltip="false">
          <template #default="scope">{{ granteeText(scope.row) }}</template>
        </el-table-column>
        <el-table-column label="限定场景" align="center" width="120">
          <template #default="scope">{{ bizTypeText(scope.row.bizType) }}</template>
        </el-table-column>
        <el-table-column label="生效时间" align="center" width="120">
          <template #default="scope">{{ scope.row.validFrom ? parseTime(scope.row.validFrom, '{y}-{m}-{d}') : '不限' }}</template>
        </el-table-column>
        <el-table-column label="失效时间" align="center" width="120">
          <template #default="scope">{{ scope.row.validTo ? parseTime(scope.row.validTo, '{y}-{m}-{d}') : '不限' }}</template>
        </el-table-column>
        <el-table-column label="状态" align="center" prop="status" width="90">
          <template #default="scope">
            <el-tag :type="statusTag(scope.row.status)">{{ statusText(scope.row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="备注" align="center" prop="remark" show-overflow-tooltip />
        <el-table-column label="操作" align="center" width="160" class-name="small-padding fixed-width" :show-overflow-tooltip="false">
          <template #default="scope">
            <el-button v-hasPermi="['system:sign:grant:edit']" link type="primary" icon="Edit" @click="handleUpdate(scope.row)">修改</el-button>
            <el-button v-hasPermi="['system:sign:grant:remove']" link type="danger" icon="Delete" @click="handleDelete(scope.row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <pagination v-show="total > 0" v-model:page="queryParams.pageNum" v-model:limit="queryParams.pageSize" :total="total" @pagination="getList" />
    </el-card>

    <!-- 添加或修改用章授权对话框 -->
    <el-dialog v-model="open" :title="title" width="640px" append-to-body @close="cancel">
      <el-form ref="grantFormRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="印章" prop="sealId">
          <el-select v-model="form.sealId" placeholder="请选择公司章" filterable style="width: 100%">
            <!-- 已销毁印章置灰：后端会拒绝为其新增授权，但仍需展示名称以正确回显历史授权 -->
            <el-option
              v-for="item in sealOptions"
              :key="item.sealId"
              :label="sealOptionLabel(item)"
              :value="item.sealId"
              :disabled="item.status === '3'"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="授权方式">
          <!-- 编辑时锁定授权方式：后端 updateById 忽略 null 字段，改类型无法清空旧授权对象，
               会留下一条「既匹配用户、又匹配部门」的授权，放大实际授权范围 -->
          <el-radio-group v-model="grantMode" :disabled="isEdit" @change="onGrantModeChange">
            <el-radio value="user">按用户</el-radio>
            <el-radio value="dept">按部门</el-radio>
          </el-radio-group>
          <div v-if="isEdit" class="text-gray-400 text-xs">授权对象类型创建后不可变更，如需改为另一种授权对象请删除后重新新增</div>
        </el-form-item>
        <el-form-item v-if="grantMode === 'user'" label="被授权用户" prop="granteeUserId">
          <el-input v-model="granteeUserName" placeholder="请选择被授权用户" readonly>
            <template #append>
              <el-button icon="Search" @click="userSelectRef?.open()" />
            </template>
          </el-input>
        </el-form-item>
        <el-form-item v-else label="被授权部门" prop="granteeDeptId">
          <el-tree-select
            v-model="form.granteeDeptId"
            :data="deptOptions"
            :props="{ value: 'id', label: 'label', children: 'children' } as any"
            value-key="id"
            placeholder="请选择被授权部门"
            check-strictly
            filterable
            style="width: 100%"
          />
          <div class="text-gray-400 text-xs">部门级授权对该部门下的全部用户生效</div>
        </el-form-item>
        <el-form-item label="限定场景" prop="bizType">
          <el-select v-model="form.bizType" placeholder="不限" clearable style="width: 100%">
            <el-option v-for="item in BIZ_TYPE_OPTIONS" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
          <div class="text-gray-400 text-xs">留空表示不限业务场景</div>
        </el-form-item>
        <el-form-item label="生效时间" prop="validFrom">
          <el-date-picker v-model="form.validFrom" type="date" value-format="YYYY-MM-DD" placeholder="不限（立即生效）" style="width: 100%" />
        </el-form-item>
        <el-form-item label="失效时间" prop="validTo">
          <el-date-picker v-model="form.validTo" type="date" value-format="YYYY-MM-DD" placeholder="不限（长期有效）" style="width: 100%" />
        </el-form-item>
        <el-form-item label="状态" prop="status">
          <el-radio-group v-model="form.status">
            <el-radio value="1">启用</el-radio>
            <el-radio value="0">停用</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="备注" prop="remark">
          <el-input v-model="form.remark" type="textarea" :rows="3" maxlength="255" placeholder="请输入内容" />
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button type="primary" @click="submitForm">确 定</el-button>
          <el-button @click="cancel">取 消</el-button>
        </div>
      </template>
    </el-dialog>

    <!-- 用户选择（复用共享组件，单选）；两个实例分别服务搜索栏与表单 -->
    <UserSelect ref="queryUserSelectRef" :multiple="false" @confirm-call-back="onPickQueryGrantee" />
    <UserSelect ref="userSelectRef" :multiple="false" @confirm-call-back="onPickGrantee" />
  </div>
</template>

<script setup name="SignGrant" lang="ts">
import { addGrant, delGrant, listGrant, updateGrant, type GrantForm, type GrantQuery, type GrantVO } from '@/api/system/sign/grant';
import { listSeal, type SealVO } from '@/api/system/sign/seal';
import { deptTreeSelect } from '@/api/system/user';
import type { DeptTreeVO } from '@/api/system/dept/types';
import type { UserVO } from '@/api/system/user/types';
import type { FormInstance } from 'element-plus';
import { useCrud } from '@/composables/useCrud';
import UserSelect from '@/components/UserSelect/index.vue';

const { proxy } = getCurrentInstance() as ComponentInternalInstance;

/** 授权状态（后端 EsSealGrant.status） */
const GRANT_STATUS_OPTIONS = [
  { value: '1', label: '启用' },
  { value: '0', label: '停用' }
];
/** 业务场景标签（后端 SignBizType 全集：列表可能含其它模块写入的记录，展示需全量映射） */
const BIZ_TYPE_TEXT: Record<string, string> = {
  APPROVAL: '流程审批',
  SOP_VIEW: 'SOP查阅',
  NCR_DISPOSITION: 'NCR处置',
  SO_CONTRACT: '销售合同',
  PU_CONTRACT: '采购合同'
};
/** 授权可选场景（留空=不限；不含 SOP_VIEW / NCR_DISPOSITION —— 服务端用章入口未纳入本期授权范围） */
const BIZ_TYPE_OPTIONS = [
  { value: 'APPROVAL', label: '流程审批' },
  { value: 'SO_CONTRACT', label: '销售合同' },
  { value: 'PU_CONTRACT', label: '采购合同' }
];

const queryRef = ref<FormInstance>();
const grantFormRef = ref<FormInstance>();
const queryUserSelectRef = ref<any>();
const userSelectRef = ref<any>();
/** 授权方式：按用户 / 按部门（后端两个字段二选一，用前端模式收敛表单） */
const grantMode = ref<'user' | 'dept'>('user');
/** 表单中已选被授权用户昵称（UserSelect 只回调对象，需自行回显） */
const granteeUserName = ref('');
/** 搜索栏中已选被授权用户昵称 */
const queryGranteeUserName = ref('');
/** 公司章下拉候选 */
const sealOptions = ref<SealVO[]>([]);
/** 部门下拉树（搜索栏 + 表单共用） */
const deptOptions = ref<DeptTreeVO[]>([]);

const queryParams = ref<GrantQuery>({
  pageNum: 1,
  pageSize: 10,
  sealId: undefined,
  granteeUserId: undefined,
  granteeDeptId: undefined,
  status: ''
});

const GRANT_STATUS_TEXT: Record<string, string> = { '0': '停用', '1': '启用' };
const GRANT_STATUS_TAG: Record<string, ElTagType> = { '0': 'info', '1': 'success' };

const statusText = (value?: string) => GRANT_STATUS_TEXT[value ?? ''] ?? '-';
const statusTag = (value?: string) => GRANT_STATUS_TAG[value ?? ''] ?? 'info';
/** 限定场景：bizType 为空即不限 */
const bizTypeText = (value?: string) => (value ? (BIZ_TYPE_TEXT[value] ?? value) : '不限');

/** 授权对象：按用户授权显示昵称，按部门授权显示「部门：xxx」 */
const granteeText = (row: GrantVO) => {
  if (row.granteeUserId != null) {
    return row.granteeUserName || `用户 #${row.granteeUserId}`;
  }
  if (row.granteeDeptId != null) {
    return `部门：${row.granteeDeptName || row.granteeDeptId}`;
  }
  return '—';
};

/** 下拉标签附带状态，避免管理员把草稿/停用印章当成生效印章选中 */
const sealOptionLabel = (seal: SealVO) => (seal.status === '1' ? seal.sealName : `${seal.sealName}（${statusText(seal.status)}）`);

/** 日期回填：接口返回 yyyy-MM-dd HH:mm:ss，日期选择器的值格式为 YYYY-MM-DD，需截断到日期部分 */
const toDateOnly = (value?: string) => (value ? String(value).slice(0, 10) : undefined);

const {
  loading,
  list,
  total,
  open,
  title,
  showSearch,
  form,
  getList,
  handleQuery,
  reset,
  submitForm,
  cancel,
  handleAdd: rawHandleAdd
} = useCrud<GrantVO & GrantForm, GrantQuery>({
  apis: { list: listGrant, add: addGrant, update: updateGrant, del: delGrant },
  idField: 'grantId',
  entityName: '用章授权',
  formRef: grantFormRef,
  queryParams,
  initForm: () => ({
    grantId: undefined,
    sealId: undefined,
    granteeUserId: undefined,
    granteeDeptId: undefined,
    bizType: '',
    validFrom: undefined,
    validTo: undefined,
    status: '1',
    remark: ''
  }),
  // 按用户/按部门二选一：提交前必须清空另一侧的字段（后端只校验「至少一项」，同时提交会得到一条既匹配用户又匹配部门的授权）
  beforeSubmit: (data) => {
    if (grantMode.value === 'user') {
      data.granteeDeptId = undefined;
    } else {
      data.granteeUserId = undefined;
    }
  },
  onReset: () => {
    grantMode.value = 'user';
    granteeUserName.value = '';
  }
});

const isEdit = computed(() => form.value.grantId != null);

/** 必填规则随授权方式切换：按用户必填 granteeUserId，按部门必填 granteeDeptId */
const rules = computed<ElFormRules>(() => ({
  sealId: [{ required: true, message: '请选择印章', trigger: 'change' }],
  granteeUserId: grantMode.value === 'user' ? [{ required: true, message: '请选择被授权用户', trigger: 'change' }] : [],
  granteeDeptId: grantMode.value === 'dept' ? [{ required: true, message: '请选择被授权部门', trigger: 'change' }] : []
}));

/** 加载公司章候选（个人章不可授权；已销毁印章仍参与回显但不可选） */
const loadSealOptions = async () => {
  const { rows } = await listSeal({ pageNum: 1, pageSize: 100, sealType: '2' });
  sealOptions.value = rows ?? [];
};

/** 加载部门树 */
const loadDeptOptions = async () => {
  const { data } = await deptTreeSelect();
  deptOptions.value = data ?? [];
};

/** 切换授权方式时清空另一侧，避免界面上残留已失效的授权对象 */
const onGrantModeChange = () => {
  if (grantMode.value === 'user') {
    form.value.granteeDeptId = undefined;
  } else {
    form.value.granteeUserId = undefined;
    granteeUserName.value = '';
  }
};

const onPickGrantee = (users: UserVO[]) => {
  const user = users?.[0];
  if (!user) return;
  form.value.granteeUserId = user.userId;
  granteeUserName.value = user.nickName;
};

const onPickQueryGrantee = (users: UserVO[]) => {
  const user = users?.[0];
  if (!user) return;
  queryParams.value.granteeUserId = user.userId;
  queryGranteeUserName.value = user.nickName;
};

/** 清除搜索栏的被授权用户 */
const onClearQueryGrantee = () => {
  queryParams.value.granteeUserId = undefined;
  queryGranteeUserName.value = '';
};

/** 新增授权：默认按用户 + 启用 */
const handleAdd = () => rawHandleAdd();

/**
 * 修改授权：后端无单条详情接口，列表行已含全部可编辑字段，直接回填。
 * <p>granteeUserName / granteeDeptName 由列表的翻译结果带入，仅用于表单回显。</p>
 */
const handleUpdate = (row: GrantVO) => {
  reset();
  grantMode.value = row.granteeUserId != null ? 'user' : 'dept';
  granteeUserName.value = row.granteeUserName ?? '';
  form.value = {
    grantId: row.grantId,
    sealId: row.sealId,
    granteeUserId: row.granteeUserId,
    granteeDeptId: row.granteeDeptId,
    bizType: row.bizType ?? '',
    validFrom: toDateOnly(row.validFrom),
    validTo: toDateOnly(row.validTo),
    status: row.status ?? '1',
    remark: row.remark ?? ''
  };
  open.value = true;
  title.value = '修改用章授权';
};

/** 删除授权：确认文案带印章与被授权对象，便于管理员核对 */
const handleDelete = async (row: GrantVO) => {
  try {
    await proxy?.$modal.confirm(`是否确认删除印章「${row.sealName ?? row.sealId}」对「${granteeText(row)}」的用章授权？`);
  } catch {
    return;
  }
  await delGrant(row.grantId as string);
  proxy?.$modal.msgSuccess('删除成功');
  await getList();
};

/** 重置搜索条件（用户昵称不在查询参数里，需一并清空） */
const resetQuery = () => {
  proxy?.resetForm('queryRef');
  onClearQueryGrantee();
  queryParams.value.pageNum = 1;
  return getList();
};

onMounted(() => {
  getList();
  loadSealOptions();
  loadDeptOptions();
});
</script>
