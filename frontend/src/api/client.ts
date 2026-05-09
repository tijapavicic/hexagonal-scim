import axios from 'axios';
import kc from '../keycloak';

/**
 * Axios instance configured to attach the Keycloak Bearer token on every request.
 * baseURL is empty so that Vite's dev proxy (/ → localhost:8080) handles routing.
 * In Docker the Nginx reverse proxy handles the same forwarding.
 */
const apiClient = axios.create({
  headers: { 'Content-Type': 'application/json' },
});

apiClient.interceptors.request.use((config) => {
  if (kc.token) {
    config.headers.Authorization = `Bearer ${kc.token}`;
  }
  return config;
});

export default apiClient;

