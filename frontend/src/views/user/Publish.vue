<template>
  <div class="publish-page" data-testid="publish-page">
    <DemoBanner />

    <header class="pub-head">
      <div>
        <h1>{{ isEdit ? '编辑闲置' : '发布闲置' }}</h1>
        <p>{{ isEdit ? '修改后会重新进入审核，通过后自动上架' : '拍几张照片、写清楚成色，审核通过后就会出现在同学们的首页' }}</p>
      </div>
      <ol v-if="!isEdit" class="steps" aria-label="发布进度">
        <li v-for="(step, index) in steps" :key="step.label" :class="{ 'is-done': step.done, 'is-on': index === currentStep }">
          <span class="steps__dot">
            <el-icon v-if="step.done"><Check /></el-icon>
            <template v-else>{{ index + 1 }}</template>
          </span>
          {{ step.label }}
        </li>
      </ol>
    </header>

    <div v-loading="loading" class="pub-grid">
      <el-form ref="formRef" :model="form" :rules="rules" label-position="top" class="pub-form" @submit.prevent>
        <!-- ① 商品图片 -->
        <section class="ct-card pub-sec">
          <h2 class="pub-sec__title">
            <span class="pub-sec__no">1</span>商品图片
            <small>第一张自动作为封面，可拖拽排序 · jpg / png / webp，单张 ≤ 5MB</small>
          </h2>

          <el-upload
            v-if="!form.images.length && !uploadingCount"
            class="dropzone"
            drag
            multiple
            accept=".jpg,.jpeg,.png,.webp"
            :show-file-list="false"
            :before-upload="beforeUpload"
            :http-request="customUpload"
          >
            <div class="dropzone__inner">
              <span class="dropzone__ico"><el-icon><UploadFilled /></el-icon></span>
              <b>拖拽或点击上传，最多 9 张</b>
              <span>清晰的实拍图能让闲置卖得更快</span>
            </div>
          </el-upload>

          <TransitionGroup v-else name="img" tag="div" class="img-grid">
            <div
              v-for="(image, index) in form.images"
              :key="image"
              class="img-cell"
              draggable="true"
              @dragstart="onDragStart(index)"
              @dragover.prevent
              @drop="onDrop(index)"
            >
              <img :src="image" alt="" />
              <span v-if="index === 0" class="ct-badge img-cell__cover">封面</span>
              <div class="img-cell__ops">
                <button type="button" :disabled="index === 0" aria-label="前移" @click="moveImage(index, -1)"><el-icon><ArrowLeft /></el-icon></button>
                <button type="button" :disabled="index === form.images.length - 1" aria-label="后移" @click="moveImage(index, 1)"><el-icon><ArrowRight /></el-icon></button>
                <button type="button" class="is-del" aria-label="删除" @click="removeImage(index)"><el-icon><Delete /></el-icon></button>
              </div>
            </div>
            <div v-for="n in uploadingCount" :key="`uploading-${n}`" class="img-cell is-uploading">
              <span class="ct-spinner" />
              <span>上传中…</span>
            </div>
            <el-upload
              v-if="form.images.length + uploadingCount < MAX_IMAGES"
              key="adder"
              class="img-adder"
              multiple
              accept=".jpg,.jpeg,.png,.webp"
              :show-file-list="false"
              :before-upload="beforeUpload"
              :http-request="customUpload"
            >
              <div class="img-adder__inner">
                <el-icon><Plus /></el-icon>
                <span class="num">{{ form.images.length }}/{{ MAX_IMAGES }}</span>
              </div>
            </el-upload>
          </TransitionGroup>
          <p v-if="imageError" class="field-error">{{ imageError }}</p>
        </section>

        <!-- ② 商品信息 -->
        <section class="ct-card pub-sec">
          <h2 class="pub-sec__title"><span class="pub-sec__no">2</span>商品信息</h2>

          <el-form-item label="标题" prop="title">
            <el-input v-model="form.title" maxlength="60" show-word-limit placeholder="简洁描述闲置物品，比如「iPad Air 4 64G 蓝色 带笔」" />
          </el-form-item>

          <div class="two-col">
            <el-form-item label="分类" prop="categoryId">
              <div class="chip-col">
                <div class="chips">
                  <button
                    v-for="node in categoryTree"
                    :key="node.id"
                    type="button"
                    class="ct-chip is-sm"
                    :class="{ 'is-active': String(activeParentId) === String(node.id) }"
                    @click="pickParent(node)"
                  >
                    {{ node.name }}
                  </button>
                </div>
                <Transition name="fade-down">
                  <div v-if="childCategories.length" :key="activeParentId" class="chips chips--sub">
                    <button
                      v-for="child in childCategories"
                      :key="child.id"
                      type="button"
                      class="ct-chip is-sm is-soft"
                      :class="{ 'is-active': String(form.categoryId) === String(child.id) }"
                      @click="pickCategory(child.id)"
                    >
                      {{ child.name }}
                    </button>
                  </div>
                </Transition>
              </div>
            </el-form-item>

            <el-form-item label="成色" prop="condition">
              <div class="chips">
                <button
                  v-for="item in conditionOptions"
                  :key="item.value"
                  type="button"
                  class="ct-chip is-sm"
                  :class="{ 'is-active': form.condition === item.value }"
                  @click="pickCondition(item.value)"
                >
                  {{ item.label }}
                </button>
              </div>
            </el-form-item>
          </div>

          <el-form-item label="描述" prop="description">
            <el-input
              v-model="form.description"
              type="textarea"
              :rows="5"
              maxlength="1000"
              show-word-limit
              placeholder="补充成色、入手渠道、交易说明等"
            />
          </el-form-item>

          <el-form-item label="交易方式" prop="tradeMethod">
            <div class="trade-opts">
              <button
                v-for="item in tradeOptions"
                :key="item.value"
                type="button"
                class="trade-opt"
                :class="{ 'is-on': form.tradeMethod === item.value }"
                @click="pickTrade(item.value)"
              >
                <span class="trade-opt__ico"><el-icon><component :is="TRADE_ICONS[item.value] || 'Van'" /></el-icon></span>
                <b>{{ item.label }}</b>
                <small>{{ TRADE_HINTS[item.value] }}</small>
                <el-icon class="trade-opt__check"><CircleCheckFilled /></el-icon>
              </button>
            </div>
          </el-form-item>
        </section>

        <!-- ③ 定价 -->
        <section class="ct-card pub-sec">
          <h2 class="pub-sec__title"><span class="pub-sec__no">3</span>定价</h2>
          <div class="price-grid">
            <div>
              <el-form-item label="售价" prop="price">
                <el-input
                  v-model="form.price"
                  class="price-input"
                  :class="{ 'is-flash': priceFlash }"
                  placeholder="0.00"
                  inputmode="decimal"
                >
                  <template #prefix><span class="price-input__yen">¥</span></template>
                </el-input>
              </el-form-item>
              <el-form-item label="原价（选填）" prop="originalPrice">
                <el-input v-model="form.originalPrice" placeholder="入手价，买家会看到省了多少" inputmode="decimal">
                  <template #prefix>¥</template>
                </el-input>
              </el-form-item>
            </div>

            <div class="ai-card" :class="{ 'is-loading': suggesting }">
              <div class="ai-card__hd">
                <span class="ai-card__ico"><el-icon><MagicStick /></el-icon></span>
                <b>智能定价建议</b>
                <el-button size="small" round class="ct-btn-soft" :loading="suggesting" @click="fetchSuggestion">智能定价</el-button>
              </div>

              <div v-if="suggesting" class="ai-card__sk">
                <div v-for="n in 7" :key="n" class="ct-sk" :style="{ height: `${20 + ((n * 37) % 50)}px` }" />
              </div>
              <template v-else-if="suggestion">
                <template v-if="hasEnoughSamples">
                  <div class="ai-card__range num">
                    ¥{{ formatPrice(suggestion.minPrice) }} – ¥{{ formatPrice(suggestion.maxPrice) }}
                  </div>
                  <div class="ai-bar">
                    <div class="ai-bar__fill" :style="{ left: `${suggestBar.left}%`, right: `${100 - suggestBar.right}%` }" />
                    <div class="ai-bar__mid" :style="{ left: `${suggestBar.mid}%` }" />
                  </div>
                  <p class="ai-card__meta">建议价 <b class="num">¥{{ formatPrice(suggestion.suggestedPrice) }}</b> · 参考同类 {{ suggestion.sampleCount }} 件</p>
                </template>
                <template v-else>
                  <!-- 设计答复 #5：样本 < 5 时 badge-gray + 参考价，区间条和分布图整块隐藏 -->
                  <span class="ct-badge ct-badge--gray ai-card__few-badge">同类样本较少</span>
                  <p v-if="suggestion.suggestedPrice != null" class="ai-card__ref">
                    参考价 <b class="num">¥{{ formatPrice(suggestion.suggestedPrice) }}</b>
                  </p>
                </template>
                <p v-if="suggestion.reason" class="ai-card__reason">{{ suggestion.reason }}</p>
                <el-button
                  class="ct-btn-hot ai-card__adopt"
                  round
                  :disabled="suggestion.suggestedPrice == null"
                  @click="adoptSuggestion"
                >
                  一键采用建议价
                </el-button>
              </template>
              <p v-else-if="suggestFailed" class="ai-card__few">同类样本较少，暂不提供建议</p>
              <p v-else class="ai-card__empty">选好分类和成色后，会根据同类成交给出参考价</p>
            </div>
          </div>
        </section>
      </el-form>

      <!-- 侧栏：实时预览 + 完整度 -->
      <aside class="pub-side">
        <div class="ct-card side-card">
          <h3>实时预览</h3>
          <div class="preview" aria-hidden="true">
            <GoodsCard :goods="previewGoods" :show-favorite="false" />
          </div>
        </div>
        <div class="ct-card side-card">
          <div class="done-hd">
            <h3>信息完整度</h3>
            <b class="num">{{ completeness }}%</b>
          </div>
          <div class="done-bar"><span :style="{ width: `${completeness}%` }" /></div>
          <ul class="done-list">
            <li v-for="item in checklist" :key="item.label" :class="{ 'is-ok': item.ok }">
              <el-icon><component :is="item.ok ? 'CircleCheckFilled' : 'CircleClose'" /></el-icon>{{ item.label }}
            </li>
          </ul>
        </div>
      </aside>
    </div>

    <div class="pub-bar">
      <div class="pub-bar__inner">
        <span class="pub-bar__tip"><el-icon><InfoFilled /></el-icon>提交后由管理员审核，通常 1 小时内完成</span>
        <el-button v-if="!isEdit" round size="large" class="ct-btn-ghost" @click="saveDraft">存草稿</el-button>
        <el-button
          ref="submitRef"
          type="primary"
          round
          size="large"
          class="pub-bar__submit"
          :loading="submitting"
          data-testid="publish-submit"
          @click="handleSubmit"
        >
          {{ isEdit ? '保存修改' : '发布闲置' }}
        </el-button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useDebounceFn } from '@vueuse/core'
import gsap from 'gsap'
import { getCategoryTree } from '@/api/category'
import { getGoodsDetail, publishGoods, updateGoods } from '@/api/goods'
import { uploadFile } from '@/api/file'
import { suggestPrice } from '@/api/price'
import DemoBanner from '@/components/DemoBanner.vue'
import GoodsCard from '@/components/GoodsCard.vue'
import { confettiFrom, isReducedMotion } from '@/composables/useMotion'
import { GOODS_CONDITION_LABELS, TRADE_METHOD_LABELS, TradeMethod, enumOptions } from '@/constants/enums'
import { useUserStore } from '@/stores/user'
import { formatPrice } from '@/utils/format'

const MAX_IMAGES = 9
const MAX_SIZE = 5 * 1024 * 1024
const ALLOWED_TYPES = ['image/jpeg', 'image/png', 'image/webp']
const ALLOWED_EXT = ['.jpg', '.jpeg', '.png', '.webp']
const DRAFT_KEY = 'campus_trade_publish_draft'
const TRADE_ICONS = { [TradeMethod.FACE]: 'Location', [TradeMethod.EXPRESS]: 'Van', [TradeMethod.EITHER]: 'Switch' }
const TRADE_HINTS = {
  [TradeMethod.FACE]: '校内约个地方当面验货',
  [TradeMethod.EXPRESS]: '买家承担运费，寄出后填单号',
  [TradeMethod.EITHER]: '面交或邮寄都可以',
}

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const formRef = ref()
const submitRef = ref()
const loading = ref(false)
const submitting = ref(false)
const suggesting = ref(false)
const suggestion = ref(null)
const suggestFailed = ref(false)
const imageError = ref('')
const categoryTree = ref([])
const activeParentId = ref('')
const dragIndex = ref(-1)
const uploadingCount = ref(0)
const priceFlash = ref(false)

const form = reactive({
  title: '',
  description: '',
  categoryId: '',
  condition: '',
  tradeMethod: '',
  originalPrice: '',
  price: '',
  images: [],
})

const conditionOptions = enumOptions(GOODS_CONDITION_LABELS)
const tradeOptions = enumOptions(TRADE_METHOD_LABELS)
const isEdit = computed(() => Boolean(route.query.id))
const goodsId = computed(() => (route.query.id ? String(route.query.id) : ''))

const rules = {
  title: [
    { required: true, message: '请填写标题', trigger: 'blur' },
    { min: 2, max: 60, message: '标题长度为 2–60 个字', trigger: 'blur' },
  ],
  description: [
    { required: true, message: '请填写商品描述', trigger: 'blur' },
    { min: 8, message: '描述至少 8 个字，方便买家了解', trigger: 'blur' },
  ],
  categoryId: [{ required: true, message: '请选择分类', trigger: 'change' }],
  condition: [{ required: true, message: '请选择成色', trigger: 'change' }],
  tradeMethod: [{ required: true, message: '请选择交易方式', trigger: 'change' }],
  price: [
    { required: true, message: '请填写售价', trigger: 'blur' },
    {
      validator(_rule, value, callback) {
        if (value === '' || value == null) {
          callback()
          return
        }
        const num = Number(value)
        if (Number.isNaN(num) || num <= 0) callback(new Error('售价需大于 0'))
        else callback()
      },
      trigger: 'blur',
    },
  ],
  originalPrice: [
    {
      validator(_rule, value, callback) {
        if (value === '' || value == null) {
          callback()
          return
        }
        const num = Number(value)
        if (Number.isNaN(num) || num < 0) {
          callback(new Error('请输入正确的原价'))
          return
        }
        if (form.price !== '' && num < Number(form.price)) {
          callback(new Error('原价不能低于售价'))
          return
        }
        callback()
      },
      trigger: 'blur',
    },
  ],
}

/* ---------- 分类：两级 Chip，只能选叶子 ---------- */
const activeParent = computed(() => categoryTree.value.find((node) => String(node.id) === String(activeParentId.value)))
const childCategories = computed(() => activeParent.value?.children || [])
const categoryName = computed(() => {
  for (const node of categoryTree.value) {
    if (String(node.id) === String(form.categoryId)) return node.name
    const child = (node.children || []).find((item) => String(item.id) === String(form.categoryId))
    if (child) return child.name
  }
  return ''
})

function pickParent(node) {
  activeParentId.value = node.id
  if (!node.children?.length) pickCategory(node.id)
}

function pickCategory(id) {
  form.categoryId = String(id)
  suggestion.value = null
  formRef.value?.validateField('categoryId').catch(() => {})
}

function pickCondition(value) {
  form.condition = value
  formRef.value?.validateField('condition').catch(() => {})
}

function pickTrade(value) {
  form.tradeMethod = value
  formRef.value?.validateField('tradeMethod').catch(() => {})
}

function findParentId(nodes, id) {
  for (const node of nodes || []) {
    if (String(node.id) === String(id)) return node.id
    if ((node.children || []).some((child) => String(child.id) === String(id))) return node.id
  }
  return ''
}

/* ---------- 图片 ---------- */
function beforeUpload(file) {
  imageError.value = ''
  const name = (file.name || '').toLowerCase()
  const extOk = ALLOWED_EXT.some((ext) => name.endsWith(ext))
  const typeOk = !file.type || ALLOWED_TYPES.includes(file.type)
  if (!extOk || !typeOk) {
    imageError.value = '仅支持 jpg、jpeg、png、webp 图片'
    ElMessage.error(imageError.value)
    return false
  }
  if (file.size > MAX_SIZE) {
    imageError.value = '单张图片不能超过 5MB'
    ElMessage.error(imageError.value)
    return false
  }
  if (form.images.length + uploadingCount.value >= MAX_IMAGES) {
    imageError.value = '最多上传 9 张图片'
    ElMessage.error(imageError.value)
    return false
  }
  return true
}

async function customUpload({ file, onSuccess, onError }) {
  uploadingCount.value += 1
  try {
    const data = await uploadFile(file)
    const url = data?.url
    if (!url) throw new Error('上传失败')
    form.images.push(url)
    onSuccess(data)
  } catch (error) {
    onError(error)
  } finally {
    uploadingCount.value -= 1
  }
}

function moveImage(index, step) {
  const target = index + step
  if (target < 0 || target >= form.images.length) return
  const copy = form.images.slice()
  const [item] = copy.splice(index, 1)
  copy.splice(target, 0, item)
  form.images = copy
}

function removeImage(index) {
  form.images.splice(index, 1)
}

function onDragStart(index) {
  dragIndex.value = index
}

function onDrop(index) {
  if (dragIndex.value < 0 || dragIndex.value === index) return
  moveImage(dragIndex.value, index - dragIndex.value)
  dragIndex.value = -1
}

/* ---------- 定价建议 ---------- */
const hasEnoughSamples = computed(
  () => Number(suggestion.value?.sampleCount || 0) >= 5 && suggestion.value?.minPrice != null && suggestion.value?.maxPrice != null,
)
const suggestBar = computed(() => {
  const data = suggestion.value
  if (!data) return { left: 0, right: 0, mid: 0 }
  const min = Number(data.minPrice)
  const max = Number(data.maxPrice)
  const lo = min * 0.6
  const hi = max * 1.4 || 1
  const pct = (value) => Math.min(100, Math.max(0, ((value - lo) / (hi - lo || 1)) * 100))
  return { left: pct(min), right: pct(max), mid: pct(Number(data.suggestedPrice)) }
})

async function fetchSuggestion({ silent = false } = {}) {
  if (!form.categoryId || !form.condition) {
    if (!silent) ElMessage.warning('请先选择分类和成色')
    return
  }
  suggesting.value = true
  suggestFailed.value = false
  try {
    suggestion.value = await suggestPrice({
      categoryId: form.categoryId,
      condition: form.condition,
      originalPrice: form.originalPrice === '' ? undefined : Number(form.originalPrice),
      title: form.title || undefined,
    })
  } catch {
    suggestion.value = null
    suggestFailed.value = true
  } finally {
    suggesting.value = false
  }
}

// 选好分类和成色后防抖 400ms 自动获取一次；没填原价时不自动请求，避免样本不足的报错提示
const autoSuggest = useDebounceFn(() => {
  if (form.categoryId && form.condition && form.originalPrice !== '') fetchSuggestion({ silent: true })
}, 400)
watch(() => [form.categoryId, form.condition, form.originalPrice], autoSuggest)

// 点击「采用建议价」：价格数字滚动到建议价并闪一下高亮
function adoptSuggestion() {
  if (suggestion.value?.suggestedPrice == null) return
  const target = Number(suggestion.value.suggestedPrice)
  const from = Number(form.price) || 0
  if (isReducedMotion() || from === target) {
    form.price = formatPrice(target)
  } else {
    const box = { v: from }
    gsap.to(box, {
      v: target,
      duration: 0.6,
      ease: 'power3.out',
      onUpdate: () => {
        form.price = box.v.toFixed(2)
      },
      onComplete: () => {
        form.price = formatPrice(target)
      },
    })
  }
  priceFlash.value = false
  requestAnimationFrame(() => {
    priceFlash.value = true
    setTimeout(() => {
      priceFlash.value = false
    }, 900)
  })
}

/* ---------- 预览 / 完整度 / 步骤 ---------- */
const previewGoods = computed(() => ({
  id: 'preview',
  title: form.title || '你的闲置标题会显示在这里',
  price: form.price === '' ? 0 : Number(form.price) || 0,
  originalPrice: form.originalPrice === '' ? null : Number(form.originalPrice),
  condition: form.condition,
  categoryName: categoryName.value,
  coverUrl: form.images[0] || '',
  status: 'ON_SHELF',
  seller: {
    id: userStore.userInfo?.id,
    nickname: userStore.userInfo?.nickname || '我',
    avatar: userStore.userInfo?.avatar,
  },
  createTime: new Date().toISOString(),
}))
const checklist = computed(() => [
  { label: '至少 1 张图片', ok: form.images.length > 0, w: 25 },
  { label: '标题', ok: form.title.trim().length >= 2, w: 15 },
  { label: '分类和成色', ok: Boolean(form.categoryId && form.condition), w: 15 },
  { label: '描述（8 字以上）', ok: form.description.trim().length >= 8, w: 15 },
  { label: '交易方式', ok: Boolean(form.tradeMethod), w: 10 },
  { label: '售价', ok: Number(form.price) > 0, w: 15 },
  { label: '原价（建议项）', ok: form.originalPrice !== '', w: 5 },
])
const completeness = computed(() => checklist.value.reduce((sum, item) => sum + (item.ok ? item.w : 0), 0))
const steps = computed(() => [
  { label: '上传图片', done: form.images.length > 0 },
  {
    label: '商品信息',
    done: Boolean(form.title.trim() && form.categoryId && form.condition && form.description.trim() && form.tradeMethod),
  },
  { label: '定价', done: Number(form.price) > 0 },
  { label: '提交审核', done: false },
])
const currentStep = computed(() => {
  const index = steps.value.findIndex((step) => !step.done)
  return index < 0 ? steps.value.length - 1 : index
})

/* ---------- 草稿（仅存本机浏览器） ---------- */
function saveDraft() {
  localStorage.setItem(DRAFT_KEY, JSON.stringify({ ...form, images: form.images.slice() }))
  ElMessage.success('草稿已保存在本机，下次打开发布页会自动带出')
}

function restoreDraft() {
  try {
    const draft = JSON.parse(localStorage.getItem(DRAFT_KEY) || 'null')
    if (!draft) return
    Object.keys(form).forEach((key) => {
      if (draft[key] != null) form[key] = key === 'images' ? draft.images.slice(0, MAX_IMAGES) : draft[key]
    })
    activeParentId.value = findParentId(categoryTree.value, form.categoryId)
    ElMessage.info('已带出上次保存的草稿')
  } catch {
    localStorage.removeItem(DRAFT_KEY)
  }
}

async function loadDetail() {
  if (!goodsId.value) return
  loading.value = true
  try {
    const data = await getGoodsDetail(goodsId.value)
    form.title = data.title || ''
    form.description = data.description || ''
    form.categoryId = data.categoryId ? String(data.categoryId) : ''
    form.condition = data.condition || ''
    form.tradeMethod = data.tradeMethod || ''
    form.originalPrice = data.originalPrice == null ? '' : String(data.originalPrice)
    form.price = data.price == null ? '' : String(data.price)
    form.images = Array.isArray(data.images) && data.images.length
      ? data.images.map(String)
      : data.coverUrl
        ? [data.coverUrl]
        : []
    activeParentId.value = findParentId(categoryTree.value, form.categoryId)
  } finally {
    loading.value = false
  }
}

async function handleSubmit() {
  if (!formRef.value || submitting.value) return
  imageError.value = ''
  if (!form.images.length) {
    imageError.value = '请至少上传 1 张图片'
    ElMessage.error(imageError.value)
    return
  }
  try {
    await formRef.value.validate()
  } catch {
    return
  }

  const payload = {
    title: form.title.trim(),
    description: form.description.trim(),
    categoryId: String(form.categoryId),
    condition: form.condition,
    tradeMethod: form.tradeMethod,
    price: Number(form.price),
    images: form.images.slice(),
    coverUrl: form.images[0],
  }
  if (form.originalPrice !== '') payload.originalPrice = Number(form.originalPrice)

  submitting.value = true
  try {
    if (isEdit.value) {
      await updateGoods(goodsId.value, payload)
    } else {
      await publishGoods(payload)
      localStorage.removeItem(DRAFT_KEY)
    }
    confettiFrom(submitRef.value?.$el)
    ElMessage.success('已提交审核')
    await new Promise((resolve) => setTimeout(resolve, isReducedMotion() ? 0 : 900))
    await router.push({ path: '/user', query: { tab: 'goods', status: 'PENDING_AUDIT' } })
  } finally {
    submitting.value = false
  }
}

onMounted(async () => {
  try {
    const data = await getCategoryTree()
    categoryTree.value = Array.isArray(data) ? data : []
  } catch {
    categoryTree.value = []
  }
  if (goodsId.value) await loadDetail()
  else restoreDraft()
})

watch(goodsId, async () => {
  if (goodsId.value) await loadDetail()
})
</script>

<style scoped lang="scss">
.publish-page {
  padding-bottom: 120px;
}

.pub-head {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-end;
  justify-content: space-between;
  gap: 20px;
  margin: 32px 0 24px;

  h1 {
    margin: 0;
    font-size: 32px;
    font-weight: 800;
    letter-spacing: -0.02em;
  }

  p {
    margin: 6px 0 0;
    color: var(--ct-text-3);
  }
}

.steps {
  display: flex;
  align-items: center;
  gap: 8px;
  margin: 0;
  padding: 0;
  list-style: none;

  li {
    display: flex;
    align-items: center;
    gap: 8px;
    color: var(--ct-text-3);
    font-size: 13px;
    font-weight: 600;

    &:not(:last-child)::after {
      content: '';
      width: 28px;
      height: 2px;
      margin-left: 4px;
      border-radius: 2px;
      background: var(--ct-border-strong);
    }

    &.is-on {
      color: var(--ct-text);
    }

    &.is-done {
      color: var(--ct-text-2);

      &::after {
        background: var(--ct-primary);
      }
    }
  }
}

.steps__dot {
  display: grid;
  place-items: center;
  width: 26px;
  height: 26px;
  border: 1.5px solid var(--ct-border-strong);
  border-radius: 50%;
  background: var(--ct-surface);
  font-size: 12px;
  transition:
    background var(--ct-dur-base),
    border-color var(--ct-dur-base);

  .is-on & {
    border-color: var(--ct-primary);
    color: var(--ct-primary);
    box-shadow: var(--ct-ring);
  }

  .is-done & {
    border-color: transparent;
    background: var(--ct-gradient-brand);
    color: #fff;
  }
}

.pub-grid {
  display: grid;
  grid-template-columns: 8fr 4fr;
  gap: 28px;
  align-items: start;
}

.pub-form {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.pub-sec {
  padding: 26px 28px 10px;
}

.pub-sec__title {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 10px;
  margin: 0 0 20px;
  font-size: 18px;
  font-weight: 800;

  small {
    color: var(--ct-text-3);
    font-size: 12px;
    font-weight: 500;
  }
}

.pub-sec__no {
  display: grid;
  place-items: center;
  width: 26px;
  height: 26px;
  border-radius: 9px;
  background: var(--ct-primary-soft);
  color: var(--ct-primary);
  font-size: 13px;
}

/* 上传 */
.dropzone {
  margin-bottom: 18px;

  :deep(.el-upload-dragger) {
    padding: 40px 20px;
    border: 2px dashed var(--ct-border-strong);
    border-radius: var(--ct-radius-lg);
    background: var(--ct-surface-2);
    transition:
      border-color var(--ct-dur-base),
      background var(--ct-dur-base);

    &:hover,
    &.is-dragover {
      border-color: var(--ct-primary);
      background: var(--ct-primary-soft);
    }
  }
}

.dropzone__inner {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
  color: var(--ct-text-3);
  font-size: 13px;

  b {
    color: var(--ct-text);
    font-size: 15px;
  }
}

.dropzone__ico {
  display: grid;
  place-items: center;
  width: 56px;
  height: 56px;
  margin-bottom: 6px;
  border-radius: 18px;
  background: var(--ct-gradient-brand);
  color: #fff;
  font-size: 26px;
  box-shadow: var(--ct-shadow-glow);
}

.img-grid {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 12px;
  margin-bottom: 18px;
}

.img-cell,
.img-adder :deep(.el-upload) {
  position: relative;
  aspect-ratio: 1;
  overflow: hidden;
  border-radius: 18px;
}

.img-cell {
  background: var(--ct-surface-sunken);
  cursor: grab;

  img {
    display: block;
    width: 100%;
    height: 100%;
    object-fit: cover;
  }

  &.is-uploading {
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    gap: 8px;
    color: var(--ct-text-3);
    font-size: 12px;

    .ct-spinner {
      border-color: var(--ct-primary-soft-2);
      border-top-color: var(--ct-primary);
    }
  }
}

.img-cell__cover {
  position: absolute;
  top: 8px;
  left: 8px;
  background: var(--ct-gradient-brand);
  color: #fff;
}

.img-cell__ops {
  position: absolute;
  inset: auto 0 0;
  display: flex;
  justify-content: center;
  gap: 6px;
  padding: 24px 6px 8px;
  background: linear-gradient(transparent, rgba(15, 18, 40, 0.6));
  opacity: 0;
  transform: translateY(8px);
  transition:
    opacity var(--ct-dur-base),
    transform var(--ct-dur-base) var(--ct-ease-out);

  button {
    display: grid;
    place-items: center;
    width: 28px;
    height: 28px;
    border: 0;
    border-radius: 50%;
    background: var(--ct-surface);
    color: var(--ct-text);
    cursor: pointer;

    &:disabled {
      opacity: 0.4;
      cursor: not-allowed;
    }

    &.is-del:hover {
      background: var(--el-color-danger);
      color: #fff;
    }
  }
}

.img-cell:hover .img-cell__ops,
.img-cell:focus-within .img-cell__ops {
  opacity: 1;
  transform: none;
}

.img-adder :deep(.el-upload) {
  display: grid;
  width: 100%;
  place-items: center;
  border: 2px dashed var(--ct-border-strong);
  background: var(--ct-surface-2);
  transition:
    border-color var(--ct-dur-base),
    color var(--ct-dur-base);

  &:hover {
    border-color: var(--ct-primary);
    color: var(--ct-primary);
  }
}

.img-adder__inner {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
  color: var(--ct-text-3);
  font-size: 12px;

  .el-icon {
    font-size: 22px;
  }
}

.img-enter-active,
.img-leave-active {
  transition:
    opacity var(--ct-dur-base),
    transform var(--ct-dur-base) var(--ct-ease-spring);
}

.img-enter-from,
.img-leave-to {
  opacity: 0;
  transform: scale(0.85);
}

.img-move {
  transition: transform var(--ct-dur-slow) var(--ct-ease-out);
}

.field-error {
  margin: -8px 0 16px;
  color: var(--el-color-danger);
  font-size: 12px;
}

/* 信息 */
.two-col {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 20px;
}

.chip-col {
  display: flex;
  flex-direction: column;
  gap: 10px;
  width: 100%;
}

.chips {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;

  &--sub {
    padding: 10px;
    border-radius: 14px;
    background: var(--ct-surface-2);
  }
}

.fade-down-enter-active {
  transition:
    opacity var(--ct-dur-base),
    transform var(--ct-dur-base) var(--ct-ease-out);
}

.fade-down-enter-from {
  opacity: 0;
  transform: translateY(-6px);
}

.trade-opts {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
  width: 100%;
}

.trade-opt {
  position: relative;
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 4px;
  padding: 16px;
  border: 1.5px solid var(--ct-border);
  border-radius: var(--ct-radius-lg);
  background: var(--ct-surface);
  color: var(--ct-text);
  font: inherit;
  text-align: left;
  cursor: pointer;
  transition:
    border-color var(--ct-dur-base),
    box-shadow var(--ct-dur-base),
    transform var(--ct-dur-base) var(--ct-ease-out);

  b {
    font-size: 14px;
    line-height: 1.4;
  }

  small {
    color: var(--ct-text-3);
    font-size: 12px;
    line-height: 1.5;
  }

  &:hover {
    transform: translateY(-2px);
    border-color: var(--ct-primary-soft-2);
  }

  &.is-on {
    border-color: var(--ct-primary);
    background: var(--ct-primary-soft);
    box-shadow: var(--ct-ring);
  }
}

.trade-opt__ico {
  display: grid;
  place-items: center;
  width: 34px;
  height: 34px;
  margin-bottom: 6px;
  border-radius: 11px;
  background: var(--ct-surface-sunken);
  color: var(--ct-primary);
  font-size: 16px;

  .is-on & {
    background: var(--ct-gradient-brand);
    color: #fff;
  }
}

.trade-opt__check {
  position: absolute;
  top: 12px;
  right: 12px;
  color: var(--ct-primary);
  font-size: 18px;
  opacity: 0;
  transform: scale(0.5);
  transition:
    opacity var(--ct-dur-base),
    transform var(--ct-dur-base) var(--ct-ease-spring);

  .is-on & {
    opacity: 1;
    transform: none;
  }
}

/* 定价 */
.price-grid {
  display: grid;
  grid-template-columns: 1fr 1.1fr;
  gap: 24px;
  align-items: start;
}

.price-input {
  :deep(.el-input__wrapper) {
    height: 64px;
    border-radius: var(--ct-radius-lg);
    transition:
      box-shadow var(--ct-dur-base),
      background var(--ct-dur-base);
  }

  :deep(.el-input__inner) {
    color: var(--ct-price);
    font-family: var(--ct-font-num);
    font-size: 26px;
    font-weight: 800;
  }

  &.is-flash :deep(.el-input__wrapper) {
    animation: price-flash 0.9s var(--ct-ease-out);
  }
}

.price-input__yen {
  color: var(--ct-price);
  font-size: 20px;
  font-weight: 800;
}

@keyframes price-flash {
  30% {
    background: var(--ct-sun-soft);
    box-shadow:
      0 0 0 1px var(--ct-sun),
      0 0 0 6px rgba(255, 197, 61, 0.25);
  }
}

/* 智能定价卡：流动渐变描边 */
.ai-card {
  position: relative;
  margin-bottom: 18px;
  padding: 18px;
  border-radius: var(--ct-radius-lg);
  background: var(--ct-surface);
  isolation: isolate;

  &::before {
    content: '';
    position: absolute;
    inset: -1.5px;
    z-index: -2;
    border-radius: inherit;
    background: linear-gradient(120deg, var(--ct-primary), var(--ct-mint), var(--ct-accent), var(--ct-primary));
    background-size: 300% 300%;
    animation: ai-flow 6s linear infinite;
  }

  &::after {
    content: '';
    position: absolute;
    inset: 0;
    z-index: -1;
    border-radius: inherit;
    background: linear-gradient(160deg, var(--ct-primary-soft), var(--ct-surface) 60%);
  }
}

@keyframes ai-flow {
  to {
    background-position: 300% 0;
  }
}

.ai-card__hd {
  display: flex;
  align-items: center;
  gap: 8px;

  b {
    flex: 1;
  }
}

.ai-card__ico {
  display: grid;
  place-items: center;
  width: 28px;
  height: 28px;
  border-radius: 9px;
  background: var(--ct-gradient-brand);
  color: #fff;
}

.ai-card__sk {
  display: flex;
  align-items: flex-end;
  gap: 6px;
  height: 72px;
  margin-top: 16px;

  .ct-sk {
    flex: 1;
    border-radius: 6px;
  }
}

.ai-card__range {
  margin-top: 14px;
  font-size: 24px;
  font-weight: 800;
}

.ai-bar {
  position: relative;
  height: 8px;
  margin: 12px 0;
  border-radius: 99px;
  background: var(--ct-surface-sunken);
}

.ai-bar__fill {
  position: absolute;
  top: 0;
  bottom: 0;
  border-radius: 99px;
  background: linear-gradient(90deg, var(--ct-mint), var(--ct-primary));
}

.ai-bar__mid {
  position: absolute;
  top: 50%;
  width: 14px;
  height: 14px;
  margin: -7px;
  border: 3px solid var(--ct-accent);
  border-radius: 50%;
  background: var(--ct-surface);
}

.ai-card__meta {
  margin: 8px 0 0;
  color: var(--ct-text-2);
  font-size: 13px;

  b {
    color: var(--ct-price);
  }
}

.ai-card__few,
.ai-card__empty {
  margin: 14px 0 0;
  color: var(--ct-text-3);
  font-size: 13px;
}

.ai-card__few {
  color: var(--ct-text-2);
  font-weight: 600;
}

.ai-card__few-badge {
  margin-top: 14px;
}

.ai-card__ref {
  margin: 10px 0 0;
  color: var(--ct-text-2);
  font-size: 13px;

  b {
    color: var(--ct-price);
    font-size: 24px;
    font-weight: 800;
  }
}

.ai-card__reason {
  margin: 8px 0 0;
  color: var(--ct-text-3);
  font-size: 12px;
  line-height: 1.6;
}

.ai-card__adopt {
  width: 100%;
  margin-top: 14px;
}

/* 侧栏 */
.pub-side {
  position: sticky;
  top: calc(var(--ct-header-h) + 20px);
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.side-card {
  padding: 20px;

  h3 {
    margin: 0 0 14px;
    font-size: 15px;
    font-weight: 800;
  }
}

.preview {
  pointer-events: none;

  :deep(.goods-card__cover) {
    background: var(--ct-surface-sunken);
  }
}

.done-hd {
  display: flex;
  align-items: baseline;
  justify-content: space-between;

  b {
    color: var(--ct-primary);
    font-size: 20px;
  }
}

.done-bar {
  height: 8px;
  overflow: hidden;
  border-radius: 99px;
  background: var(--ct-surface-sunken);

  span {
    display: block;
    height: 100%;
    border-radius: inherit;
    background: var(--ct-gradient-brand);
    transition: width 0.6s var(--ct-ease-out);
  }
}

.done-list {
  display: grid;
  gap: 8px;
  margin: 16px 0 0;
  padding: 0;
  list-style: none;
  font-size: 13px;

  li {
    display: flex;
    align-items: center;
    gap: 8px;
    color: var(--ct-text-3);

    &.is-ok {
      color: var(--ct-text-2);

      .el-icon {
        color: var(--ct-mint);
      }
    }
  }
}

/* 底部玻璃操作栏 */
.pub-bar {
  position: fixed;
  right: 0;
  bottom: 0;
  left: 0;
  z-index: 20;
  border-top: 1px solid var(--ct-border);
  background: var(--ct-glass);
  backdrop-filter: blur(18px) saturate(1.6);
}

.pub-bar__inner {
  display: flex;
  align-items: center;
  gap: 12px;
  max-width: var(--ct-container);
  margin: 0 auto;
  padding: 14px 32px;
}

.pub-bar__tip {
  display: flex;
  flex: 1;
  align-items: center;
  gap: 6px;
  color: var(--ct-text-3);
  font-size: 13px;
}

.pub-bar__submit {
  min-width: 148px;
  margin-left: 0 !important;
}

@media (max-width: 1024px) {
  .pub-grid {
    grid-template-columns: 1fr;
  }

  .pub-side {
    position: static;
  }
}

@media (max-width: 768px) {
  .img-grid {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }

  .two-col,
  .price-grid,
  .trade-opts {
    grid-template-columns: 1fr;
  }

  .pub-bar__tip {
    display: none;
  }

  .pub-bar__inner {
    padding: 12px 16px;

    .el-button {
      flex: 1;
    }
  }
}

@media (prefers-reduced-motion: reduce) {
  .ai-card::before,
  .price-input.is-flash :deep(.el-input__wrapper) {
    animation: none;
  }

  .img-enter-active,
  .img-leave-active,
  .img-move,
  .trade-opt,
  .img-cell__ops {
    transition: none;
  }
}
</style>
