<template>
  <div class="announce-page" data-testid="announcement-detail" v-loading="loading">
    <el-empty v-if="!loading && !item" :description="loadError || '公告不存在或已下架'" />
    <article v-else-if="item" class="announce-article">
      <h1>{{ item.title }}</h1>
      <p class="announce-meta">
        {{ item.publishTime || item.createTime || '' }}
      </p>
      <div class="announce-content">{{ item.content }}</div>
    </article>
  </div>
</template>

<script setup>
import { ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { getAnnouncementDetail } from '@/api/announcement'
import { BizCode, bizErrorText, isApiOffline, API_OFFLINE_TEXT } from '@/constants/errorCodes'

const route = useRoute()
const loading = ref(false)
const item = ref(null)
const loadError = ref('')

async function load() {
  const id = String(route.params.id || '')
  if (!id) {
    item.value = null
    loadError.value = '公告不存在或已下架'
    return
  }
  loading.value = true
  try {
    item.value = await getAnnouncementDetail(id, { silent: true })
    loadError.value = ''
  } catch (error) {
    item.value = null
    if (isApiOffline(error)) loadError.value = API_OFFLINE_TEXT
    else if (Number(error.code) === BizCode.ANNOUNCEMENT_NOT_FOUND) loadError.value = '公告不存在或已下架'
    else loadError.value = bizErrorText(error, '公告不存在或已下架')
  } finally {
    loading.value = false
  }
}

watch(() => route.params.id, load, { immediate: true })
</script>

<style scoped lang="scss">
.announce-page {
  padding: 24px 0 48px;
  max-width: 800px;
}

.announce-article {
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: var(--ct-radius-lg);
  padding: 28px 32px 36px;

  h1 {
    margin: 0 0 8px;
    font-size: 24px;
    line-height: 1.4;
  }
}

.announce-meta {
  margin: 0 0 24px;
  color: var(--color-text-secondary);
  font-size: 13px;
}

.announce-content {
  white-space: pre-wrap;
  word-break: break-word;
  line-height: 1.8;
  font-size: 15px;
}
</style>
