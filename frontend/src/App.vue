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
      <RouterLink to="/" class="brand" aria-label="返回首页"><span class="brand-mark">仓</span><span>仓库 ↔ 生产车间</span></RouterLink>
      <div class="header-actions"><RouterLink v-if="route.path!=='/'" to="/" class="header-home">首页</RouterLink><button v-if="authenticated" class="header-auth" @click="signOut">退出</button><RouterLink v-else-if="route.path!=='/login'" to="/login" class="header-auth">登录</RouterLink></div>
    </header>
    <main id="main-content" tabindex="-1"><RouterView/></main>
  </div>
</template>
<style scoped>.header-actions{display:flex;align-items:center;gap:14px}.header-auth{border:0;background:none;color:#174a68;font-weight:650;min-height:44px;padding:0;display:flex;align-items:center;cursor:pointer}</style>
