<template>
  <el-dialog
    v-model="visible"
    title="评测详细报告"
    width="820px"
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
            <div class="code-actions">
              <!-- 若尚未生成 AI 报告，且当前登录用户是本人或管理员，也在右上角提供一个快捷入口 -->
              <el-button
                v-if="!submission.aiReport && canTriggerAi"
                size="small"
                type="primary"
                plain
                :loading="aiLoading"
                @click="handleAiAnalyze"
              >
                ✨ {{ aiLoading ? 'AI 思考中...' : 'AI 代码诊断' }}
              </el-button>
              <el-button size="small" link type="primary" @click="copyCode">复制源码</el-button>
            </div>
          </div>
          <pre class="code-preview"><code>{{ submission.code }}</code></pre>
        </div>

        <!-- 3. AI 智能诊断板块 (已生成则直接展示报告；未生成且是本人/管理员则展示触发引导卡片) -->
        <div v-if="submission.aiReport" class="ai-report-section">
          <div class="ai-header">
            <span class="ai-badge">✨ AI 代码导师诊断报告</span>
            <span class="ai-congratulation">{{ submission.aiReport.congratulation }}</span>
          </div>

          <div class="ai-cards-grid">
            <!-- 板块 A：算法与方法分析 -->
            <div class="ai-sub-card">
              <div class="sub-card-title">🧭 算法与方法分析</div>
              <div class="ai-field">
                <span class="field-label">当前方法：</span>
                <span class="field-val">{{ submission.aiReport.method?.currentMethod }}</span>
              </div>
              <div class="ai-field">
                <span class="field-label">推荐方法：</span>
                <span class="field-val highlight-text">{{ submission.aiReport.method?.suggestedMethod }}</span>
              </div>
              <div class="ai-field">
                <span class="field-label">核心考点：</span>
                <el-tag size="small" effect="plain" type="primary">{{ submission.aiReport.method?.coreConcept }}</el-tag>
              </div>
              <div v-if="submission.aiReport.method?.thinkingPrompt" class="thinking-box">
                <div class="thinking-title">💡 启发式思考：</div>
                <div class="thinking-content">{{ submission.aiReport.method.thinkingPrompt }}</div>
              </div>
            </div>

            <!-- 板块 B：运行效率与复杂度 -->
            <div class="ai-sub-card">
              <div class="sub-card-title">⚡ 运行效率与复杂度</div>
              <div class="complexity-row">
                <div class="comp-item">
                  <div class="comp-label">当前时间复杂度</div>
                  <div class="comp-val">{{ submission.aiReport.efficiency?.currentTimeComplexity }}</div>
                </div>
                <div class="comp-arrow">➔</div>
                <div class="comp-item">
                  <div class="comp-label">目标最优复杂度</div>
                  <div class="comp-val best">{{ submission.aiReport.efficiency?.suggestedTimeComplexity }}</div>
                </div>
              </div>
              <div class="ai-field" style="margin-top: 10px;">
                <span class="field-label">优化建议：</span>
                <span class="field-val">{{ submission.aiReport.efficiency?.optimizationAdvice }}</span>
              </div>
            </div>

            <!-- 板块 C：代码风格与规范 -->
            <div class="ai-sub-card full-width">
              <div class="sub-card-title">🎨 代码风格与工程规范</div>
              <div class="style-tags">
                <span>可读性评级：<el-tag size="small" type="success">{{ submission.aiReport.style?.readability }}</el-tag></span>
                <span>结构化评级：<el-tag size="small" type="info">{{ submission.aiReport.style?.structure }}</el-tag></span>
              </div>
              <div class="ai-field" style="margin-top: 8px;">
                <span class="field-label">改进建议：</span>
                <span class="field-val">{{ submission.aiReport.style?.advice }}</span>
              </div>
            </div>
          </div>
        </div>

        <!-- 若尚未生成报告，且当前用户有权触发 (本人或管理员)，显示引导横幅 -->
        <div v-else-if="canTriggerAi" class="ai-trigger-banner">
          <div class="trigger-left">
            <span class="trigger-icon">🤖</span>
            <div>
              <div class="trigger-title">需要 AI 导师帮你分析这份代码吗？</div>
              <div class="trigger-desc">获取专属的算法思路点拨、时空复杂度评估与代码规范建议（每次提交仅限生成一次）</div>
            </div>
          </div>
          <el-button
            type="primary"
            :loading="aiLoading"
            round
            @click="handleAiAnalyze"
          >
            {{ aiLoading ? 'AI 正在全力诊断中...' : '✨ 立即生成 AI 诊断' }}
          </el-button>
        </div>

        <!-- 4. 错误诊断控制台 (编译错误 / 系统错误 时展示) -->
        <div
          v-if="submission.verdict === 'Compile Error' || submission.verdict === 'System Error'"
          class="error-log-section"
        >
          <h4 style="color: #f56c6c;">报错诊断日志:</h4>
          <pre class="error-terminal"><code>{{ getErrorMessage(submission) }}</code></pre>
        </div>

        <!-- 5. 测试点明细表格 -->
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
import { getSubmissionDetailApi, analyzeSubmissionApi } from '../api/submission'
import { useUserStore } from '../stores/user'
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

const userStore = useUserStore()

const visible = computed({
  get: () => props.modelValue,
  set: (val) => emit('update:modelValue', val)
})

const loading = ref(false)
const aiLoading = ref(false)
const submission = ref<Submission | null>(null)

/**
 * 判断当前登录用户是否有权点击“生成 AI 诊断报告”按钮：
 * 1. 评测状态必须已处于终态 (非 PENDING / RUNNING)
 * 2. 身份必须是：该次提交的作者本人 (userId 相等) 或 管理员 (ADMIN / ROOT)
 */
const canTriggerAi = computed(() => {
  if (!submission.value) return false
  const v = submission.value.verdict
  if (!v || v === 'PENDING' || v === 'RUNNING') return false

  // 兼容 userStore 中 userInfo 的常见结构
  const currentUser = (userStore as any).userInfo || (userStore as any).user
  const currentUserId = currentUser?.id ?? (userStore as any).userId
  const currentRole = currentUser?.role ?? (userStore as any).role

  if (!currentUserId) return false
  const isSelf = Number(currentUserId) === Number(submission.value.userId)
  const isAdmin = currentRole === 'ADMIN' || currentRole === 'ROOT'
  return isSelf || isAdmin
})

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
      const targetId = props.submissionId || props.data?.id
      if (targetId) {
        loading.value = true
        try {
          submission.value = await getSubmissionDetailApi(targetId)
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

// 触发 AI 代码诊断
const handleAiAnalyze = async () => {
  if (!submission.value?.id || aiLoading.value) return
  aiLoading.value = true
  try {
    const report = await analyzeSubmissionApi(submission.value.id)
    submission.value.aiReport = report
    ElMessage.success('AI 代码诊断报告生成完毕！')
  } catch {
    // 错误提示已由 request.ts 响应拦截器统一弹出 ElMessage.error
  } finally {
    aiLoading.value = false
  }
}

const copyCode = async () => {
  const text = submission.value?.code
  if (!text) return

  try {
    if (navigator.clipboard && window.isSecureContext) {
      await navigator.clipboard.writeText(text)
    } else {
      const textArea = document.createElement('textarea')
      textArea.value = text
      textArea.setAttribute('readonly', '')
      textArea.style.position = 'fixed'
      textArea.style.top = '-9999px'
      textArea.style.left = '-9999px'
      textArea.style.opacity = '0'

      document.body.appendChild(textArea)
      textArea.focus()
      textArea.select()
      textArea.setSelectionRange(0, textArea.value.length)

      const success = document.execCommand('copy')
      document.body.removeChild(textArea)

      if (!success) {
        throw new Error('execCommand 返回 false')
      }
    }
    ElMessage.success('代码已复制到剪贴板')
  } catch (err) {
    console.error('复制失败的具体原因:', err)
    ElMessage.warning('复制失败，请手动选择复制')
  }
}

const handleClosed = () => {
  submission.value = null
  aiLoading.value = false
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
  max-height: 72vh;
  overflow-y: auto;
  min-height: 120px;
  padding-right: 4px;
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
  margin-bottom: 18px;
}
.code-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
}
.code-actions {
  display: flex;
  align-items: center;
  gap: 12px;
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
  max-height: 280px;
}

/* ================= AI 触发引导条与诊断报告样式 ================= */
.ai-trigger-banner {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 14px 18px;
  margin-bottom: 18px;
  border-radius: 8px;
  background: linear-gradient(135deg, #f5f3ff 0%, #ede9fe 100%);
  border: 1px solid #ddd6fe;
}
.trigger-left {
  display: flex;
  align-items: center;
  gap: 12px;
}
.trigger-icon {
  font-size: 26px;
}
.trigger-title {
  font-weight: 600;
  color: #4c1d95;
  font-size: 14px;
}
.trigger-desc {
  font-size: 12px;
  color: #6d28d9;
  margin-top: 2px;
}

.ai-report-section {
  margin-bottom: 20px;
  padding: 16px;
  border-radius: 10px;
  background: linear-gradient(180deg, #faf8ff 0%, #ffffff 100%);
  border: 1px solid #e4ddfe;
  box-shadow: 0 2px 12px rgba(109, 40, 217, 0.05);
}
.ai-header {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 10px;
  margin-bottom: 14px;
  padding-bottom: 10px;
  border-bottom: 1px dashed #e5e7eb;
}
.ai-badge {
  background: linear-gradient(90deg, #7c3aed, #4f46e5);
  color: #fff;
  font-size: 12px;
  font-weight: 600;
  padding: 4px 10px;
  border-radius: 20px;
}
.ai-congratulation {
  font-size: 14px;
  font-weight: 600;
  color: #1f2937;
}
.ai-cards-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
}
.ai-sub-card {
  background: #ffffff;
  border: 1px solid #f0eefc;
  border-radius: 8px;
  padding: 12px 14px;
}
.ai-sub-card.full-width {
  grid-column: 1 / -1;
}
.sub-card-title {
  font-size: 13px;
  font-weight: 700;
  color: #4338ca;
  margin-bottom: 10px;
}
.ai-field {
  font-size: 13px;
  line-height: 1.6;
  margin-bottom: 6px;
  color: #374151;
}
.field-label {
  color: #6b7280;
  font-weight: 500;
}
.highlight-text {
  color: #4f46e5;
  font-weight: 600;
}
.thinking-box {
  margin-top: 10px;
  padding: 10px 12px;
  background-color: #fffbeb;
  border-left: 3px solid #f59e0b;
  border-radius: 4px;
}
.thinking-title {
  font-size: 12px;
  font-weight: 700;
  color: #b45309;
  margin-bottom: 4px;
}
.thinking-content {
  font-size: 12.5px;
  color: #92400e;
  line-height: 1.5;
}
.complexity-row {
  display: flex;
  align-items: center;
  justify-content: space-around;
  background: #f9fafb;
  padding: 10px;
  border-radius: 6px;
}
.comp-item {
  text-align: center;
}
.comp-label {
  font-size: 11px;
  color: #6b7280;
}
.comp-val {
  font-family: 'Fira Code', Consolas, monospace;
  font-size: 14px;
  font-weight: 700;
  color: #374151;
  margin-top: 2px;
}
.comp-val.best {
  color: #10b981;
}
.comp-arrow {
  color: #9ca3af;
  font-weight: bold;
}
.style-tags {
  display: flex;
  gap: 20px;
  font-size: 13px;
  color: #4b5563;
}

.error-log-section {
  margin-bottom: 18px;
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