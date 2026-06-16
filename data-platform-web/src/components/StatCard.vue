<template>
  <div class="stat-card" :class="variant">
    <div class="stat-icon" :class="variant">
      <component :is="iconComponent" v-if="iconComponent" />
      <svg v-else viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
        <slot name="icon">
          <path d="M12 2v4m0 12v4M4.93 4.93l2.83 2.83m8.48 8.48l2.83 2.83M2 12h4m12 0h4M4.93 19.07l2.83-2.83m8.48-8.48l2.83-2.83"/>
        </slot>
      </svg>
    </div>
    <div class="stat-info">
      <div class="stat-label">{{ label }}</div>
      <div class="stat-value" :class="variant">{{ formattedValue }}</div>
      <div v-if="trend" class="stat-trend" :class="trendDirection">{{ trend }}</div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, type Component } from 'vue'
import { formatNumber } from '@/utils/format'

type StatVariant = 'default' | 'primary' | 'success' | 'warning' | 'danger' | 'info'
type TrendDirection = 'up' | 'down' | ''

interface Props {
  label: string
  value: string | number
  variant?: StatVariant
  iconComponent?: Component
  trend?: string
  trendDirection?: TrendDirection
  prefix?: string
  suffix?: string
}

const props = withDefaults(defineProps<Props>(), {
  variant: 'default',
  trendDirection: ''
})

const formattedValue = computed(() => {
  if (typeof props.value === 'number') {
    const formatted = formatNumber(props.value)
    return `${props.prefix || ''}${formatted}${props.suffix || ''}`
  }
  return props.value
})
</script>

<style scoped>
.stat-card {
  background: var(--color-bg-light, #fff);
  border-radius: 12px;
  padding: 20px;
  display: flex;
  align-items: center;
  gap: 16px;
  border: 1px solid var(--color-border-light, #eee);
  transition: transform 0.2s, box-shadow 0.2s;
}

.stat-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.08);
}

.stat-icon {
  width: 48px;
  height: 48px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  background: linear-gradient(135deg, #6366f1, #8b5cf6);
}

.stat-icon svg {
  width: 24px;
  height: 24px;
  color: white;
}

.stat-icon.success { background: linear-gradient(135deg, #10b981, #34d399); }
.stat-icon.warning { background: linear-gradient(135deg, #f59e0b, #fbbf24); }
.stat-icon.danger { background: linear-gradient(135deg, #ef4444, #f87171); }
.stat-icon.info { background: linear-gradient(135deg, #06b6d4, #22d3ee); }
.stat-icon.primary { background: linear-gradient(135deg, #6366f1, #8b5cf6); }

.stat-info {
  flex: 1;
  min-width: 0;
}

.stat-label {
  font-size: 13px;
  color: var(--color-text-tertiary, #999);
  margin-bottom: 4px;
}

.stat-value {
  font-size: 24px;
  font-weight: 700;
  color: var(--color-text-primary, #333);
  font-variant-numeric: tabular-nums;
}

.stat-value.success { color: #10b981; }
.stat-value.danger { color: #ef4444; }
.stat-value.warning { color: #f59e0b; }

.stat-trend {
  font-size: 12px;
  margin-top: 4px;
}

.stat-trend.up { color: #10b981; }
.stat-trend.down { color: #ef4444; }
</style>
