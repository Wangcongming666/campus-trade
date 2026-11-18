<template>
  <el-dialog
    :model-value="userStore.interestPickerVisible"
    title="选择你感兴趣的分类"
    width="520px"
    :close-on-click-modal="false"
    @close="onSkip"
  >
    <p class="hint">最多选择 5 个分类，用于优化首页推荐。也可以先跳过，稍后在个人中心修改。</p>
    <div class="tags">
      <el-check-tag
        v-for="item in options"
        :key="item.id"
        :checked="selected.includes(item.id)"
        :disabled="!selected.includes(item.id) && selected.length >= max"
        @change="(checked) => toggle(item.id, checked)"
      >
        {{ item.name }}
      </el-check-tag>
    </div>
    <p v-if="selected.length >= max" class="limit">最多选择 {{ max }} 个兴趣分类</p>
    <template #footer>
      <el-button @click="onSkip">跳过</el-button>
      <el-button type="primary" :loading="saving" @click="onSave">保存</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { onMounted, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { getCategoryList } from '@/api/category'
import { useUserStore } from '@/stores/user'

const emit = defineEmits(['saved', 'skip'])
const userStore = useUserStore()
const options = ref([])
const selected = ref([])
const saving = ref(false)
const max = 5

watch(
  () => userStore.interestPickerVisible,
  async (visible) => {
    if (!visible) return
    if (!options.value.length) await loadOptions()
    selected.value = (userStore.interests || []).map((item) => String(item.id))
  },
)

async function loadOptions() {
  const data = await getCategoryList()
  const list = Array.isArray(data) ? data : []
  options.value = list
}

function toggle(id, checked) {
  const key = String(id)
  if (checked) {
    if (selected.value.includes(key)) return
    if (selected.value.length >= max) {
      ElMessage.warning(`最多选择 ${max} 个兴趣分类`)
      return
    }
    selected.value = [...selected.value, key]
  } else {
    selected.value = selected.value.filter((item) => item !== key)
  }
}

function onSkip() {
  userStore.closeInterestPicker()
  emit('skip')
}

async function onSave() {
  saving.value = true
  try {
    await userStore.saveInterests(selected.value)
    ElMessage.success(selected.value.length ? '兴趣已保存' : '已清空兴趣分类')
    userStore.closeInterestPicker()
    emit('saved', selected.value)
  } finally {
    saving.value = false
  }
}

onMounted(async () => {
  try {
    await loadOptions()
    if (userStore.isLoggedIn && !userStore.interests.length) {
      await userStore.loadInterests()
    }
  } catch {
    // 分类失败时弹窗内仍可提示
  }
})
</script>

<style scoped>
.hint {
  margin: 0 0 16px;
  color: var(--color-text-secondary);
  font-size: 13px;
  line-height: 1.6;
}

.tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.limit {
  margin: 12px 0 0;
  color: var(--ct-warning);
  font-size: 13px;
}
</style>
