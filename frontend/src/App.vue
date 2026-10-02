<script setup lang="ts">
import {onBeforeUnmount,onMounted,ref} from 'vue';
import {useRoute} from 'vue-router';
const route=useRoute();
const uploadNotice=ref('');let hideTimer:number|undefined;
function queueChanged(event:Event){const {status,pending,message}=(event as CustomEvent).detail;window.clearTimeout(hideTimer);if(status==='failed'){uploadNotice.value=`照片上传失败：${message||'请进入记录详情重新上传'}`;hideTimer=window.setTimeout(()=>uploadNotice.value='',8000)}else if(status==='complete'&&pending===0){uploadNotice.value='照片已上传完成';hideTimer=window.setTimeout(()=>uploadNotice.value='',3000)}else if(status==='retrying')uploadNotice.value=`网络不稳定，${pending} 张照片等待重试`;else if(pending>0)uploadNotice.value=`记录已保存，${pending} 张照片正在上传`}
onMounted(()=>window.addEventListener('photo-upload-queue',queueChanged));
onBeforeUnmount(()=>window.removeEventListener('photo-upload-queue',queueChanged));
</script>
<template>
  <div class="app-shell">
    <a class="skip-link" href="#main-content">跳到主要内容</a>
    <header class="app-header">
      <RouterLink to="/" class="brand" aria-label="返回首页"><span class="brand-mark">仓</span><span>仓库 ↔ 生产车间</span></RouterLink>
      <RouterLink v-if="route.path!=='/'" to="/" class="header-home">首页</RouterLink>
    </header>
    <main id="main-content" tabindex="-1"><RouterView/></main>
    <div v-if="uploadNotice" class="toast" role="status">{{uploadNotice}}</div>
  </div>
</template>
