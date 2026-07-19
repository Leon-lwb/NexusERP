<template>
  <div>
    <svg-icon icon-class="documentation" @click="click" />
  </div>
</template>

<script setup lang="ts">
const url = ref('http://nexuserp.website/index');

const click = () => {
  window.open(url.value, '_blank');
};
</script>
