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

</style>
