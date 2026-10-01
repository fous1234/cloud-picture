<script setup lang="ts">
import { RouterView } from 'vue-router'
import { ConfigProvider } from 'ant-design-vue'

const theme = {
  token: {
    colorPrimary: '#111827',
    colorInfo: '#111827',
    colorLink: '#111827',
    colorBgLayout: '#f8f9fa',
    colorBgContainer: '#ffffff',
    colorBorder: '#e5e7eb',
    colorText: '#111827',
    colorTextSecondary: '#4b5563',
    colorTextTertiary: '#9ca3af',
    colorSuccess: '#059669',
    colorWarning: '#d97706',
    colorError: '#dc2626',
    borderRadius: 8,
    controlHeight: 42,
    fontFamily: "Inter, 'Plus Jakarta Sans', -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif",
  },
}
</script>

<template>
  <ConfigProvider :theme="theme">
    <RouterView />
  </ConfigProvider>
</template>
