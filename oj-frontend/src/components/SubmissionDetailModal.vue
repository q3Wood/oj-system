<template>
  <el-dialog
    v-model="visible"
    title="评测详细报告"
    width="780px"
    destroy-on-close
    @closed="handleClosed"
  >
    <div v-loading="loading" class="report-modal-body">
      <template v-if="submission">
        <!-- 1. 顶部总评横幅 -->
        <div class="result-banner" :class="getVerdictBannerClass(submission.verdict)">
          <div class="banner-title">{{ submission.verdict }}</div>
          <div class="banner-meta">
            <span>题目: <b>#{{ submission.problemId }} {{ submission.problemTitle || '' }}</b></span>
            <span>作者: <b>{{ submission.username || `#${submission.userId}` }}</b></span>
            <span>耗时: <b>{{ submission.maxTimeMs }} ms</b></span>
            <span>内存: <b>{{ submission.peakMemoryKb }} KB</b></span>
          </div>
        </div>

        <!-- 2. 提交源代码区域 -->
        <div v-if="submission.code" class="code-section">
          <div class="code-header">
            <h4>提交源码 ({{ submission.language }}):</h4>
            <el-button size="small" link type="primary" @click="copyCode">复制源码</el-button>
          </div>
          <pre class="code-preview"><code>{{ submission.code }}</code></pre>
        </div>

        <!-- 3. 错误诊断控制台 (编译错误 / 系统错误 时展示) -->
        <div
          v-if="submission.verdict === 'Compile Error' || submission.verdict === 'System Error'"
          class="error-log-section"
        >
          <h4 style="color: #f56c6c;">报错诊断日志:</h4>
          <pre class="error-terminal"><code>{{ getErrorMessage(submission) }}</code></pre>
        </div>

        <!-- 4. 测试点明细表格 -->
        <div v-else-if="parsedDetails.length > 0" class="testcases-section">
          <h4>测试点明细:</h4>
          <el-table :data="parsedDetails" border size="small" style="width: 100%">
            <el-table-column label="#" width="60" align="center">
              <template #default="{ row, $index }">{{ row.id ?? row.caseId ?? ($index + 1) }}</template>
            </el-table-column>
            <el-table-column prop="verdict" label="状态" width="160">
              <template #default="{ row }">
                <el-tag :type="getVerdictTagType(row.verdict)" size="small">
                  {{ row.verdict }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="耗时" width="90" align="center">
              <template #default="{ row }">{{ row.time_ms ?? row.timeMs ?? 0 }} ms</template>
            </el-table-column>
            <el-table-column label="内存" width="100" align="center">
              <template #default="{ row }">{{ row.memory_kb ?? row.memoryKb ?? 0 }} KB</template>
            </el-table-column>
            <el-table-column prop="message" label="说明" min-width="160" show-overflow-tooltip />
          </el-table>
        </div>
      </template>
    </div>
    <template #footer>
      <el-button type="primary" @click="visible = false">关 闭</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { getSubmissionDetailApi } from '../api/submission'
import type { Submission, CaseDetail } from '../types'

interface Props {
  modelValue: boolean
  submissionId?: number | null
  data?: Submission | null
}

const props = defineProps<Props>()
const emit = defineEmits<{
  (e: 'update:modelValue', value: boolean): void
}>()

const visible = computed({
  get: () => props.modelValue,
  set: (val) => emit('update:modelValue', val)
})

const loading = ref(false)
const submission = ref<Submission | null>(null)

const parsedDetails = computed<CaseDetail[]>(() => {
  if (!submission.value?.judgeDetails) return []
  const d = submission.value.judgeDetails
  if (typeof d === 'string') {
    try {
      const p = JSON.parse(d)
      return Array.isArray(p) ? p : []
    } catch {
      return []
    }
  }
  return Array.isArray(d) ? d : []
})

watch(
  () => props.modelValue,
  async (isOpen) => {
    if (isOpen) {
      if (props.submissionId) {
        loading.value = true
        try {
          submission.value = await getSubmissionDetailApi(props.submissionId)
        } catch {
          // 若无权查看 (如赛中偷看他人代码 403)，自动关闭弹窗避免留白
          visible.value = false
        } finally {
          loading.value = false
        }
      } else if (props.data) {
        submission.value = props.data
      }
    }
  }
)

const copyCode = async () => {
  if (!submission.value?.code) return
  try {
    await navigator.clipboard.writeText(submission.value.code)
    ElMessage.success('代码已复制到剪贴板')
  } catch {
    ElMessage.warning('复制失败，请手动选择复制')
  }
}

const handleClosed = () => {
  submission.value = null
}

const getVerdictBannerClass = (v: string) => {
  if (v === 'Accepted' || v === 'AC') return 'banner-ac'
  if (v === 'Compile Error') return 'banner-ce'
  return 'banner-wa'
}

const getVerdictTagType = (v: string) => {
  if (v === 'Accepted' || v === 'AC') return 'success'
  if (v === 'Time Limit Exceeded' || v === 'Memory Limit Exceeded') return 'warning'
  return 'danger'
}

const getErrorMessage = (sub: Submission | null) => {
  if (!sub || !sub.judgeDetails) return '无报错日志'
  const raw = sub.judgeDetails
  if (typeof raw === 'string') {
    try {
      const p = JSON.parse(raw)
      return typeof p === 'string' ? p : JSON.stringify(p, null, 2)
    } catch {
      return raw
    }
  }
  return JSON.stringify(raw, null, 2)
}
</script>

<style scoped>
.report-modal-body {
  max-height: 70vh;
  overflow-y: auto;
  min-height: 120px;
}
.result-banner {
  padding: 14px 20px;
  border-radius: 8px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  flex-wrap: wrap;
  gap: 10px;
  margin-bottom: 16px;
}
.banner-title {
  font-size: 20px;
  font-weight: bold;
}
.banner-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 16px;
  font-size: 13px;
}
.banner-ac { background-color: #f0f9eb; color: #67c23a; border: 1px solid #e1f3d8; }
.banner-wa { background-color: #fef0f0; color: #f56c6c; border: 1px solid #fde2e2; }
.banner-ce { background-color: #f4f4f5; color: #909399; border: 1px solid #e9e9eb; }

.code-section {
  margin-bottom: 20px;
}
.code-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
}
.code-header h4, .testcases-section h4 {
  margin: 0;
  color: #303133;
}
.testcases-section h4 {
  margin-bottom: 8px;
}
.code-preview {
  background-color: #1e1e1e;
  color: #d4d4d4;
  padding: 16px;
  border-radius: 6px;
  overflow-x: auto;
  font-family: 'Fira Code', Consolas, Monaco, monospace;
  font-size: 13px;
  line-height: 1.5;
  margin: 0;
}
.error-terminal {
  background-color: #1e1e1e;
  color: #f56c6c;
  padding: 14px;
  border-radius: 6px;
  font-family: 'Fira Code', Consolas, Monaco, monospace;
  font-size: 13px;
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-all;
  max-height: 250px;
  overflow-y: auto;
  border: 1px solid #333;
}
</style>