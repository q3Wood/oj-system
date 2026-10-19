<template>
  <div class="login-wrapper">
    <el-card class="login-card">
      <div class="header-title">
        <h2>Core OJ 判题系统</h2>
        <p>{{ isRegister ? '创建新账号以开始做题' : '欢迎回来，请输入账号登录' }}</p>
      </div>

      <el-form ref="formRef" :model="formData" :rules="formRules" label-position="top">
        <el-form-item label="用户名" prop="username">
          <el-input 
            v-model="formData.username" 
            placeholder="请输入 4-16 位用户名" 
            prefix-icon="User" 
            clearable 
          />
        </el-form-item>

        <el-form-item label="密码" prop="password">
          <el-input 
            v-model="formData.password" 
            type="password" 
            placeholder="请输入 5-16 位密码" 
            prefix-icon="Lock" 
            show-password 
          />
        </el-form-item>

        <el-form-item v-if="isRegister" label="确认密码" prop="confirmPassword">
          <el-input 
            v-model="formData.confirmPassword" 
            type="password" 
            placeholder="请再次输入密码" 
            prefix-icon="Lock" 
            show-password 
          />
        </el-form-item>

        <el-button 
          type="primary" 
          class="submit-btn" 
          :loading="loading" 
          @click="handleSubmit"
        >
          {{ isRegister ? '立即注册' : '登 录' }}
        </el-button>
      </el-form>

      <div class="switch-box">
        <span v-if="!isRegister">
          还没有账号？
          <el-link type="primary" :underline="false" @click="toggleMode">立即注册</el-link>
        </span>
        <span v-else>
          已有账号？
          <el-link type="primary" :underline="false" @click="toggleMode">返回登录</el-link>
        </span>
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { userRegisterApi, userLoginApi, getUserInfoApi } from '../../api/user'
import { useUserStore } from '../../stores/user'


const router = useRouter()
const userStore = useUserStore()
const formRef = ref<FormInstance>()
const isRegister = ref(false)
const loading = ref(false)

const formData = reactive({
  username: '',
  password: '',
  confirmPassword: ''
})

const formRules = reactive<FormRules>({
  username: [
    { required: true, message: '用户名不能为空', trigger: 'blur' },
    { min: 4, max: 16, message: '长度需在 4 到 16 个字符', trigger: 'blur' }
  ],
  password: [
    { required: true, message: '密码不能为空', trigger: 'blur' },
    { min: 5, max: 16, message: '长度需在 5 到 16 个字符', trigger: 'blur' }
  ],
  confirmPassword: [
    {
      validator: (rule: any, value: string, callback: any) => {
        if (isRegister.value && value !== formData.password) {
          callback(new Error('两次输入的密码不一致！'))
        } else {
          callback()
        }
      },
      trigger: 'blur'
    }
  ]
})

const toggleMode = () => {
  isRegister.value = !isRegister.value
  formRef.value?.resetFields()
}

const handleSubmit = async () => {
  if (!formRef.value) return

  await formRef.value.validate(async (valid) => {
    if (!valid) return

    loading.value = true
    try {
      if (isRegister.value) {
        await userRegisterApi({
          username: formData.username,
          password: formData.password
        })
        ElMessage.success('注册成功！已切换为登录模式')
        isRegister.value = false
        formData.confirmPassword = ''
      } else {
        // 1. 登录拿 Token
        const token = await userLoginApi({
          username: formData.username,
          password: formData.password
        })


        console.log('登录成功，返回 Token:', token)
        // 2. 存进 Pinia Store
        userStore.setToken(token)

        // 3. 携带 Token 查询个人信息存入 Pinia
        const user = await getUserInfoApi()
        userStore.setUserInfo(user)

        ElMessage.success('登录成功！')
        router.push('/problems') // 平滑跳转题库大厅
      }
    } catch (err) {
      // 错误由 request.ts 拦截器统一弹出
    } finally {
      loading.value = false
    }
  })
}
</script>

<style scoped>
.login-wrapper {
  display: flex;
  justify-content: center;
  align-items: center;
  min-height: calc(100vh - 120px);
}
.login-card {
  width: 420px;
  border-radius: 12px;
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.08);
  padding: 10px 20px;
}
.header-title {
  text-align: center;
  margin-bottom: 24px;
}
.header-title h2 {
  font-size: 24px;
  color: #303133;
  margin-bottom: 8px;
}
.header-title p {
  font-size: 14px;
  color: #909399;
}
.submit-btn {
  width: 100%;
  height: 40px;
  font-size: 16px;
  margin-top: 10px;
}
.switch-box {
  margin-top: 20px;
  text-align: center;
  font-size: 14px;
  color: #606266;
}
</style>