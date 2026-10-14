<template>
  <div v-if="plan.pages.length" class="report-paper-view">
    <div class="paper-toolbar">
      <span>共 {{ plan.pages.length }} 页 · {{ plan.rowCount }} 条明细</span
      ><el-select v-model="zoom" style="width: 105px"
        ><el-option v-for="z in [0.5, 0.75, 1, 1.25]" :key="z" :label="Math.round(z * 100) + '%'" :value="z" /></el-select
      ><el-pagination v-model:current-page="pageNo" :page-size="1" :total="plan.pages.length" layout="prev, pager, next" />
    </div>
    <div class="paper-scroll">
      <div class="paper-space" :style="{ width: paper.width * ratio * zoom + 'px', height: paper.height * ratio * zoom + 'px' }">
        <div class="paper" :style="{ width: paper.width * ratio + 'px', height: paper.height * ratio + 'px', transform: 'scale(' + zoom + ')' }">
          <div v-for="(cell, index) in paper.cells" :key="index" class="paper-cell" :style="cellStyle(cell)">
            <div v-for="(line, lineIndex) in cell.lines" :key="lineIndex">{{ line || ' ' }}</div>
          </div>
          <!-- 固定计划中的私有PNG与PDF使用相同坐标。 -->
          <img
            v-for="(media, index) in paper.media || []"
            :key="'media-' + index"
            :src="'data:image/png;base64,' + plan.rasters?.[media.resource]?.png"
            alt="报表图片或业务条码"
            :style="{
              position: 'absolute',
              left: media.x * ratio + 'px',
              top: media.y * ratio + 'px',
              width: media.width * ratio + 'px',
              height: media.height * ratio + 'px'
            }"
          />
        </div>
      </div>
    </div>
  </div>
</template>
<script setup lang="ts">
/** 原生报表页面组件，沿用权限、精确值和固定契约行为。 */

import { ref, computed, watch } from 'vue';
import type { RenderPlan, RenderCell } from '@/types/report';
/**
 * 保存当前页面或测试的props状态。
 */
const props = defineProps<{
  /** 保存当前 `plan` 值。 */
  plan: RenderPlan;
}>();
/**
 * 保存当前页面或测试的ratio状态。
 */
const ratio = 4 / 3;
/**
 * 保存当前页面或测试的zoom状态。
 */
const zoom = ref(0.75);
/**
 * 保存当前页面或测试的pageNo状态。
 */
const pageNo = ref(1);
/**
 * 保存当前页面或测试的paper状态。
 */
const paper = computed(() => props.plan.pages[Math.min(pageNo.value - 1, props.plan.pages.length - 1)]);
watch(
  () => props.plan,
  () => {
    pageNo.value = 1;
  }
);
/** 处理当前报表的 `cellStyle` 操作。 */
function cellStyle(cell: RenderCell) {
  return {
    left: cell.x * ratio + 'px',
    top: cell.y * ratio + 'px',
    width: cell.width * ratio + 'px',
    height: cell.height * ratio + 'px',
    fontSize: cell.style.fontSize * ratio + 'px',
    fontWeight: cell.style.bold ? '700' : '400',
    textAlign: cell.style.align.toLowerCase() as 'left' | 'center' | 'right',
    color: cell.style.color,
    backgroundColor: cell.style.background,
    lineHeight: '1.2'
  };
}
</script>
<style scoped>
.paper-toolbar {
  display: flex;
  align-items: center;
  gap: 16px;
  flex-wrap: wrap;
  padding: 12px 0;
}
.paper-scroll {
  overflow: auto;
  padding: 18px;
  background: #e9edf2;
  border-radius: 8px;
}
.paper-space {
  margin: auto;
}
.paper {
  position: relative;
  background: #fff;
  box-shadow: 0 3px 16px #0002;
  transform-origin: top left;
}
.paper-cell {
  position: absolute;
  box-sizing: border-box;
  border: 0.5px solid #d2d8e0;
  padding: 5.33px;
  overflow: hidden;
  white-space: pre;
  font-family: SimHei, 'Noto Sans CJK SC', sans-serif;
}
</style>
