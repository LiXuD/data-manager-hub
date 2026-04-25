import { THEME_MODE, STORAGE_KEYS } from '@/constants'

type ThemeMode = typeof THEME_MODE[keyof typeof THEME_MODE]

export const applyTheme = (theme: ThemeMode) => {
  const body = document.body

  if (theme === THEME_MODE.AUTO) {
    body.classList.remove('light-theme')
    const prefersDark = window.matchMedia('(prefers-color-scheme: dark)').matches
    if (!prefersDark) {
      body.classList.add('light-theme')
    }
  } else if (theme === THEME_MODE.LIGHT) {
    body.classList.add('light-theme')
  } else {
    body.classList.remove('light-theme')
  }
}

export const getStoredTheme = (): ThemeMode => {
  return (localStorage.getItem(STORAGE_KEYS.THEME) as ThemeMode) || THEME_MODE.DARK
}

export const setStoredTheme = (theme: ThemeMode) => {
  localStorage.setItem(STORAGE_KEYS.THEME, theme)
}
