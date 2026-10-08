import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import './styles/tokens.css'
import './index.css'
import App from './App.jsx'
import DialogProvider from './components/dialog/DialogProvider.jsx'
// Al final, para que los estilos base sean la versión oficial de las clases compartidas
import './styles/components.css'

createRoot(document.getElementById('root')).render(
  <StrictMode>
    <DialogProvider>
      <App />
    </DialogProvider>
  </StrictMode>,
)
