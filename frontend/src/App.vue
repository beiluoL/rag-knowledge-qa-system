<template>
  <router-view v-slot="{ Component, route }">
    <!-- 后台布局(含 el-menu)不能包在 mode="out-in" 的 <Transition> 内：
         el-menu 内部自带 ElMenuCollapseTransition(out-in)，嵌套 out-in 会触发
         "Slot default invoked outside of the render function" 警告，并因异步子路由
         频繁切换打断 transition 导致白屏。后台子页的淡入由 AdminLayout 内部处理。 -->
    <component :is="Component" v-if="route.meta.requiresAdmin" />
    <transition v-else name="page-fade" mode="out-in">
      <component :is="Component" />
    </transition>
  </router-view>
  <SettingsModal />
</template>

<script setup lang="ts">
import SettingsModal from '@/components/SettingsModal.vue'
</script>
