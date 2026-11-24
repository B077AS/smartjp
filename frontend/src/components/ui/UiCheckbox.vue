<script setup lang="ts">
// Our own checkbox (Reka UI Checkbox). modelValue is true, false or 'indeterminate' (some, not all).
// Clicking an indeterminate box makes it checked, as users expect from a "select all" box.
import { useId } from 'vue'
import { CheckboxIndicator, CheckboxRoot } from 'reka-ui'
import { mdiCheck, mdiMinus } from '@mdi/js'
import Icon from '../Icon.vue'

withDefaults(defineProps<{ modelValue?: boolean | 'indeterminate' }>(), { modelValue: false })
const emit = defineEmits<{ 'update:modelValue': [value: boolean | 'indeterminate'] }>()
const id = useId()
</script>

<template>
  <span class="ui-check">
    <CheckboxRoot :id="id" class="ui-check-box" :model-value="modelValue" @update:model-value="emit('update:modelValue', $event)">
      <CheckboxIndicator class="ui-check-mark">
        <Icon :path="modelValue === 'indeterminate' ? mdiMinus : mdiCheck" :size="16" />
      </CheckboxIndicator>
    </CheckboxRoot>
    <label :for="id" class="ui-check-label"><slot /></label>
  </span>
</template>
