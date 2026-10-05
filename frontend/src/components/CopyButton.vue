<script setup lang="ts">
import { onBeforeUnmount, ref } from "vue";

const props = defineProps<{ value: string; label: string }>();
const feedback = ref("");
const copying = ref(false);
let timer: ReturnType<typeof setTimeout> | undefined;

function copyFallback(value: string) {
  const previousFocus = document.activeElement as HTMLElement | null;
  const field = document.createElement("textarea");
  field.value = value;
  field.readOnly = true;
  field.style.position = "fixed";
  field.style.opacity = "0";
  document.body.appendChild(field);
  try {
    field.select();
    field.setSelectionRange(0, value.length);
    if (!document.execCommand("copy")) throw new Error("Copy failed");
  } finally {
    field.remove();
    previousFocus?.focus({ preventScroll: true });
  }
}

async function copy() {
  copying.value = true;
  clearTimeout(timer);
  try {
    if (navigator.clipboard?.writeText) {
      try {
        await navigator.clipboard.writeText(props.value);
      } catch {
        copyFallback(props.value);
      }
    } else {
      copyFallback(props.value);
    }
    feedback.value = "已复制";
  } catch {
    feedback.value = "复制失败，请长按或选中文字复制";
  } finally {
    copying.value = false;
    timer = setTimeout(() => { feedback.value = ""; }, 3000);
  }
}
onBeforeUnmount(() => clearTimeout(timer));
</script>

<template>
  <span class="copy-control">
    <button type="button" :disabled="copying || !value" :aria-label="`复制${label}`" @click="copy">复制</button>
    <span class="copy-feedback" role="status">{{ feedback }}</span>
  </span>
</template>

<style scoped>
.copy-control { display: inline-flex; align-items: center; gap: 6px; font-family: inherit; }
button { min-height: 36px; padding: 4px 10px; border: 1px solid #b9c7ce; border-radius: 6px; background: #f3f7f8; color: #174a68; font: inherit; font-size: 12px; cursor: pointer; white-space: nowrap; }
button:hover { background: #e3eef2; }
button:focus-visible { outline: 3px solid #267566; outline-offset: 2px; }
button:disabled { cursor: default; opacity: .6; }
.copy-feedback { font-size: 12px; }
.copy-feedback:not(:empty) { max-width: 220px; }
</style>
