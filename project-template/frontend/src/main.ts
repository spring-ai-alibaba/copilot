import {createApp} from 'vue'
import {createPinia} from 'pinia'
import Antd from 'ant-design-vue'
import 'ant-design-vue/dist/reset.css'

import App from './App.vue'
import router from './router'

const app = createApp(App)

app.use(createPinia())
app.use(router)
app.use(Antd)

app.mount('#app')

console.log('Spring AI Chat Frontend Started!')
console.log('Version: 1.0.0')
console.log('Environment:', import.meta.env.MODE)
