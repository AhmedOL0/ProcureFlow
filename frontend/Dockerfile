# ProcureFlow frontend — production image (Angular build + nginx SPA server).
FROM node:22-alpine AS build
WORKDIR /app
COPY package*.json ./
RUN npm ci
COPY . ./
# The API base is baked at build time (Angular has no runtime env).
# Compose passes ANGULAR_API_URL; plain `docker build` keeps localhost:8080.
ARG ANGULAR_API_URL=http://localhost:8080
RUN sed -i "s|http://localhost:8080|${ANGULAR_API_URL}|g" src/environments/environment.ts \
  && sed -i "s|__ANGULAR_API_URL__|${ANGULAR_API_URL}|g" nginx.conf \
  && npm run build

FROM nginx:stable-alpine
COPY --from=build /app/nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist/frontend/browser /usr/share/nginx/html
# Run as the unprivileged nginx user: own the cache, pid, log and content
# paths it must write at runtime (config itself stays root-owned).
RUN mkdir -p /var/cache/nginx /var/log/nginx \
  && chown -R nginx:nginx /var/cache/nginx /var/log/nginx \
    /usr/share/nginx/html /etc/nginx/conf.d \
  && sed -i 's|/run/nginx.pid|/var/cache/nginx/nginx.pid|' /etc/nginx/nginx.conf
USER nginx
EXPOSE 8080
