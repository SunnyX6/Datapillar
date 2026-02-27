import { createBrowserRouter } from 'react-router-dom'
import { buildRoutes } from './buildRoutes'

export const router = createBrowserRouter(buildRoutes())
