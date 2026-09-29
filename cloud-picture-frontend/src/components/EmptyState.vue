<script setup lang="ts">
import { Empty } from 'ant-design-vue'

defineProps<{ description: string }>()
</script>

<template>
  <div class="empty-state">
    <Empty :description="description">
      <slot />
    </Empty>
  </div>
</template>

<style scoped>
.empty-state {
  padding: 48px 0;
  background: var(--cp-bg-soft);
  border-radius: var(--cp-radius);
}
</style>