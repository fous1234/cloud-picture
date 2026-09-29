<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'
import { Avatar, Button, Drawer, Dropdown, Input, Menu, MenuItem, message } from 'ant-design-vue'
import {
  AppstoreOutlined,
  CloudUploadOutlined,
  DownOutlined,
  MenuOutlined,
  PictureOutlined,
  SearchOutlined,
  UserOutlined,
} from '@ant-design/icons-vue'
import { logout } from '../api/user'
import { clearSession, isAdmin, isLoggedIn, session } from '../stores/session'
import { openUpload } from '../stores/ui'

const route = useRoute()
const router = useRouter()

const keyword = ref('')
const drawerOpen = ref(false)

watch(
  () => route.query.q,
  (value) => {
    keyword.value = typeof value === 'string' ? value : ''
  },
  { immediate: true },
)

const navLinks = computed(() => {
  const links = [{ name: 'gallery', label: '图库' }]
  if (isAdmin()) {
    links.push({ name: 'admin-images', label: '图片审核' })
    links.push({ name: 'admin-users', label: '用户管理' })
  }
  links.push({ name: 'my-images', label: '我的上传' })
  return links
})

function goto(name: string) {
  drawerOpen.value = false
  router.push({ name })
}

function submitSearch() {
  drawerOpen.value = false
  router.push({ name: 'gallery', query: keyword.value ? { q: keyword.value } : {} })
}

async function handleLogout() {
  try {
    await logout()
  } catch {
    // 退出接口失败也要清掉本地会话，避免停留在“已登录但令牌已失效”的状态
  }
  clearSession()
  message.success('已退出登录')
  router.push({ name: 'login' })
}
</script>

<template>
  <div class="app-header">
    <header class="header">
    <div class="cp-container header-inner">
      <RouterLink :to="{ name: 'gallery' }" class="brand" aria-label="云图库首页">
        <img class="brand-logo" src="/assets/cloud-picture-logo.svg" alt="云图库" />
      </RouterLink>

      <div class="header-search">
        <Input
          v-model:value="keyword"
          placeholder="搜索图片标题"
          allow-clear
          @press-enter="submitSearch"
        >
          <template #prefix><SearchOutlined /></template>
        </Input>
      </div>

      <nav class="header-nav" aria-label="主导航">
        <template v-if="isLoggedIn()">
          <RouterLink
            v-for="link in navLinks"
            :key="link.name"
            class="nav-link"
            :to="{ name: link.name }"
          >
            {{ link.label }}
          </RouterLink>
          <Button type="primary" class="upload-btn" @click="openUpload">
            <template #icon><CloudUploadOutlined /></template>
            上传图片
          </Button>
          <Dropdown placement="bottomRight" :trigger="['click']">
            <button class="user-trigger" type="button">
              <Avatar :src="session.user?.avatar || undefined" :size="28">
                {{ (session.user?.name || session.user?.account || 'U').slice(0, 1) }}
              </Avatar>
              <span class="user-name">{{ session.user?.name || session.user?.account }}</span>
              <DownOutlined class="user-caret" />
            </button>
            <template #overlay>
              <Menu>
                <MenuItem key="profile" @click="goto('profile')">
                  <template #icon><UserOutlined /></template>
                  个人资料
                </MenuItem>
                <MenuItem key="logout" danger @click="handleLogout">退出登录</MenuItem>
              </Menu>
            </template>
          </Dropdown>
        </template>

        <template v-else>
          <RouterLink class="nav-link" :to="{ name: 'login' }">登录</RouterLink>
          <RouterLink :to="{ name: 'register' }">
            <Button type="primary">注册</Button>
          </RouterLink>
        </template>
      </nav>

      <Button
        class="header-search-btn"
        type="text"
        aria-label="搜索图片"
        @click="drawerOpen = true"
      >
        <template #icon><SearchOutlined /></template>
      </Button>
      <Button class="header-menu-btn" type="text" aria-label="打开导航菜单" @click="drawerOpen = true">
        <template #icon><MenuOutlined /></template>
      </Button>
    </div>

    <Drawer v-model:open="drawerOpen" title="导航" placement="right" :width="280">
      <Input
        v-model:value="keyword"
        placeholder="搜索图片标题"
        allow-clear
        @press-enter="submitSearch"
      >
        <template #prefix><SearchOutlined /></template>
      </Input>
      <div class="drawer-links">
        <template v-if="isLoggedIn()">
          <button
            v-for="link in navLinks"
            :key="link.name"
            class="drawer-link"
            type="button"
            @click="goto(link.name)"
          >
            {{ link.label }}
          </button>
          <Button type="primary" block @click="drawerOpen = false; openUpload()">上传图片</Button>
          <Button block @click="goto('profile')">个人资料</Button>
          <Button block danger @click="drawerOpen = false; handleLogout()">退出登录</Button>
        </template>
        <template v-else>
          <Button block @click="goto('login')">登录</Button>
          <Button block type="primary" @click="goto('register')">注册</Button>
        </template>
      </div>
    </Drawer>

    </header>

    <nav v-if="isLoggedIn()" class="mobile-tabbar" aria-label="移动端导航">
      <RouterLink :to="{ name: 'gallery' }" class="mobile-tab">
        <AppstoreOutlined />
        <span>图库</span>
      </RouterLink>
      <RouterLink :to="{ name: 'my-images' }" class="mobile-tab">
        <PictureOutlined />
        <span>我的上传</span>
      </RouterLink>
      <button class="mobile-tab mobile-upload" type="button" @click="openUpload">
        <CloudUploadOutlined />
        <span>上传</span>
      </button>
      <RouterLink :to="{ name: 'profile' }" class="mobile-tab">
        <UserOutlined />
        <span>我的</span>
      </RouterLink>
    </nav>
  </div>
</template>

<style scoped>
.header {
  position: sticky;
  top: 0;
  z-index: 20;
  background: rgba(255, 255, 255, 0.92);
  backdrop-filter: blur(8px);
  border-bottom: 1px solid var(--cp-border);
}

.header-inner {
  display: flex;
  align-items: center;
  gap: 16px;
  height: var(--cp-header-height);
}

.brand {
  display: inline-flex;
  align-items: center;
  white-space: nowrap;
}

.brand-logo {
  display: block;
  width: 128px;
  height: 32px;
}

.header-search {
  flex: 1;
  max-width: 420px;
}

.header-nav {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-left: auto;
}

.nav-link {
  color: var(--cp-text-soft);
  font-weight: 500;
  white-space: nowrap;
}

.nav-link:hover,
.nav-link.router-link-active {
  color: var(--cp-text);
}

.nav-link.router-link-active {
  position: relative;
}

.nav-link.router-link-active::after {
  position: absolute;
  right: 0;
  bottom: -22px;
  left: 0;
  height: 2px;
  background: var(--cp-accent);
  content: '';
}

.upload-btn {
  white-space: nowrap;
}

.user-trigger {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 4px 8px;
  border: none;
  border-radius: 999px;
  background: transparent;
  cursor: pointer;
  font: inherit;
}

.user-trigger:hover {
  background: var(--cp-bg-soft);
}

.user-name {
  max-width: 120px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.user-caret {
  font-size: 10px;
  color: var(--cp-text-soft);
}

.header-menu-btn {
  display: none;
  margin-left: auto;
  font-size: 18px;
}

.header-search-btn {
  display: none;
  font-size: 17px;
}

.drawer-links {
  display: flex;
  flex-direction: column;
  gap: 10px;
  margin-top: 20px;
}

.drawer-link {
  padding: 10px 12px;
  border: none;
  border-radius: 8px;
  background: var(--cp-bg-soft);
  text-align: left;
  font: inherit;
  cursor: pointer;
}

.mobile-tabbar {
  display: none;
}

@media (max-width: 991px) {
  .header-search {
    display: none;
  }

  .header-nav {
    display: flex;
    gap: 0;
    margin-left: auto;
  }

  .header-nav > a,
  .header-nav .upload-btn {
    display: none;
  }

  .header-search-btn {
    display: inline-flex;
  }

  .header-menu-btn {
    display: inline-flex;
    margin-left: 0;
  }
}

@media (max-width: 767px) {
  .header {
    background: #fff;
  }

  .header-inner {
    height: 58px;
  }

  .brand-logo {
    width: 112px;
    height: 28px;
  }

  .mobile-tabbar {
    position: fixed;
    z-index: 30;
    right: 0;
    bottom: 0;
    left: 0;
    display: grid;
    grid-template-columns: repeat(4, 1fr);
    min-height: 64px;
    padding: 7px 8px max(7px, env(safe-area-inset-bottom));
    border-top: 1px solid var(--cp-border);
    background: rgba(255, 255, 255, 0.97);
    backdrop-filter: blur(12px);
  }

  .mobile-tab {
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    gap: 2px;
    border: 0;
    background: none;
    color: var(--cp-text-soft);
    font: inherit;
    font-size: 11px;
  }

  .mobile-tab :deep(.anticon) {
    font-size: 18px;
  }

  .mobile-tab.router-link-active {
    color: var(--cp-accent);
  }

  .mobile-upload {
    color: var(--cp-accent);
    cursor: pointer;
  }
}

@media (max-width: 575px) {
  .header-inner {
    gap: 8px;
  }

  .user-name,
  .user-caret {
    display: none;
  }
}
</style>
