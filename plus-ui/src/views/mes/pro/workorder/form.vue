<template>
  <el-dialog v-model="visible" :title="title" width="640px" append-to-body>
    <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
      <el-form-item label="工单编号" prop="woNo">
        <el-input v-model="form.woNo" placeholder="请输入工单编号" :disabled="form.mesWoId != null" />
      </el-form-item>
      <el-form-item label="产品" prop="materialId">
        <el-select
          v-model="form.materialId"
          placeholder="请选择产品"
          filterable
          :disabled="form.mesWoId != null"
          @change="handleMaterialChange"
          style="width: 100%"
        >
          <el-option
            v-for="item in materialOptions"
            :key="item.materialId"
            :label="item.materialCode + ' - ' + item.materialName"
            :value="item.materialId"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="计划数量" prop="plannedQty">
        <el-input-number v-model="form.plannedQty" :min="0.0001" :precision="4" :step="1" style="width: 100%" />
      </el-form-item>
      <el-form-item label="优先级" prop="priority">
        <el-input-number v-model="form.priority" :min="0" style="width: 100%" />
      </el-form-item>
      <el-form-item label="计划开工" prop="planStartDate">
        <el-date-picker
          v-model="form.planStartDate"
          type="datetime"
          value-format="YYYY-MM-DD HH:mm:ss"
          placeholder="请选择计划开工时间"
          style="width: 100%"
        />
      </el-form-item>
      <el-form-item label="计划完工" prop="planEndDate">
        <el-date-picker
          v-model="form.planEndDate"
          type="datetime"
          value-format="YYYY-MM-DD HH:mm:ss"
          placeholder="请选择计划完工时间"
          style="width: 100%"
        />
      </el-form-item>
      <el-form-item label="备注" prop="remark">
        <el-input v-model="form.remark" type="textarea" placeholder="请输入备注" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="close">取消</el-button>
      <el-button type="primary" @click="submit">确定</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts" name="MesWorkOrderForm">
import { addWorkOrder, updateWorkOrder } from '@/api/mes/pro/workorder';
import { listMaterial } from '@/api/erp/bd/material/index';
import type { ProWorkOrderForm, ProWorkOrderVO } from '@/api/mes/pro/types';

const { proxy } = getCurrentInstance() as ComponentInternalInstance;
const emit = defineEmits(['refresh']);

const visible = ref(false);
const title = ref('');
const formRef = ref();
const materialOptions = ref<any[]>([]);

const initForm: ProWorkOrderForm = {
  woNo: undefined,
  materialId: undefined,
  materialCode: undefined,
  materialName: undefined,
  plannedQty: undefined,
  priority: 0,
  planStartDate: undefined,
  planEndDate: undefined,
  remark: undefined
};
const form = ref<ProWorkOrderForm>({ ...initForm });

const rules = {
  woNo: [{ required: true, message: '工单编号不能为空', trigger: 'blur' }],
  materialId: [{ required: true, message: '请选择产品', trigger: 'change' }],
  plannedQty: [{ required: true, message: '计划数量不能为空', trigger: 'blur' }]
};

const open = (row?: ProWorkOrderVO) => {
  reset();
  if (row && row.mesWoId) {
    title.value = '修改MES工单';
    form.value = { ...row };
  } else {
    title.value = '新增MES工单';
  }
  loadMaterials();
  visible.value = true;
};

const loadMaterials = async () => {
  const res = await listMaterial({ pageNum: 1, pageSize: 1000 } as any);
  materialOptions.value = res.rows || [];
};

const handleMaterialChange = (materialId: number) => {
  const m = materialOptions.value.find((item: any) => item.materialId === materialId);
  if (m) {
    form.value.materialCode = m.materialCode;
    form.value.materialName = m.materialName;
  }
};

const reset = () => {
  form.value = { ...initForm };
  formRef.value?.resetFields();
};

const close = () => {
  visible.value = false;
  reset();
};

const submit = async () => {
  await formRef.value?.validate();
  if (form.value.mesWoId != null) {
    await updateWorkOrder(form.value);
  } else {
    await addWorkOrder(form.value);
  }
  proxy?.$modal.msgSuccess('保存成功');
  emit('refresh');
  close();
};

defineExpose({ open });
</script>
