// Ruta relativa siempre: en dev el proxy de Vite la reenvía a
// http://localhost:8080 (quitando /api) y en producción lo hace Nginx.
// Así el navegador nunca hace una petición cross-origin y CORS no aplica.
export const API_URL = "/api";
