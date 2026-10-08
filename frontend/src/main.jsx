import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import './styles/tokens.css'
import './index.css'
import App from './App.jsx'
import DialogProvider from './components/dialog/DialogProvider.jsx'
// Al final, para que los estilos base sean la versión oficial de las clases compartidas
import './styles/components.css'
import './styles/app.css'

// Tema elegido por la persona (claro u oscuro); si no hay, sigue al sistema
try {
  const tema = localStorage.getItem('sigac-tema')
  if (tema) document.documentElement.setAttribute('data-theme', tema)
} catch {
  /* sin almacenamiento: se usa el tema del sistema */
}

createRoot(document.getElementById('root')).render(
  <StrictMode>
    <DialogProvider>
      <App />
    </DialogProvider>
  </StrictMode>,
)
