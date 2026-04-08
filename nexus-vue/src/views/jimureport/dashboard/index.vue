<template>
  <div class="jimureport-container">
    <div v-loading="loading" :style="'height:' + height">
      <iframe
        :src="dashboardUrl"
        frameborder="no"
        style="width: 100%; height: 100%"
        scrolling="auto"
        @load="onIframeLoad"
      />
    </div>
  </div>
</template>

<script setup>
import { getToken } from '@/utils/auth'

const height = ref(document.documentElement.clientHeight - 94.5 + 'px')
const loading = ref(true)

const dashboardUrl = computed(() => {
  const token = getToken()
  const baseUrl = import.meta.env.VITE_APP_ENV === 'production' ? '' : 'http://localhost:8080'
  return `${baseUrl}/drag/list?token=${token}`
})

const onIframeLoad = () => {
  loading.value = false
}

onMounted(() => {
  window.onresize = () => {
    height.value = document.documentElement.clientHeight - 94.5 + 'px'
  }
})
</script>

<style lang="scss" scoped>
.jimureport-container {
  padding: 0;
  margin: 0;
  height: 100%;
  
  iframe {
    border: none;
  }
}
</style>
