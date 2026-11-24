<template>
  <div class="changelog-container">
    <!-- 顶部横幅与操作区 -->
    <div class="changelog-header">
      <div class="header-left">
        <h2 class="page-title">🚀 系统更新日志</h2>
        <p class="page-subtitle">记录 Core OJ 从安全沙箱、ICPC 竞赛到 AI 代码导师的每一次演进历程</p>
      </div>
      <div class="header-right">
        <el-button
          v-if="isRoot"
          type="primary"
          :icon="Plus"
          round
          @click="openCreateDialog"
        >
          发布更新日志
        </el-button>
      </div>
    </div>

    <!-- 类型筛选胶囊栏 -->
    <div class="filter-tabs">
      <button
        v-for="tab in typeTabs"
        :key="tab.value"
        type="button"
        class="filter-pill"
        :class="{ active: activeType === tab.value }"
        @click="handleTypeChange(tab.value)"
      >
        {{ tab.label }}
      </button>
    </div>

    <!-- 垂直时间轴主体 -->
    <el-card class="timeline-card" shadow="never" v-loading="loading">
      <el-empty v-if="!loading && list.length === 0" description="暂无系统更新记录" />

      <el-timeline v-else class="custom-timeline">
        <el-timeline-item
          v-for="(item, index) in list"
          :key="item.id"
          :timestamp="formatDateOnly(item.releaseDate)"
          placement="top"
          :color="getTypeMeta(item.type).color"
          :hollow="index !== 0"
          size="large"
        >
          <div class="log-item-card" :class="{ 'is-latest': index === 0 && queryParams.pageNum === 1 && !activeType }">
            <div class="log-card-header">
              <div class="log-title-group">
                <span class="version-badge">{{ item.version }}</span>
                <el-tag :type="getTypeMeta(item.type).tagType" size="small" effect="light">
                  {{ getTypeMeta(item.type).label }}
                </el-tag>
                <span class="log-title">{{ item.title }}</span>
                <span
                  v-if="index === 0 && queryParams.pageNum === 1 && !activeType && isWithin7Days(item.releaseDate)"
                  class="inline-new-tag"
                >
                  NEW
                </span>
              </div>

              <!-- 仅 ROOT 超级管理员可见的编辑/删除按钮 -->
              <div v-if="isRoot" class="root-actions">
                <el-button link type="primary" size="small" :icon="Edit" @click="openEditDialog(item)">
                  编辑
                </el-button>
                <el-button link type="danger" size="small" :icon="Delete" @click="handleDelete(item)">
                  删除
                </el-button>
              </div>
            </div>

            <!-- Markdown 渲染正文 -->
            <div class="log-content" v-html="renderMarkdown(item.content)"></div>

            <div class="log-footer">
              <span>发布人：{{ item.authorName || 'root' }}</span>
              <span>发布时间：{{ item.releaseDate }}</span>
            </div>
          </div>
        </el-timeline-item>
      </el-timeline>

      <!-- 分页条 -->
      <div v-if="total > queryParams.pageSize" class="pagination-bar">
        <el-pagination
          v-model:current-page="queryParams.pageNum"
          v-model:page-size="queryParams.pageSize"
          background
          layout="total, prev, pager, next"
          :total="total"
          @current-change="fetchList"
        />
      </div>
    </el-card>

    <!-- ROOT 专属：发布 / 编辑更新日志弹窗 -->
    <el-dialog
      v-model="dialogVisible"
      :title="editingId ? '编辑更新日志' : '发布新更新日志'"
      width="640px"
      destroy-on-close
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="版本号" prop="version">
              <el-input v-model.trim="form.version" placeholder="例如：v1.4.0" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="更新类型" prop="type">
              <el-select v-model="form.type" style="width: 100%">
                <el-option label="✨ 新功能 (FEAT)" value="FEAT" />
                <el-option label="⚡ 体验优化 (OPTIMIZE)" value="OPTIMIZE" />
                <el-option label="🐛 问题修复 (FIX)" value="FIX" />
                <el-option label="📢 系统公告 (NOTICE)" value="NOTICE" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>

        <el-form-item label="更新标题" prop="title">
          <el-input v-model.trim="form.title" placeholder="一句话概括本次更新核心亮点" />
        </el-form-item>

        <el-form-item label="发布时间" prop="releaseDate">
          <el-date-picker
            v-model="form.releaseDate"
            type="datetime"
            value-format="YYYY-MM-DD HH:mm:ss"
            placeholder="留空则默认当前时间（支持补录历史版本）"
            style="width: 100%"
            clearable
          />
        </el-form-item>

        <el-form-item label="更新详情" prop="content">
          <el-input
            v-model="form.content"
            type="textarea"
            :rows="7"
            placeholder="支持 Markdown 列表与加粗，例如：&#10;- 新增 **AI 代码导师**，支持时空复杂度胶囊切换&#10;- 优化 `Redis` 分布式防重锁与配额限流"
          />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="dialogVisible = false">取 消</el-button>
        <el-button type="primary" :loading="submitting" @click="handleSubmit">
          {{ editingId ? '保存修改' : '确认发布' }}
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { Plus, Edit, Delete } from '@element-plus/icons-vue'
import {
  getChangelogListApi,
  getLatestChangelogApi,
  createChangelogApi,
  updateChangelogApi,
  deleteChangelogApi
} from '../../api/changelog'
import { useUserStore } from '../../stores/user'
import type { Changelog, ChangelogType } from '../../types'

const READ_STORAGE_KEY = 'oj_read_changelog_id'

const userStore = useUserStore()

// 严格仅限 ROOT 超级管理员可维护
const isRoot = computed(() => {
  const user = (userStore as any).userInfo || (userStore as any).user
  const role = user?.role ?? (userStore as any).role
  return role === 'ROOT'
})

const typeTabs: { label: string; value: ChangelogType | '' }[] = [
  { label: '全部更新', value: '' },
  { label: '✨ 新功能', value: 'FEAT' },
  { label: '⚡ 体验优化', value: 'OPTIMIZE' },
  { label: '🐛 问题修复', value: 'FIX' },
  { label: '📢 系统公告', value: 'NOTICE' }
]

const loading = ref(false)
const list = ref<Changelog[]>([])
const total = ref(0)
const activeType = ref<ChangelogType | ''>('')

const queryParams = reactive({
  pageNum: 1,
  pageSize: 20
})

// 弹窗状态
const dialogVisible = ref(false)
const submitting = ref(false)
const editingId = ref<number | null>(null)
const formRef = ref<FormInstance>()

const form = reactive<{
  version: string
  title: string
  type: ChangelogType
  content: string
  releaseDate: string
}>({
  version: '',
  title: '',
  type: 'FEAT',
  content: '',
  releaseDate: ''
})

const rules: FormRules = {
  version: [{ required: true, message: '请输入版本号 (如 v1.4.0)', trigger: 'blur' }],
  title: [{ required: true, message: '请输入更新标题', trigger: 'blur' }],
  type: [{ required: true, message: '请选择更新类型', trigger: 'change' }],
  content: [{ required: true, message: '请输入更新详情正文', trigger: 'blur' }]
}

const fetchList = async () => {
  loading.value = true
  try {
    const res = await getChangelogListApi({
      pageNum: queryParams.pageNum,
      pageSize: queryParams.pageSize,
      type: activeType.value || undefined
    })
    list.value = res.items || []
    total.value = res.total || 0
  } finally {
    loading.value = false
  }
}

// 方案 B：进入日志页自动标记最新日志为已读，并通知顶栏消除 NEW 角标
const markLatestAsRead = async () => {
  try {
    const latest = await getLatestChangelogApi()
    if (latest && latest.id) {
      localStorage.setItem(READ_STORAGE_KEY, String(latest.id))
      window.dispatchEvent(new CustomEvent('oj-changelog-read', { detail: latest.id }))
    }
  } catch {
    // 忽略静默错误
  }
}

const handleTypeChange = (type: ChangelogType | '') => {
  activeType.value = type
  queryParams.pageNum = 1
  fetchList()
}

const openCreateDialog = () => {
  editingId.value = null
  form.version = ''
  form.title = ''
  form.type = 'FEAT'
  form.content = ''
  form.releaseDate = ''
  dialogVisible.value = true
}

const openEditDialog = (item: Changelog) => {
  editingId.value = item.id
  form.version = item.version
  form.title = item.title
  form.type = item.type
  form.content = item.content
  form.releaseDate = item.releaseDate || ''
  dialogVisible.value = true
}

const handleSubmit = async () => {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    submitting.value = true
    try {
      const payload = {
        version: form.version,
        title: form.title,
        type: form.type,
        content: form.content,
        releaseDate: form.releaseDate || undefined
      }
      if (editingId.value) {
        await updateChangelogApi(editingId.value, payload)
        ElMessage.success('更新日志修改成功！')
      } else {
        await createChangelogApi(payload)
        ElMessage.success('新版本更新日志发布成功！')
      }
      dialogVisible.value = false
      await fetchList()
      await markLatestAsRead()
    } finally {
      submitting.value = false
    }
  })
}

const handleDelete = (item: Changelog) => {
  ElMessageBox.confirm(`确定要删除更新日志「${item.version} - ${item.title}」吗？`, '删除确认', {
    confirmButtonText: '确认删除',
    cancelButtonText: '取消',
    type: 'warning'
  })
    .then(async () => {
      await deleteChangelogApi(item.id)
      ElMessage.success('已删除该条更新日志')
      await fetchList()
    })
    .catch(() => {})
}

const getTypeMeta = (type: string) => {
  switch (type) {
    case 'FEAT':
      return { label: '✨ 新功能', color: '#4f46e5', tagType: 'primary' as const }
    case 'OPTIMIZE':
      return { label: '⚡ 体验优化', color: '#10b981', tagType: 'success' as const }
    case 'FIX':
      return { label: '🐛 问题修复', color: '#f59e0b', tagType: 'warning' as const }
    case 'NOTICE':
      return { label: '📢 系统公告', color: '#6b7280', tagType: 'info' as const }
    default:
      return { label: type, color: '#409eff', tagType: 'info' as const }
  }
}

const formatDateOnly = (dt?: string) => (dt ? dt.slice(0, 10) : '')

const isWithin7Days = (dt?: string) => {
  if (!dt) return false
  const t = new Date(dt.replace(/-/g, '/')).getTime()
  return !Number.isNaN(t) && Date.now() - t <= 7 * 24 * 60 * 60 * 1000
}

/**
 * 轻量安全 Markdown 渲染 (支持无序列表、加粗、行内代码、标题与换行，防 XSS)
 */
const renderMarkdown = (raw: string) => {
  if (!raw) return ''
  const escaped = raw
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')

  const lines = escaped.split('\n')
  const htmlParts: string[] = []
  let inList = false

  for (const line of lines) {
    const formatted = line
      .replace(/\*\*(.+?)\*\*/g, '<strong>$1</strong>')
      .replace(/`([^`]+)`/g, '<code class="inline-code">$1</code>')

    if (/^\s*[-*]\s+/.test(formatted)) {
      if (!inList) {
        htmlParts.push('<ul class="md-list">')
        inList = true
      }
      htmlParts.push(`<li>${formatted.replace(/^\s*[-*]\s+/, '')}</li>`)
    } else {
      if (inList) {
        htmlParts.push('</ul>')
        inList = false
      }
      if (/^###\s+/.test(formatted)) {
        htmlParts.push(`<h4 class="md-h4">${formatted.replace(/^###\s+/, '')}</h4>`)
      } else if (formatted.trim() !== '') {
        htmlParts.push(`<p class="md-p">${formatted}</p>`)
      }
    }
  }
  if (inList) htmlParts.push('</ul>')
  return htmlParts.join('')
}

onMounted(() => {
  fetchList()
  markLatestAsRead()
})
</script>

<style scoped>
.changelog-container {
  max-width: 960px;
  margin: 0 auto;
  padding: 8px 0 32px;
}
.changelog-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 18px;
}
.page-title {
  margin: 0 0 6px;
  font-size: 22px;
  color: #1f2937;
}
.page-subtitle {
  margin: 0;
  font-size: 13.5px;
  color: #6b7280;
}
.filter-tabs {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-bottom: 18px;
}
.filter-pill {
  border: 1px solid #e5e7eb;
  background: #ffffff;
  color: #4b5563;
  padding: 6px 14px;
  border-radius: 20px;
  font-size: 13px;
  cursor: pointer;
  transition: all 0.2s;
}
.filter-pill:hover {
  border-color: #c7d2fe;
  color: #4f46e5;
}
.filter-pill.active {
  background: #4f46e5;
  border-color: #4f46e5;
  color: #ffffff;
  font-weight: 600;
}
.timeline-card {
  border-radius: 10px;
  border: 1px solid #ebeef5;
  padding: 12px 16px;
}
.custom-timeline {
  padding-left: 8px;
  margin-top: 8px;
}
.log-item-card {
  background: #f9fafb;
  border: 1px solid #eef0f5;
  border-radius: 10px;
  padding: 16px 20px;
  transition: box-shadow 0.2s;
}
.log-item-card:hover {
  box-shadow: 0 4px 14px rgba(0, 0, 0, 0.05);
}
.log-item-card.is-latest {
  background: linear-gradient(180deg, #faf8ff 0%, #ffffff 100%);
  border-color: #ddd6fe;
}
.log-card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  flex-wrap: wrap;
  gap: 10px;
  margin-bottom: 12px;
}
.log-title-group {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 10px;
}
.version-badge {
  font-family: 'Fira Code', Consolas, monospace;
  font-size: 13px;
  font-weight: 700;
  color: #ffffff;
  background: linear-gradient(90deg, #4f46e5, #7c3aed);
  padding: 2px 10px;
  border-radius: 6px;
}
.log-title {
  font-size: 16px;
  font-weight: 600;
  color: #111827;
}
.inline-new-tag {
  font-size: 10px;
  font-weight: 700;
  color: #ffffff;
  background: #ef4444;
  padding: 1px 6px;
  border-radius: 10px;
  letter-spacing: 0.5px;
}
.root-actions {
  display: flex;
  gap: 8px;
}
.log-content {
  font-size: 14px;
  line-height: 1.75;
  color: #374151;
}
.log-content :deep(.md-list) {
  margin: 4px 0 8px;
  padding-left: 20px;
}
.log-content :deep(.md-list li) {
  margin-bottom: 4px;
}
.log-content :deep(.md-p) {
  margin: 4px 0;
}
.log-content :deep(.md-h4) {
  margin: 8px 0 4px;
  font-size: 14.5px;
  color: #1f2937;
}
.log-content :deep(.inline-code) {
  background: #eef2ff;
  color: #4338ca;
  padding: 1px 5px;
  border-radius: 4px;
  font-family: 'Fira Code', Consolas, monospace;
  font-size: 12.5px;
}
.log-footer {
  display: flex;
  justify-content: space-between;
  margin-top: 12px;
  padding-top: 10px;
  border-top: 1px dashed #e5e7eb;
  font-size: 12px;
  color: #9ca3af;
}
.pagination-bar {
  display: flex;
  justify-content: center;
  margin-top: 20px;
}
</style>