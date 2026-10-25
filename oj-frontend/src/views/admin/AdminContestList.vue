<template>
  <div class="admin-contest-list">
    <el-card shadow="never">
      <!-- 顶部操作栏 -->
      <div class="toolbar">
        <div class="search-box">
          <el-input
            v-model.trim="keyword"
            placeholder="按比赛标题搜索..."
            :prefix-icon="Search"
            clearable
            style="width: 260px;"
            @clear="handleSearch"
            @keyup.enter="handleSearch"
          />
          <el-button type="primary" :icon="Search" @click="handleSearch">搜索</el-button>
          <el-button :icon="Refresh" @click="handleReset">重置</el-button>
        </div>

        <el-button type="success" :icon="Plus" @click="openCreateModal">
          发布新比赛
        </el-button>
      </div>

      <!-- 比赛管理表格 -->
      <el-table :data="contestList" v-loading="loading" stripe style="width: 100%">
        <el-table-column prop="id" label="#" width="70" align="center" />

        <el-table-column prop="title" label="比赛标题" min-width="240">
          <template #default="{ row }">
            <el-link type="primary" :underline="false" @click="$router.push(`/contests/${row.id}`)">
              {{ row.title }}
            </el-link>
          </template>
        </el-table-column>

        <el-table-column prop="status" label="当前状态" width="120" align="center">
          <template #default="{ row }">
            <el-tag :type="getStatusTagType(row.status)" size="small" effect="dark">
              {{ getStatusText(row.status) }}
            </el-tag>
          </template>
        </el-table-column>

        <el-table-column label="起止时间" width="310" align="center">
          <template #default="{ row }">
            <span style="font-size: 13px;">{{ row.startTime }} 至 {{ row.endTime }}</span>
          </template>
        </el-table-column>

        <el-table-column prop="problemCount" label="包含题数" width="90" align="center">
          <template #default="{ row }">
            <el-tag type="info" size="small">{{ row.problemCount ?? 0 }} 题</el-tag>
          </template>
        </el-table-column>

        <el-table-column prop="registeredCount" label="报名人数" width="90" align="center">
          <template #default="{ row }">
            <b style="color: #409eff;">{{ row.registeredCount ?? 0 }} 人</b>
          </template>
        </el-table-column>

        <!-- 操作栏 -->
        <el-table-column label="操作" width="260" align="center" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" :icon="Edit" @click="openEditModal(row)">
              编辑
            </el-button>

            <!-- 赛后一键公开题目 -->
            <el-button
              link
              type="success"
              size="small"
              :icon="Unlock"
              :disabled="row.isAllPublic"
              @click="handlePublishProblems(row)"
            >
              {{ row.isAllPublic ? '题目已公开' : '一键公开题目' }}
            </el-button>

            <el-button link type="danger" size="small" :icon="Delete" @click="handleDelete(row)">
              删除
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <!-- 分页 -->
      <div class="pagination-bar">
        <el-pagination
          v-model:current-page="pageNum"
          v-model:page-size="pageSize"
          :page-sizes="[10, 20, 50]"
          background
          layout="total, sizes, prev, pager, next"
          :total="total"
          @size-change="handleSearch"
          @current-change="fetchContests"
        />
      </div>
    </el-card>

    <!-- 创建 / 编辑比赛通用模态框 -->
    <el-dialog
      v-model="modalVisible"
      :title="isEdit ? '编辑比赛规程与题目' : '创建新比赛 (自动发布置顶公告)'"
      width="780px"
      destroy-on-close
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-position="top" v-loading="modalLoading">
        <el-form-item label="比赛标题" prop="title">
          <el-input v-model.trim="form.title" placeholder="例如: 2026 第一届 Core OJ 新星对抗赛" />
        </el-form-item>

        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="开始时间" prop="startTime">
              <el-date-picker
                v-model="form.startTime"
                type="datetime"
                value-format="YYYY-MM-DD HH:mm:ss"
                placeholder="选择开赛时间"
                style="width: 100%"
                :disabled="isEdit && currentEditingStatus !== 'NOT_STARTED'"
              />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="结束时间" prop="endTime">
              <el-date-picker
                v-model="form.endTime"
                type="datetime"
                value-format="YYYY-MM-DD HH:mm:ss"
                placeholder="选择结赛时间"
                style="width: 100%"
                :disabled="isEdit && currentEditingStatus === 'ENDED'"
              />
            </el-form-item>
          </el-col>
        </el-row>

        <el-form-item label="比赛规程说明 (Markdown)" prop="description">
          <el-input v-model="form.description" type="textarea" :rows="6" placeholder="输入比赛说明规程..." />
        </el-form-item>

        <!-- 比赛题目挑选与 A/B/C 自动排序配置 -->
        <el-form-item label="比赛题目配置 (系统自动按添加顺序编号为 A, B, C... 仅未开赛时允许修改题目)">
          <div v-for="(pItem, idx) in form.problems" :key="idx" class="problem-pick-row">
            <span class="auto-letter">{{ String.fromCharCode(65 + idx) }} 题</span>

            <el-select
              v-model="pItem.problemId"
              placeholder="从题库中挑选题目"
              filterable
              style="width: 280px;"
              :disabled="isEdit && currentEditingStatus !== 'NOT_STARTED'"
              @change="(val: any) => onProblemSelect(val, pItem)"
            >
              <el-option
                v-for="p in allProblemOptions"
                :key="p.id"
                :label="`#${p.id} ${p.title} (${p.isPublic === 1 ? '公开' : '保密'})`"
                :value="p.id"
                :disabled="isProblemAlreadySelected(p.id, idx)"
              />
            </el-select>

            <el-input
              v-model.trim="pItem.displayTitle"
              placeholder="比赛内展示标题"
              style="flex: 1;"
              :disabled="isEdit && currentEditingStatus !== 'NOT_STARTED'"
            />

            <el-button
              type="danger"
              :icon="Delete"
              circle
              plain
              size="small"
              :disabled="isEdit && currentEditingStatus !== 'NOT_STARTED'"
              @click="removeProblemItem(idx)"
            />
          </div>

          <el-button
            type="primary"
            plain
            :icon="Plus"
            size="small"
            style="margin-top: 8px;"
            :disabled="isEdit && currentEditingStatus !== 'NOT_STARTED'"
            @click="addProblemItem"
          >
            添加比赛题目
          </el-button>
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="modalVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="handleSubmit">
          {{ isEdit ? '保存修改' : '立即发布比赛' }}
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance } from 'element-plus'
import { Search, Refresh, Plus, Edit, Delete, Unlock } from '@element-plus/icons-vue'
import {
  getContestListApi,
  getContestDetailApi,
  getContestProblemsApi,
  createContestApi,
  updateContestApi,
  deleteContestApi,
  publishContestProblemsApi
} from '../../api/contest'
import { getProblemListApi } from '../../api/problem'
import type { Contest, Problem } from '../../types'

const loading = ref(false)
const contestList = ref<Contest[]>([])
const total = ref(0)
const pageNum = ref(1)
const pageSize = ref(10)
const keyword = ref('')

// 模态框相关
const modalVisible = ref(false)
const modalLoading = ref(false)
const submitting = ref(false)
const isEdit = ref(false)
const editingId = ref<number | null>(null)
const currentEditingStatus = ref<string>('')
const formRef = ref<FormInstance>()

const allProblemOptions = ref<Problem[]>([])

const defaultContestDesc =
  '### 比赛规则\n1. 采用标准 **ACM/ICPC** 赛制；\n2. 最终排名首先按解题数降序排序，相同者按总罚时升序排序；\n3. 严禁作弊与代码雷同！'

const form = reactive({
  title: '',
  description: defaultContestDesc,
  startTime: '',
  endTime: '',
  problems: [{ problemId: null as number | null, displayTitle: '' }]
})

const rules = {
  title: [{ required: true, message: '比赛标题不能为空', trigger: 'blur' }],
  startTime: [{ required: true, message: '请选择开始时间', trigger: 'blur' }],
  endTime: [{ required: true, message: '请选择结束时间', trigger: 'blur' }],
  description: [{ required: true, message: '说明规程不能为空', trigger: 'blur' }]
}

const fetchContests = async () => {
  loading.value = true
  try {
    const data = await getContestListApi({
      pageNum: pageNum.value,
      pageSize: pageSize.value,
      keyword: keyword.value || undefined
    })
    contestList.value = data.items
    total.value = data.total
  } catch {
  } finally {
    loading.value = false
  }
}

const loadProblemOptions = async () => {
  try {
    const data = await getProblemListApi({ pageNum: 1, pageSize: 100 })
    allProblemOptions.value = data.items
  } catch {}
}

const handleSearch = () => {
  pageNum.value = 1
  fetchContests()
}

const handleReset = () => {
  keyword.value = ''
  pageNum.value = 1
  fetchContests()
}

const addProblemItem = () => {
  form.problems.push({ problemId: null, displayTitle: '' })
}

const removeProblemItem = (idx: number) => {
  if (form.problems.length <= 1) {
    ElMessage.warning('比赛至少需要保留一道题目！')
    return
  }
  form.problems.splice(idx, 1)
}

// 防止在同一场比赛的不同题号中重复选择同一道题
const isProblemAlreadySelected = (problemId: number, currentIndex: number) => {
  return form.problems.some((item, idx) => idx !== currentIndex && item.problemId === problemId)
}

const onProblemSelect = (problemId: number, item: { problemId: number | null; displayTitle: string }) => {
  const found = allProblemOptions.value.find(p => p.id === problemId)
  if (found) {
    item.displayTitle = found.title
  }
}

const openCreateModal = () => {
  isEdit.value = false
  editingId.value = null
  currentEditingStatus.value = ''
  form.title = ''
  form.description = defaultContestDesc
  form.startTime = ''
  form.endTime = ''
  form.problems = [{ problemId: null, displayTitle: '' }]
  modalVisible.value = true
  loadProblemOptions()
}

const openEditModal = async (row: Contest) => {
  isEdit.value = true
  editingId.value = row.id
  currentEditingStatus.value = row.status
  modalVisible.value = true
  modalLoading.value = true
  loadProblemOptions()

  try {
    const detail = await getContestDetailApi(row.id)
    form.title = detail.title
    form.description = detail.description
    form.startTime = detail.startTime
    form.endTime = detail.endTime

    const pList = await getContestProblemsApi(row.id)
    form.problems =
      pList.length > 0
        ? pList.map(p => ({
            problemId: p.problemId,
            displayTitle: p.displayTitle
          }))
        : [{ problemId: null, displayTitle: '' }]
  } catch {
    modalVisible.value = false
  } finally {
    modalLoading.value = false
  }
}

const handleSubmit = async () => {
  if (!formRef.value || submitting.value) return
  for (const item of form.problems) {
    if (!item.problemId) {
      ElMessage.warning('请为所有比赛题目选择对应的题库编号！')
      return
    }
    if (!item.displayTitle) {
      item.displayTitle = '未命名赛题'
    }
  }

  try {
    await formRef.value.validate()
  } catch {
    return
  }

  submitting.value = true
  try {
    if (isEdit.value && editingId.value) {
      await updateContestApi(editingId.value, form)
      ElMessage.success('比赛修改成功！')
    } else {
      await createContestApi(form)
      ElMessage.success('比赛创建成功，赛事公告已自动同步至讨论区！')
    }
    modalVisible.value = false
    fetchContests()
  } catch {
  } finally {
    submitting.value = false
  }
}

const handlePublishProblems = async (row: Contest) => {
  try {
    await ElMessageBox.confirm(
      `确定要将比赛《${row.title}》的所有保密题目同步开放至公共题库大厅吗？`,
      '公开题目确认',
      { confirmButtonText: '确定公开', cancelButtonText: '取消', type: 'success' }
    )
    await publishContestProblemsApi(row.id)
    ElMessage.success('成功将比赛题目全部开放至公共题库！')
    fetchContests()
  } catch {}
}

const handleDelete = async (row: Contest) => {
  try {
    await ElMessageBox.confirm(
      `确定要删除比赛《${row.title}》吗？注意：底层的题目和测试点不会被删除。`,
      '删除确认',
      { confirmButtonText: '确定删除', cancelButtonText: '取消', type: 'warning' }
    )
    await deleteContestApi(row.id)
    ElMessage.success('比赛已删除！')
    fetchContests()
  } catch {}
}

const getStatusTagType = (status: string) => {
  switch (status) {
    case 'RUNNING':
      return 'success'
    case 'NOT_STARTED':
      return 'warning'
    default:
      return 'info'
  }
}

const getStatusText = (status: string) => {
  switch (status) {
    case 'RUNNING':
      return '进行中'
    case 'NOT_STARTED':
      return '未开始'
    case 'ENDED':
      return '已结束'
    default:
      return '未知'
  }
}

onMounted(() => {
  fetchContests()
})
</script>

<style scoped>
.toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  flex-wrap: wrap;
  gap: 12px;
  margin-bottom: 20px;
}
.search-box {
  display: flex;
  gap: 12px;
}
.pagination-bar {
  display: flex;
  justify-content: flex-end;
  margin-top: 20px;
}
.problem-pick-row {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 10px;
  width: 100%;
}
.auto-letter {
  font-weight: bold;
  color: #409eff;
  width: 45px;
  text-align: center;
  font-size: 15px;
  flex-shrink: 0;
}
</style>