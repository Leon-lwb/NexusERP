<template>
  <div v-if="controller.enabled" class="engineering-chat-panel">
    <div class="mb-2 flex flex-wrap items-center gap-2">
      <el-select
        v-model="selectedDeviceId"
        clearable
        :disabled="disabled || !controller.canSelect"
        :loading="controller.deviceLoading"
        placeholder="工程设备（主动选择后启用）"
        style="width: 260px"
        @visible-change="(shown) => shown && controller.loadDevices()"
      >
        <el-option
          v-for="device in controller.devices"
          :key="device.deviceId"
          :value="device.deviceId"
          :disabled="device.status === 'REVOKED'"
          :label="`${device.deviceName || device.deviceId} · ${device.onlineLabel || (device.online ? '在线' : '离线')} · ${device.transportMode || '传输未上报'}`"
        />
      </el-select>
      <el-button size="small" :disabled="disabled || !controller.canSelect" @click="controller.loadDevices">刷新设备</el-button>
    </div>
    <p v-if="selected" class="text-sm text-gray-500">
      实际传输：{{ selected.transportMode || '未上报' }}；实际能力：{{ selected.capabilities?.join('、') || '尚未上报' }}。{{
        engineeringTransport(selected).notice
      }}
    </p>
    <el-alert
      v-if="controller.deviceError || controller.taskError"
      :title="controller.taskError || controller.deviceError"
      type="error"
      :closable="false"
      class="mb-2"
    />
    <EngineeringTaskCard v-for="task in controller.tasks" :key="task.taskId" :task="task" @changed="controller.refresh" />
  </div>
</template>

<script setup lang="ts" name="EngineeringChatPanel">
import { computed } from 'vue';
import { engineeringTransport } from '@/views/ai/engineering/model';
import type { EngineeringChatController } from './useEngineeringChat';
import EngineeringTaskCard from './EngineeringTaskCard.vue';
const props = defineProps<{ controller: EngineeringChatController; disabled?: boolean }>();
const selectedDeviceId = computed({ get: () => props.controller.selectedDeviceId, set: (value: string) => props.controller.selectDevice(value) });
const selected = computed(() => props.controller.devices.find((device) => device.deviceId === props.controller.selectedDeviceId));
</script>

<style scoped>
.engineering-chat-panel {
  max-height: 320px;
  overflow: auto;
  margin-bottom: 8px;
  padding: 8px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 4px;
}
</style>
