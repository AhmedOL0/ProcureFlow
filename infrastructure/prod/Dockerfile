# Production-shape TLS edge (nginx + openssl for first-boot snakeoil).
FROM nginx:1.27-alpine
RUN apk add --no-cache openssl
COPY edge.conf /etc/nginx/conf.d/default.conf
COPY entrypoint.sh /entrypoint.sh
RUN chmod +x /entrypoint.sh \
  && mkdir -p /tls \
  && chown nginx:nginx /tls
# 443 needs privilege; the master keeps root while workers drop to nginx
# (default image behavior, kept deliberately for the TLS socket).
EXPOSE 443
ENTRYPOINT ["/entrypoint.sh"]
