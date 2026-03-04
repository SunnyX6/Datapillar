import { Navigate,Outlet } from 'react-router-dom'
import { useAuthStore } from '@/state'

const LOGIN_PATH = '/login'

/**
 * Authentication guard:Only responsible for determining login status.*/
export function AuthGate() {
 const loading = useAuthStore((state) => state.loading)
 const isAuthenticated = useAuthStore((state) => state.isAuthenticated)

 if (loading) {
 return null
 }

 if (!isAuthenticated) {
 return <Navigate to={LOGIN_PATH} replace />
 }

 return <Outlet />
}
