<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'
import { Alert, Button, Form, FormItem, Input, InputPassword } from 'ant-design-vue'
import { getCurrentUser, login } from '../api/user'
import { errorMessage } from '../api/http'
import { setToken, setUser } from '../stores/session'

const route = useRoute()
const router = useRouter()

const formRef = ref<{ validate: () => Promise<unknown> } | null>(null)
const submitting = ref(false)
const errorText = ref('')
const form = ref({ userAccount: '', userPassword: '' })

onMounted(() => {
  const account = route.query.account
  if (typeof account === 'string') form.value.userAccount = account
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
    const result = await login(form.value)
    setToken(result.token)
    setUser(await getCurrentUser())
    const redirect = route.query.redirect
    router.replace(typeof redirect === 'string' && redirect ? redirect : { name: 'gallery' })
  } catch (error) {
    // 登录失败保留已填账号，仅提示错误
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
        <span>CURATED EDITORIAL</span>
      </div>
      <div class="auth-aside-copy">
        <p class="auth-kicker">COLLECTION // VOL. 08</p>
        <h1 class="auth-headline">发现灵感，<br />收藏每一刻</h1>
        <p class="auth-desc">面向校园团队的高质量共享图库平台，摄影优先，沉淀珍贵影像资产。</p>
      </div>
      <div class="auth-aside-meta">
        <span>天然晨光 · 自然采撷</span>
        <span>PLATE NO. ARCH-2024-C</span>
      </div>
    </section>

    <section class="auth-main">
      <div class="auth-card">
        <div class="auth-mobile-brand">
          <img src="/assets/cloud-picture-logo-stitch.png" alt="云图库" />
        </div>
        <h2 class="auth-title">欢迎回来</h2>
        <p class="auth-subtitle">请登录您的账号以访问共享图库</p>

        <Alert
          v-if="errorText"
          class="auth-alert"
          type="error"
          show-icon
          message="登录失败"
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
              placeholder="请输入账号"
              autocomplete="username"
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
              placeholder="请输入密码"
              autocomplete="current-password"
              @press-enter="submit"
            />
          </FormItem>
          <Button type="primary" size="large" block :loading="submitting" html-type="submit">
            {{ submitting ? '登录中' : '登录' }}
          </Button>
        </Form>

        <p class="auth-switch">
          还没有账号？
          <RouterLink :to="{ name: 'register' }">立即注册</RouterLink>
        </p>
        <p class="auth-footer">SECURED UNIVERSITY PHOTOGRAPHY VAULT · VER 2.4</p>
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
  margin-bottom: 28px;
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
  margin: 6px 0 30px;
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
  margin: 36px 0 0;
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
    margin-bottom: 40px;
  }
}
</style>
