<template>
  <div>
    <svg-icon icon-class="github" @click="goto" />
  </div>
</template>

<script setup lang="ts">
const url = ref('https://gitee.com/helloworld_wh18/nexus-erp');

function goto() {
  window.open(url.value);
}
</script>
