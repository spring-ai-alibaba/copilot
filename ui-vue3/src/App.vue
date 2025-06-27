<template>
  <div id="app">
    <a-config-provider :theme="{ algorithm: theme.defaultAlgorithm }">
      <router-view />
    </a-config-provider>
  </div>
</template>

<script setup lang="ts">
import {theme} from 'ant-design-vue'
</script>

<style>
#app {
  height: 100vh;
  width: 100vw;
}

body {
  margin: 0;
  padding: 0;
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', 'PingFang SC', 'Hiragino Sans GB',
    'Microsoft YaHei', 'Helvetica Neue', Helvetica, Arial, sans-serif, 'Apple Color Emoji',
    'Segoe UI Emoji', 'Segoe UI Symbol';
}
</style>
