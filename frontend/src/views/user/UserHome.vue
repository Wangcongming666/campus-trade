<template>
  <div class="page-placeholder" v-loading="loading">
    <el-card v-if="profile" shadow="never">
      <div class="user-home">
        <el-avatar :size="64" :src="profile.avatar">
          {{ (profile.nickname || '同').slice(0, 1) }}
        </el-avatar>
        <div>
          <h2 class="page-placeholder__title">{{ profile.nickname || '校园用户' }}</h2>
          <p>
            信誉分 {{ profile.creditScore ?? '—' }}
            · {{ profile.school || '学校未填写' }}
            · {{ profile.studentVerified ? '已学生认证' : '未认证' }}
          </p>
          <p v-if="profile.createTime">注册于 {{ profile.createTime }}</p>
          <el-button
            v-if="!isSelf"
            class="report-btn"
            data-testid="report-user-btn"
            @click="openReport"
          >
            举报
          </el-button>
        </div>
      </div>
    </el-card>
    <el-empty v-else-if="!loading" description="用户不存在" />

    <ReportDialog
      v-model="reportVisible"
      :target-type="ReportTargetType.USER"
      :target-id="userId"
      :target-name="profile?.nickname"
    />
  </div>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getUserProfile } from '@/api/user'
import ReportDialog from '@/components/ReportDialog.vue'
import { ReportTargetType } from '@/constants/enums'
import { useUserStore } from '@/stores/user'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const loading = ref(false)
const profile = ref(null)
const reportVisible = ref(false)
const userId = computed(() => String(route.params.id || ''))
const isSelf = computed(() => String(userStore.userInfo?.id || '') === userId.value)

function openReport() {
  if (!userStore.token) {
    router.push({ path: '/login', query: { redirect: route.fullPath } })
    return
  }
  reportVisible.value = true
}

async function load() {
  if (!userId.value) {
    profile.value = null
    return
  }
  loading.value = true
  try {
    profile.value = await getUserProfile(userId.value)
  } catch {
    profile.value = null
  } finally {
    loading.value = false
  }
}

watch(userId, load, { immediate: true })
</script>

<style scoped>
.user-home {
  display: flex;
  align-items: center;
  gap: 16px;
}

.user-home p {
  margin: 0;
  color: var(--color-text-secondary);
}

.user-home p + p {
  margin-top: 4px;
  font-size: 13px;
}

.report-btn {
  margin-top: 12px;
}
</style>
