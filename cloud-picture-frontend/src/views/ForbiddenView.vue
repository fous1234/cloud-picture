<script setup lang="ts">
import { Button, Result } from 'ant-design-vue'
import { useRouter } from 'vue-router'

const router = useRouter()
</script>

<template>
  <div class="cp-container cp-page">
    <Result status="403" title="无权限访问" sub-title="该页面仅管理员可访问，如果你认为这是错误，请联系管理员。">
      <template #extra>
        <Button type="primary" @click="router.push({ name: 'gallery' })">返回图库</Button>
      </template>
    </Result>
  </div>
</template>