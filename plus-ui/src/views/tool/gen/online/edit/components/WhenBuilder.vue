<template>
  <div class="when-builder">
    <div class="when-header">
      <span class="when-title">条件（when）</span>
      <el-switch v-model="enabled" active-text="启用" inactive-text="不启用" />
    </div>

    <div v-if="enabled" class="when-body">
      <el-tabs v-model="activeTab" type="border-card">
        <el-tab-pane label="all（全部满足）" name="all">
          <div class="cond-actions">
            <el-button size="small" type="primary" @click="add('all')">新增条件</el-button>
            <el-button size="small" @click="clear('all')">清空</el-button>
          </div>

          <div v-for="(c, idx) in local.all" :key="`all-${idx}`" class="cond-row">
            <el-select v-model="c.field" filterable clearable allow-create default-first-option placeholder="字段" style="width: 220px">
              <el-option v-for="f in fields" :key="f.value" :label="f.label" :value="f.value" />
            </el-select>

            <el-select v-model="c.op" placeholder="操作" style="width: 140px">
              <el-option v-for="op in OPS" :key="op" :label="op" :value="op" />
            </el-select>

            <template v-if="needsValue(c.op)">
              <el-select
                v-if="isListOp(c.op)"
                v-model="c.value as any"
                multiple
                filterable
                clearable
                allow-create
                default-first-option
                collapse-tags
                placeholder="值（多个）"
                style="width: 260px"
              />
              <el-input v-else v-model="c.value as any" placeholder="值" style="width: 260px" clearable />
            </template>
            <span v-else class="cond-value-muted">（无需值）</span>

            <el-button link type="danger" @click="remove('all', idx)">删除</el-button>
          </div>
        </el-tab-pane>

        <el-tab-pane label="any（任一满足）" name="any">
          <div class="cond-actions">
            <el-button size="small" type="primary" @click="add('any')">新增条件</el-button>
            <el-button size="small" @click="clear('any')">清空</el-button>
          </div>

          <div v-for="(c, idx) in local.any" :key="`any-${idx}`" class="cond-row">
            <el-select v-model="c.field" filterable clearable allow-create default-first-option placeholder="字段" style="width: 220px">
              <el-option v-for="f in fields" :key="f.value" :label="f.label" :value="f.value" />
            </el-select>

            <el-select v-model="c.op" placeholder="操作" style="width: 140px">
              <el-option v-for="op in OPS" :key="op" :label="op" :value="op" />
            </el-select>

            <template v-if="needsValue(c.op)">
              <el-select
                v-if="isListOp(c.op)"
                v-model="c.value as any"
                multiple
                filterable
                clearable
                allow-create
                default-first-option
                collapse-tags
                placeholder="值（多个）"
                style="width: 260px"
              />
              <el-input v-else v-model="c.value as any" placeholder="值" style="width: 260px" clearable />
            </template>
            <span v-else class="cond-value-muted">（无需值）</span>

            <el-button link type="danger" @click="remove('any', idx)">删除</el-button>
          </div>
        </el-tab-pane>
      </el-tabs>
    </div>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref, watch } from 'vue';

type Op = 'eq' | 'ne' | 'gt' | 'ge' | 'lt' | 'le' | 'in' | 'notIn' | 'empty' | 'notEmpty' | 'contains';

export type WhenCond = { field: string; op: Op; value?: any };
export type WhenClause = { all?: WhenCond[]; any?: WhenCond[] };
export type FieldOption = { label: string; value: string };

const OPS: Op[] = ['eq', 'ne', 'gt', 'ge', 'lt', 'le', 'in', 'notIn', 'empty', 'notEmpty', 'contains'];

const props = defineProps<{
  modelValue?: WhenClause;
  fields: FieldOption[];
}>();

const emit = defineEmits<{
  (e: 'update:modelValue', v?: WhenClause): void;
}>();

const activeTab = ref<'all' | 'any'>('all');
const enabled = ref(false);
const local = reactive<{ all: WhenCond[]; any: WhenCond[] }>({ all: [], any: [] });

const needsValue = (op: Op) => !['empty', 'notEmpty'].includes(op);
const isListOp = (op: Op) => ['in', 'notIn'].includes(op);

const normalize = () => {
  if (!enabled.value) {
    emit('update:modelValue', undefined);
    return;
  }
  const out: WhenClause = {};
  if (local.all.length) out.all = local.all.map((c) => ({ ...c }));
  if (local.any.length) out.any = local.any.map((c) => ({ ...c }));
  emit('update:modelValue', out.all?.length || out.any?.length ? out : undefined);
};

watch(
  () => props.modelValue,
  (v) => {
    const all = Array.isArray(v?.all) ? v!.all! : [];
    const any = Array.isArray(v?.any) ? v!.any! : [];
    local.all = all.map((c: any) => ({ field: String(c?.field || ''), op: (c?.op || 'eq') as Op, value: c?.value }));
    local.any = any.map((c: any) => ({ field: String(c?.field || ''), op: (c?.op || 'eq') as Op, value: c?.value }));
    enabled.value = local.all.length + local.any.length > 0;
  },
  { immediate: true, deep: true }
);

watch([() => enabled.value, () => local.all, () => local.any], normalize, { deep: true });

const add = (k: 'all' | 'any') => {
  local[k].push({ field: '', op: 'eq', value: '' });
};
const remove = (k: 'all' | 'any', idx: number) => {
  local[k].splice(idx, 1);
};
const clear = (k: 'all' | 'any') => {
  local[k].splice(0, local[k].length);
};
</script>

<style scoped>
.when-builder {
  border: 1px solid var(--el-border-color);
  border-radius: 6px;
  padding: 10px;
  background: var(--el-fill-color-blank);
}
.when-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 10px;
}
.when-title {
  font-weight: 600;
}
.cond-actions {
  display: flex;
  gap: 8px;
  margin-bottom: 8px;
}
.cond-row {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 6px 0;
}
.cond-value-muted {
  color: var(--el-text-color-placeholder);
  width: 260px;
}
</style>
