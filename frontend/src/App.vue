<script setup lang="ts">
import {useRoute} from 'vue-router';
const route=useRoute();
</script>
<template>
  <div class="app-shell">
    <a class="skip-link" href="#main-content">跳到主要内容</a>
    <header class="app-header">
      <RouterLink to="/" class="brand" aria-label="返回首页"><span class="brand-mark">仓</span><span>仓库 ↔ 生产车间</span></RouterLink>
      <RouterLink v-if="route.path!=='/'" to="/" class="header-home">首页</RouterLink>
    </header>
    <main id="main-content" tabindex="-1"><RouterView/></main>
  </div>
</template>
