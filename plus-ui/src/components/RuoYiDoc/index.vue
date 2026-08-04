<template>
  <div>
    <svg-icon icon-class="documentation" @click="click" />
  </div>
</template>

<script setup lang="ts">
const url = ref('/manual/index.html');

const click = () => {
  window.open(url.value, '_blank');
};
</script>
