<script setup lang="ts">
import { RouterView } from 'vue-router'
import { ConfigProvider } from 'ant-design-vue'

const theme = {
  token: {
    colorPrimary: '#111827',
    colorInfo: '#111827',
    colorLink: '#111827',
    borderRadius: 10,
    controlHeight: 40,
  },
}
</script>

<template>
  <ConfigProvider :theme="theme">
    <RouterView />
  </ConfigProvider>
</template>
