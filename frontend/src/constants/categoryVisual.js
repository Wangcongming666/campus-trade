// 分类图块的图标和渐变色：按一级分类名称映射（layouts.md §1，图标/颜色只在前端决定）
const MAP = [
  { key: '数码', icon: 'Iphone', c1: '#8b8cff', c2: '#5b5cf0' },
  { key: '书', icon: 'Reading', c1: '#ffd27a', c2: '#f59e0b' },
  { key: '教材', icon: 'Reading', c1: '#ffd27a', c2: '#f59e0b' },
  { key: '生活', icon: 'House', c1: '#5eead4', c2: '#14c9a2' },
  { key: '服饰', icon: 'Handbag', c1: '#f9a8d4', c2: '#ec4899' },
  { key: '运动', icon: 'Basketball', c1: '#86efac', c2: '#22c55e' },
  { key: '美妆', icon: 'Brush', c1: '#fda4af', c2: '#f43f5e' },
  { key: '乐器', icon: 'Headset', c1: '#c4b5fd', c2: '#8b5cf6' },
  { key: '文娱', icon: 'Headset', c1: '#c4b5fd', c2: '#8b5cf6' },
  { key: '交通', icon: 'Bicycle', c1: '#7dd3fc', c2: '#38bdf8' },
  { key: '出行', icon: 'Bicycle', c1: '#7dd3fc', c2: '#38bdf8' },
]
const FALLBACK = [
  { icon: 'Goods', c1: '#ffb199', c2: '#ff6b4a' },
  { icon: 'Present', c1: '#a5b4fc', c2: '#6366f1' },
]

export function categoryVisual(name = '', index = 0) {
  const hit = MAP.find((item) => name.includes(item.key))
  return hit || FALLBACK[index % FALLBACK.length]
}
