import { useEffect } from 'react'
import { useThemeStore } from '@/state'

/**
 * Topic initialization:Synchronize the theme to DOM.*/
export function useThemeBootstrap() {
 useEffect(() => {
 useThemeStore.getState().initialize()
 },[])
}
