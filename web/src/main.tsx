import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { BrowserRouter } from 'react-router-dom'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import './styles/prototype.css'
import './styles/additions.css'
import App from './App'
import { ThemeProvider } from './app/ThemeProvider'
import { ToastProvider } from './app/ToastProvider'
import { ModalProvider } from './app/ModalProvider'
import { AuthProvider } from './app/AuthProvider'

const queryClient = new QueryClient({
  defaultOptions: {
    queries: { refetchOnWindowFocus: false, retry: 1, staleTime: 10_000 },
  },
})

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <QueryClientProvider client={queryClient}>
      <BrowserRouter>
        <ThemeProvider>
          <ToastProvider>
            <ModalProvider>
              <AuthProvider>
                <App />
              </AuthProvider>
            </ModalProvider>
          </ToastProvider>
        </ThemeProvider>
      </BrowserRouter>
    </QueryClientProvider>
  </StrictMode>,
)
