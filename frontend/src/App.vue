<template>
  <router-view />
  <InterestPicker />
</template>

<script setup>
import { watch } from 'vue'
import InterestPicker from '@/components/InterestPicker.vue'
import { useChatStore } from '@/stores/chat'
import { useUserStore } from '@/stores/user'

const userStore = useUserStore()
const chatStore = useChatStore()

watch(
  () => userStore.isLoggedIn,
  async (loggedIn) => {
    if (loggedIn) {
      try {
        await userStore.loadInterests()
      } catch {
        // 兴趣接口失败不影响主流程
      }
      try {
        await chatStore.startRealtime()
      } catch {
        // 会话/STOMP 未就绪时不阻断页面
      }
    } else {
      await chatStore.stopRealtime()
    }
  },
  { immediate: true },
)
</script>
