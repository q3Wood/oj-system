<template>
  <div class="create-problem-container">
    <el-card class="box-card" shadow="never">
      <template #header>
        <div class="card-header">
          <div class="header-left">
            <el-button link icon="Back" @click="$router.push('/problems')">返回题库</el-button>
            <h2 class="title">创建新题目 (出题管理)</h2>
          </div>
        </div>
      </template>

      <el-form ref="formRef" :model="form" :rules="rules" label-position="top">
        <el-row :gutter="20">
          <el-col :span="8">
            <el-form-item label="题目编号 (ID)" prop="id">
              <el-input-number 
                v-model="form.id" 
                :min="1000" 
                :max="99999" 
                style="width: 100%" 
                placeholder="例如 1007" 
              />
            </el-form-item>
          </el-col>
          <el-col :span="16">
            <el-form-item label="题目标题" prop="title">
              <el-input v-model="form.title" placeholder="请输入题目标题 (如: 快速排序)" />
            </el-form-item>
          </el-col>
        </el-row>

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

        <el-form-item label="题面描述 (Markdown 格式)" prop="description">
          <el-input
            v-model="form.description"
            type="textarea"
            :rows="12"
            placeholder="支持标准 Markdown 语法，包含题目描述、输入格式、输出格式与样例..."
          />
        </el-form-item>

        <!-- 测试用例 Zip 压缩包上传 -->
        <el-form-item label="测试用例压缩包 (.zip)" required>
          <el-upload
            class="upload-box"
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
              将测试用例压缩包拖到此处，或 <em>点击上传</em>
            </div>
            <template #tip>
              <div class="el-upload__tip">
                请上传 .zip 格式压缩包，包内需包含成对的测试点文件（如 <code>1.in</code>, <code>1.ans</code>, <code>2.in</code>, <code>2.ans</code>）
              </div>
            </template>
          </el-upload>
        </el-form-item>

        <div class="form-actions">
          <el-button @click="$router.push('/problems')">取 消</el-button>
          <el-button type="primary" :loading="submitting" @click="handlePublish">发布题目</el-button>
        </div>
      </el-form>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, type FormInstance, type FormRules, type UploadFile } from 'element-plus'
import { UploadFilled } from '@element-plus/icons-vue'
import { createProblemApi } from '../../api/problem'

const router = useRouter()
const formRef = ref<FormInstance>()
const submitting = ref(false)
const selectedFile = ref<File | null>(null)

// 初始默认模板
const defaultDescription = 
`### 题目描述
请在此处输入详细题目描述。

### 输入格式
输入第一行包含...

### 输出格式
输出包含...

### 样例输入
\`\`\`
1 2
\`\`\`

### 样例输出
\`\`\`
3
\`\`\`
`

const form = reactive({
  id: 1007,
  title: '',
  description: defaultDescription,
  timeLimitMs: 1000,
  memoryLimitMb: 256
})

const rules = reactive<FormRules>({
  id: [{ required: true, message: '请输入题目 ID', trigger: 'blur' }],
  title: [{ required: true, message: '请输入题目标题', trigger: 'blur' }],
  description: [{ required: true, message: '请输入题面描述', trigger: 'blur' }]
})

const handleFileChange = (file: UploadFile) => {
  if (file.raw) {
    selectedFile.value = file.raw
  }
}

const handleFileRemove = () => {
  selectedFile.value = null
}

const handlePublish = async () => {
  if (!formRef.value) return
  if (!selectedFile.value) {
    ElMessage.warning('请选择要上传的测试用例 .zip 压缩包！')
    return
  }

  await formRef.value.validate(async (valid) => {
    if (!valid) return

    submitting.value = true
    try {
      // 构造 multipart/form-data 数据包
      const formData = new FormData()
      formData.append('id', String(form.id))
      formData.append('title', form.title)
      formData.append('description', form.description)
      formData.append('timeLimitMs', String(form.timeLimitMs))
      formData.append('memoryLimitMb', String(form.memoryLimitMb))
      formData.append('file', selectedFile.value!)

      const newProblem = await createProblemApi(formData)

      ElMessage.success('题目创建成功，测试点已自动解压就绪！')
      // 成功后直接跳转到新做题页
      router.push(`/problems/${newProblem.id}`)
    } catch (error) {
      // 错误由 request.ts 统一弹出提示
    } finally {
      submitting.value = false
    }
  })
}
</script>

<style scoped>
.create-problem-container {
  max-width: 900px;
  margin: 0 auto;
}
.box-card {
  border-radius: 8px;
  padding: 10px;
}
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.header-left {
  display: flex;
  align-items: center;
  gap: 16px;
}
.title {
  font-size: 20px;
  margin: 0;
  color: #303133;
}
.upload-box {
  width: 100%;
}
.form-actions {
  display: flex;
  justify-content: flex-end;
  gap: 16px;
  margin-top: 24px;
}
</style>