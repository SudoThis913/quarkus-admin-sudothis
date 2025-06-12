FROM certbot/dns-cloudflare

COPY entrypoint.sh /entrypoint.sh
RUN chmod +x /entrypoint.sh \
 && ls -lh /entrypoint.sh \
 && head -n 1 /entrypoint.sh

ENTRYPOINT ["/entrypoint.sh"]
