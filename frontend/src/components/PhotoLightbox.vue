<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, ref, watch } from "vue";

type GalleryPhoto = { url: string; originalName: string };

const props = defineProps<{ photos: GalleryPhoto[] }>();
const currentIndex = ref(-1);
const dialog = ref<HTMLDivElement>();
const closeButton = ref<HTMLButtonElement>();
const isOpen = computed(() => currentIndex.value >= 0);
const activePhoto = computed(() => isOpen.value ? props.photos[currentIndex.value] : undefined);
let opener: HTMLElement | undefined;
let previousOverflow: string | undefined;
let pointerStart: { x: number; y: number } | undefined;

function open(index: number, trigger?: HTMLElement) {
  if (!props.photos.length) return;
  currentIndex.value = Math.max(0, Math.min(index, props.photos.length - 1));
  opener = trigger;
}

function close() {
  currentIndex.value = -1;
}

function previous() {
  if (currentIndex.value > 0) currentIndex.value -= 1;
}

function next() {
  if (currentIndex.value < props.photos.length - 1) currentIndex.value += 1;
}

function restorePage() {
  window.removeEventListener("keydown", onKeydown);
  if (previousOverflow !== undefined) {
    document.body.style.overflow = previousOverflow;
    previousOverflow = undefined;
  }
  if (opener?.isConnected) opener.focus();
  opener = undefined;
}

function onKeydown(event: KeyboardEvent) {
  if (!isOpen.value) return;
  if (event.key === "Escape") {
    event.preventDefault();
    close();
  } else if (event.key === "ArrowLeft") {
    event.preventDefault();
    previous();
  } else if (event.key === "ArrowRight") {
    event.preventDefault();
    next();
  } else if (event.key === "Tab") {
    const buttons = dialog.value?.querySelectorAll<HTMLButtonElement>("button:not(:disabled)");
    if (!buttons?.length) return;
    const first = buttons[0];
    const last = buttons[buttons.length - 1];
    if (event.shiftKey && document.activeElement === first) {
      event.preventDefault();
      last.focus();
    } else if (!event.shiftKey && document.activeElement === last) {
      event.preventDefault();
      first.focus();
    } else if (!dialog.value?.contains(document.activeElement)) {
      event.preventDefault();
      first.focus();
    }
  }
}

function onPointerDown(event: PointerEvent) {
  if (event.pointerType === "touch") pointerStart = { x: event.clientX, y: event.clientY };
}

function onPointerUp(event: PointerEvent) {
  if (!pointerStart) return;
  const deltaX = event.clientX - pointerStart.x;
  const deltaY = event.clientY - pointerStart.y;
  pointerStart = undefined;
  if (Math.abs(deltaX) < 48 || Math.abs(deltaX) < Math.abs(deltaY) * 1.25) return;
  if (deltaX > 0) next();
  else previous();
}

watch(isOpen, async (opened, wasOpen) => {
  if (opened) {
    previousOverflow = document.body.style.overflow;
    document.body.style.overflow = "hidden";
    window.addEventListener("keydown", onKeydown);
    await nextTick();
    closeButton.value?.focus();
  } else if (wasOpen) {
    restorePage();
  }
});

watch(() => props.photos.length, length => {
  if (!length) close();
  else if (currentIndex.value >= length) currentIndex.value = length - 1;
});

onBeforeUnmount(() => {
  if (previousOverflow !== undefined) restorePage();
});

defineExpose({ open, close });
</script>

<template>
  <Teleport to="body">
    <div
      v-if="isOpen && activePhoto"
      ref="dialog"
      class="photo-lightbox"
      role="dialog"
      aria-modal="true"
      :aria-label="`照片查看器：${activePhoto.originalName}`"
      @click.self="close"
    >
      <header class="lightbox-header">
        <span class="lightbox-count" aria-live="polite">{{ currentIndex + 1 }} / {{ photos.length }}</span>
        <span class="lightbox-name" :title="activePhoto.originalName">{{ activePhoto.originalName }}</span>
        <button ref="closeButton" type="button" class="lightbox-close" aria-label="关闭照片" title="关闭" @click="close">×</button>
      </header>
      <div
        class="lightbox-stage"
        @pointerdown="onPointerDown"
        @pointerup="onPointerUp"
        @pointercancel="pointerStart = undefined"
      >
        <button
          v-if="photos.length > 1"
          type="button"
          class="lightbox-nav previous"
          aria-label="上一张照片"
          title="上一张"
          :disabled="currentIndex === 0"
          @click="previous"
        >←</button>
        <img :src="activePhoto.url" :alt="activePhoto.originalName" draggable="false" />
        <button
          v-if="photos.length > 1"
          type="button"
          class="lightbox-nav next"
          aria-label="下一张照片"
          title="下一张"
          :disabled="currentIndex === photos.length - 1"
          @click="next"
        >→</button>
      </div>
    </div>
  </Teleport>
</template>

<style scoped>
.photo-lightbox{position:fixed;inset:0;z-index:2000;background:rgba(8,14,18,.96);color:#fff}
.lightbox-header{position:absolute;inset:env(safe-area-inset-top) 12px auto;display:grid;grid-template-columns:auto minmax(0,1fr) 44px;align-items:center;gap:14px;min-height:56px}
.lightbox-count{font-size:13px;font-variant-numeric:tabular-nums;white-space:nowrap;color:#d5e0e5}
.lightbox-name{overflow:hidden;text-overflow:ellipsis;white-space:nowrap;text-align:center;font-size:13px;color:#e4ebee}
.lightbox-close,.lightbox-nav{display:grid;place-items:center;width:44px;height:44px;padding:0;border:1px solid #ffffff3b;border-radius:50%;background:#ffffff18;color:white;font:inherit;font-size:26px;line-height:1;cursor:pointer}
.lightbox-close:hover,.lightbox-nav:not(:disabled):hover{background:#ffffff30}
.lightbox-close:focus-visible,.lightbox-nav:focus-visible{outline:2px solid #8de0d1;outline-offset:3px}
.lightbox-stage{position:absolute;inset:calc(56px + env(safe-area-inset-top)) 12px calc(16px + env(safe-area-inset-bottom));display:grid;place-items:center;touch-action:none;user-select:none}
.lightbox-stage img{display:block;max-width:100%;max-height:100%;object-fit:contain}
.lightbox-nav{position:absolute;top:50%;z-index:1;transform:translateY(-50%);width:48px;height:56px;border-radius:6px;background:#101a20b8;font-size:30px}
.lightbox-nav.previous{left:0}.lightbox-nav.next{right:0}.lightbox-nav:disabled{opacity:.35;cursor:default}
@media(max-width:520px){.lightbox-header{gap:8px}.lightbox-count{font-size:12px}.lightbox-name{font-size:12px}.lightbox-stage{inset-inline:8px}.lightbox-nav{width:44px;height:52px}}
</style>
