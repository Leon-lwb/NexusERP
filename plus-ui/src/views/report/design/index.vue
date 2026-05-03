<template>
  <div>
    <i-frame :src="url"></i-frame>
  </div>
</template>

<script setup lang="ts">
import { getToken } from '@/utils/auth';

const baseUrl = import.meta.env.VITE_APP_JMUREPORT_DESIGN_URL;
const token = getToken();
const url = ref(`${baseUrl}?token=${token}`);
</script>
