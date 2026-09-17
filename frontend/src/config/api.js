export const API_URL = import.meta.env.MODE === "development"
    ? "http://localhost:8080"
    : "/api";
