<template>
  <div>
    <svg-icon icon-class="github" @click="click" />
  </div>
</template>

<script setup lang="ts">
const url = ref('https://github.com/wanghuanla/nexus-erp');

const click = () => {
  window.open(url.value, '_blank');
};
</script>
