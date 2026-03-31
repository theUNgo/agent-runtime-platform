import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import LoginView from '../views/LoginView.vue'
import DashboardView from '../views/DashboardView.vue'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/login', name: 'login', component: LoginView },
    { path: '/', name: 'dashboard', component: DashboardView }
  ]
})

router.beforeEach(async (to) => {
  const authStore = useAuthStore()
  if (authStore.token && !authStore.user) {
    await authStore.fetchMe().catch(() => authStore.logout())
  }
  if (to.name !== 'login' && !authStore.token) {
    return { name: 'login' }
  }
  if (to.name === 'login' && authStore.token) {
    return { name: 'dashboard' }
  }
  return true
})

export default router
