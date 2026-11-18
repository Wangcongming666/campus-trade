<template>
  <div class="auth-page">
    <div class="auth-card">
      <div class="auth-brand">
        <el-icon :size="28" color="var(--color-primary)"><ShoppingBag /></el-icon>
        <h1>校园二手交易智能平台</h1>
        <p>登录后发布闲置、沟通买家、管理订单</p>
      </div>

      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        label-position="top"
        size="large"
        @submit.prevent="handleSubmit"
      >
        <el-form-item label="账号" prop="account">
          <el-input
            v-model="form.account"
            placeholder="用户名 / 手机号 / 学号"
            clearable
            maxlength="32"
          >
            <template #prefix>
              <el-icon><User /></el-icon>
            </template>
          </el-input>
        </el-form-item>

        <el-form-item label="密码" prop="password">
          <el-input
            v-model="form.password"
            type="password"
            placeholder="请输入密码"
            show-password
            maxlength="32"
            @keyup.enter="handleSubmit"
          >
            <template #prefix>
              <el-icon><Lock /></el-icon>
            </template>
          </el-input>
        </el-form-item>

        <el-form-item label="验证码" prop="captchaCode">
          <div class="captcha-row">
            <el-input
              v-model="form.captchaCode"
              placeholder="请输入图形验证码"
              maxlength="6"
              @keyup.enter="handleSubmit"
            />
            <button
              class="captcha-image"
              type="button"
              title="点击刷新验证码"
              @click="refreshCaptcha"
            >
              <img v-if="captchaImage" :src="captchaImage" alt="图形验证码" />
              <span v-else>{{ captchaLoading ? '加载中' : '点击获取' }}</span>
            </button>
          </div>
        </el-form-item>

        <el-form-item>
          <el-button
            type="primary"
            class="submit-btn"
            :loading="loading"
            native-type="submit"
            @click="handleSubmit"
          >
            登录
          </el-button>
        </el-form-item>
      </el-form>

      <div class="auth-extra">
        还没有账号？
        <router-link to="/register">立即注册</router-link>
      </div>
    </div>
  </div>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useCaptcha } from '@/composables/useCaptcha'
import { isCaptchaError } from '@/constants/errorCodes'
import { useUserStore } from '@/stores/user'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()
const { captchaKey, captchaImage, loading: captchaLoading, refresh } = useCaptcha()

const formRef = ref()
const loading = ref(false)
const form = reactive({
  account: '',
  password: '',
  captchaCode: '',
})

const rules = {
  account: [{ required: true, message: '请输入用户名、手机号或学号', trigger: 'blur' }],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, max: 32, message: '密码长度为 6–32 位', trigger: 'blur' },
  ],
  captchaCode: [{ required: true, message: '请输入验证码', trigger: 'blur' }],
}

async function refreshCaptcha() {
  form.captchaCode = ''
  await refresh()
}

function resolveRedirect() {
  const redirect = route.query.redirect
  if (typeof redirect === 'string' && redirect.startsWith('/') && !redirect.startsWith('//')) {
    return redirect
  }
  return '/'
}

async function handleSubmit() {
  if (!formRef.value || loading.value) return
  try {
    await formRef.value.validate()
  } catch {
    return
  }

  loading.value = true
  try {
    await userStore.login({
      account: form.account.trim(),
      password: form.password,
      captchaKey: captchaKey.value,
      captchaCode: form.captchaCode.trim(),
    })
    if (sessionStorage.getItem('campus_trade_pick_interests')) {
      sessionStorage.removeItem('campus_trade_pick_interests')
      userStore.openInterestPicker()
    }
    const target = resolveRedirect()
    if (userStore.isAdmin && (target === '/' || !target)) {
      await router.replace('/admin/dashboard')
    } else {
      await router.replace(target)
    }
  } catch (error) {
    if (!isCaptchaError(error?.code)) {
      await refreshCaptcha()
    }
  } finally {
    loading.value = false
  }
}
</script>

<style scoped lang="scss">
.auth-page {
  min-height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 32px 16px;
  background:
    var(--ct-gradient-mesh),
    var(--ct-bg);
}

.auth-card {
  width: 100%;
  max-width: 420px;
  padding: 36px 32px 28px;
  background: var(--color-surface);
  border: 1px solid var(--ct-border);
  border-radius: var(--ct-radius-xl);
  box-shadow: var(--ct-shadow-3);
}

.auth-brand {
  text-align: center;
  margin-bottom: 28px;

  h1 {
    margin: 12px 0 8px;
    font-size: 22px;
  }

  p {
    margin: 0;
    color: var(--color-text-secondary);
    font-size: 13px;
  }
}

.captcha-row {
  display: flex;
  gap: 10px;
  width: 100%;
}

.captcha-image {
  width: 120px;
  height: 40px;
  padding: 0;
  border: 1px solid var(--color-border);
  border-radius: 8px;
  overflow: hidden;
  background: var(--color-primary-bg);
  cursor: pointer;
  flex-shrink: 0;

  img {
    width: 100%;
    height: 100%;
    display: block;
    object-fit: cover;
  }

  span {
    font-size: 12px;
    color: var(--color-text-secondary);
  }
}

.submit-btn {
  width: 100%;
}

.auth-extra {
  text-align: center;
  color: var(--color-text-secondary);
  font-size: 14px;
}
</style>
