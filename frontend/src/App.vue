<script setup lang="ts">
import {onMounted} from 'vue';
import {useRoute} from 'vue-router';
import {useRouter} from 'vue-router';
import {refreshAuth,logout,useAuth} from './auth';
const route=useRoute();
const router=useRouter();
const {authenticated}=useAuth();
onMounted(refreshAuth);
async function signOut(){await logout();await router.replace('/')}
</script>
<template>
  <div class="app-shell">
    <a class="skip-link" href="#main-content">跳到主要内容</a>
    <header class="app-header">
      <RouterLink to="/" class="brand" aria-label="返回首页"><span class="brand-mark">仓</span><span class="brand-name">仓库 ↔ 生产车间</span></RouterLink>
      <nav class="header-nav" aria-label="主要功能"><RouterLink to="/production-tasks">生产任务</RouterLink><RouterLink to="/products">产品管理</RouterLink></nav>
      <div class="header-actions"><RouterLink v-if="route.path!=='/'" to="/" class="header-home">首页</RouterLink><button v-if="authenticated" class="header-auth" @click="signOut">退出</button><RouterLink v-else-if="route.path!=='/login'" to="/login" class="header-auth">登录</RouterLink></div>
    </header>
    <main id="main-content" tabindex="-1"><RouterView/></main>
  </div>
</template>
<style scoped>
.app-header{display:grid;grid-template-columns:1fr auto 1fr;gap:28px}.header-nav{display:flex;justify-content:center;gap:24px}.header-nav a{display:flex;align-items:center;min-height:44px;padding:0 8px;color:#174a68;font-weight:650;white-space:nowrap}.header-nav a.router-link-active{box-shadow:inset 0 -3px #174a68}.header-actions{justify-self:end}.header-auth{padding:0 8px!important;margin-left:12px}
@media(max-width:700px){.brand-name{display:none}.app-header{gap:12px;grid-template-columns:auto 1fr auto}.header-nav{gap:8px}.header-nav a{padding:0 5px;font-size:14px}.header-actions{gap:4px!important}.header-home{display:none}.header-auth{margin-left:8px}}
.header-actions{display:flex;align-items:center;gap:14px}.header-auth{border:0;background:none;color:#174a68;font-weight:650;min-height:44px;padding:0;display:flex;align-items:center;cursor:pointer}</style>
