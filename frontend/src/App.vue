<template>
  <router-view v-slot="{ Component }">
    <transition name="page-fade" mode="out-in">
      <component :is="Component" />
    </transition>
  </router-view>
  <SettingsModal />
</template>

<script setup lang="ts">
import SettingsModal from '@/components/SettingsModal.vue'
</script>
