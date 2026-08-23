<template>
  <div class="app-container">
    <el-form v-model="queryParams" ref="queryFormRef" size="small" :inline="true" v-show="showSearch" label-width="68px">
      <el-form-item label="用户账号" prop="userName">
        <el-input v-model="queryParams.userName" placeholder="请输入用户账号" clearable @keyup.enter.native="handleQuery" />
      </el-form-item>
      <el-form-item label="用户昵称" prop="nickName">
        <el-input v-model="queryParams.nickName" placeholder="请输入用户昵称" clearable @keyup.enter.native="handleQuery" />
      </el-form-item>
      <el-form-item label="用户邮箱" prop="email">
        <el-input v-model="queryParams.email" placeholder="请输入用户邮箱" clearable @keyup.enter.native="handleQuery" />
      </el-form-item>
      <el-form-item label="头像地址" prop="avatar">
        <el-input v-model="queryParams.avatar" placeholder="请输入头像地址" clearable @keyup.enter.native="handleQuery" />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" icon="el-icon-search" size="small" @click="handleQuery">搜索</el-button>
        <el-button icon="el-icon-refresh" size="small" @click="resetQuery">重置</el-button>
      </el-form-item>
    </el-form>

    <el-row :gutter="10" class="mb8">
      <el-col :span="1.5">
        <el-button type="primary" plain icon="el-icon-plus" size="small" @click="handleAdd" v-hasPermi="['workflow:demo:add']">新增</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="success" plain icon="el-icon-edit" size="small" :disabled="single" @click="handleUpdate" v-hasPermi="['workflow:demo:edit']"
          >修改</el-button
        >
      </el-col>
      <el-col :span="1.5">
        <el-button
          type="danger"
          plain
          icon="el-icon-delete"
          size="small"
          :disabled="multiple"
          @click="handleDelete"
          v-hasPermi="['workflow:demo:remove']"
          >删除</el-button
        >
      </el-col>
      <el-col :span="1.5">
        <el-button type="warning" plain icon="el-icon-download" size="small" @click="handleExport" v-hasPermi="['workflow:demo:export']"
          >导出</el-button
        >
      </el-col>
      <right-toolbar v-model:showSearch="showSearch" @queryTable="getList"></right-toolbar>
    </el-row>
    <el-table v-loading="loading" :data="demoList" @selection-change="handleSelectionChange">
      <el-table-column type="selection" width="20" align="center" />
      <el-table-column label="用户ID" align="center" prop="demoId" v-if="true" />
      <el-table-column label="用户账号" align="center" prop="userName" />
      <el-table-column label="用户昵称" align="center" prop="nickName" />
      <el-table-column label="流程状态" align="center" prop="actStatus" />
      <el-table-column label="待处理节点" align="center" prop="taskName" />
      <el-table-column label="处理人" align="center" prop="todoUsers" />
      <el-table-column label="操作" align="center" width="420">
        <template #default="scope">
          <act-apply-btn @success="getList" :data-id="String(scope.row.demoId)" :serviceName="serviceName" :variables="scope.row"></act-apply-btn>
          <!--<act-handle-btn @success="getList" :data-id="scope.row.demoId" :type="0" text="通过"></act-handle-btn>
          <act-handle-btn @success="getList" :data-id="scope.row.demoId" :type="1" text="驳回"></act-handle-btn>
          <act-handle-btn @success="getList" :data-id="scope.row.demoId" :type="2" text="退回"></act-handle-btn>
          <act-cancel-btn @success="getList" :data-id="scope.row.demoId"></act-cancel-btn>-->
          <a-divider type="vertical" />
          <act-historic-detail-btn :data-id="String(scope.row.demoId)"></act-historic-detail-btn>
          <a-divider type="vertical" />
          <el-button size="default" type="primary" icon="el-icon-edit" @click="handleUpdate(scope.row)" v-hasPermi="['workflow:demo:edit']"
            >修改</el-button
          >
          <el-button size="default" type="primary" icon="el-icon-delete" @click="handleDelete(scope.row)" v-hasPermi="['workflow:demo:remove']"
            >删除</el-button
          >
        </template>
      </el-table-column>
    </el-table>

    <pagination v-show="total > 0" :total="total" v-model:page="queryParams.pageNum" v-model:limit="queryParams.pageSize" @pagination="getList" />

    <!-- 添加或修改DEMO对话框 -->
    <el-dialog :title="title" v-model="open" width="500px" append-to-body>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="80px">
        <el-form-item label="用户账号" prop="userName">
          <el-input v-model="form.userName" placeholder="请输入用户账号" />
        </el-form-item>
        <el-form-item label="用户昵称" prop="nickName">
          <el-input v-model="form.nickName" placeholder="请输入用户昵称" />
        </el-form-item>
        <el-form-item label="用户邮箱" prop="email">
          <el-input v-model="form.email" placeholder="请输入用户邮箱" />
        </el-form-item>
        <el-form-item label="头像地址" prop="avatar">
          <el-input v-model="form.avatar" placeholder="请输入头像地址" />
        </el-form-item>
        <el-form-item label="备注" prop="remark">
          <el-input v-model="form.remark" type="textarea" placeholder="请输入内容" />
        </el-form-item>
      </el-form>
      <template v-slot:footer>
        <div class="dialog-footer">
          <el-button :loading="buttonLoading" type="primary" @click="submitForm">确 定</el-button>
          <el-button @click="cancel">取 消</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup name="Demo" lang="ts">
import { listDemo, getDemo, delDemo, addDemo, updateDemo } from '@/api/workflow/demo/index';
import type { WfDemo, WfDemoQuery } from '@/api/workflow/demo/types';
import ActApplyBtn from '../components/ActApplyBtn.vue';
//import ActCancelBtn from "../components/ActCancelBtn";
//import ActHandleBtn from "../components/ActHandleBtn";
import ActHistoricDetailBtn from '../components/ActHistoricDetailBtn.vue';

const { proxy } = getCurrentInstance() as ComponentInternalInstance;
const queryFormRef = ref(null);
const formRef = ref<ElFormInstance>();
// 按钮loading
const buttonLoading = ref(false);
// 遮罩层
const loading = ref(true);
// 选中数组
const ids = ref<any>([]);
// 非单个禁用
const single = ref(true);
// 非多个禁用
const multiple = ref(true);
// 显示搜索条件
const showSearch = ref(true);
// 总条数
const total = ref(0);
// DEMO表格数据
const demoList = ref<any>([]);
// 弹出层标题
const title = ref('');
// 是否显示弹出层
const open = ref(false);
// 查询参数
const queryParams = ref<WfDemoQuery>({
  pageNum: 1,
  pageSize: 10,
  userName: undefined,
  nickName: undefined,
  email: undefined,
  avatar: undefined,
  status: undefined
});
const serviceName = ref('wfDemoService');
// 表单参数
const form = ref<WfDemo>({
  demoId: undefined,
  userName: '',
  nickName: ''
});
// 表单校验
const rules = {
  demoId: [{ required: true, message: 'DEMO-ID不能为空', trigger: 'blur' }],
  userName: [{ required: true, message: '用户账号不能为空', trigger: 'blur' }]
};
/** 查询DEMO列表 */
const getList = () => {
  loading.value = true;
  listDemo(queryParams.value).then((response) => {
    demoList.value = response.rows;
    total.value = response.total;
    loading.value = false;
  });
};
// 取消按钮
const cancel = () => {
  open.value = false;
  reset();
};
// 表单重置
const reset = () => {
  form.value = {
    demoId: undefined,
    userName: '',
    nickName: '',
    email: undefined,
    avatar: undefined,
    status: undefined,
    delFlag: undefined,
    createBy: undefined,
    createTime: undefined,
    updateBy: undefined,
    updateTime: undefined,
    remark: undefined
  };
  formRef.value?.resetFields();
};
/** 搜索按钮操作 */
const handleQuery = () => {
  queryParams.value.pageNum = 1;
  getList();
};
/** 重置按钮操作 */
const resetQuery = () => {
  queryFormRef.value?.resetFields();
  handleQuery();
};
// 多选框选中数据
const handleSelectionChange = (selection) => {
  ids.value = selection.map((item) => item.demoId);
  single.value = selection.length !== 1;
  multiple.value = !selection.length;
};
/** 新增按钮操作 */
const handleAdd = () => {
  reset();
  open.value = true;
  title.value = '添加DEMO';
};
/** 修改按钮操作 */
const handleUpdate = (row) => {
  loading.value = true;
  reset();
  const demoId = row.demoId || ids.value;
  getDemo(demoId).then((response) => {
    loading.value = false;
    form.value = response.data;
    open.value = true;
    title.value = '修改DEMO';
  });
};
/** 提交按钮 */
const submitForm = () => {
  formRef.value?.validate(async (valid: boolean) => {
    if (valid) {
      buttonLoading.value = true;
      if (form.value.demoId != null) {
        updateDemo(form.value)
          .then((response) => {
            proxy?.$modal.msgSuccess('修改成功');
            open.value = false;
            getList();
          })
          .finally(() => {
            buttonLoading.value = false;
          });
      } else {
        addDemo(form.value)
          .then((response) => {
            proxy?.$modal.msgSuccess('新增成功');
            open.value = false;
            getList();
          })
          .finally(() => {
            buttonLoading.value = false;
          });
      }
    }
  });
};
/** 删除按钮操作 */
const handleDelete = (row) => {
  const demoIds = row.demoId || ids.value;
  proxy?.$modal
    .confirm('是否确认删除DEMO编号为"' + demoIds + '"的数据项？')
    .then(() => {
      loading.value = true;
      return delDemo(demoIds);
    })
    .then(() => {
      loading.value = false;
      getList();
      proxy?.$modal.msgSuccess('删除成功');
    })
    .catch(() => {})
    .finally(() => {
      loading.value = false;
    });
};
/** 导出按钮操作 */
const handleExport = () => {
  proxy?.download(
    'workflow/demo/export',
    {
      ...queryParams.value
    },
    `demo_${new Date().getTime()}.xlsx`
  );
};

onMounted(() => {
  getList();
});
</script>
<style lang="scss" scoped>
.small-padding-demo {
  .cell {
    padding-left: 5px;
    padding-right: 5px;
  }
}

.fixed-width-demo {
  .el-button--mini {
    padding: 7px 10px;
    width: 60px;
  }
}
</style>
