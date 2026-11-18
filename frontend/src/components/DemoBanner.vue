<template>
  <el-alert
    v-if="visible"
    class="demo-banner"
    title="当前展示为演示数据，非真实商品与用户，仅供开发预览。"
    type="warning"
    show-icon
    :closable="false"
  />
</template>

<script setup>
import { USE_MOCK } from '@/utils/format'

const visible = USE_MOCK
</script>

<style scoped>
.demo-banner {
  margin-bottom: 16px;
}
</style>
