<template>
  <div class="chat-view">
    <ChatComponent />
  </div>
</template>

<script setup lang="ts">
import ChatComponent from '@/components/ChatComponent.vue'
</script>

<style scoped>
.chat-view {
  width: 100%;
  height: 100vh;
  overflow: hidden;
}
</style>
