<script setup lang="ts">
import { RouterLink } from 'vue-router'
import type { RouteLocationRaw } from 'vue-router'

/**
 * 面包屑（路径栏）：每级带 to 即可点，最后一级为当前页不可点。
 * 全站统一样式，页面只需传 items。
 */
defineProps<{
  items: { label: string; to?: RouteLocationRaw }[]
}>()
</script>

<template>
  <nav class="breadcrumb" aria-label="面包屑">
    <template v-for="(item, index) in items" :key="`${index}-${item.label}`">
      <span v-if="index > 0" class="breadcrumb-sep" aria-hidden="true">/</span>
      <RouterLink v-if="item.to" class="breadcrumb-link" :to="item.to">{{ item.label }}</RouterLink>
      <span v-else class="breadcrumb-current" aria-current="page">{{ item.label }}</span>
    </template>
  </nav>
</template>

<style scoped>
.breadcrumb {
  display: flex;
  align-items: center;
  min-width: 0;
  overflow: hidden;
  color: var(--cp-text-muted);
  font-size: 12px;
  white-space: nowrap;
}

.breadcrumb-link {
  color: inherit;
  transition: color 0.15s ease;
}

.breadcrumb-link:hover {
  color: var(--cp-text);
  text-decoration: underline;
  text-underline-offset: 2px;
}

.breadcrumb-sep {
  padding: 0 6px;
  color: var(--cp-border);
}

.breadcrumb-current {
  overflow: hidden;
  color: var(--cp-text);
  font-weight: 500;
  text-overflow: ellipsis;
}
</style>