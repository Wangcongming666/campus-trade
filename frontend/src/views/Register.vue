<template>
  <div class="auth-page">
    <div class="auth-card">
      <div class="auth-brand">
        <el-icon :size="28" color="var(--color-primary)"><ShoppingBag /></el-icon>
        <h1>注册账号</h1>
        <p>创建账号后即可发布闲置、收藏与下单</p>
      </div>

      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        label-position="top"
        size="large"
        @submit.prevent="handleSubmit"
      >
        <el-form-item label="用户名" prop="username">
          <el-input v-model="form.username" placeholder="3–20 位字母、数字或下划线" maxlength="20" clearable />
        </el-form-item>

        <el-form-item label="手机号（选填）" prop="phone">
          <el-input v-model="form.phone" placeholder="11 位手机号" maxlength="11" clearable />
        </el-form-item>

        <el-form-item label="密码" prop="password">
          <el-input v-model="form.password" type="password" placeholder="6–32 位密码" show-password maxlength="32" />
        </el-form-item>

        <el-form-item label="确认密码" prop="confirmPassword">
          <el-input
            v-model="form.confirmPassword"
            type="password"
            placeholder="请再次输入密码"
            show-password
            maxlength="32"
          />
        </el-form-item>

        <el-form-item label="验证码" prop="captchaCode">
          <div class="captcha-row">
            <el-input v-model="form.captchaCode" placeholder="请输入图形验证码" maxlength="6" />
            <button class="captcha-image" type="button" title="点击刷新验证码" @click="refreshCaptcha">
              <img v-if="captchaImage" :src="captchaImage" alt="图形验证码" />
              <span v-else>{{ captchaLoading ? '加载中' : '点击获取' }}</span>
            </button>
          </div>
        </el-form-item>

        <el-form-item>
          <el-button type="primary" class="submit-btn" :loading="loading" native-type="submit" @click="handleSubmit">
            注册
          </el-button>
        </el-form-item>
      </el-form>

      <div class="auth-extra">
        已有账号？
        <router-link to="/login">去登录</router-link>
      </div>
    </div>
  </div>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { register } from '@/api/auth'
import { useCaptcha } from '@/composables/useCaptcha'
import { isCaptchaError } from '@/constants/errorCodes'

const router = useRouter()
const { captchaKey, captchaImage, loading: captchaLoading, refresh } = useCaptcha()
const formRef = ref()
const loading = ref(false)
const form = reactive({
  username: '',
  phone: '',
  password: '',
  confirmPassword: '',
  captchaCode: '',
})

const usernamePattern = /^[A-Za-z0-9_]{3,20}$/
const phonePattern = /^1[3-9]\d{9}$/

const rules = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    {
      validator(_rule, value, callback) {
        if (!value || usernamePattern.test(value)) callback()
        else callback(new Error('用户名需为 3–20 位字母、数字或下划线'))
      },
      trigger: 'blur',
    },
  ],
  phone: [
    {
      validator(_rule, value, callback) {
        if (!value || phonePattern.test(value)) callback()
        else callback(new Error('请输入正确的 11 位手机号'))
      },
      trigger: 'blur',
    },
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, max: 32, message: '密码长度为 6–32 位', trigger: 'blur' },
  ],
  confirmPassword: [
    { required: true, message: '请再次输入密码', trigger: 'blur' },
    {
      validator(_rule, value, callback) {
        if (value === form.password) callback()
        else callback(new Error('两次输入的密码不一致'))
      },
      trigger: 'blur',
    },
  ],
  captchaCode: [{ required: true, message: '请输入验证码', trigger: 'blur' }],
}

async function refreshCaptcha() {
  form.captchaCode = ''
  await refresh()
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
    const payload = {
      username: form.username.trim(),
      password: form.password,
      captchaKey: captchaKey.value,
      captchaCode: form.captchaCode.trim(),
    }
    if (form.phone.trim()) payload.phone = form.phone.trim()
    await register(payload)
    sessionStorage.setItem('campus_trade_pick_interests', '1')
    ElMessage.success('注册成功，请登录')
    await router.replace('/login')
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
  margin-bottom: 24px;

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
