<script setup lang="ts">
import { RouterView } from 'vue-router'
import AppHeader from '../components/AppHeader.vue'
import UploadModal from '../components/UploadModal.vue'
import ImageEditModal from '../components/ImageEditModal.vue'
import ReviewModal from '../components/ReviewModal.vue'
import { uploadOpen } from '../stores/ui'
</script>

<template>
  <div class="layout">
    <AppHeader />
    <main class="layout-main">
      <RouterView />
    </main>
    <footer class="layout-footer">
      <div class="cp-container">云图库 · 毕业设计演示项目</div>
    </footer>

    <UploadModal v-model:open="uploadOpen" />
    <ImageEditModal />
    <ReviewModal />
  </div>
</template>

<style scoped>
.layout {
  display: flex;
  flex-direction: column;
  min-height: 100vh;
}

.layout-main {
  flex: 1;
}

.layout-footer {
  padding: 24px 0;
  border-top: 1px solid var(--cp-border);
  color: var(--cp-text-soft);
  font-size: 12px;
  text-align: center;
}

@media (max-width: 767px) {
  .layout-footer {
    display: none;
  }
}
</style>
