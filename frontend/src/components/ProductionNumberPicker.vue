<script setup lang="ts">
import { computed, useId } from 'vue';
const props = defineProps<{ modelValue?: string | null; numbers?: string[]; allowUnspecified?: boolean }>();
const emit = defineEmits<{ 'update:modelValue': [value: string] }>();
const id = useId();
const options = computed(() => [...new Set(props.numbers || [])]);
</script>
<template>
  <div class="field">
    <label :for="id" :class="{ required: options.length > 1 && !allowUnspecified }">生产编号</label>
    <input :id="id" class="input mono" :list="`${id}-numbers`" :value="modelValue || ''" maxlength="100"
      :required="options.length > 1 && !allowUnspecified" placeholder="选择已有编号或输入新编号（可选）"
      @input="emit('update:modelValue', ($event.target as HTMLInputElement).value.trim())" />
    <datalist :id="`${id}-numbers`"><option v-for="number in options" :key="number" :value="number" /></datalist>
    <small v-if="allowUnspecified" class="hint">这条历史记录未填写生产编号，留空可保留原记录。</small>
    <small v-else-if="options.length > 1" class="hint">有 {{ options.length }} 个生产编号，请选择本次实际使用的编号。</small>
  </div>
</template>
