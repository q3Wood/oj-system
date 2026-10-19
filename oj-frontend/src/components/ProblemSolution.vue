<template>
  <div class="problem-solution-wrap">
    <!-- 1. 题解列表视图 (未选中某篇题解时展示) -->
    <div v-if="!currentSolution" class="solution-list-view">
      <div class="solution-header-bar">
        <span class="sub-title">精选题解 ({{ solutionTotal }})</span>
        <el-button type="primary" size="small" icon="Edit" @click="openCreateSolutionDialog">
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
            <div class="solution-item-title">{{ item.title }}</div>
            <div class="solution-item-meta">
              <span class="author">
                <el-icon><UserFilled /></el-icon> {{ item.authorName }}
              </span>
              <span class="time">{{ item.createdAt }}</span>
            </div>
          </div>
        </template>
        <el-empty v-else description="本题暂无题解，快来抢先发布第一篇吧！" />
      </div>
    </div>

    <!-- 2. 题解正文阅读视图 (选中某篇题解时沉浸阅读) -->
    <div v-else class="solution-detail-view" v-loading="detailLoading">
<div class="solution-read-header">
     <div class="read-top-bar">
       <el-link :underline="false" icon="Back" @click="currentSolution = null">
         返回题解列表
       </el-link>
       <!-- 仅作者本人或 ADMIN 允许编辑与删除 -->
       <div v-if="canManage(currentSolution)" class="manage-btn-group">
         <el-button size="small" icon="Edit" @click="handleEditSolution">编辑</el-button>
         <el-button size="small" type="danger" icon="Delete" @click="handleDeleteSolution">删除</el-button>
       </div>
     </div>
     <h3 class="read-title">{{ currentSolution.title }}</h3>
     <div class="read-meta">
       <span>作者: <b>{{ currentSolution.authorName }}</b></span>
       <span>发布时间: {{ currentSolution.createdAt }}</span>
     </div>
   </div>
      <!-- 渲染题解的 Markdown 正文与代码块 -->
      <div class="markdown-body solution-markdown" v-html="renderedSolutionContent"></div>
    </div>

    <!-- 3. 发布题解弹窗 -->
    <el-dialog v-model="createSolutionDialogVisible" title="发布新题解" width="700px" destroy-on-close>
      <el-form ref="solutionFormRef" :model="solutionForm" :rules="solutionRules" label-position="top">
        <el-form-item label="题解标题" prop="title">
          <el-input v-model="solutionForm.title" placeholder="请输入标题 (例如: 贪心 + 双指针清晰思路分析)" />
        </el-form-item>
        <el-form-item label="题解正文 (Markdown 语法)" prop="content">
          <el-input 
            v-model="solutionForm.content" 
            type="textarea" 
            :rows="12" 
            placeholder="支持输入 Markdown 标题、复杂度分析与 ```cpp 代码块..." 
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createSolutionDialogVisible = false">取 消</el-button>
        <el-button type="primary" :loading="publishing" @click="submitNewSolution">发 布</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { UserFilled, Edit, Back, Delete } from '@element-plus/icons-vue'
import { marked } from 'marked'
import { getSolutionListApi, getSolutionDetailApi, createSolutionApi, updateSolutionApi, deleteSolutionApi } from '../api/solution'
import { useUserStore } from '../stores/user'
import type { Solution } from '../types'



// 接收外部传入的题目 ID
const props = defineProps<{
  problemId: number
}>()
const isEditing = ref(false) // 是否处于编辑模式
const router = useRouter()
const userStore = useUserStore()

const solutionList = ref<Solution[]>([])
const solutionTotal = ref(0)
const solutionLoading = ref(false)
const currentSolution = ref<Solution | null>(null)
const detailLoading = ref(false)

const createSolutionDialogVisible = ref(false)
const publishing = ref(false)
const solutionFormRef = ref()
const solutionForm = reactive({
  title: '',
  content: `### 解题思路\n请在此处输入思路分析...\n\n### 核心代码\n\`\`\`cpp\n// 输入代码\n\`\`\`\n`
})

const solutionRules = {
  title: [
    { required: true, message: '请输入题解标题', trigger: 'blur' },
    { min: 4, message: '标题至少 4 个字', trigger: 'blur' }
  ],
  content: [{ required: true, message: '请输入题解正文', trigger: 'blur' }]
}

const renderedSolutionContent = computed(() => {
  if (!currentSolution.value?.content) return ''
  return marked.parse(currentSolution.value.content) as string
})

// 获取题解列表
const fetchSolutions = async () => {
  if (!props.problemId) return
  solutionLoading.value = true
  try {
    const data = await getSolutionListApi({ problemId: props.problemId, pageNum: 1, pageSize: 20 })
    solutionList.value = data.items
    solutionTotal.value = data.total
  } finally {
    solutionLoading.value = false
  }
}

// 查看某篇详情
const viewSolutionDetail = async (id: number) => {
  detailLoading.value = true
  try {
    const data = await getSolutionDetailApi(id)
    currentSolution.value = data
  } finally {
    detailLoading.value = false
  }
}

// 打开写题解
const openCreateSolutionDialog = () => {
  if (!userStore.token) {
    ElMessage.warning('请先登录再发布题解！')
    router.push('/login')
    return
  }
  isEditing.value = false
  solutionForm.title = ''
  solutionForm.content = `### 解题思路\n请在此处输入思路分析...\n\n### 核心代码\n\`\`\`cpp\n// 输入代码\n\`\`\`\n`
  createSolutionDialogVisible.value = true
}

// 打开编辑题解
const handleEditSolution = () => {
  if (!currentSolution.value) return
  isEditing.value = true
  solutionForm.title = currentSolution.value.title
  solutionForm.content = currentSolution.value.content || ''
  createSolutionDialogVisible.value = true
}

// 删除题解确认
const handleDeleteSolution = () => {
  if (!currentSolution.value) return
  ElMessageBox.confirm('确定要删除这篇题解吗？删除后将无法恢复。', '删除确认', {
    confirmButtonText: '确定删除',
    cancelButtonText: '取消',
    type: 'warning'
  }).then(async () => {
    await deleteSolutionApi(currentSolution.value!.id)
    ElMessage.success('题解已成功删除！')
    currentSolution.value = null
    fetchSolutions() // 刷新列表
  })
}


// 统一提交 (区分是新建还是修改)
const submitNewSolution = async () => {
  if (!solutionFormRef.value) return
  await solutionFormRef.value.validate(async (valid: boolean) => {
    if (!valid) return
    publishing.value = true
    try {
      if (isEditing.value && currentSolution.value) {
        // 调用修改接口
        const updated = await updateSolutionApi(currentSolution.value.id, {
          title: solutionForm.title,
          content: solutionForm.content
        })
        ElMessage.success('题解修改成功！')
        currentSolution.value.title = updated.title
        currentSolution.value.content = updated.content
      } else {
        // 调用新建接口
        await createSolutionApi({
          problemId: props.problemId,
          title: solutionForm.title,
          content: solutionForm.content
        })
        ElMessage.success('题解发布成功！')
      }
      createSolutionDialogVisible.value = false
      fetchSolutions()
    } finally {
      publishing.value = false
    }
  })
}

const canManage = (solution: Solution | null) => {
  if (!solution || !userStore.userInfo) return false
  return userStore.userInfo?.id === solution.userId || userStore.userInfo?.role === 'ADMIN'
}

// 监听题目 ID 变化自动拉取
watch(() => props.problemId, () => {
  currentSolution.value = null
  fetchSolutions()
})

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
.solution-item-title {
  font-size: 15px;
  font-weight: 500;
  color: #303133;
  margin-bottom: 8px;
}
.solution-item-meta {
  display: flex;
  justify-content: space-between;
  font-size: 12px;
  color: #909399;
}
.solution-read-header {
  margin-bottom: 16px;
  padding-bottom: 12px;
  border-bottom: 1px solid #eee;
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
.solution-markdown {
  margin-top: 12px;
  font-size: 15px;
  line-height: 1.7;
}
.solution-markdown :deep(pre) {
  background-color: #f6f8fa;
  border-radius: 6px;
  padding: 12px;
  overflow-x: auto;
}

.read-top-bar {
  display: flex;                   /* 启用 Flex 布局 */
  justify-content: space-between;  /* 两端对齐：左边的靠左，右边的靠右 */
  align-items: center;             /* 垂直居中对齐 */
  margin-bottom: 16px;             /* 给底部加一点间距，不至于和正文贴太紧 (可按需调整) */
}
</style>