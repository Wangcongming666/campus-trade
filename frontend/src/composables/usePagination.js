import { ref } from 'vue'

/**
 * 通用分页 composable。
 * 请求：pageNum（从 1 开始）、pageSize
 * 返回：{ records, total, pageNum, pageSize, pages }
 *
 * @param {(params: object) => Promise<object>} fetcher
 * @param {{ pageNum?: number, pageSize?: number }} options
 */
const MAX_PAGE_SIZE = 100

function capPageSize(size, fallback = 10) {
  const num = Number(size)
  if (!Number.isFinite(num) || num <= 0) return fallback
  return Math.min(MAX_PAGE_SIZE, Math.max(1, Math.floor(num)))
}

export function usePagination(fetcher, options = {}) {
  const pageNum = ref(options.pageNum || 1)
  const pageSize = ref(capPageSize(options.pageSize, 10))
  const total = ref(0)
  const pages = ref(0)
  const records = ref([])
  const loading = ref(false)
  const finished = ref(false)

  async function fetchData(extraParams = {}, { append = false } = {}) {
    if (append && (loading.value || finished.value)) return records.value
    loading.value = true
    try {
      const data = (await fetcher({
        pageNum: pageNum.value,
        pageSize: capPageSize(pageSize.value),
        ...extraParams,
      })) || {}
      const list = data.records || []
      records.value = append ? records.value.concat(list) : list
      total.value = data.total ?? 0
      pages.value = data.pages ?? 0
      if (data.pageNum != null) pageNum.value = data.pageNum
      if (data.pageSize != null) pageSize.value = capPageSize(data.pageSize, pageSize.value)
      finished.value = pageNum.value >= (pages.value || 1) || records.value.length >= total.value
      return data
    } finally {
      loading.value = false
    }
  }

  function handleSizeChange(size) {
    pageSize.value = capPageSize(size)
    pageNum.value = 1
    finished.value = false
    return fetchData()
  }

  function handleCurrentChange(current) {
    pageNum.value = current
    finished.value = false
    return fetchData()
  }

  async function loadMore(extraParams = {}) {
    if (loading.value || finished.value) return
    pageNum.value += 1
    return fetchData(extraParams, { append: true })
  }

  function reset() {
    pageNum.value = options.pageNum || 1
    pageSize.value = capPageSize(options.pageSize, 10)
    total.value = 0
    pages.value = 0
    records.value = []
    finished.value = false
  }

  return {
    pageNum,
    pageSize,
    total,
    pages,
    records,
    loading,
    finished,
    fetchData,
    loadMore,
    handleSizeChange,
    handleCurrentChange,
    reset,
  }
}
