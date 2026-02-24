import { createBrowserRouter } from 'react-router-dom'
import { appRoutes } from './routes/app.routes'
import { publicRoutes } from './routes/public.routes'
import { setupRoutes } from './routes/setup.routes'

export const router = createBrowserRouter([
  ...setupRoutes,
  ...appRoutes,
  ...publicRoutes,
])
