<template>
  <div class="problem-solution-wrap">
    <!-- 1. 题解列表视图 -->
    <div v-if="!currentSolution" class="solution-list-view">
      <div class="solution-header-bar">
        <span class="sub-title">精选题解 ({{ solutionTotal }})</span>
        <el-button type="primary" size="small" :icon="Edit" @click="openCreateSolutionDialog">
          写题解
        </el-button>
      </div>

      <div v-loading="solutionLoading" class="solution-items">
        <template v-if="solutionList.length > 0">
          <div
            v-for="item in solutionList"
            :key="item.id"
            class="solution-card-item"
            @click="viewSolutionDetail(item.id)"
          >
            <div class="solution-item-top">
              <div class="solution-item-title">{{ item.title }}</div>
              <!-- 点赞按钮 (阻止冒泡避免触发点开详情) -->
              <el-button
                size="small"
                :type="item.hasLiked ? 'primary' : 'default'"
                :plain="!item.hasLiked"
                round
                @click.stop="handleToggleLike(item)"
              >
                👍 {{ item.likes || 0 }}
              </el-button>
            </div>
            <div class="solution-item-meta">
              <span class="author">
                <el-icon><UserFilled /></el-icon> {{ item.authorName }}
              </span>
              <span class="time">{{ item.createdAt }}</span>
            </div>
          </div>

          <!-- 题解分页 -->
          <div v-if="solutionTotal > pageSize" class="pagination-wrap">
            <el-pagination
              v-model:current-page="pageNum"
              :page-size="pageSize"
              layout="prev, pager, next"
              small
              background
              :total="solutionTotal"
              @current-change="fetchSolutions"
            />
          </div>
        </template>
        <el-empty v-else description="本题暂无题解，快来抢先发布第一篇吧！" />
      </div>
    </div>

    <!-- 2. 题解正文阅读视图 -->
    <div v-else class="solution-detail-view" v-loading="detailLoading">
      <div class="solution-read-header">
        <div class="read-top-bar">
          <el-link :underline="false" :icon="Back" @click="currentSolution = null">
            返回题解列表
          </el-link>
          <div class="action-right">
            <el-button
              size="small"
              :type="currentSolution.hasLiked ? 'primary' : 'default'"
              @click="handleToggleLike(currentSolution)"
            >
              👍 {{ currentSolution.hasLiked ? '已赞' : '点赞' }} ({{ currentSolution.likes || 0 }})
            </el-button>
            <!-- 作者本人或管理员 (ADMIN / ROOT) 可编辑删除 -->
            <template v-if="canManage(currentSolution)">
              <el-button size="small" :icon="Edit" @click="handleEditSolution">编辑</el-button>
              <el-button size="small" type="danger" :icon="Delete" @click="handleDeleteSolution">删除</el-button>
            </template>
          </div>
        </div>
        <h3 class="read-title">{{ currentSolution.title }}</h3>
        <div class="read-meta">
          <span>作者: <b>{{ currentSolution.authorName }}</b></span>
          <span>发布时间: {{ currentSolution.createdAt }}</span>
        </div>
      </div>

      <!-- 复用通用 Markdown + KaTeX 渲染组件 -->
      <ProblemDescription :content="currentSolution.content" />
    </div>

    <!-- 3. 发布 / 编辑题解弹窗 -->
    <el-dialog
      v-model="createSolutionDialogVisible"
      :title="isEditing ? '编辑修改题解' : '发布新题解'"
      width="700px"
      destroy-on-close
    >
      <el-form ref="solutionFormRef" :model="solutionForm" :rules="solutionRules" label-position="top">
        <el-form-item label="题解标题" prop="title">
          <el-input
            v-model.trim="solutionForm.title"
            maxlength="64"
            show-word-limit
            placeholder="请输入标题 (4 ~ 64 个字符，例如: 贪心 + 双指针清晰思路分析)"
          />
        </el-form-item>
        <el-form-item label="题解正文 (支持 Markdown 与 LaTeX 数学公式)" prop="content">
          <el-input
            v-model="solutionForm.content"
            type="textarea"
            :rows="12"
            placeholder="支持输入 Markdown 标题、$O(N\log N)$ 复杂度公式与 ```cpp 代码块..."
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createSolutionDialogVisible = false">取 消</el-button>
        <el-button type="primary" :loading="publishing" @click="submitNewSolution">
          {{ isEditing ? '保存修改' : '立即发布' }}
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox, type FormInstance } from 'element-plus'
import { UserFilled, Edit, Back, Delete } from '@element-plus/icons-vue'
import ProblemDescription from './ProblemDescription.vue'
import {
  getSolutionListApi,
  getSolutionDetailApi,
  createSolutionApi,
  updateSolutionApi,
  deleteSolutionApi,
  toggleLikeSolutionApi
} from '../api/solution'
import { useUserStore } from '../stores/user'
import type { Solution } from '../types'

const props = defineProps<{
  problemId: number
}>()

const router = useRouter()
const userStore = useUserStore()

const isEditing = ref(false)
const solutionList = ref<Solution[]>([])
const solutionTotal = ref(0)
const pageNum = ref(1)
const pageSize = ref(10)
const solutionLoading = ref(false)

const currentSolution = ref<Solution | null>(null)
const detailLoading = ref(false)

const createSolutionDialogVisible = ref(false)
const publishing = ref(false)
const solutionFormRef = ref<FormInstance>()

const defaultTemplate = `### 解题思路\n请在此处输入思路分析...\n\n### 复杂度分析\n- 时间复杂度：$O(N)$\n- 空间复杂度：$O(1)$\n\n### 核心代码\n\`\`\`cpp\n// 在此粘贴你的 AC 代码\n\`\`\`\n`

const solutionForm = reactive({
  title: '',
  content: defaultTemplate
})

const solutionRules = {
  title: [
    { required: true, message: '请输入题解标题', trigger: 'blur' },
    { min: 4, max: 64, message: '标题长度需在 4 到 64 个字符之间', trigger: 'blur' }
  ],
  content: [{ required: true, message: '请输入题解正文', trigger: 'blur' }]
}

const fetchSolutions = async () => {
  if (!props.problemId) return
  solutionLoading.value = true
  try {
    const data = await getSolutionListApi({
      problemId: props.problemId,
      pageNum: pageNum.value,
      pageSize: pageSize.value
    })
    solutionList.value = data.items
    solutionTotal.value = data.total
  } catch {
  } finally {
    solutionLoading.value = false
  }
}

const viewSolutionDetail = async (id: number) => {
  detailLoading.value = true
  try {
    currentSolution.value = await getSolutionDetailApi(id)
  } catch {
  } finally {
    detailLoading.value = false
  }
}

// 点赞 / 取消点赞
const handleToggleLike = async (target: Solution) => {
  if (!userStore.token) {
    ElMessage.warning('请先登录后再点赞！')
    router.push('/login')
    return
  }
  try {
    const liked = await toggleLikeSolutionApi(target.id)
    target.hasLiked = liked
    target.likes = Math.max(0, (target.likes || 0) + (liked ? 1 : -1))
    // 同步更新列表中的对应项
    const inList = solutionList.value.find(item => item.id === target.id)
    if (inList && inList !== target) {
      inList.hasLiked = target.hasLiked
      inList.likes = target.likes
    }
  } catch {}
}

const openCreateSolutionDialog = () => {
  if (!userStore.token) {
    ElMessage.warning('请先登录再发布题解！')
    router.push('/login')
    return
  }
  isEditing.value = false
  solutionForm.title = ''
  solutionForm.content = defaultTemplate
  createSolutionDialogVisible.value = true
}

const handleEditSolution = () => {
  if (!currentSolution.value) return
  isEditing.value = true
  solutionForm.title = currentSolution.value.title
  solutionForm.content = currentSolution.value.content || ''
  createSolutionDialogVisible.value = true
}

const handleDeleteSolution = async () => {
  if (!currentSolution.value) return
  try {
    await ElMessageBox.confirm('确定要删除这篇题解吗？删除后将无法恢复。', '删除确认', {
      confirmButtonText: '确定删除',
      cancelButtonText: '取消',
      type: 'warning'
    })
    await deleteSolutionApi(currentSolution.value.id)
    ElMessage.success('题解已成功删除！')
    currentSolution.value = null
    fetchSolutions()
  } catch {}
}

const submitNewSolution = async () => {
  if (!solutionFormRef.value || publishing.value) return
  try {
    await solutionFormRef.value.validate()
  } catch {
    return
  }

  publishing.value = true
  try {
    if (isEditing.value && currentSolution.value) {
      const updated = await updateSolutionApi(currentSolution.value.id, {
        title: solutionForm.title,
        content: solutionForm.content
      })
      ElMessage.success('题解修改成功！')
      currentSolution.value.title = updated.title
      currentSolution.value.content = updated.content
    } else {
      await createSolutionApi({
        problemId: props.problemId,
        title: solutionForm.title,
        content: solutionForm.content
      })
      ElMessage.success('题解发布成功！')
      pageNum.value = 1
    }
    createSolutionDialogVisible.value = false
    fetchSolutions()
  } catch {
  } finally {
    publishing.value = false
  }
}

// 修复：同时允许作者本人、ADMIN 和 ROOT 管理题解
const canManage = (solution: Solution | null) => {
  if (!solution || !userStore.userInfo) return false
  return (
    userStore.userInfo.id === solution.userId ||
    userStore.userInfo.role === 'ADMIN' ||
    userStore.userInfo.role === 'ROOT'
  )
}

watch(
  () => props.problemId,
  () => {
    currentSolution.value = null
    pageNum.value = 1
    fetchSolutions()
  }
)

onMounted(() => {
  fetchSolutions()
})
</script>

<style scoped>
.problem-solution-wrap {
  width: 100%;
}
.solution-header-bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
  padding-bottom: 8px;
  border-bottom: 1px solid #f0f2f5;
}
.sub-title {
  font-weight: bold;
  color: #303133;
}
.solution-card-item {
  padding: 14px 16px;
  border-radius: 8px;
  background-color: #fafafa;
  margin-bottom: 12px;
  cursor: pointer;
  border: 1px solid #ebeef5;
  transition: all 0.2s;
}
.solution-card-item:hover {
  background-color: #f0f7ff;
  border-color: #c6e2ff;
}
.solution-item-top {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
}
.solution-item-title {
  font-size: 15px;
  font-weight: 500;
  color: #303133;
}
.solution-item-meta {
  display: flex;
  justify-content: space-between;
  font-size: 12px;
  color: #909399;
}
.author {
  display: inline-flex;
  align-items: center;
  gap: 4px;
}
.pagination-wrap {
  display: flex;
  justify-content: center;
  margin-top: 16px;
}
.solution-read-header {
  margin-bottom: 16px;
  padding-bottom: 12px;
  border-bottom: 1px solid #eee;
}
.read-top-bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}
.action-right {
  display: flex;
  align-items: center;
  gap: 8px;
}
.read-title {
  font-size: 18px;
  margin: 12px 0 6px 0;
  color: #303133;
}
.read-meta {
  display: flex;
  gap: 16px;
  font-size: 12px;
  color: #909399;
}
</style>