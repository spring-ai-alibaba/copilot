import {createRouter, createWebHistory} from 'vue-router'
import TaskView from '@/views/TaskView.vue'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/',
      name: 'task',
      component: TaskView
    }
  ]
})

export default router
