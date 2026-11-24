<script setup lang="ts" generic="V extends string | number">
// Our own dropdown (Reka UI Select, styled in styles/ui.css): the browser's <select> looks different in every
// web view. Values may be numbers, Reka wants strings, so they are converted here.
import { computed } from 'vue'
import {
  SelectContent,
  SelectItem,
  SelectItemIndicator,
  SelectItemText,
  SelectPortal,
  SelectRoot,
  SelectTrigger,
  SelectViewport,
} from 'reka-ui'
import { mdiCheck, mdiChevronDown } from '@mdi/js'
import Icon from '../Icon.vue'
import type { UiOption } from './types'

const props = defineProps<{ modelValue: V; options: UiOption<V>[]; id?: string }>()
const emit = defineEmits<{ 'update:modelValue': [value: V] }>()

const currentLabel = computed(() => props.options.find((o) => String(o.value) === String(props.modelValue))?.label ?? '')

function pick(value: unknown): void {
  const option = props.options.find((o) => String(o.value) === value)
  if (option) emit('update:modelValue', option.value)
}
</script>

<template>
  <SelectRoot :model-value="String(modelValue)" @update:model-value="pick">
    <SelectTrigger :id="id" class="ui-select">
      <span class="ui-select-value">{{ currentLabel }}</span>
      <Icon class="ui-select-chevron" :path="mdiChevronDown" :size="18" />
    </SelectTrigger>
    <SelectPortal>
      <!-- :body-lock="false": by default Reka hides the page scrollbar while the list is open, and the page jumps sideways -->
      <SelectContent class="ui-select-content" position="popper" :side-offset="4" :body-lock="false">
        <SelectViewport>
          <SelectItem v-for="option in options" :key="option.value" :value="String(option.value)" class="ui-select-item">
            <SelectItemText>{{ option.label }}</SelectItemText>
            <SelectItemIndicator class="ui-select-check">
              <Icon :path="mdiCheck" :size="16" />
            </SelectItemIndicator>
          </SelectItem>
        </SelectViewport>
      </SelectContent>
    </SelectPortal>
  </SelectRoot>
</template>
