<template>
  <div class="app-container">
    <el-card class="box-card">
      <template #header>
        <span>发起流程</span>
      </template>
      <div class="form-conf" v-if="dialog.visible">
        <!-- 使用 nform 的 FormPreview 组件 -->
        <FormRender
          :form-content="formContent"
          :model-value="dialog.visible"
          :dict-options="dictOptions"
          @close="dialog.visible = false"
          @update:model-value="dialog.visible = $event"
          ref="formRenderRef"
        />
        <div class="cu-submit">
          <el-button type="primary" @click="submit">提交</el-button>
          <el-button @click="reset">重置</el-button>
        </div>
      </div>
    </el-card>
  </div>
</template>

<script setup name="WorkStart" lang="ts">
import { getProcessForm, startProcess } from '@/api/workflow/work/process';
import { getNform } from '@/api/workflow/nform';
import { getToken } from '@/utils/auth';
import { getDicts } from '@/api/system/dict/data';
import FormRender from '@/components/DesignerForm/FormRender.vue';
import { addOrUpdateOnlineData } from '@/api/online';

import { ComponentInternalInstance, ref, reactive, onBeforeMount, onMounted, getCurrentInstance } from 'vue';

import { useRoute } from 'vue-router';
const route = useRoute();
const { proxy } = getCurrentInstance() as ComponentInternalInstance;

const deployId = ref();
const definitionId = ref();
const formContent = ref('');
const formData = ref({});
const formRenderRef = ref<any>(null);
const dictOptions = ref<Record<string, any[]>>({});
let nbcioHeader: any;

const dialog = reactive<DialogOption>({
  visible: false,
  title: ''
});

// 获取字典数据
const loadDictData = async (dictType: string) => {
  if (!dictType || dictOptions.value[dictType]) return;
  try {
    const res = await getDicts(dictType);
    if (res.code === 200) {
      const options = res.data.map((item: any) => ({
        label: item.dictLabel,
        value: item.dictValue
      }));
      dictOptions.value[dictType] = options;
    }
  } catch (error) {
    console.error(`获取字典数据失败 ${dictType}:`, error);
  }
};

// 加载表单中所有需要的字典数据
const loadAllDictData = async (formComponents: any[]) => {
  const dictTypes = new Set<string>();
  formComponents.forEach((component) => {
    // 兼容 props.dictType 与 顶层 dictType 两种写法
    const dictTypeFromProps = component?.props?.dictType;
    const dictTypeFromTop = component?.dictType;
    if ((component?.props?.dataSource === 'dict' && dictTypeFromProps) || dictTypeFromTop) {
      dictTypes.add(dictTypeFromProps || dictTypeFromTop);
    }

    // 子表内字段也可能使用字典
    if (component?.type === 'subtable' && Array.isArray(component?.subTableColumns)) {
      component.subTableColumns.forEach((col: any) => {
        const colDictTypeFromProps = col?.props?.dictType;
        const colDictTypeFromTop = col?.dictType;
        if ((col?.props?.dataSource === 'dict' && colDictTypeFromProps) || colDictTypeFromTop) {
          dictTypes.add(colDictTypeFromProps || colDictTypeFromTop);
        }
      });
    }
  });

  for (const dictType of dictTypes) {
    await loadDictData(dictType);
  }
};

const parseFormMeta = () => {
  try {
    const parsed: any = JSON.parse(formContent.value || '{}');
    const tableInfo = parsed?.formConfig?.tableInfo || {};
    const main = tableInfo?.mainTable || tableInfo || {};
    const sub = tableInfo?.subTable || {};

    // 兼容多种键名
    const pickId = (obj: any) => obj?.tableId || obj?.id || obj?.table_id || '';
    const pickName = (obj: any) => obj?.tableName || obj?.name || obj?.table_name || '';

    const mainTableName = pickName(main);
    const mainTableId = pickId(main);

    let subTableName = pickName(sub);
    // 兜底：从 subtable 组件的 field/props.tableName 推断
    if (!subTableName) {
      const subComp = (parsed?.formComponents || []).find((c: any) => c.type === 'subtable');
      subTableName = subComp?.props?.tableName || subComp?.field || '';
    }
    const subField = (parsed?.formComponents || []).find((c: any) => c.type === 'subtable')?.field || '';

    return { mainTableName, mainTableId, subTableName, subField };
  } catch (e) {
    console.warn('[start] parseFormMeta failed:', e);
    return { mainTableName: '', mainTableId: '', subTableName: '', subField: '' };
  }
};

const initData = async () => {
  deployId.value = route.params && route.params.deployId;
  definitionId.value = route.query && route.query.definitionId;

  try {
    // 获取流程表单数据
    const res = await getProcessForm({ definitionId: definitionId.value, deployId: deployId.value });

    // 如果返回的是 nform 表单ID，则获取 nform 表单内容
    if (res.data.formId) {
      const nformRes = await getNform(res.data.formId);
      formContent.value = nformRes.data.formContent;
    } else if (res.data.formModel) {
      // 直接使用 formModel 作为 formContent
      formContent.value = JSON.stringify(res.data.formModel);
    }

    parseFormMeta();

    // 加载表单中需要的字典数据
    const parsed = JSON.parse(formContent.value || '{}');
    if (parsed.formComponents && Array.isArray(parsed.formComponents)) {
      await loadAllDictData(parsed.formComponents);
    }

    dialog.visible = true;
  } catch (error) {
    console.error('获取表单数据失败:', error);
    proxy?.$modal.msgError('获取表单数据失败');
  }
};

const submit = async () => {
  try {
    // 通过 FormRender 获取 NForm 的主/子表数据
    const dataObj = await formRenderRef.value?.getFormData?.();
    const mainData = dataObj?.main || {};
    const subDataObj = dataObj?.sub || {};
    const meta = parseFormMeta();
    // 组装提交数据（与 online/test/index.vue 类似）
    let submitData: any = mainData;
    if (meta.subField && meta.subTableName) {
      const subRows = Array.isArray(subDataObj?.[meta.subField]) ? subDataObj[meta.subField] : [];
      submitData = {
        main: mainData,
        sub: {
          tableName: meta.subTableName,
          data: subRows
        }
      };
    }

    // 清理驼峰键，保留下划线/全小写键，避免未知列（goodCode 等）
    const isAllowedKey = (k: string) => /^[a-z0-9_]+$/.test(k);
    const cleanObjectKeys = (obj: any) => {
      if (!obj || typeof obj !== 'object') return obj;
      const out: any = {};
      Object.keys(obj).forEach((k) => {
        if (isAllowedKey(k)) out[k] = obj[k];
      });
      return out;
    };
    const cleanSubmitData = (data: any) => {
      if (!data || typeof data !== 'object') return data;
      if (data.main && data.sub && data.sub.data) {
        return {
          main: cleanObjectKeys(data.main),
          sub: {
            tableName: data.sub.tableName,
            data: (Array.isArray(data.sub.data) ? data.sub.data : []).map((row: any) => cleanObjectKeys(row))
          }
        };
      }
      return cleanObjectKeys(data);
    };
    const submitDataClean = cleanSubmitData(submitData);

    // 先保存业务数据到对应表
    try {
      if (meta.mainTableId && meta.mainTableName) {
        if (meta.subField && meta.subTableName && (!Array.isArray(subDataObj?.[meta.subField]) || subDataObj[meta.subField].length === 0)) {
          console.warn('[start][submit] 警告：检测到子表配置但当前无子表数据，subField=', meta.subField);
        }
        const saveRes: any = await addOrUpdateOnlineData(meta.mainTableId, meta.mainTableName, submitDataClean);
      } else if (meta.mainTableName) {
        // 兼容：只有表名时，使用按表名保存
        const { addOrUpdateOnlineDataByName } = await import('@/api/online');
        const saveRes: any = await addOrUpdateOnlineDataByName(meta.mainTableName, submitDataClean);
      } else {
        console.warn('[start][submit] 缺少表信息，跳过落库，仅发起流程');
      }
    } catch (e) {
      console.error('[start][submit] 落库失败:', e);
      proxy?.$modal.msgError('业务数据保存失败');
      return;
    }

    // 再发起流程，携带业务变量（使用 submitData）
    if (definitionId.value) {
      const res = await startProcess(definitionId.value, JSON.stringify(submitData));
      proxy?.$modal.msgSuccess(res.msg);
      const obj = { path: '/work/own' };
      proxy?.$tab.closeOpenPage(obj);
    }
  } catch (error) {
    console.error('提交失败:', error);
    proxy?.$modal.msgError('提交失败');
  }
};

const reset = () => {
  // 重置表单数据
  formData.value = {};
};

// 监听表单数据变化
const handleFormDataChange = (data: any) => {
  formData.value = data;
};

onBeforeMount(() => {
  const clientid = import.meta.env.VITE_APP_CLIENT_ID;
  nbcioHeader = { 'Authorization': 'Bearer ' + getToken(), 'Clientid': clientid };
});

onMounted(() => {
  initData();
});
</script>

<style lang="scss" scoped>
.form-conf {
  margin: 15px auto;
  width: 80%;
  padding: 15px;
}

.cu-submit {
  margin-top: 20px;
  text-align: center;
}
</style>
