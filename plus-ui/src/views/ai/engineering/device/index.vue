<template>
  <div class="app-container">
    <el-alert v-if="!ENGINEERING_ENABLED" :title="ENGINEERING_DISABLED_NOTICE" type="info" :closable="false" class="mb-3" />
    <el-alert v-if="error" :title="error" type="error" :closable="false" class="mb-3" />
    <el-card v-if="ENGINEERING_ENABLED" shadow="never">
      <template #header>
        <div class="flex flex-wrap items-center justify-between gap-3">
          <span>工程设备</span>
          <div>
            <el-button v-hasPermi="['ai:engineering:device']" type="primary" icon="Plus" @click="pairDevice">配对新设备</el-button>
            <el-button icon="Refresh" @click="refresh">刷新</el-button>
          </div>
        </div>
      </template>
      <el-table v-loading="loading" :data="devices" border empty-text="尚未配对工程设备，请生成配对码后在连接器中兑换">
        <el-table-column label="设备" min-width="180"
          ><template #default="{ row }">{{ row.deviceName || row.deviceId }}</template></el-table-column
        >
        <el-table-column label="状态" width="140"
          ><template #default="{ row }"
            ><el-tag :type="row.online ? 'success' : 'info'">{{ row.onlineLabel || (row.online ? '在线' : '离线') }}</el-tag></template
          ></el-table-column
        >
        <el-table-column label="实际传输 / 诊断" min-width="300"
          ><template #default="{ row }"
            ><p>{{ engineeringTransport(row).label }}</p>
            <span class="text-sm text-gray-500">{{ engineeringTransport(row).notice }}</span></template
          ></el-table-column
        >
        <el-table-column prop="connectorVersion" label="连接器版本" min-width="130" />
        <el-table-column prop="cliVersion" label="CLI 版本" min-width="120" />
        <el-table-column label="编辑器连接" min-width="130"
          ><template #default="{ row }">{{
            row.editorAvailable === true ? '已连接' : row.editorAvailable === false ? '不可用' : '未上报'
          }}</template></el-table-column
        >
        <el-table-column prop="editorVersion" label="编辑器版本" min-width="130" />
        <el-table-column label="实际能力" min-width="260"
          ><template #default="{ row }"
            ><span v-if="!row.capabilities?.length">尚未上报</span
            ><el-tag v-for="capability in row.capabilities" :key="capability" class="mr-1 mb-1" type="info">{{ capability }}</el-tag></template
          ></el-table-column
        >
        <el-table-column label="最近心跳" min-width="190"
          ><template #default="{ row }">{{ engineeringTime(row.lastHeartbeatAt) }}</template></el-table-column
        >
        <el-table-column label="操作" width="100" fixed="right"
          ><template #default="{ row }"
            ><el-button v-hasPermi="['ai:engineering:device']" link type="danger" @click="unpair(row)">解绑</el-button></template
          ></el-table-column
        >
      </el-table>
    </el-card>
    <el-dialog v-model="pairingVisible" title="配对工程设备" width="640px" @closed="clearPairing">
      <el-alert title="在本机连接器中输入以下一次性配对码；网页无需填写内网地址或 EDA Token" type="info" :closable="false" class="mb-4" />
      <template v-if="pairing">
        <el-input :model-value="pairing.pairingCode" readonly type="textarea" :rows="3" />
        <p>有效期至 {{ engineeringTime(pairing.expiresAt) }}（剩余 {{ remaining }} 秒）</p>
        <el-button type="primary" @click="copyPairing">复制配对码</el-button>
      </template>
      <el-empty v-else description="配对码已兑换或过期，请核对设备列表" />
      <template #footer><el-button @click="pairingVisible = false">关闭</el-button></template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts" name="EngineeringDevice">
import { getCurrentInstance, onBeforeUnmount, ref, type ComponentInternalInstance } from 'vue';
import {
  listEngineeringDevices,
  createEngineeringPairingCode,
  unpairEngineeringDevice,
  type EngineeringDevice,
  type EngineeringPairingCode
} from '@/api/ai/engineering';
import { engineeringError, engineeringTime, engineeringTransport } from '../model';
import { useEngineeringPolling } from '../useEngineeringPolling';
import { ENGINEERING_ENABLED, ENGINEERING_DISABLED_NOTICE } from '../engineering-config';
const { proxy } = getCurrentInstance() as ComponentInternalInstance;
const devices = ref<EngineeringDevice[]>([]);
const loading = ref(false);
const error = ref('');
const pairing = ref<EngineeringPairingCode>();
const pairingVisible = ref(false);
const remaining = ref(0);
let baseline = new Set<string>();
let expiryTimer: ReturnType<typeof setInterval> | undefined;
/** 过期或兑换完成后立即清除一次性配对码。 */
function clearPairing() {
  pairing.value = undefined;
  remaining.value = 0;
  if (expiryTimer) clearInterval(expiryTimer);
  expiryTimer = undefined;
}
/** 设备读取遵循页面生命周期，保留服务端真实在线状态。 */
async function loadDevices(signal: AbortSignal): Promise<boolean> {
  if (!ENGINEERING_ENABLED) return false;
  loading.value = devices.value.length === 0;
  try {
    const result = await listEngineeringDevices(signal);
    if (!signal.aborted) {
      devices.value = result.data || [];
      error.value = '';
      if (pairing.value && devices.value.some((device) => !baseline.has(device.deviceId))) clearPairing();
    }
  } finally {
    if (!signal.aborted) loading.value = false;
  }
  return true;
}
const { refresh } = useEngineeringPolling(loadDevices, (cause) => {
  loading.value = false;
  error.value = engineeringError(cause).message;
});
/** 向后端申请配对码，倒计时仅用于展示，兑换鉴权由服务端决定。 */
async function pairDevice() {
  try {
    clearPairing();
    baseline = new Set(devices.value.map((device) => device.deviceId));
    const result = await createEngineeringPairingCode();
    pairing.value = result.data;
    pairingVisible.value = true;
    const tick = () => {
      remaining.value = Math.max(0, Math.ceil((Date.parse(pairing.value?.expiresAt || '') - Date.now()) / 1000));
      if (!Number.isFinite(remaining.value) || !remaining.value) clearPairing();
    };
    tick();
    if (pairing.value) expiryTimer = setInterval(tick, 1000);
    await refresh();
  } catch (cause) {
    error.value = engineeringError(cause).message;
  }
}
/** 复制行为只在用户点击时触发，不记录配对码。 */
async function copyPairing() {
  if (!pairing.value) return;
  try {
    await navigator.clipboard.writeText(pairing.value.pairingCode);
    proxy?.$modal.msgSuccess('配对码已复制');
  } catch {
    proxy?.$modal.msgWarning('剪贴板不可用，请手动复制配对码');
  }
}
/** 解绑前明确确认，完成后重新读取设备。 */
async function unpair(device: EngineeringDevice) {
  try {
    await proxy?.$modal.confirm(`确认解绑设备“${device.deviceName || device.deviceId}”？解绑后该设备无法继续回报或上传。`);
  } catch {
    return;
  }
  try {
    await unpairEngineeringDevice(device.deviceId);
    proxy?.$modal.msgSuccess('设备已解绑');
    await refresh();
  } catch (cause) {
    error.value = engineeringError(cause).message;
  }
}
onBeforeUnmount(clearPairing);
</script>
