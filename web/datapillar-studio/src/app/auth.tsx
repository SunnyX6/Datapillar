import { useEffect } from 'react'
import { useAuthStore } from '@/state'

/**
 * Authentication initialization:Restore session state when app starts.*/
export function useAuthBootstrap() {
 useEffect(() => {
 void useAuthStore.getState().initializeAuth()
 },[])
}
