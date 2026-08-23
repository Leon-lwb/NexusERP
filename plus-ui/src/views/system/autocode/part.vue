<template>
  <div class="app-container">
    <el-form :model="queryParams" ref="queryFormRef" size="small" :inline="true" v-show="showSearch" label-width="68px">
      <el-form-item label="组成编码" prop="partCode">
        <el-input v-model="queryParams.partCode" placeholder="请输入组成编码" clearable @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="组成名称" prop="partName">
        <el-input v-model="queryParams.partName" placeholder="请输入组成名称" clearable @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" icon="Search" size="small" @click="handleQuery">搜索</el-button>
        <el-button icon="Refresh" size="small" @click="resetQuery">重置</el-button>
      </el-form-item>
    </el-form>

    <el-row :gutter="10" class="mb8">
      <el-col :span="1.5">
        <el-button type="primary" plain icon="Plus" size="small" @click="handleAdd" v-hasPermi="['system:autocode:part:add']">新增</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="success" plain icon="Edit" size="small" :disabled="single" @click="handleUpdate" v-hasPermi="['system:autocode:part:edit']"
          >修改</el-button
        >
      </el-col>
      <el-col :span="1.5">
        <el-button
          type="danger"
          plain
          icon="Delete"
          size="small"
          :disabled="multiple"
          @click="handleDelete"
          v-hasPermi="['system:autocode:part:remove']"
          >删除</el-button
        >
      </el-col>
      <el-col :span="1.5">
        <el-button type="warning" plain icon="Close" size="small" @click="handleClose">关闭</el-button>
      </el-col>
      <right-toolbar v-model:showSearch="showSearch" @queryTable="getList"></right-toolbar>
    </el-row>

    <el-table v-loading="loading" :data="partList" @selection-change="handleSelectionChange">
      <el-table-column type="selection" width="55" align="center" />
      <el-table-column label="组成编码" align="center" prop="partCode" />
      <el-table-column label="组成名称" align="center" prop="partName" />
      <el-table-column label="分段序号" align="center" prop="partIndex" />
      <el-table-column label="分段类型" align="center" prop="partType">
        <template #default="scope">
          <dict-tag :options="sys_autocode_parttype" :value="scope.row.partType" />
        </template>
      </el-table-column>
      <el-table-column label="分段长度" align="center" prop="partLength" />
      <el-table-column label="备注" align="center" prop="remark" :show-overflow-tooltip="true" />
      <el-table-column label="创建时间" align="center" prop="createTime" width="180">
        <template #default="scope">
          <span>{{ proxy.parseTime(scope.row.createTime) }}</span>
        </template>
      </el-table-column>
      <el-table-column label="操作" align="center" width="150" class-name="small-padding fixed-width">
        <template #default="scope">
          <el-tooltip content="修改" placement="top">
            <el-button link type="primary" icon="Edit" @click="handleUpdate(scope.row)" v-hasPermi="['system:autocode:part:edit']"></el-button>
          </el-tooltip>
          <el-tooltip content="删除" placement="top">
            <el-button link type="primary" icon="Delete" @click="handleDelete(scope.row)" v-hasPermi="['system:autocode:part:remove']"></el-button>
          </el-tooltip>
        </template>
      </el-table-column>
    </el-table>

    <pagination v-show="total > 0" :total="total" v-model:page="queryParams.pageNum" v-model:limit="queryParams.pageSize" @pagination="getList" />

    <el-dialog v-model="open" :title="title" width="900px" append-to-body>
      <el-form ref="formRef" :model="form" :inline="true" :rules="rules" label-width="110px">
        <el-row>
          <el-col :span="12">
            <el-form-item label="分段编码" prop="partCode">
              <el-input v-model="form.partCode" placeholder="请输入分段编码" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="分段名称" prop="partName">
              <el-input v-model="form.partName" placeholder="请输入分段名称" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row>
          <el-col :span="12">
            <el-form-item label="分段序号" prop="partIndex">
              <el-input-number v-model="form.partIndex" placeholder="请输入分段序号" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="分段长度" prop="partLength">
              <el-input-number v-model="form.partLength" placeholder="请输入分段长度" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row>
          <el-col :span="12">
            <el-form-item label="分段类型" prop="partType">
              <el-select v-model="form.partType">
                <el-option v-for="item in sys_autocode_parttype" :key="item.value" :label="item.label" :value="item.value"></el-option>
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="日期时间格式" prop="dateFormat" v-if="form.partType == 'NOWDATE'">
              <el-input v-model="form.dateFormat" placeholder="请输入日期时间格式" />
            </el-form-item>

            <el-form-item label="输入字符" prop="inputCharacter" v-if="form.partType == 'INPUTCHAR'">
              <el-input v-model="form.inputCharacter" placeholder="请填写输入字符" />
            </el-form-item>

            <el-form-item label="固定字符" prop="fixCharacter" v-if="form.partType == 'FIXCHAR'">
              <el-input v-model="form.fixCharacter" placeholder="请填写固定字符" />
            </el-form-item>

            <el-form-item label="起始流水号" prop="seriaStartNo" v-if="form.partType == 'SERIALNO'">
              <el-input-number v-model="form.seriaStartNo" placeholder="请填写起始流水号" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row>
          <el-col :span="12">
            <el-form-item label="流水号步长" prop="seriaStep" v-if="form.partType == 'SERIALNO'">
              <el-input-number v-model="form.seriaStep" placeholder="请填写流水号步长" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="是否循环" prop="cycleFlag" v-if="form.partType == 'SERIALNO'">
              <el-radio-group v-model="form.cycleFlag">
                <el-radio label="Y">是</el-radio>
                <el-radio label="N">否</el-radio>
              </el-radio-group>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row>
          <el-col :span="12">
            <el-form-item label="循环方式" prop="cycleMethod" v-if="form.partType == 'SERIALNO' && form.cycleFlag == 'Y'">
              <el-select v-model="form.cycleMethod">
                <el-option v-for="item in sys_autocode_cyclemethod" :key="item.value" :label="item.label" :value="item.value"></el-option>
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row>
          <el-col>
            <el-form-item label="备注" prop="remark">
              <el-input v-model="form.remark" type="textarea" placeholder="请输入内容"></el-input>
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button type="primary" @click="submitForm">确 定</el-button>
          <el-button @click="cancel">取 消</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup name="Part" lang="ts">
import { ref, reactive, onMounted, toRefs, ComponentInternalInstance, getCurrentInstance } from 'vue';
import { useRoute } from 'vue-router';
import { listPart, getPart, delPart, addPart, updatePart } from '@/api/system/autocode/part';

const { proxy } = getCurrentInstance() as ComponentInternalInstance;
const route = useRoute();
const { sys_autocode_parttype, sys_autocode_cyclemethod } = toRefs<any>(proxy.useDict('sys_autocode_parttype', 'sys_autocode_cyclemethod'));

const partList = ref([]);
const loading = ref(true);
const showSearch = ref(true);
const ids = ref([]);
const single = ref(true);
const multiple = ref(true);
const total = ref(0);
const title = ref('');
const open = ref(false);
const recentRuleId = ref(undefined);
const queryFormRef = ref();
const formRef = ref();

interface PartForm {
  partId?: number;
  ruleId?: number;
  partCode?: string;
  partName?: string;
  partIndex?: number;
  partLength?: number;
  partType?: string;
  dateFormat?: string;
  inputCharacter?: string;
  fixCharacter?: string;
  seriaStartNo?: number;
  seriaStep?: number;
  cycleFlag?: string;
  cycleMethod?: string;
  remark?: string;
}

const data = reactive({
  form: {} as PartForm,
  queryParams: {
    pageNum: 1,
    pageSize: 10,
    ruleId: undefined,
    partCode: undefined,
    partName: undefined
  },
  rules: {
    partCode: [{ required: true, message: '组成编码不能为空', trigger: 'blur' }],
    partName: [{ required: true, message: '组成名称不能为空', trigger: 'blur' }],
    partIndex: [{ required: true, message: '组成序号不能为空', trigger: 'blur' }],
    remark: [{ max: 250, message: '长度必须小于250个字符', trigger: 'blur' }]
  }
});

const { queryParams, form, rules } = toRefs(data);

function getList() {
  loading.value = true;
  listPart(queryParams.value).then((response) => {
    partList.value = response.rows;
    total.value = response.total;
    loading.value = false;
  });
}

function cancel() {
  open.value = false;
  reset();
}

function reset() {
  form.value = {
    partCode: undefined,
    partName: undefined,
    partIndex: 0,
    partLength: 0,
    remark: undefined
  };
  proxy.resetForm('formRef');
}

function handleQuery() {
  queryParams.value.pageNum = 1;
  getList();
}

function handleClose() {
  const obj = { path: '/system/autocode' };
  proxy.$tab.closeOpenPage(obj);
}

function resetQuery() {
  proxy.resetForm('queryFormRef');
  queryParams.value.ruleId = recentRuleId.value;
  handleQuery();
}

function handleAdd() {
  reset();
  open.value = true;
  title.value = '添加规则组成';
  form.value.ruleId = recentRuleId.value;
}

function handleSelectionChange(selection) {
  ids.value = selection.map((item) => item.partId);
  single.value = selection.length != 1;
  multiple.value = !selection.length;
}

function handleUpdate(row) {
  reset();
  const partId = row.partId || ids.value;
  getPart(partId).then((response) => {
    form.value = response.data;
    open.value = true;
    title.value = '修改规则组成';
  });
}

function submitForm() {
  proxy.$refs['formRef'].validate((valid) => {
    if (valid) {
      if (form.value.partId != undefined) {
        updatePart(form.value).then((response) => {
          proxy.$modal.msgSuccess('修改成功');
          open.value = false;
          getList();
        });
      } else {
        addPart(form.value).then((response) => {
          proxy.$modal.msgSuccess('新增成功');
          open.value = false;
          getList();
        });
      }
    }
  });
}

function handleDelete(row) {
  const partIds = row.partId || ids.value;
  proxy.$modal
    .confirm('是否确认删除规则组成ID为"' + partIds + '"的数据项？')
    .then(function () {
      return delPart(partIds);
    })
    .then(() => {
      getList();
      proxy.$modal.msgSuccess('删除成功');
    })
    .catch(() => {});
}

onMounted(() => {
  const ruleId = route.params && route.params.ruleId;
  recentRuleId.value = ruleId;
  queryParams.value.ruleId = ruleId;
  getList();
});
</script>
