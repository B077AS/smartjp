<script setup lang="ts" generic="V extends string">
// A row of exclusive choices (Reka UI ToggleGroup, single). A setting must always have a value, and Reka reports
// "nothing" when the active item is clicked again, so that case is ignored.
import { ToggleGroupItem, ToggleGroupRoot } from 'reka-ui'
import type { UiOption } from './types'

defineProps<{ modelValue: V; options: UiOption<V>[]; ariaLabel?: string }>()
const emit = defineEmits<{ 'update:modelValue': [value: V] }>()

function change(value: unknown): void {
  if (typeof value === 'string' && value) emit('update:modelValue', value as V)
}
</script>

<template>
  <ToggleGroupRoot class="ui-seg" type="single" :model-value="modelValue" :aria-label="ariaLabel" @update:model-value="change">
    <ToggleGroupItem v-for="option in options" :key="option.value" :value="option.value" class="ui-seg-item">
      {{ option.label }}
    </ToggleGroupItem>
  </ToggleGroupRoot>
</template>
