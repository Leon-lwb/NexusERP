<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { ElMessage, ElMessageBox } from 'element-plus';
import { useFlowStore } from './useFlowStore';
import NodePalette from './NodePalette.vue';
import FlowCanvas from './FlowCanvas.vue';
import NodeParameterForm from './NodeParameterForm.vue';
import { getNodeTypeConfig } from '../nodeTypes';
import type { NodeParamSchema, NodeTypeMeta } from '@/api/ai/flow';
import { addFlow, getFlow, listNodeTypes, publishFlow, runFlow, stopFlowRun, updateFlow } from '@/api/ai/flow';

/**
 * 流程设计器页面
 * <p>顶栏：返回/保存/发布/运行/停止；左侧节点面板；中间画布；右侧属性面板。
 * 运行态通过 ai-sse-message 事件总线消费（AI_FLOW_NODE_START/END/STATUS），节点实时高亮</p>
 */

const route = useRoute();
const router = useRouter();
const store = useFlowStore();

const loading = ref(false);
const saving = ref(false);
const running = ref(false);
const runInput = ref('{}');
const runOutput = ref('');
const runError = ref('');
/** 节点类型元数据（schema 驱动表单） */
const nodeTypes = ref<NodeTypeMeta[]>([]);
/** 画布实例引用（删除节点） */
const canvasRef = ref();

/** 当前节点 schema（选中节点的类型参数定义） */
const currentNodeSchemas = computed<NodeParamSchema[]>(() => {
  const node = store.selectedNode;
  if (!node) return [];
  const meta = nodeTypes.value.find((t) => t.type === node.data.type);
  return meta?.params ?? [];
});

/** 节点高级设置是否展开 */
const advancedExpanded = ref<string[]>([]);

/** 加载节点类型元数据 */
async function loadNodeTypes() {
  try {
    const res = await listNodeTypes();
    nodeTypes.value = res.data ?? [];
  } catch (e) {
    console.error('[Flow] 加载节点类型失败:', e);
  }
}

/** 加载流程（编辑模式） */
async function loadFlow(flowId: string | number) {
  loading.value = true;
  try {
    const res = await getFlow(flowId);
    const flow = res.data;
    store.flowName = flow.flowName;
    store.flowId = flow.id;
    if (flow.configJson) {
      store.fromConfigJson(flow.configJson);
    }
  } finally {
    loading.value = false;
  }
}

/** 保存流程 */
async function saveFlow(showTip = true) {
  if (!store.flowName.trim()) {
    ElMessage.warning('请输入流程名称');
    return false;
  }
  saving.value = true;
  try {
    const configJson = store.toConfigJson();
    const data = {
      id: store.flowId ?? undefined,
      flowName: store.flowName,
      configJson,
      triggerType: 'MANUAL',
      maxRetry: 1
    };
    let res;
    if (store.flowId) {
      res = await updateFlow(data);
    } else {
      res = await addFlow(data);
      store.flowId = res.data;
    }
    if (showTip) {
      ElMessage.success('保存成功');
    }
    return true;
  } catch (e: any) {
    ElMessage.error('保存失败: ' + (e?.message ?? e));
    return false;
  } finally {
    saving.value = false;
  }
}

/** 发布流程 */
async function publish() {
  if (!store.flowId) {
    ElMessage.warning('请先保存流程');
    return;
  }
  if (!(await saveFlow(false))) return;
  try {
    await publishFlow(store.flowId);
    ElMessage.success('发布成功（版本已+1）');
  } catch (e: any) {
    ElMessage.error('发布失败: ' + (e?.message ?? e));
  }
}

/** 运行流程 */
async function run() {
  if (!store.flowId) {
    ElMessage.warning('请先保存流程');
    return;
  }
  if (!(await saveFlow(false))) return;
  running.value = true;
  runOutput.value = '';
  runError.value = '';
  store.clearNodeStatus();
  try {
    const res = await runFlow(store.flowId, runInput.value.trim() || undefined);
    store.currentInstanceId = res.data;
    ElMessage.success('流程已启动，实例ID: ' + res.data);
  } catch (e: any) {
    running.value = false;
    ElMessage.error('运行失败: ' + (e?.message ?? e));
  }
}

/** 停止流程 */
async function stop() {
  if (!store.currentInstanceId) return;
  try {
    await stopFlowRun(store.currentInstanceId);
    ElMessage.info('已请求停止（节点边界生效）');
  } catch (e: any) {
    ElMessage.warning(e?.message ?? '停止失败');
  }
}

/** SSE 事件消费（流程运行态高亮） */
function onSseMessage(e: Event) {
  const detail = (e as CustomEvent).detail;
  if (!detail || !detail.type) return;
  // 只处理当前实例的事件
  if (detail.content) {
    let payload: any = null;
    try {
      payload = JSON.parse(detail.content);
    } catch {
      return;
    }
    if (payload.instanceId !== store.currentInstanceId) return;

    if (detail.type === 'AI_FLOW_NODE_START') {
      store.setNodeStatus(payload.nodeId, 'RUNNING');
    } else if (detail.type === 'AI_FLOW_NODE_END') {
      store.setNodeStatus(payload.nodeId, payload.status === 'SUCCEEDED' ? 'SUCCEEDED' : 'FAILED');
      if (payload.errorMsg) {
        ElMessage.warning(`节点 ${payload.nodeName ?? payload.nodeId} 失败: ${payload.errorMsg}`);
      }
    } else if (detail.type === 'AI_FLOW_STATUS') {
      if (payload.status === 'SUCCEEDED') {
        runOutput.value = '流程执行成功';
        ElMessage.success('流程执行成功');
      } else if (payload.status === 'FAILED') {
        runError.value = payload.errorMsg ?? '流程执行失败';
        ElMessage.error('流程执行失败: ' + (payload.errorMsg ?? ''));
      } else if (payload.status === 'STOPPED') {
        ElMessage.info('流程已停止');
      }
      running.value = false;
    }
  }
}

/** 节点参数值更新（写回 store 的 config） */
function onConfigUpdate(value: unknown) {
  const node = store.selectedNode;
  if (!node) return;
  const index = store.nodes.findIndex((n) => n.id === node.id);
  if (index >= 0) {
    const updated = { ...store.nodes[index] };
    updated.data = { ...updated.data, config: value as Record<string, unknown> };
    store.nodes[index] = updated;
  }
}

/** 更新节点名称 */
function onLabelUpdate(label: string) {
  const node = store.selectedNode;
  if (!node) return;
  const index = store.nodes.findIndex((n) => n.id === node.id);
  if (index >= 0) {
    store.nodes[index] = { ...store.nodes[index], data: { ...store.nodes[index].data, label } };
  }
}

onMounted(async () => {
  await loadNodeTypes();
  const flowId = route.query.id;
  if (flowId) {
    await loadFlow(String(flowId));
  }
  window.addEventListener('ai-sse-message', onSseMessage);
});

onBeforeUnmount(() => {
  window.removeEventListener('ai-sse-message', onSseMessage);
});

function goBack() {
  router.push('/ai/flow/index');
}
</script>

<template>
  <div class="flow-designer">
    <!-- 顶栏 -->
    <div class="flow-designer__toolbar">
      <el-button @click="goBack">返回</el-button>
      <el-input v-model="store.flowName" placeholder="流程名称" class="flow-designer__name" maxlength="50" />
      <div class="flow-designer__spacer" />
      <el-button :loading="saving" @click="saveFlow()">保存</el-button>
      <el-button type="primary" @click="publish">发布</el-button>
      <el-button type="success" :disabled="running" @click="run">运行</el-button>
      <el-button type="danger" :disabled="!running || !store.currentInstanceId" @click="stop"> 停止 </el-button>
    </div>

    <div class="flow-designer__body">
      <!-- 左：节点面板 -->
      <NodePalette />
      <!-- 中：画布 -->
      <FlowCanvas
        ref="canvasRef"
        :nodes="store.nodes"
        :edges="store.edges"
        @update:nodes="(n) => (store.nodes = n)"
        @update:edges="(e) => (store.edges = e)"
        @node-click="(n) => (store.selectedNodeId = n ? n.id : null)"
      />
      <!-- 右：属性面板 -->
      <div class="flow-designer__property">
        <template v-if="store.selectedNode">
          <div class="flow-designer__property-header">
            <el-input :model-value="store.selectedNode.data.label" placeholder="节点名称" @input="onLabelUpdate" />
          </div>
          <el-collapse v-model="advancedExpanded">
            <el-collapse-item title="节点配置" name="config">
              <div v-if="currentNodeSchemas.length" class="flow-designer__property-form">
                <NodeParameterForm
                  v-for="schema in currentNodeSchemas"
                  :key="schema.name"
                  :schema="schema"
                  :model-value="store.selectedNode.data.config?.[schema.name] ?? schema.defaultValue"
                  :node-type="store.selectedNode.data.type"
                  @update:model-value="
                    (v) => {
                      const config = { ...(store.selectedNode.data.config ?? {}) };
                      config[schema.name] = v;
                      onConfigUpdate(config);
                    }
                  "
                />
              </div>
              <el-empty v-else description="该节点暂无参数配置" :image-size="60" />
            </el-collapse-item>
            <el-collapse-item title="高级设置" name="advanced">
              <el-form label-width="110px">
                <el-form-item label="失败策略">
                  <el-select
                    :model-value="store.selectedNode.data.onError ?? 'STOP'"
                    @update:model-value="
                      (v) => {
                        const node = store.selectedNode;
                        const idx = store.nodes.findIndex((n) => n.id === node?.id);
                        if (idx >= 0) {
                          store.nodes[idx] = { ...store.nodes[idx], data: { ...store.nodes[idx].data, onError: v } };
                        }
                      }
                    "
                    style="width: 100%"
                  >
                    <el-option label="STOP（停止流程）" value="STOP" />
                    <el-option label="CONTINUE（透传继续）" value="CONTINUE" />
                  </el-select>
                </el-form-item>
                <el-form-item label="失败重试">
                  <el-switch
                    :model-value="store.selectedNode.data.retryOnFail ?? false"
                    @update:model-value="
                      (v: boolean | string | number) => {
                        const node = store.selectedNode;
                        const idx = store.nodes.findIndex((n) => n.id === node?.id);
                        if (idx >= 0) {
                          store.nodes[idx] = { ...store.nodes[idx], data: { ...store.nodes[idx].data, retryOnFail: Boolean(v) } };
                        }
                      }
                    "
                  />
                </el-form-item>
                <el-form-item label="重试次数">
                  <el-input-number
                    :model-value="store.selectedNode.data.retryMaxTries ?? 3"
                    :min="2"
                    :max="5"
                    @update:model-value="
                      (v) => {
                        const node = store.selectedNode;
                        const idx = store.nodes.findIndex((n) => n.id === node?.id);
                        if (idx >= 0) {
                          store.nodes[idx] = { ...store.nodes[idx], data: { ...store.nodes[idx].data, retryMaxTries: v } };
                        }
                      }
                    "
                  />
                </el-form-item>
                <el-form-item label="超时(秒)">
                  <el-input-number
                    :model-value="store.selectedNode.data.timeoutSeconds ?? 600"
                    :min="1"
                    @update:model-value="
                      (v) => {
                        const node = store.selectedNode;
                        const idx = store.nodes.findIndex((n) => n.id === node?.id);
                        if (idx >= 0) {
                          store.nodes[idx] = { ...store.nodes[idx], data: { ...store.nodes[idx].data, timeoutSeconds: v } };
                        }
                      }
                    "
                  />
                </el-form-item>
              </el-form>
            </el-collapse-item>
          </el-collapse>
          <el-button type="danger" plain size="small" class="flow-designer__delete" @click="canvasRef?.onRemoveNode(store.selectedNode.id)">
            删除节点
          </el-button>
        </template>
        <el-empty v-else description="选择节点查看配置" :image-size="60" />
      </div>
    </div>

    <!-- 底部：运行面板（常驻，运行前可输入入参） -->
    <div class="flow-designer__run-panel">
      <div class="flow-designer__run-input">
        <span class="flow-designer__run-label">运行入参 (JSON)</span>
        <el-input v-model="runInput" type="textarea" :rows="2" placeholder='{"name":"张三","amount":500}' />
      </div>
      <div v-if="runOutput" class="flow-designer__run-result">✅ {{ runOutput }}</div>
      <div v-if="runError" class="flow-designer__run-error">❌ {{ runError }}</div>
    </div>
  </div>
</template>

<style scoped>
.flow-designer {
  display: flex;
  flex-direction: column;
  height: calc(100vh - 84px);
}

.flow-designer__toolbar {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  background: #fff;
  border-bottom: 1px solid #e4e7ed;
}

.flow-designer__name {
  width: 220px;
}

.flow-designer__spacer {
  flex: 1;
}

.flow-designer__body {
  flex: 1;
  display: flex;
  min-height: 0;
}

.flow-designer__property {
  width: 300px;
  background: #fff;
  border-left: 1px solid #e4e7ed;
  padding: 12px;
  overflow-y: auto;
}

.flow-designer__property-header {
  margin-bottom: 12px;
}

.flow-designer__property-form {
  padding: 4px 0;
}

.flow-designer__delete {
  width: 100%;
  margin-top: 12px;
}

.flow-designer__run-panel {
  padding: 8px 12px;
  background: #fff;
  border-top: 1px solid #e4e7ed;
  display: flex;
  gap: 16px;
  align-items: flex-start;
}

.flow-designer__run-input {
  flex: 1;
  display: flex;
  gap: 8px;
  align-items: flex-start;
}

.flow-designer__run-label {
  font-size: 12px;
  color: #909399;
  white-space: nowrap;
  padding-top: 4px;
}

.flow-designer__run-result {
  color: #52c41a;
  font-size: 13px;
  padding-top: 4px;
  white-space: pre-wrap;
  max-width: 400px;
}

.flow-designer__run-error {
  color: #fa541c;
  font-size: 13px;
  padding-top: 4px;
  white-space: pre-wrap;
  max-width: 400px;
}
</style>
