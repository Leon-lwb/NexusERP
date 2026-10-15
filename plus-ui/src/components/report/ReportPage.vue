<template>
  <section class="report-page" :aria-label="title">
    <header class="report-page__header">
      <div class="report-page__identity">
        <span class="report-page__icon" aria-hidden="true"><span :class="icon" /></span>
        <div class="report-page__heading">
          <div class="report-page__eyebrow">{{ eyebrow }}</div>
          <div class="report-page__title-row">
            <h1>{{ title }}</h1>
            <slot name="badge" />
          </div>
          <p v-if="description" class="report-page__description">{{ description }}</p>
        </div>
      </div>
      <div v-if="$slots.actions" class="report-page__actions"><slot name="actions" /></div>
    </header>
    <div class="report-page__content"><slot /></div>
  </section>
</template>
<script setup lang="ts">
/** 统一报表工作台页头与响应式内容容器，不包含业务请求或权限判断。 */
withDefaults(
  defineProps<{
    /** 当前页面标题。 */
    title: string;
    /** 标题下方的简短操作说明。 */
    description?: string;
    /** 页面所属工作区名称。 */
    eyebrow?: string;
    /** UnoCSS 图标类名。 */
    icon?: string;
  }>(),
  { description: '', eyebrow: '报表工作台', icon: 'i-ep-data-analysis' }
);
</script>
<style lang="scss" src="./report-ui.scss"></style>
