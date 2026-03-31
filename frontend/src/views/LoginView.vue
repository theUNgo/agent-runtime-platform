<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'

const authStore = useAuthStore()
const router = useRouter()
const username = ref('admin')
const password = ref('')
const submitting = ref(false)
const errorMessage = ref('')

async function submit() {
  submitting.value = true
  errorMessage.value = ''
  try {
    await authStore.login(username.value, password.value)
    await router.push('/')
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '登录失败'
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <div class="login-page">
    <div class="login-card">
      <p class="eyebrow">Agent Runtime</p>
      <h1>登录统一智能体平台</h1>
      <p class="login-text">
        平台会按用户记录安装状态、会话历史、消息内容与能力调用轨迹，也支持为每个用户单独配置模型地址、API Key、MCP 与 Agent Skills。
      </p>
      <form class="login-form" @submit.prevent="submit">
        <label>
          用户名
          <input v-model="username" type="text" autocomplete="username">
        </label>
        <label>
          密码
          <input v-model="password" type="password" autocomplete="current-password">
        </label>
        <button class="primary-button" :disabled="submitting">
          {{ submitting ? '登录中...' : '登录' }}
        </button>
      </form>
      <p v-if="errorMessage" class="error-text">{{ errorMessage }}</p>
    </div>
  </div>
</template>
