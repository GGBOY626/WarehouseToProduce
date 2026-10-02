<script setup lang="ts">
import { ref } from "vue";
import { useRoute, useRouter } from "vue-router";
import { login } from "../auth";
import { errorMessage } from "../api/http";

const route = useRoute();
const router = useRouter();
const username = ref("");
const password = ref("");
const submitting = ref(false);
const message = ref("");

async function submit() {
  if (!username.value.trim() || !password.value) {
    message.value = "请输入账号和密码。";
    return;
  }
  submitting.value = true;
  message.value = "";
  try {
    await login(username.value.trim(), password.value);
    const redirect = typeof route.query.redirect === "string" && route.query.redirect.startsWith("/")
      ? route.query.redirect : "/";
    await router.replace(redirect);
  } catch (error) {
    message.value = errorMessage(error);
  } finally {
    submitting.value = false;
  }
}
</script>

<template>
  <div class="page login-page">
    <h1 class="page-title">管理员登录</h1>
    <p class="page-lead">登录后可以新增和维护流转数据。</p>
    <form class="card card-pad stack" @submit.prevent="submit">
      <div v-if="message" class="form-alert" role="alert">{{ message }}</div>
      <div class="field"><label class="required">账号</label><input class="input" v-model="username" autocomplete="username" autofocus /></div>
      <div class="field"><label class="required">密码</label><input class="input" type="password" v-model="password" autocomplete="current-password" /></div>
      <button class="btn btn-primary btn-block" :disabled="submitting">{{ submitting ? "正在登录…" : "登录" }}</button>
    </form>
  </div>
</template>

<style scoped>.login-page{max-width:460px}.form-alert{background:#fdeced;color:#8f1d25;border-radius:8px;padding:10px 12px}</style>
