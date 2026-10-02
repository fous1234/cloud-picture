<script setup lang="ts">
import { ref } from 'vue'
import { RouterLink, useRouter } from 'vue-router'
import { Alert, Button, Form, FormItem, Input, InputPassword, message } from 'ant-design-vue'
import { register } from '../api/user'
import { errorMessage } from '../api/http'

const router = useRouter()

const formRef = ref<{ validate: () => Promise<unknown> } | null>(null)
const submitting = ref(false)
const errorText = ref('')
const form = ref({
  userAccount: '',
  userPassword: '',
  checkPassword: '',
  userName: '',
})

async function submit() {
  if (submitting.value) return
  try {
    await formRef.value?.validate()
  } catch {
    return
  }
  submitting.value = true
  errorText.value = ''
  try {
    await register({
      userAccount: form.value.userAccount,
      userPassword: form.value.userPassword,
      checkPassword: form.value.checkPassword,
      userName: form.value.userName.trim() || undefined,
    })
    message.success('注册成功，请登录')
    router.replace({ name: 'login', query: { account: form.value.userAccount } })
  } catch (error) {
    errorText.value = errorMessage(error)
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <div class="auth-page">
    <section class="auth-aside">
      <div class="auth-aside-top">
        <span>云图集 <span class="auth-slash">/</span> ARCHIVE</span>
        <span>OPEN IMAGE ARCHIVE</span>
      </div>
      <div class="auth-aside-copy">
        <p class="auth-kicker">A CREATIVE IMAGE ARCHIVE</p>
        <h1 class="auth-headline">收万象之影，藏生活之美</h1>
        <p class="auth-desc">随心上传与浏览图片，让每一次记录都值得被看见。</p>
      </div>
      <div class="auth-aside-meta">
        <span>HIGH-RES ASSETS</span>
        <span>LOSSLESS PRESERVATION</span>
        <span>RIGHTS CLEARED</span>
      </div>
    </section>

    <section class="auth-main">
      <div class="auth-card">
        <div class="auth-mobile-brand">
          <img src="/assets/cloud-picture-logo-stitch.png" alt="云图库" />
        </div>
        <h2 class="auth-title">创建账号</h2>
        <p class="auth-subtitle">加入共享图库，开启影像上传与灵感探索</p>

        <Alert
          v-if="errorText"
          class="auth-alert"
          type="error"
          show-icon
          message="注册失败"
          :description="errorText"
        />

        <Form ref="formRef" layout="vertical" :model="form" @finish="submit">
          <FormItem
            label="账号"
            name="userAccount"
            :rules="[
              { required: true, message: '请输入账号' },
              { min: 4, max: 32, message: '账号长度需在 4 到 32 之间' },
              { pattern: /^[a-zA-Z0-9_]+$/, message: '账号只能包含字母、数字和下划线' },
            ]"
          >
            <Input
              v-model:value="form.userAccount"
              size="large"
              placeholder="4-32 位字母、数字或下划线"
              autocomplete="username"
            />
          </FormItem>
          <FormItem
            label="展示昵称"
            name="userName"
            :rules="[{ max: 32, message: '昵称长度不能超过 32' }]"
          >
            <Input
              v-model:value="form.userName"
              size="large"
              placeholder="例如：张三 / 李四"
              :maxlength="32"
            />
          </FormItem>
          <FormItem
            label="密码"
            name="userPassword"
            :rules="[
              { required: true, message: '请输入密码' },
              { min: 8, max: 32, message: '密码长度需在 8 到 32 之间' },
            ]"
          >
            <InputPassword
              v-model:value="form.userPassword"
              size="large"
              placeholder="8-32 位密码"
              autocomplete="new-password"
            />
          </FormItem>
          <FormItem
            label="确认密码"
            name="checkPassword"
            :rules="[
              { required: true, message: '请再次输入密码' },
              {
                validator: async (_rule: unknown, value: string) => {
                  if (value && value !== form.userPassword) {
                    throw new Error('两次输入的密码不一致')
                  }
                },
              },
            ]"
          >
            <InputPassword
              v-model:value="form.checkPassword"
              size="large"
              placeholder="再次输入密码"
              autocomplete="new-password"
            />
          </FormItem>
          <Button type="primary" size="large" block :loading="submitting" html-type="submit">
            {{ submitting ? '注册中' : '注册' }}
          </Button>
        </Form>

        <p class="auth-switch">
          已有账号？
          <RouterLink :to="{ name: 'login' }">返回登录</RouterLink>
        </p>
        <p class="auth-footer">SECURED CAMPUS SSO COMPLIANT · © 2025 CLOUD GALLERY</p>
      </div>
    </section>
  </div>
</template>

<style scoped>
.auth-page {
  display: grid;
  grid-template-columns: 1fr 1fr;
  min-height: 100vh;
  min-height: 100dvh;
}

.auth-aside {
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  padding: 32px 48px 28px;
  background-image: linear-gradient(180deg, rgba(11, 14, 17, 0.18), rgba(11, 14, 17, 0.66)),
    url('/assets/auth-campus.jpg');
  background-position: center;
  background-size: cover;
  color: #fff;
}

.auth-aside-top,
.auth-aside-meta {
  display: inline-flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  font-size: 11px;
  letter-spacing: 0.12em;
  text-transform: uppercase;
}

.auth-aside-top span:first-child {
  padding: 7px 10px;
  border: 1px solid rgba(255, 255, 255, 0.55);
  border-radius: 999px;
}

.auth-slash {
  margin: 0 4px;
  opacity: 0.6;
}

.auth-kicker {
  margin: 0 0 12px;
  font-size: 11px;
  letter-spacing: 0.18em;
}

.auth-headline {
  margin: 0;
  font-size: clamp(36px, 4vw, 58px);
  font-weight: 600;
  line-height: 1.16;
  letter-spacing: -0.04em;
}

.auth-desc {
  max-width: 440px;
  margin: 18px 0 0;
  color: rgba(255, 255, 255, 0.75);
}

.auth-aside-meta {
  font-size: 10px;
}

.auth-main {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 48px 40px;
  background: var(--cp-surface);
}

.auth-card {
  width: 100%;
  max-width: 360px;
}

.auth-mobile-brand {
  margin-bottom: 24px;
}

.auth-mobile-brand img {
  display: block;
  width: 112px;
  height: 28px;
}

.auth-title {
  margin: 0;
  font-family: 'Plus Jakarta Sans', Inter, sans-serif;
  font-size: 26px;
  font-weight: 600;
  letter-spacing: -0.02em;
}

.auth-subtitle {
  margin: 6px 0 24px;
  color: var(--cp-text-soft);
}

.auth-alert {
  margin-bottom: 20px;
}

.auth-switch {
  margin-top: 20px;
  color: var(--cp-text-soft);
  text-align: center;
}

.auth-switch a {
  color: var(--cp-accent);
  font-weight: 600;
}

.auth-footer {
  margin: 30px 0 0;
  color: var(--cp-text-muted);
  font-size: 10px;
  letter-spacing: 0.04em;
  text-align: center;
}

.auth-card :deep(.ant-form-item-label > label) {
  font-size: 13px;
  font-weight: 600;
}

.auth-card :deep(.ant-input-affix-wrapper),
.auth-card :deep(.ant-input) {
  min-height: 46px;
  border-color: var(--cp-border);
  background: var(--cp-bg-soft);
}

.auth-card :deep(.ant-btn-lg) {
  height: 46px;
  margin-top: 10px;
  border-radius: var(--cp-radius);
}

@media (max-width: 899px) {
  .auth-page {
    grid-template-columns: 1fr;
  }

  .auth-aside {
    display: none;
  }

  .auth-mobile-brand {
    display: block;
  }
}

@media (max-width: 575px) {
  .auth-main {
    align-items: flex-start;
    padding: 32px 24px;
  }

  .auth-mobile-brand {
    margin-bottom: 36px;
  }
}
</style>
