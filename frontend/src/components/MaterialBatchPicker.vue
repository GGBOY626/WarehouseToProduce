<script setup lang="ts">
import { computed, useId } from 'vue';
const props = defineProps<{ modelValue?: string | null; batches?: string[]; required?: boolean; allowUnspecified?: boolean }>();
const emit = defineEmits<{ 'update:modelValue': [value: string] }>();
const id = useId();
const options = computed(() => [...new Set(props.batches || [])]);
</script>
<template>
  <div class="field">
    <label :for="id" :class="{required:required || (!allowUnspecified && options.length > 1)}">物料批次</label>
    <input :id="id" class="input mono" :list="`${id}-batches`" :value="modelValue || ''" maxlength="100"
      :required="required || (!allowUnspecified && options.length > 1)" placeholder="选择已有批次或输入新批次"
      @input="emit('update:modelValue', ($event.target as HTMLInputElement).value.trim())" />
    <datalist :id="`${id}-batches`"><option v-for="batch in options" :key="batch" :value="batch" /></datalist>
    <small v-if="allowUnspecified" class="hint">旧任务未限定物料批次，留空可保持原有统计口径。</small>
    <small v-else-if="options.length > 1" class="hint">有 {{ options.length }} 个批次，请选择本次实际使用的批次。</small>
  </div>
</template>
