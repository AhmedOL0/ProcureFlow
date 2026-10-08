// Production environment values. Only public, non-secret configuration
// belongs here; the API base URL is injected at Docker build time.
export const environment = {
  production: true,
  apiUrl: 'http://localhost:8080',
};
