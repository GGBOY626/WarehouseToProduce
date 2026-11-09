<script setup lang="ts">
import {onMounted,onBeforeUnmount,ref,watch} from 'vue';
import {useRoute,useRouter} from 'vue-router';
import {refreshAuth,logout,useAuth} from './auth';

const authError=ref('');
const route=useRoute();
const router=useRouter();
const {authenticated}=useAuth();
const moreMenu=ref<HTMLDetailsElement>();
function closeMore(){if(moreMenu.value)moreMenu.value.open=false}
function closeMenus(event:MouseEvent){
  document.querySelectorAll<HTMLDetailsElement>('.product-menu[open]').forEach(menu=>{if(!menu.contains(event.target as Node))menu.open=false});
  if(moreMenu.value?.open && !moreMenu.value.contains(event.target as Node))closeMore();
}
watch(()=>route.fullPath,closeMore);
onMounted(()=>{void refreshAuth();document.addEventListener('click',closeMenus)});
onBeforeUnmount(()=>document.removeEventListener('click',closeMenus));
async function signOut(){closeMore();try{await logout();await router.replace('/')}catch{authError.value='退出失败，请重试。'}}
const current=(path:string)=>path==='/' ? route.path==='/'||route.path.startsWith('/movements') : route.path.startsWith(path);
</script>
<template>
  <div class="app-shell">
    <a class="skip-link" href="#main-content">跳到主要内容</a>
    <header class="app-header">
      <RouterLink to="/" class="brand" aria-label="返回首页"><span class="brand-mark">仓</span><span class="brand-name">仓库 ↔ 生产车间</span></RouterLink>
      <nav class="header-nav" aria-label="主要功能"><RouterLink to="/" :class="{current:current('/')}">流转记录</RouterLink><RouterLink to="/production-tasks">生产任务</RouterLink><RouterLink to="/products">产品管理</RouterLink><RouterLink to="/warehouse">仓库管理</RouterLink></nav>
      <nav class="header-actions" aria-label="管理与账户"><RouterLink to="/reports">导出 PDF</RouterLink><button v-if="authenticated" type="button" @click="signOut">退出登录</button><RouterLink v-else to="/login">登录</RouterLink></nav>
      <details ref="moreMenu" class="mobile-more" @keydown.esc="closeMore"><summary aria-label="更多功能"><svg aria-hidden="true" viewBox="0 0 24 24"><path d="M5 7h14M5 12h14M5 17h14" /></svg></summary><nav aria-label="更多功能"><RouterLink v-if="authenticated" to="/persons" @click="closeMore">人员管理</RouterLink></nav></details>
    </header>
    <p v-if="authError" class="toast" role="alert">{{authError}}</p>
    <main id="main-content" tabindex="-1"><RouterView/></main>
    <nav class="bottom-nav" aria-label="主要功能">
      <RouterLink to="/" :class="{current:current('/')}" :aria-current="current('/')?'page':undefined"><svg aria-hidden="true" viewBox="0 0 24 24"><path d="M4 7h13m0 0-3-3m3 3-3 3M20 17H7m0 0 3-3m-3 3 3 3" /></svg><span>流转记录</span></RouterLink>
      <RouterLink to="/production-tasks" :class="{current:current('/production-tasks')}" :aria-current="current('/production-tasks')?'page':undefined"><svg aria-hidden="true" viewBox="0 0 24 24"><path d="M5 4h14v16H5zM9 9h6M9 13h6M9 17h4" /></svg><span>生产任务</span></RouterLink>
      <RouterLink to="/products" :class="{current:current('/products')}" :aria-current="current('/products')?'page':undefined"><svg aria-hidden="true" viewBox="0 0 24 24"><path d="m12 3 8 4.5v9L12 21l-8-4.5v-9L12 3Zm-8 4.5L12 12l8-4.5M12 12v9" /></svg><span>产品管理</span></RouterLink>
      <RouterLink to="/warehouse" :class="{current:current('/warehouse')}" :aria-current="current('/warehouse')?'page':undefined"><svg aria-hidden="true" viewBox="0 0 24 24"><path d="M3 20V7l9-4 9 4v13M3 10h18M7 20v-6h10v6M12 14v6" /></svg><span>仓库管理</span></RouterLink>
    </nav>
  </div>
</template>
<style scoped>
.app-header{gap:18px}.header-nav,.header-actions{display:flex;align-items:center;gap:4px}.header-nav{margin:auto}.header-nav a,.header-actions a,.header-actions button{display:inline-flex;align-items:center;min-height:44px;padding:0 9px;white-space:nowrap;font-size:13px;font-weight:650}.header-nav a{color:var(--muted)}.header-nav a.current,.header-nav a.router-link-active:not([href="/"]){color:var(--blue);box-shadow:inset 0 -3px var(--blue)}.header-actions a,.header-actions button{color:var(--blue);background:none;border:0;cursor:pointer}.mobile-more,.bottom-nav{display:none}
@media(max-width:1100px) and (min-width:701px){.brand-name{display:none}.app-header{gap:8px}.header-nav a,.header-actions a,.header-actions button{padding-inline:6px}}
@media(max-width:700px){
  .app-header{height:auto;min-height:98px;padding:8px max(12px,env(safe-area-inset-right)) 0 max(12px,env(safe-area-inset-left));gap:4px 8px;flex-wrap:wrap}.brand{font-size:14px;min-width:0}.brand-name{overflow:hidden;text-overflow:ellipsis;white-space:nowrap}.brand-mark{width:29px;height:29px;flex-shrink:0}.header-nav{display:none}.header-actions{display:flex;order:2;justify-content:space-around;gap:8px;width:100%;min-height:42px;border-top:1px solid var(--line)}.header-actions a,.header-actions button{justify-content:center;flex:1;min-width:0;min-height:42px;padding:0 8px;font-size:14px}.mobile-more{display:block;position:relative;margin-left:auto;flex-shrink:0}.mobile-more summary{display:grid;place-items:center;width:44px;height:44px;list-style:none;border-radius:8px;color:var(--blue);cursor:pointer}.mobile-more summary::-webkit-details-marker{display:none}.mobile-more[open] summary{background:var(--soft-blue)}.mobile-more svg,.bottom-nav svg{width:22px;height:22px;fill:none;stroke:currentColor;stroke-width:1.8;stroke-linecap:round;stroke-linejoin:round}.mobile-more nav{position:absolute;top:48px;right:0;width:170px;padding:5px;background:var(--panel);border:1px solid var(--line);border-radius:8px;box-shadow:0 8px 22px #17212624}.mobile-more nav a,.mobile-more nav button{display:flex;align-items:center;width:100%;min-height:44px;padding:9px 12px;border:0;border-radius:6px;background:none;color:var(--ink);font-size:13px;text-align:left;cursor:pointer}.mobile-more nav a:hover,.mobile-more nav button:hover,.mobile-more nav a.router-link-active{background:var(--soft-blue);color:var(--blue)}
  .bottom-nav{position:fixed;display:grid;grid-template-columns:repeat(4,minmax(0,1fr));left:0;right:0;bottom:0;z-index:35;min-height:calc(62px + env(safe-area-inset-bottom));padding:0 max(4px,env(safe-area-inset-right)) env(safe-area-inset-bottom) max(4px,env(safe-area-inset-left));background:var(--panel);border-top:1px solid var(--line);box-shadow:0 -3px 12px #1721260d}.bottom-nav a{position:relative;display:flex;flex-direction:column;justify-content:center;align-items:center;gap:3px;min-width:0;min-height:62px;padding:5px 2px;color:var(--muted);font-size:11px;font-weight:650;white-space:nowrap}.bottom-nav a.current{color:var(--blue);background:var(--soft-blue)}.bottom-nav a.current::before{content:"";position:absolute;left:20%;right:20%;top:0;height:3px;border-radius:0 0 3px 3px;background:var(--blue)}.bottom-nav a:focus-visible,.mobile-more summary:focus-visible{outline:2px solid var(--blue2);outline-offset:-3px}
}
</style>
