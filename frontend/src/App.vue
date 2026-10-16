<script setup lang="ts">
import {onMounted,onBeforeUnmount,ref} from 'vue';
import {useRoute} from 'vue-router';
import {useRouter} from 'vue-router';
import {refreshAuth,logout,useAuth} from './auth';

const authError=ref('');
const route=useRoute();
const router=useRouter();
const {authenticated}=useAuth();
function closeMenus(event:MouseEvent){document.querySelectorAll<HTMLDetailsElement>('.product-menu[open]').forEach(menu=>{if(!menu.contains(event.target as Node))menu.open=false})}
onMounted(()=>{void refreshAuth();document.addEventListener('click',closeMenus)});
onBeforeUnmount(()=>document.removeEventListener('click',closeMenus));
async function signOut(){try{await logout();await router.replace('/')}catch{authError.value='退出失败，请重试。'}}
</script>
<template>
  <div class="app-shell">
    <a class="skip-link" href="#main-content">跳到主要内容</a>
    <header class="app-header">
      <RouterLink to="/" class="brand" aria-label="返回首页"><span class="brand-mark">仓</span><span class="brand-name">仓库 ↔ 生产车间</span></RouterLink>
      <nav class="header-nav" aria-label="主要功能"><RouterLink to="/" :class="{current:route.path==='/'}">流转记录</RouterLink><RouterLink to="/production-tasks">生产任务</RouterLink><RouterLink to="/products">产品管理</RouterLink></nav>
      <nav class="header-actions" aria-label="管理与账户"><RouterLink to="/reports">导出 PDF</RouterLink><RouterLink v-if="authenticated" to="/persons">人员管理</RouterLink><button v-if="authenticated" @click="signOut">退出登录</button><RouterLink v-else to="/login">登录管理</RouterLink></nav>
    </header>
    <p v-if="authError" class="toast" role="alert">{{authError}}</p>
    <main id="main-content" tabindex="-1"><RouterView/></main>
  </div>
</template>
<style scoped>
.app-header{display:grid;grid-template-columns:1fr auto 1fr;gap:20px}.header-nav{display:flex;justify-content:center;gap:16px}.header-nav a{display:flex;align-items:center;min-height:44px;padding:0 8px;color:var(--muted);font-weight:650;white-space:nowrap;font-size:14px}.header-nav a.router-link-active:not([href="/"]),.header-nav a.current{color:var(--blue);box-shadow:inset 0 -3px var(--blue)}@media(max-width:700px){.app-header{grid-template-columns:1fr auto;gap:4px}.brand{display:none}.header-nav{justify-content:space-between;gap:0}.header-nav a{padding:0 8px;font-size:13px}}
.app-header{grid-template-columns:auto 1fr auto;gap:12px}.header-actions{display:flex;flex-wrap:wrap;justify-content:flex-end;gap:8px}.header-actions a,.header-actions button{display:flex;align-items:center;min-height:44px;padding:0 8px;font:inherit;font-size:13px;color:var(--blue);background:none;border:0;cursor:pointer;white-space:nowrap}@media(max-width:900px){.app-header{display:flex;flex-wrap:wrap;justify-content:space-between}.brand{display:none}.header-nav{flex-wrap:wrap;gap:4px}.header-actions{gap:4px}}
</style>
