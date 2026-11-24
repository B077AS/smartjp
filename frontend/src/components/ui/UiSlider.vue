<script setup lang="ts">
// Our own slider (Reka UI Slider, one thumb). `ticks` draws a small mark at each of those values. Keyboard: the arrow keys move by `step`.
import { computed } from 'vue'
import { SliderRange, SliderRoot, SliderThumb, SliderTrack } from 'reka-ui'

const props = withDefaults(defineProps<{ modelValue: number; min?: number; max: number; step?: number; ticks?: number[]; ariaLabel?: string }>(), {
  min: 0,
  step: 1,
  ticks: () => [],
})
const emit = defineEmits<{ 'update:modelValue': [value: number] }>()

const value = computed(() => [props.modelValue])
const at = (tick: number): string => ((tick - props.min) / (props.max - props.min)) * 100 + '%'

function change(next: number[] | undefined): void {
  if (next && next.length > 0) emit('update:modelValue', next[0])
}
</script>

<template>
  <SliderRoot class="ui-slider" :model-value="value" :min="min" :max="max" :step="step" @update:model-value="change">
    <SliderTrack class="ui-slider-track">
      <SliderRange class="ui-slider-range" />
      <span v-for="tick in ticks" :key="tick" class="ui-slider-tick" :style="{ left: at(tick) }" aria-hidden="true"></span>
    </SliderTrack>
    <SliderThumb class="ui-slider-thumb" :aria-label="ariaLabel" />
  </SliderRoot>
</template>
