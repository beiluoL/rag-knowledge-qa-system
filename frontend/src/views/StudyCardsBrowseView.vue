<template>
  <div class="page-container cards-browse">
    <!-- 顶部 -->
    <div class="page-header">
      <div>
        <h1 class="page-title">学习卡片库</h1>
        <p class="page-subtitle">浏览知识库生成的学习卡片，支持列表与时间轴两种视图，点击查看完整内容。</p>
      </div>
      <el-button :icon="ArrowLeft" @click="router.push('/learn')">返回学习中心</el-button>
    </div>

    <el-card>
      <template #header>
        <div class="card-header">
          <div class="header-left">
            <el-select v-model="selectedKbId" placeholder="选择知识库" class="kb-select" :loading="kbLoading"
              @change="loadCards">
              <el-option v-for="kb in knowledgeBases" :key="kb.id" :label="kb.name" :value="kb.id" />
            </el-select>
            <el-tag v-if="cards.length" type="info" effect="plain" size="small">{{ cards.length }} 张卡片</el-tag>
          </div>
          <div class="header-actions">
            <el-radio-group v-model="viewMode" size="small" class="view-toggle">
              <el-radio-button value="list">
                <el-icon><List /></el-icon> 列表
              </el-radio-button>
              <el-radio-button value="timeline">
                <el-icon><Milestone /></el-icon> 时间轴
              </el-radio-button>
            </el-radio-group>
            <el-button type="primary" :icon="Play" :disabled="!selectedKbId"
              @click="router.push(`/learn/list?kb=${selectedKbId}`)">进入学习</el-button>
          </div>
        </div>
      </template>

      <!-- 加载 / 空 -->
      <div v-if="loading" class="ui-loading" aria-busy="true">
        <el-icon class="is-loading"><Loader2 /></el-icon> 正在加载卡片…
      </div>
      <div v-else-if="!selectedKbId" class="ui-empty">
        <el-icon class="empty-icon"><Library /></el-icon>
        <p>请选择左侧知识库以浏览其学习卡片。</p>
      </div>
      <div v-else-if="cards.length === 0" class="ui-empty">
        <el-icon class="empty-icon"><FileQuestion /></el-icon>
        <p>该知识库暂无可学习的卡片，请先在后台上传文档。</p>
      </div>

      <!-- 列表视图 -->
      <div v-else-if="viewMode === 'list'" class="list-mode">
        <div v-for="(c, i) in cards" :key="i" class="list-item" role="button" tabindex="0"
          :aria-label="`第 ${i + 1} 张：${c.front}`" @click="openDetail(i)" @keyup.enter="openDetail(i)">
          <span class="li-idx">{{ i + 1 }}</span>
          <div class="li-body">
            <div class="li-front">{{ c.front }}</div>
            <div class="li-doc"><el-icon><BookOpen /></el-icon> {{ c.documentTitle }}</div>
          </div>
          <el-icon class="li-arrow"><ChevronRight /></el-icon>
        </div>
      </div>

      <!-- 时间轴视图 -->
      <div v-else class="timeline-mode">
        <template v-for="(grp, gi) in grouped" :key="gi">
          <div class="tl-group">
            <span class="tl-group-dot"><el-icon><BookOpen /></el-icon></span>
            <span class="tl-group-title">{{ grp.docTitle }}</span>
            <span class="tl-group-count">{{ grp.items.length }} 张</span>
          </div>
          <div v-for="item in grp.items" :key="item.index" class="tl-item" role="button" tabindex="0"
            :aria-label="`第 ${item.index + 1} 张：${item.front}`"
            @click="openDetail(item.index)" @keyup.enter="openDetail(item.index)">
            <span class="tl-dot"></span>
            <div class="tl-card">
              <div class="tl-front">{{ item.front }}</div>
              <el-icon class="tl-arrow"><ChevronRight /></el-icon>
            </div>
          </div>
        </template>
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  ArrowLeft, List, Milestone, Play, Loader2, Library,
  FileQuestion, BookOpen, ChevronRight
} from 'lucide-vue-next'
import { getStudyCards, type StudyCard } from '@/api/learning'
import { listKnowledgeBases } from '@/api/knowledgeBase'

const router = useRouter()
const route = useRoute()

const knowledgeBases = ref<Array<{ id: number; name: string }>>([])
const kbLoading = ref(false)
const selectedKbId = ref<number | null>(null)
const cards = ref<StudyCard[]>([])
const loading = ref(false)
const viewMode = ref<'list' | 'timeline'>('list')

// 按文档分组（时间轴用）
const grouped = computed(() => {
  const map = new Map<string, { docTitle: string; items: { index: number; front: string }[] }>()
  cards.value.forEach((c, i) => {
    if (!map.has(c.documentTitle)) map.set(c.documentTitle, { docTitle: c.documentTitle, items: [] })
    map.get(c.documentTitle)!.items.push({ index: i, front: c.front })
  })
  return Array.from(map.values())
})

async function loadKbs() {
  kbLoading.value = true
  try {
    const { data } = await listKnowledgeBases()
    knowledgeBases.value = data || []
    const qKb = route.query.kb ? Number(route.query.kb) : null
    if (qKb && knowledgeBases.value.some(k => k.id === qKb)) {
      selectedKbId.value = qKb
      await loadCards()
    } else if (knowledgeBases.value.length) {
      selectedKbId.value = knowledgeBases.value[0].id
      await loadCards()
    }
  } finally {
    kbLoading.value = false
  }
}

async function loadCards() {
  if (!selectedKbId.value) return
  loading.value = true
  try {
    const { data } = await getStudyCards(selectedKbId.value, 'list')
    cards.value = data || []
  } finally {
    loading.value = false
  }
}

function openDetail(index: number) {
  if (selectedKbId.value == null) return
  router.push(`/learn/card/${selectedKbId.value}/${index}`)
}

onMounted(loadKbs)
</script>

<style scoped>
.card-header { display: flex; justify-content: space-between; align-items: center; flex-wrap: wrap; gap: var(--space-md); }
.header-left { display: flex; align-items: center; gap: var(--space-md); }
.header-actions { display: flex; align-items: center; gap: var(--space-sm); flex-wrap: wrap; }
.kb-select { width: 220px; }
.view-toggle { margin-right: 4px; }

/* 列表视图 */
.list-mode { display: flex; flex-direction: column; gap: var(--space-sm); }
.list-item {
  display: flex; align-items: center; gap: var(--space-md);
  padding: var(--space-md) var(--space-lg);
  background: var(--surface); border: 1px solid var(--border); border-radius: var(--radius-md);
  cursor: pointer; transition: border-color var(--duration-fast), box-shadow var(--duration-fast), transform var(--duration-fast);
}
.list-item:hover { border-color: var(--primary-500); box-shadow: var(--shadow-sm); transform: translateX(2px); }
.li-idx {
  width: 26px; height: 26px; flex-shrink: 0; border-radius: 50%;
  background: var(--brand-gradient); color: var(--text-inverse); font-size: var(--text-xs); font-weight: 700;
  display: flex; align-items: center; justify-content: center;
}
.li-body { flex: 1; min-width: 0; }
.li-front { font-size: var(--text-sm); color: var(--text-primary); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.li-doc { font-size: 11px; color: var(--text-muted); display: flex; align-items: center; gap: 4px; margin-top: 2px; }
.li-doc :deep(.el-icon) { font-size: 12px; }
.li-arrow { color: var(--text-muted); flex-shrink: 0; }

/* 时间轴视图 */
.timeline-mode { padding-left: 8px; }
.tl-group { display: flex; align-items: center; gap: var(--space-sm); margin: var(--space-lg) 0 var(--space-sm); }
.tl-group-dot {
  width: 26px; height: 26px; border-radius: 50%; background: var(--primary-50); color: var(--primary-600);
  display: flex; align-items: center; justify-content: center; font-size: 14px; flex-shrink: 0;
}
.tl-group-title { font-size: var(--text-base); font-weight: 700; color: var(--text-primary); }
.tl-group-count { font-size: 11px; color: var(--text-muted); }

.tl-item { position: relative; padding: 0 0 var(--space-md) 38px; }
/* 连接线 */
.tl-item::before {
  content: ''; position: absolute; left: 12px; top: 6px; bottom: -6px; width: 2px; background: var(--border);
}
.tl-item:last-child::before { display: none; }
.tl-dot {
  position: absolute; left: 6px; top: 6px; width: 14px; height: 14px; border-radius: 50%;
  background: var(--surface); border: 3px solid var(--primary-500);
}
.tl-card {
  display: flex; align-items: center; justify-content: space-between; gap: var(--space-md);
  padding: var(--space-md) var(--space-lg);
  background: var(--surface); border: 1px solid var(--border); border-radius: var(--radius-md);
  cursor: pointer; transition: border-color var(--duration-fast), box-shadow var(--duration-fast);
}
.tl-card:hover { border-color: var(--primary-500); box-shadow: var(--shadow-sm); }
.tl-front { font-size: var(--text-sm); color: var(--text-primary); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.tl-arrow { color: var(--text-muted); flex-shrink: 0; }

/* 空 / 加载 */
.empty-icon { font-size: 40px; color: var(--text-muted); display: block; margin-bottom: var(--space-md); }
.ui-empty p { color: var(--text-muted); max-width: 360px; margin: 0 auto var(--space-md); line-height: 1.6; text-align: center; }
.ui-loading { text-align: center; padding: 48px; color: var(--text-secondary); display: flex; align-items: center; justify-content: center; gap: var(--space-sm); }
.is-loading { animation: rotating 1.2s linear infinite; }
@keyframes rotating { from { transform: rotate(0); } to { transform: rotate(360deg); } }

@media (max-width: 480px) {
  .kb-select { width: 100%; }
  .header-left { width: 100%; }
}
</style>
