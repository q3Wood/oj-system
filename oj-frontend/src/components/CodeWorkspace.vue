<template>
  <div class="code-workspace">
    <el-card class="pane-card editor-pane" shadow="never">
      <template #header>
        <div class="editor-header">
          <div class="lang-select">
            <span class="label">语言:</span>
            <el-select v-model="selectedLanguage" size="small" style="width: 120px" @change="handleLanguageChange">
              <el-option label="C++ (g++)" value="cpp" />
              <el-option label="C (gcc)" value="c" />
            </el-select>
            <span class="save-tip">已开启本地自动保存</span>
          </div>
          <div class="action-buttons">
            <el-button size="small" :icon="Refresh" @click="resetCodeTemplate">重置模板</el-button>
            <el-button
              type="success"
              size="small"
              :icon="VideoPlay"
              :loading="submitting"
              @click="handleSubmitCode"
            >
              提交评测
            </el-button>
          </div>
        </div>
      </template>

      <!-- Monaco Editor -->
      <div class="code-editor-wrap">
        <MonacoEditor
          v-model="sourceCode"
          :language="selectedLanguage"
          theme="vs-dark"
        />
      </div>
    </el-card>

    <!-- 右下角极简悬浮评测状态卡片 -->
    <transition name="el-zoom-in-bottom">
      <div v-if="floatingCardVisible" class="floating-judge-card" :class="getFloatingCardClass()">
        <div class="card-left">
          <el-icon v-if="isEvaluating" class="is-loading" :size="20"><Loading /></el-icon>
          <el-icon v-else :size="20">
            <Check v-if="isAccepted(latestSubmission?.verdict)" />
            <Close v-else />
          </el-icon>
          <div class="card-info">
            <div class="card-status-title">
              {{ isEvaluating ? '正在排队评测中...' : latestSubmission?.verdict }}
            </div>
            <div class="card-sub-info" v-if="!isEvaluating && latestSubmission">
              耗时: {{ latestSubmission.maxTimeMs }}ms | 内存: {{ latestSubmission.peakMemoryKb }}KB
            </div>
          </div>
        </div>
        <div class="card-right">
          <el-button
            v-if="!isEvaluating && latestSubmission"
            link
            type="primary"
            size="small"
            @click="emit('view-report', latestSubmission)"
          >
            查看报告
          </el-button>
          <el-button link :icon="Close" @click="floatingCardVisible = false" />
        </div>
      </div>
    </transition>
  </div>
</template>

<script setup lang="ts">
import { ref, watch, onMounted, onBeforeUnmount } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Loading, Check, Close, Refresh, VideoPlay } from '@element-plus/icons-vue'
import MonacoEditor from './MonacoEditor.vue'
import { submitCodeApi, getSubmissionDetailApi } from '../api/submission'
import { useUserStore } from '../stores/user'
import type { Submission } from '../types'

const props = defineProps<{
  problemId: number
  contestId?: number // 可选：如果传入 contestId，则作为比赛代码提交
}>()

const emit = defineEmits<{
  (e: 'submitted', submission: Submission): void
  (e: 'judge-finished', submission: Submission): void
  (e: 'view-report', submission: Submission): void
}>()

const router = useRouter()
const userStore = useUserStore()

const CODE_TEMPLATES: Record<string, string> = {
  cpp: `#include <iostream>\nusing namespace std;\n\nint main() {\n    ios::sync_with_stdio(false);\n    cin.tie(nullptr);\n    \n    return 0;\n}\n`,
  c: `#include <stdio.h>\n\nint main() {\n    \n    return 0;\n}\n`
}

const selectedLanguage = ref('cpp')
const sourceCode = ref(CODE_TEMPLATES.cpp)
const submitting = ref(false)
const isEvaluating = ref(false)
const floatingCardVisible = ref(false)
const latestSubmission = ref<Submission | null>(null)
let pollTimer: ReturnType<typeof setInterval> | null = null

// 生成当前用户、当前题目、当前语言的专属本地缓存 Key
const getStorageKey = () => {
  const uid = userStore.userInfo?.id || 'guest'
  return `oj:code:${uid}:${props.problemId}:${selectedLanguage.value}`
}

// 从 localStorage 加载草稿代码，若无则使用默认模板
const loadSavedCode = () => {
  const saved = localStorage.getItem(getStorageKey())
  sourceCode.value = saved !== null ? saved : (CODE_TEMPLATES[selectedLanguage.value] || '')
}

// 实时自动保存草稿到 localStorage
watch(sourceCode, (newCode) => {
  if (props.problemId) {
    localStorage.setItem(getStorageKey(), newCode)
  }
})

// 切换题目 ID 时重新加载对应题目的代码草稿
watch(() => props.problemId, () => {
  loadSavedCode()
})

const handleLanguageChange = () => {
  loadSavedCode()
}

const resetCodeTemplate = async () => {
  try {
    await ElMessageBox.confirm('确定要清空当前代码并恢复初始模板吗？', '重置确认', {
      confirmButtonText: '确定重置',
      cancelButtonText: '取消',
      type: 'warning'
    })
    sourceCode.value = CODE_TEMPLATES[selectedLanguage.value] || ''
    localStorage.removeItem(getStorageKey())
    ElMessage.success('已恢复默认代码模板')
  } catch {}
}

const isAccepted = (verdict?: string) => {
  return verdict === 'Accepted' || verdict === 'AC'
}

const stopPolling = () => {
  if (pollTimer) {
    clearInterval(pollTimer)
    pollTimer = null
  }
}

const handleSubmitCode = async () => {
  if (!userStore.token) {
    ElMessage.warning('请先登录再提交代码！')
    router.push('/login')
    return
  }
  if (!sourceCode.value.trim()) {
    ElMessage.warning('代码不能为空！')
    return
  }

  stopPolling()
  submitting.value = true
  isEvaluating.value = true
  floatingCardVisible.value = true

  try {
    const initialSubmission = await submitCodeApi({
      problemId: props.problemId,
      contestId: props.contestId,
      language: selectedLanguage.value,
      code: sourceCode.value
    } as any)

    latestSubmission.value = initialSubmission
    emit('submitted', initialSubmission)

    // 开启轮询
    pollTimer = setInterval(async () => {
      try {
        const latest = await getSubmissionDetailApi(initialSubmission.id)
        latestSubmission.value = latest

        if (latest.verdict !== 'PENDING' && latest.verdict !== 'RUNNING') {
          stopPolling()
          isEvaluating.value = false
          submitting.value = false

          if (isAccepted(latest.verdict)) {
            ElMessage.success('恭喜通过全部测试点！')
          } else {
            ElMessage.error(`评测未通过：${latest.verdict}`)
          }
          emit('judge-finished', latest)
        }
      } catch {
        stopPolling()
        isEvaluating.value = false
        submitting.value = false
      }
    }, 800)
  } catch {
    submitting.value = false
    isEvaluating.value = false
    floatingCardVisible.value = false
  }
}

const getFloatingCardClass = () => {
  if (isEvaluating.value) return 'card-pending'
  if (isAccepted(latestSubmission.value?.verdict)) return 'card-accepted'
  return 'card-failed'
}

onMounted(() => {
  loadSavedCode()
})

// 组件销毁时务必清除定时器，防止内存泄漏与幽灵请求
onBeforeUnmount(() => {
  stopPolling()
})
</script>

<style scoped>
.code-workspace {
  height: 100%;
}
.pane-card {
  height: 100%;
  display: flex;
  flex-direction: column;
  border-radius: 8px;
}
:deep(.el-card__body) {
  flex: 1;
  overflow: hidden;
  padding: 0;
}
.editor-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.lang-select {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 14px;
  color: #606266;
}
.save-tip {
  font-size: 12px;
  color: #909399;
  margin-left: 6px;
}
.code-editor-wrap {
  height: 100%;
  background-color: #1e1e1e;
}
.floating-judge-card {
  position: fixed;
  right: 32px;
  bottom: 32px;
  min-width: 300px;
  max-width: 380px;
  padding: 14px 18px;
  border-radius: 8px;
  background-color: #fff;
  box-shadow: 0 6px 20px rgba(0, 0, 0, 0.15);
  display: flex;
  justify-content: space-between;
  align-items: center;
  z-index: 2000;
  transition: all 0.3s ease;
}
.card-left {
  display: flex;
  align-items: center;
  gap: 12px;
}
.card-info {
  display: flex;
  flex-direction: column;
}
.card-status-title {
  font-size: 15px;
  font-weight: bold;
}
.card-sub-info {
  font-size: 12px;
  opacity: 0.85;
  margin-top: 2px;
}
.card-right {
  display: flex;
  align-items: center;
  gap: 8px;
}
.card-pending { background-color: #ecf5ff; border: 1px solid #d9ecff; color: #409eff; }
.card-accepted { background-color: #f0f9eb; border: 1px solid #e1f3d8; color: #67c23a; }
.card-failed { background-color: #fef0f0; border: 1px solid #fde2e2; color: #f56c6c; }
</style>