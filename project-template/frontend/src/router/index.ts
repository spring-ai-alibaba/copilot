import {createRouter, createWebHistory} from 'vue-router'
import ChatView from '@/views/ChatView.vue'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    {
      path: '/',
      name: 'home',
      redirect: '/chat'
    },
    {
      path: '/chat',
      name: 'chat',
      component: ChatView,
      meta: {
        title: 'AI聊天'
      }
    }
  ]
})

// 路由守卫 - 设置页面标题
router.beforeEach((to, from, next) => {
  if (to.meta?.title) {
    document.title = `${to.meta.title} - Spring AI Chat`
  } else {
    document.title = 'Spring AI Chat'
  }
  next()
})

export default router
