<template>
  <div class="login-wrapper">
    <el-card class="login-card">
      <div class="header-title">
        <h2>Core OJ 判题系统</h2>
        <p>{{ isRegister ? '创建新账号以开始做题' : '欢迎回来，请输入账号登录' }}</p>
      </div>

      <!-- 增加 @submit.prevent 阻止默认提交，增加 @keyup.enter 支持回车键直接登录/注册 -->
      <el-form
        ref="formRef"
        :model="formData"
        :rules="formRules"
        label-position="top"
        @submit.prevent
        @keyup.enter="handleSubmit"
      >
        <el-form-item label="用户名" prop="username">
          <el-input
            v-model.trim="formData.username"
            placeholder="请输入 4-16 位用户名"
            :prefix-icon="User"
            clearable
          />
        </el-form-item>

        <el-form-item label="密码" prop="password">
          <el-input
            v-model.trim="formData.password"
            type="password"
            placeholder="请输入 5-16 位密码"
            :prefix-icon="Lock"
            show-password
          />
        </el-form-item>

        <el-form-item v-if="isRegister" label="确认密码" prop="confirmPassword">
          <el-input
            v-model.trim="formData.confirmPassword"
            type="password"
            placeholder="请再次输入密码"
            :prefix-icon="Lock"
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
import { useRouter, useRoute } from 'vue-router'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { User, Lock } from '@element-plus/icons-vue'
import { userRegisterApi, userLoginApi, getUserInfoApi } from '../../api/user'
import { useUserStore } from '../../stores/user'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()

const formRef = ref<FormInstance>()
const isRegister = ref(false)
const loading = ref(false)

const formData = reactive({
  username: '',
  password: '',
  confirmPassword: ''
})

// 密码校验：当修改密码时，若确认密码已填写，则触发确认密码的联动校验
const validatePass = (_rule: any, _value: string, callback: any) => {
  if (isRegister.value && formData.confirmPassword !== '') {
    formRef.value?.validateField('confirmPassword')
  }
  callback()
}

// 确认密码校验
const validateConfirmPass = (_rule: any, value: string, callback: any) => {
  if (!isRegister.value) {
    return callback()
  }
  if (!value) {
    callback(new Error('请再次输入密码'))
  } else if (value !== formData.password) {
    callback(new Error('两次输入的密码不一致！'))
  } else {
    callback()
  }
}

const formRules = reactive<FormRules>({
  username: [
    { required: true, message: '用户名不能为空', trigger: 'blur' },
    { min: 4, max: 16, message: '长度需在 4 到 16 个字符', trigger: 'blur' }
  ],
  password: [
    { required: true, message: '密码不能为空', trigger: 'blur' },
    { min: 5, max: 16, message: '长度需在 5 到 16 个字符', trigger: 'blur' },
    { validator: validatePass, trigger: 'blur' }
  ],
  confirmPassword: [
    { required: true, validator: validateConfirmPass, trigger: 'blur' }
  ]
})

const toggleMode = () => {
  isRegister.value = !isRegister.value
  // 重置表单值并移除校验报错红字
  formRef.value?.resetFields()
}

const handleSubmit = async () => {
  if (!formRef.value || loading.value) return

  // 1. 独立执行表单校验 (不传 callback 时返回 boolean 的 Promise)
  try {
    await formRef.value.validate()
  } catch {
    return // 表单校验未通过，直接中断
  }

  // 2. 发起网络请求
  loading.value = true
  try {
    if (isRegister.value) {
      await userRegisterApi({
        username: formData.username,
        password: formData.password
      })
      ElMessage.success('注册成功！请直接登录')
      // 切换回登录模式，保留刚填好的 username 和 password 方便用户直接点登录，仅清空确认密码和红字
      isRegister.value = false
      formData.confirmPassword = ''
      formRef.value.clearValidate()
    } else {
      // 1. 登录拿 Token
      const token = await userLoginApi({
        username: formData.username,
        password: formData.password
      })

      // 2. 存进 Pinia Store
      userStore.setToken(token)

      // 3. 携带 Token 查询个人信息存入 Pinia
      const user = await getUserInfoApi()
      userStore.setUserInfo(user)

      ElMessage.success('登录成功！')
      // 如果 URL 带有 redirect 参数则跳回原页面，否则默认跳转题库大厅
      const redirect = (route.query.redirect as string) || '/problems'
      router.push(redirect)
    }
  } catch (err) {
    // 业务异常已由 request.ts 响应拦截器统一弹窗处理
  } finally {
    loading.value = false
  }
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