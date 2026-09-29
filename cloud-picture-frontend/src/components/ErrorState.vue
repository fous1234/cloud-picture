<script setup lang="ts">
import { Alert, Button } from 'ant-design-vue'

defineProps<{ message: string; description?: string }>()
const emit = defineEmits<{ retry: [] }>()
</script>

<template>
  <Alert type="error" show-icon :message="message" :description="description">
    <template #action>
      <Button size="small" @click="emit('retry')">重试</Button>
    </template>
  </Alert>
</template>