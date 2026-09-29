<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { Alert, Avatar, Button, Descriptions, DescriptionsItem, Form, FormItem, Input, Skeleton, Tag, Textarea, message } from 'ant-design-vue'
import type { UserVO } from '../api/types'
import { getCurrentUser, updateCurrentUser } from '../api/user'
import { errorMessage } from '../api/http'
import { session, setUser } from '../stores/session'
import { formatDateTime } from '../utils/format'

const loading = ref(true)
const loadError = ref<string | null>(null)
const saving = ref(false)
const saveError = ref<string | null>(null)
const formRef = ref<{ validate: () => Promise<unknown> } | null>(null)
const user = ref<UserVO | null>(null)
const form = ref({ userName: '', userProfile: '' })

async function load() {
  loading.value = true
  loadError.value = null
  try {
    const current = await getCurrentUser()
    user.value = current
    setUser(current)
    form.value = { userName: current.name ?? '', userProfile: current.profile ?? '' }
  } catch (error) {
    loadError.value = errorMessage(error)
  } finally {
    loading.value = false
  }
}

async function save() {
  if (saving.value) return
  try {
    await formRef.value?.validate()
  } catch {
    return
  }
  saving.value = true
  saveError.value = null
  try {
    await updateCurrentUser({
      userName: form.value.userName.trim(),
      userProfile: form.value.userProfile.trim(),
    })
    if (user.value) {
      const next = { ...user.value, name: form.value.userName.trim(), profile: form.value.userProfile.trim() }
      user.value = next
      setUser(next)
    }
    message.success('资料已保存')
  } catch (error) {
    saveError.value = errorMessage(error)
  } finally {
    saving.value = false
  }
}

onMounted(load)
</script>

<template>
  <div class="cp-container cp-page">
    <h1 class="cp-page-title">个人资料</h1>
    <p class="cp-page-subtitle">更新昵称和简介，账号与角色不可修改</p>

    <Alert v-if="loadError" class="profile-alert" type="error" show-icon message="加载失败" :description="loadError">
      <template #action>
        <Button size="small" @click="load">重试</Button>
      </template>
    </Alert>

    <Skeleton v-else-if="loading" :paragraph="{ rows: 6 }" active />

    <div v-else-if="user" class="profile">
      <div class="profile-card">
        <div class="profile-identity">
          <Avatar :src="user.avatar || undefined" :size="64">
            {{ (user.name || user.account).slice(0, 1) }}
          </Avatar>
          <div>
            <div class="profile-name">{{ user.name || user.account }}</div>
            <div class="profile-account">@{{ user.account }}</div>
          </div>
        </div>

        <Descriptions :column="1" size="small">
          <DescriptionsItem label="账号">{{ user.account }}</DescriptionsItem>
          <DescriptionsItem label="角色">
            <Tag :color="user.role === 'ADMIN' ? 'gold' : 'blue'">
              {{ user.role === 'ADMIN' ? '管理员' : '普通用户' }}
            </Tag>
          </DescriptionsItem>
          <DescriptionsItem label="账号状态">
            <Tag :color="user.status === 1 ? 'success' : 'error'">
              {{ user.status === 1 ? '正常' : '已禁用' }}
            </Tag>
          </DescriptionsItem>
          <DescriptionsItem label="注册时间">{{ formatDateTime(user.createTime) }}</DescriptionsItem>
          <DescriptionsItem label="头像">
            {{ user.avatar ? '已设置' : '暂无头像（后端暂无头像上传接口）' }}
          </DescriptionsItem>
        </Descriptions>
      </div>

      <div class="profile-card">
        <Alert v-if="saveError" class="profile-alert" type="error" show-icon message="保存失败" :description="saveError" />
        <Form ref="formRef" layout="vertical" :model="form">
          <FormItem
            label="昵称"
            name="userName"
            :rules="[{ max: 32, message: '昵称长度不能超过 32' }]"
          >
            <Input v-model:value="form.userName" :maxlength="32" placeholder="请输入昵称" />
          </FormItem>
          <FormItem
            label="简介"
            name="userProfile"
            :rules="[{ max: 512, message: '简介长度不能超过 512' }]"
          >
            <Textarea
              v-model:value="form.userProfile"
              :rows="4"
              :maxlength="512"
              show-count
              placeholder="简单介绍一下自己"
            />
          </FormItem>
          <Button type="primary" :loading="saving" @click="save">
            {{ saving ? '保存中' : '保存' }}
          </Button>
        </Form>
      </div>
    </div>

    <p v-if="session.user" class="profile-hint">
      退出登录会清除本地令牌和用户状态，需要重新登录才能继续浏览图库。
    </p>
  </div>
</template>

<style scoped>
.profile {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(0, 1.2fr);
  gap: 24px;
  margin-top: 20px;
  align-items: start;
}

.profile-card {
  padding: 24px;
  border: 1px solid var(--cp-border);
  border-radius: var(--cp-radius);
}

.profile-identity {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-bottom: 20px;
}

.profile-name {
  font-size: 18px;
  font-weight: 600;
}

.profile-account {
  color: var(--cp-text-soft);
  font-size: 13px;
}

.profile-alert {
  margin-bottom: 16px;
}

.profile-hint {
  margin-top: 24px;
  color: var(--cp-text-soft);
  font-size: 13px;
}

@media (max-width: 899px) {
  .profile {
    grid-template-columns: 1fr;
  }
}
</style>