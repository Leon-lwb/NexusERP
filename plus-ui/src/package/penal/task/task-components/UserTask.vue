<template>
  <div>
    <el-row>
      <h4><b>审批人设置</b></h4>
      <el-radio-group v-model="dataType" @change="changeDataType">
        <el-radio value="USERS">指定用户</el-radio>
        <el-radio value="ROLES">角色</el-radio>
        <el-radio value="DEPTS">部门</el-radio>
        <el-radio value="INITIATOR">发起人</el-radio>
        <el-radio value="MANAGER">部门经理</el-radio>
      </el-radio-group>
    </el-row>
    <el-row>
      <div v-if="dataType === 'USERS'" style="width: 100%">
        <el-tag v-for="userText in selectedUser.text" :key="userText" effect="plain">
          {{ userText }}
        </el-tag>
        <div class="element-drawer__button">
          <el-button size="small" type="primary" icon="el-icon-plus" @click="onSelectUsers()">添加用户</el-button>
        </div>
      </div>
      <div v-if="dataType === 'ROLES'" style="width: 100%">
        <el-select v-model="roleIds" multiple size="small" placeholder="请选择 角色" @change="changeSelectRoles">
          <el-option
            v-for="item in roleOptions"
            :key="item.roleId"
            :label="item.roleName"
            :value="`ROLE${item.roleId}`"
            :disabled="item.status === 1"
          >
          </el-option>
        </el-select>
      </div>
      <div v-if="dataType === 'DEPTS'" style="width: 100%">
        <el-tree-select
          v-model="deptIds"
          :data="deptTreeData"
          :props="deptTreeProps"
          show-checkbox
          default-expand-all
          multiple
          clearable
          checkStrictly
          :checkedKeys="deptIds"
          @check="nodeClick"
        >
        </el-tree-select>
      </div>
    </el-row>
    <el-row>
      <div v-show="showMultiFlog">
        <el-divider />
        <h4><b>多实例审批方式</b></h4>
        <el-row>
          <el-radio-group v-model="multiLoopType" @change="changeMultiLoopType()">
            <el-row><el-radio value="Null">无</el-radio></el-row>
            <el-row><el-radio value="SequentialMultiInstance">会签（需所有审批人同意）</el-radio></el-row>
            <el-row><el-radio value="ParallelMultiInstance">或签（一名审批人同意即可）</el-radio></el-row>
            <el-row><el-radio value="CustomMultiInstance">自定义会签条件</el-radio></el-row>
          </el-radio-group>
        </el-row>
        <el-row v-if="multiLoopType === 'CustomMultiInstance'">
          <el-input v-model="CustomCompletionCondition" clearable @change="updateLoopCondition" />
        </el-row>
        <el-row v-if="multiLoopType !== 'Null'">
          <el-tooltip content="开启后，实例需按顺序轮流审批" placement="top-start" @click.stop.prevent>
            <i class="header-icon el-icon-info"></i>
          </el-tooltip>
          <span class="custom-label">顺序审批：</span>
          <el-switch v-model="isSequential" @change="changeMultiLoopType()" />
        </el-row>
      </div>
    </el-row>
    <el-row>
      <el-divider />
      <h4><b>电子签章</b></h4>
      <el-row>
        <el-tooltip
          content="开启后，本节点审批办结时必须进行电子签章（二级认证），否则无法通过。签章失败会中止审批并回滚。"
          placement="top-start"
          @click.stop.prevent
        >
          <i class="header-icon el-icon-info"></i>
        </el-tooltip>
        <span class="custom-label">要求签章：</span>
        <el-switch v-model="needSign" active-text="是" inactive-text="否" @change="updateElementNeedSign()" />
      </el-row>
    </el-row>

    <!-- 候选用户弹窗 -->
    <el-dialog title="候选用户" v-model="userOpen" width="60%" append-to-body>
      <el-row type="flex" :gutter="20">
        <!--部门数据-->
        <el-col :span="7">
          <el-card shadow="never" style="height: 100%">
            <template #header>
              <span>部门列表</span>
            </template>
            <div class="head-container">
              <el-input
                v-model="deptName"
                placeholder="请输入部门名称"
                clearable
                size="small"
                prefix-icon="el-icon-search"
                style="margin-bottom: 20px"
              />
              <el-tree
                :data="deptOptions"
                :props="deptProps"
                :expand-on-click-node="false"
                :filter-node-method="filterNode"
                ref="tree"
                default-expand-all
                @node-click="handleNodeClick"
              />
            </div>
          </el-card>
        </el-col>
        <el-col :span="17">
          <el-table ref="multipleTable" height="600" :data="userTableList" border @selection-change="handleSelectionChange">
            <el-table-column type="selection" width="50" align="center" />
            <el-table-column label="用户名" align="center" prop="nickName" />
            <el-table-column label="部门" align="center" prop="dept.deptName" />
          </el-table>
          <pagination :total="userTotal" v-model:page="queryParams.pageNum" v-model:limit="queryParams.pageSize" @pagination="getUserList" />
        </el-col>
      </el-row>
      <template #footer>
        <el-button type="primary" @click="handleTaskUserComplete">确 定</el-button>
        <el-button @click="userOpen = false">取 消</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script lang="ts" setup>
import type { TreeOptionProps } from 'element-plus';
import { listUser, deptTreeSelect } from '@/api/system/user';
import { listRole } from '@/api/system/role';
import type { RoleQuery } from '@/api/system/role/types';

defineOptions({ name: 'UserTask' });
const props = defineProps({
  id: String,
  type: String
});

const userTaskForm = ref({
  dataType: '',
  assignee: '',
  candidateUsers: '',
  candidateGroups: '',
  text: ''
  // dueDate: '',
  // followUpDate: '',
  // priority: ''
});

const { proxy } = getCurrentInstance() as ComponentInternalInstance;
const bpmnElement = ref();
const multiLoopInstance = ref(null);
const bpmnInstances = () => (window as any)?.bpmnInstances;

const dateRange = ref<[DateModelType, DateModelType]>(['', '']);
const loading = ref(false);
const dataType = ref('USERS');
const selectedUser = ref({
  ids: [],
  text: []
});
const userOpen = ref(false);
const deptName = ref(undefined);
const deptOptions = ref<any[]>([]);
const deptProps = ref({
  label: 'label',
  children: 'children'
});
const deptTempOptions = ref<any[]>([]);
const userTableList = ref<any[]>([]);
const userTotal = ref(0);
const selectedUserDate = ref<any[]>([]);
const roleOptions = ref<any[]>([]);
const roleIds = ref<any[]>([]);
const deptTreeData = ref<any[]>([]);
const deptIds = ref<any[]>([]);
// el-tree-select 运行时支持通过 props.value 指定取值字段，但 TreeOptionProps 类型未声明该键，这里显式扩展
const deptTreeProps: TreeOptionProps & { value?: string } = { value: 'id', label: 'label', children: 'children' };
// 查询参数
const queryParams = ref({
  pageNum: 1,
  pageSize: 10,
  deptId: ''
});
const showMultiFlog = ref(false);
const isSequential = ref(false);
const multiLoopType = ref('Null');
const CustomCompletionCondition = ref('${nrOfCompletedInstances/nrOfInstances>=1}');
// 节点级扩展属性：该用户任务审批办结时是否强制电子签章（随 BPMN XML 持久化）
const needSign = ref(false);

const multipleTable = ref(null);
const tree = ref(null);

const resetTaskForm = () => {
  bpmnElement.value = bpmnInstances().bpmnElement;
  const bpmnElementObj = toRaw(bpmnElement.value?.businessObject);
  if (!bpmnElementObj) {
    return;
  }
  clearOptionsData();
  // 存量流程未设置该属性时读到 undefined（descriptor 刻意不声明 default），按「未配置」处理即 false。
  // 判空必须走字符串比较：undefined === true 为 false，String(undefined) 也不等于 'true'，不会报错。
  const needSignValue = bpmnElementObj['needSign'];
  needSign.value = needSignValue === true || String(needSignValue).toLowerCase() === 'true';
  dataType.value = bpmnElementObj['dataType'];
  if (dataType.value === 'USERS') {
    const userIdData = bpmnElementObj['candidateUsers'] || bpmnElementObj['assignee'];
    const userText = bpmnElementObj['text'] || [];
    if (userIdData && userIdData.toString().length > 0 && userText && userText.length > 0) {
      selectedUser.value.ids = userIdData?.toString().split(',');
      selectedUser.value.text = userText?.split(',');
    }
    if (selectedUser.value.ids.length > 1) {
      showMultiFlog.value = true;
    }
  } else if (dataType.value === 'ROLES') {
    getRoleOptions();
    const roleIdData = bpmnElementObj['candidateGroups'] || [];
    if (roleIdData && roleIdData.length > 0) {
      roleIds.value = roleIdData.split(',');
    }
    showMultiFlog.value = true;
  } else if (dataType.value === 'DEPTS') {
    getDeptTreeData();
    const deptIdData = bpmnElementObj['candidateGroups'] || [];
    if (deptIdData && deptIdData.length > 0) {
      deptIds.value = deptIdData.split(',');
    }
    showMultiFlog.value = true;
  }
  getElementLoop(bpmnElementObj);
};
/**
 * 清空选项数据
 */
const clearOptionsData = () => {
  selectedUser.value.ids = [];
  selectedUser.value.text = [];
  roleIds.value = [];
  deptIds.value = [];
};
// 完成条件
const updateLoopCondition = (condition) => {};
/**
 * 更新节点数据
 */
const updateElementTask = () => {
  const taskAttr = Object.create(null);
  for (const key in userTaskForm.value) {
    taskAttr[key] = userTaskForm.value[key] || null;
  }
  bpmnInstances().modeling.updateProperties(toRaw(bpmnElement.value), taskAttr);
};
/**
 * 更新节点的签章要求：写入 businessObject 的 needSign 扩展属性，保存时序列化为
 * flowable:needSign="true"（开启）或 flowable:needSign="false"（关闭）。
 *
 * <p><b>注意：descriptor 里刻意不给 needSign 声明 default。</b>bpmn-moddle 会省略「等于默认值」的属性，
 * 若声明 default: false，把开关从「是」拨回「否」时属性会被整个删掉，后端只能看到「未配置」，
 * 于是继续回退到流程实例变量——即「本节点不签章」这个明确意图会丢失。不声明 default 时：
 * 从未设置过的节点读到 undefined、序列化时照样不写（不会污染存量流程 XML），
 * 而显式关闭会写出 ="false" 并被后端视为权威结论。改动前请先看后端 ApprovalSignGate 的三级优先级。</p>
 */
const updateElementNeedSign = () => {
  const element = toRaw(bpmnElement.value);
  bpmnInstances().modeling.updateModdleProperties(element, toRaw(element.businessObject), { needSign: needSign.value });
};
/**
 * 查询部门下拉树结构
 */
const getDeptOptions = () => {
  return new Promise<void>((resolve, reject) => {
    if (!deptOptions.value || deptOptions.value.length <= 0) {
      deptTreeSelect().then((response) => {
        deptTempOptions.value = response.data;
        deptOptions.value = response.data;
        resolve();
      });
    } else {
      reject();
    }
  });
};

/**
 * 查询部门下拉树结构（含部门前缀）
 */
const getDeptTreeData = () => {
  return new Promise<void>((resolve, reject) => {
    if (!deptTreeData.value || deptTreeData.value.length <= 0) {
      getDeptOptions()
        .then(() => {
          deptTreeData.value = refactorTree(deptOptions.value);
          resolve();
        })
        .catch(() => {
          reject();
        });
    } else {
      resolve();
    }
  });
};

interface DeptTreeNode {
  id: string;
  label: string;
  parentId: any;
  weight: any;
  children?: DeptTreeNode[];
}
const refactorTree = (data: any[]): DeptTreeNode[] => {
  return data.map((node) => {
    const treeData: DeptTreeNode = { id: `DEPT${node.id}`, label: node.label, parentId: node.parentId, weight: node.weight };
    if (node.children && node.children.length > 0) {
      treeData.children = refactorTree(node.children);
    }
    return treeData;
  });
};

/**
 * 查询部门下拉树结构
 */
const getRoleOptions = () => {
  if (!roleOptions.value || roleOptions.value.length <= 0) {
    // 原实现不传查询参数（拉取全部角色），此处保持不变，仅满足 listRole 的参数类型
    listRole({} as RoleQuery).then((response) => (roleOptions.value = response.rows));
  }
};
/** 查询用户列表 */
const getUserList = () => {
  listUser(proxy?.addDateRange(queryParams.value, dateRange.value)).then((response) => {
    userTableList.value = response.rows;
    userTotal.value = response.total;
  });
};
// 筛选节点
const filterNode = (value, data) => {
  if (!value) return true;
  return data.label.indexOf(value) !== -1;
};
// 节点单击事件
const handleNodeClick = (data) => {
  queryParams.value.deptId = data.id;
  getUserList();
};
// 关闭标签
const handleClose = (tag) => {
  selectedUserDate.value.splice(selectedUserDate.value?.indexOf(tag), 1);
  multipleTable.value?.toggleRowSelection(tag);
};
// 多选框选中数据
const handleSelectionChange = (selection) => {
  selectedUserDate.value = selection;
};
const onSelectUsers = () => {
  selectedUserDate.value = [];
  multipleTable.value?.clearSelection();
  getDeptOptions();
  userOpen.value = true;
};
const handleTaskUserComplete = () => {
  if (!selectedUserDate.value || selectedUserDate.value.length <= 0) {
    proxy?.$modal.msgError('请选择用户');
    return;
  }
  userTaskForm.value.dataType = 'USERS';
  selectedUser.value.text = selectedUserDate.value.map((k) => k.nickName) || [];
  if (selectedUserDate.value.length === 1) {
    const data = selectedUserDate.value[0];
    userTaskForm.value.assignee = data.userName;
    userTaskForm.value.text = data.nickName;
    userTaskForm.value.candidateUsers = '';
    showMultiFlog.value = false;
    multiLoopType.value = 'Null';
    changeMultiLoopType();
  } else {
    userTaskForm.value.candidateUsers = selectedUserDate.value.map((k) => k.userName).join() || '';
    userTaskForm.value.text = selectedUserDate.value.map((k) => k.nickName).join() || '';
    userTaskForm.value.assignee = '';
    showMultiFlog.value = true;
  }
  updateElementTask();
  userOpen.value = false;
};
const changeSelectRoles = (val) => {
  let groups = null;
  let text = null;
  if (val && val.length > 0) {
    userTaskForm.value.dataType = 'ROLES';
    groups = val.join() || null;
    const textArr = roleOptions.value.filter((k) => val.indexOf(`ROLE${k.roleId}`) >= 0);
    text = textArr?.map((k) => k.roleName).join() || null;
  } else {
    userTaskForm.value.dataType = '';
    multiLoopType.value = 'Null';
  }
  userTaskForm.value.candidateGroups = groups;
  userTaskForm.value.text = text;
  updateElementTask();
  changeMultiLoopType();
};

const nodeClick = (data, node, vm) => {
  const checkedIds = JSON.parse(JSON.stringify(node.checkedNodes));
  let groups = null;
  let text = null;
  deptIds.value = checkedIds;
  if (checkedIds && checkedIds.length > 0) {
    userTaskForm.value.dataType = 'DEPTS';
    groups = checkedIds?.map((k) => k.id).join() || null;
    text = checkedIds?.map((k) => k.label).join() || '';
  } else {
    userTaskForm.value.dataType = '';
    multiLoopType.value = 'Null';
  }
  userTaskForm.value.candidateGroups = groups;
  userTaskForm.value.text = text || '';
  updateElementTask();
  changeMultiLoopType();
};

const checkedDeptChange = (checkedIds) => {
  let groups = null;
  let text = null;
  const selectedIds = toRaw(checkedIds);
  deptIds.value = selectedIds;
  if (selectedIds && selectedIds.length > 0) {
    userTaskForm.value.dataType = 'DEPTS';
    groups = selectedIds.join() || null;
    const textArr = [];
    const treeStarkData = JSON.parse(JSON.stringify(deptTreeData.value));
    selectedIds.forEach((id) => {
      let stark = [];
      stark = stark.concat(treeStarkData);
      while (stark.length) {
        const temp = stark.shift();
        if (temp.children) {
          stark = temp.children.concat(stark);
        }
        if (id === temp.id) {
          textArr.push(temp);
        }
      }
    });
    text = textArr?.map((k) => k.label).join() || '';
  } else {
    userTaskForm.value.dataType = '';
    multiLoopType.value = 'Null';
  }
  userTaskForm.value.candidateGroups = groups;
  userTaskForm.value.text = text || '';
  updateElementTask();
  changeMultiLoopType();
};
const changeDataType = (val) => {
  if (val === 'ROLES' || val === 'DEPTS' || (val === 'USERS' && selectedUser.value.ids.length > 1)) {
    showMultiFlog.value = true;
  } else {
    showMultiFlog.value = false;
  }
  multiLoopType.value = 'Null';
  changeMultiLoopType();
  // 清空 userTaskForm 所有属性值
  Object.keys(userTaskForm.value).forEach((key) => (userTaskForm.value[key] = null));
  userTaskForm.value.dataType = val;
  if (val === 'USERS') {
    if (selectedUser.value && selectedUser.value.ids && selectedUser.value.ids.length > 0) {
      if (selectedUser.value.ids.length === 1) {
        userTaskForm.value.assignee = selectedUser.value.ids[0];
      } else {
        userTaskForm.value.candidateUsers = selectedUser.value.ids.join();
      }
      userTaskForm.value.text = selectedUser.value.text?.join() || '';
    }
  } else if (val === 'ROLES') {
    getRoleOptions();
    if (roleIds.value && roleIds.value.length > 0) {
      userTaskForm.value.candidateGroups = roleIds.value.join() || '';
      const textArr = roleOptions.value.filter((k) => roleIds.value.indexOf(`ROLE${k.roleId}`) >= 0);
      userTaskForm.value.text = textArr?.map((k) => k.roleName).join() || '';
    }
  } else if (val === 'DEPTS') {
    getDeptTreeData();
    if (deptIds.value && deptIds.value.length > 0) {
      userTaskForm.value.candidateGroups = deptIds.value.join() || '';
      const textArr = [];
      const treeStarkData = JSON.parse(JSON.stringify(deptTreeData.value));
      deptIds.value.forEach((id) => {
        let stark = [];
        stark = stark.concat(treeStarkData);
        while (stark.length) {
          const temp = stark.shift();
          if (temp.children) {
            stark = temp.children.concat(stark);
          }
          if (id === temp.id) {
            textArr.push(temp);
          }
        }
      });
      userTaskForm.value.text = textArr?.map((k) => k.label).join() || '';
    }
  } else if (val === 'MANAGER') {
    userTaskForm.value.assignee = '${DepManagerHandler.getUser(execution)}';
    userTaskForm.value.text = '部门经理';
  } else if (val === 'INITIATOR') {
    userTaskForm.value.assignee = '${initiator}';
    userTaskForm.value.text = '流程发起人';
  }
  updateElementTask();
};
const getElementLoop = (businessObject) => {
  if (!businessObject.loopCharacteristics) {
    multiLoopType.value = 'Null';
    return;
  }
  isSequential.value = businessObject.loopCharacteristics.isSequential;
  if (businessObject.loopCharacteristics.completionCondition) {
    if (businessObject.loopCharacteristics.completionCondition.body === '${nrOfCompletedInstances >= nrOfInstances}') {
      multiLoopType.value = 'SequentialMultiInstance';
    } else if (businessObject.loopCharacteristics.completionCondition.body === '${nrOfCompletedInstances > 0}') {
      multiLoopType.value = 'ParallelMultiInstance';
    } else {
      multiLoopType.value = 'CustomMultiInstance';
    }
  }
};
const changeMultiLoopType = () => {
  // 取消多实例配置
  if (multiLoopType.value === 'Null') {
    bpmnInstances().modeling.updateProperties(toRaw(bpmnElement.value), { loopCharacteristics: null, assignee: null });
    return;
  }
  multiLoopInstance.value = bpmnInstances().moddle.create('bpmn:MultiInstanceLoopCharacteristics', { isSequential: isSequential.value });
  // 更新多实例配置
  bpmnInstances().modeling.updateProperties(toRaw(bpmnElement.value), {
    loopCharacteristics: toRaw(multiLoopInstance.value),
    assignee: '${assignee}'
  });
  // 完成条件
  let completionCondition = null;
  // 会签
  if (multiLoopType.value === 'SequentialMultiInstance') {
    completionCondition = bpmnInstances().moddle.create('bpmn:FormalExpression', { body: '${nrOfCompletedInstances >= nrOfInstances}' });
  }
  // 或签
  if (multiLoopType.value === 'ParallelMultiInstance') {
    completionCondition = bpmnInstances().moddle.create('bpmn:FormalExpression', { body: '${nrOfCompletedInstances > 0}' });
  }
  // 自定义会签
  if (multiLoopType.value === 'CustomMultiInstance') {
    completionCondition = bpmnInstances().moddle.create('bpmn:FormalExpression', { body: CustomCompletionCondition.value });
  }
  // 更新模块属性信息
  bpmnInstances().modeling.updateModdleProperties(toRaw(bpmnElement.value), multiLoopInstance.value, {
    collection: '${multiInstanceHandler.getUserNames(execution)}',
    elementVariable: 'assignee',
    completionCondition
  });
};

watch(
  () => props.id,
  () => {
    bpmnElement.value = bpmnInstances().bpmnElement;
    nextTick(() => {
      resetTaskForm();
    });
  },
  { immediate: true }
);
watch(
  // 根据名称筛选部门树
  () => deptName.value,
  (val) => {
    tree.value?.filter(val);
  }
);

onBeforeUnmount(() => {
  multiLoopInstance.value = null;
  bpmnElement.value = null;
});
</script>

<style scoped lang="scss">
.el-row .el-radio-group {
  margin-bottom: 15px;
  .el-radio {
    line-height: 28px;
  }
}
.el-tag {
  margin-bottom: 10px;
  + .el-tag {
    margin-left: 10px;
  }
}

.custom-label {
  padding-left: 5px;
  font-weight: 500;
  font-size: 14px;
  color: #606266;
}
</style>
