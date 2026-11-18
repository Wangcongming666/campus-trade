<template>
  <el-dialog
    v-model="visible"
    title="举报"
    width="460px"
    append-to-body
    destroy-on-close
    @closed="reset"
  >
    <p class="report-target">
      举报{{ targetType === ReportTargetType.USER ? '用户' : '商品' }}：
      <strong>{{ targetName || '该内容' }}</strong>
    </p>
    <el-form ref="formRef" :model="form" :rules="rules" label-position="top">
      <el-form-item label="举报原因" prop="reason">
        <el-select v-model="form.reason" placeholder="请选择原因" style="width: 100%">
          <el-option v-for="item in REPORT_REASON_OPTIONS" :key="item" :label="item" :value="item" />
        </el-select>
      </el-form-item>
      <el-form-item label="补充说明（选填）" prop="detail">
        <el-input
          v-model="form.detail"
          type="textarea"
          :rows="4"
          maxlength="500"
          show-word-limit
          placeholder="请补充具体说明，便于管理员核实"
        />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" :loading="submitting" @click="submit">提交举报</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { computed, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { createReport } from '@/api/report'
import { REPORT_REASON_OPTIONS, ReportTargetType } from '@/constants/enums'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  targetType: { type: String, default: ReportTargetType.GOODS },
  targetId: { type: [String, Number], default: '' },
  targetName: { type: String, default: '' },
})

const emit = defineEmits(['update:modelValue', 'submitted'])

const visible = computed({
  get: () => props.modelValue,
  set: (value) => emit('update:modelValue', value),
})

const formRef = ref()
const submitting = ref(false)
const form = reactive({ reason: '', detail: '' })
const rules = {
  reason: [{ required: true, message: '请选择举报原因', trigger: 'change' }],
}

function reset() {
  form.reason = ''
  form.detail = ''
  formRef.value?.clearValidate()
}

async function submit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  if (!props.targetId) {
    ElMessage.warning('缺少举报对象')
    return
  }
  submitting.value = true
  try {
    await createReport({
      targetType: props.targetType,
      targetId: String(props.targetId),
      reason: form.reason,
      detail: form.detail.trim() || undefined,
    })
    ElMessage.success('举报已提交，请等待处理')
    visible.value = false
    emit('submitted')
  } finally {
    submitting.value = false
  }
}
</script>

<style scoped>
.report-target {
  margin: 0 0 12px;
  color: var(--color-text-secondary);
}
</style>
