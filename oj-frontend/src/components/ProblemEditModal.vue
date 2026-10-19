<template>
  <el-dialog
    v-model="visible"
    :title="isEdit ? `编辑题目 (#${problemId})` : '发布新题目 (带用例)'"
    width="760px"
    destroy-on-close
    @closed="handleClosed"
  >
    <el-form ref="formRef" :model="form" :rules="rules" label-position="top" v-loading="loading">
      <el-form-item label="题目标题" prop="title">
        <el-input v-model="form.title" placeholder="请输入题目标题" />
      </el-form-item>

      <el-row :gutter="20">
        <el-col :span="12">
          <el-form-item label="时间限制 (ms)" prop="timeLimitMs">
            <el-input-number v-model="form.timeLimitMs" :min="100" :step="100" style="width: 100%" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="内存限制 (MB)" prop="memoryLimitMb">
            <el-input-number v-model="form.memoryLimitMb" :min="16" :step="64" style="width: 100%" />
          </el-form-item>
        </el-col>
      </el-row>

      <el-form-item label="公开状态">
        <el-radio-group v-model="form.isPublic">
          <el-radio :label="1">公开到题库大厅</el-radio>
          <el-radio :label="0">设为比赛保密题</el-radio>
        </el-radio-group>
      </el-form-item>

      <el-form-item label="题面描述 (Markdown 格式)" prop="description">
        <el-input
          v-model="form.description"
          type="textarea"
          :rows="10"
          placeholder="支持标准 Markdown 语法..."
        />
      </el-form-item>

      <!-- 测试用例 Zip 压缩包上传 (新建时必传，编辑时选传覆盖) -->
      <el-form-item :label="isEdit ? '更新测试用例压缩包 (选填，不传则保留原用例)' : '测试用例压缩包 (.zip)'">
        <el-upload
          class="upload-demo"
          drag
          action="#"
          :auto-upload="false"
          :limit="1"
          accept=".zip"
          :on-change="handleFileChange"
          :on-remove="handleFileRemove"
        >
          <el-icon class="el-icon--upload"><upload-filled /></el-icon>
          <div class="el-upload__text">
            将测试用例 .zip 拖到此处，或 <em>点击上传</em>
          </div>
          <template #tip>
            <div class="el-upload__tip">
              必须包含成对的 <code>1.in</code>, <code>1.ans</code> 等文件
            </div>
          </template>
        </el-upload>
      </el-form-item>
    </el-form>

    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" :loading="submitting" @click="handleSubmit">
        {{ isEdit ? '保存修改' : '立即发布' }}
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, reactive, computed, watch } from 'vue'
import { ElMessage, type UploadFile } from 'element-plus'
import { UploadFilled } from '@element-plus/icons-vue'
import { getProblemDetailApi, createProblemApi, updateProblemApi } from '../api/problem'

interface Props {
  modelValue: boolean
  problemId?: number | null // 有 ID 为编辑模式，无 ID 为新建模式
}

const props = defineProps<Props>()
const emit = defineEmits<{
  (e: 'update:modelValue', val: boolean): void
  (e: 'success'): void
}>()

const visible = computed({
  get: () => props.modelValue,
  set: (val) => emit('update:modelValue', val)
})

const isEdit = computed(() => !!props.problemId)
const loading = ref(false)
const submitting = ref(false)
const formRef = ref()
const selectedFile = ref<File | null>(null)

const defaultDesc = `### 题目描述\n\n### 输入格式\n\n### 输出格式\n\n### 样例输入\n\`\`\`\n\n\`\`\`\n### 样例输出\n\`\`\`\n\n\`\`\`\n`

const form = reactive({
  title: '',
  description: defaultDesc,
  timeLimitMs: 1000,
  memoryLimitMb: 256,
  isPublic: 1
})

const rules = {
  title: [{ required: true, message: '标题不能为空', trigger: 'blur' }],
  description: [{ required: true, message: '题面描述不能为空', trigger: 'blur' }]
}

const handleFileChange = (file: UploadFile) => {
  if (file.raw) selectedFile.value = file.raw
}

const handleFileRemove = () => {
  selectedFile.value = null
}

const handleClosed = () => {
  form.title = ''
  form.description = defaultDesc
  form.timeLimitMs = 1000
  form.memoryLimitMb = 256
  form.isPublic = 1
  selectedFile.value = null
}

// 打开弹窗时拉取详情
watch(
  () => props.modelValue,
  async (isOpen) => {
    if (isOpen && props.problemId) {
      loading.value = true
      try {
        const data = await getProblemDetailApi(props.problemId)
        form.title = data.title
        form.description = data.description || ''
        form.timeLimitMs = data.timeLimitMs
        form.memoryLimitMb = data.memoryLimitMb
        form.isPublic = (data as any).isPublic ?? 1
      } finally {
        loading.value = false
      }
    }
  }
)

const handleSubmit = async () => {
  if (!formRef.value) return
  if (!isEdit.value && !selectedFile.value) {
    ElMessage.warning('发布新题必须上传测试用例 .zip 压缩包！')
    return
  }

  await formRef.value.validate(async (valid: boolean) => {
    if (!valid) return
    submitting.value = true
    try {
      // 核心：全部组装为 FormData，彻底解决 Content-Type 冲突
      const formData = new FormData()
      formData.append('title', form.title)
      formData.append('description', form.description)
      formData.append('timeLimitMs', String(form.timeLimitMs))
      formData.append('memoryLimitMb', String(form.memoryLimitMb))
      formData.append('isPublic', String(form.isPublic))
      if (selectedFile.value) {
        formData.append('file', selectedFile.value)
      }

      if (isEdit.value) {
        await updateProblemApi(props.problemId!, formData)
        ElMessage.success('题目修改成功！')
      } else {
        await createProblemApi(formData)
        ElMessage.success('题目发布成功，测试点已自动解压就绪！')
      }
      visible.value = false
      emit('success')
    } finally {
      submitting.value = false
    }
  })
}
</script>

<style scoped>
.upload-demo {
  width: 100%;
}
</style>