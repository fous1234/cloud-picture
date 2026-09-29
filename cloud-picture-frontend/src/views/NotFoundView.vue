<script setup lang="ts">
import { Button, Result } from 'ant-design-vue'
import { useRouter } from 'vue-router'

const router = useRouter()
</script>

<template>
  <div class="cp-container cp-page">
    <Result status="404" title="页面不存在" sub-title="你访问的页面已被移除或地址有误。">
      <template #extra>
        <Button type="primary" @click="router.push({ name: 'gallery' })">返回图库</Button>
      </template>
    </Result>
  </div>
</template>