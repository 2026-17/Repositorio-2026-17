FROM nginx:stable-alpine

COPY default.conf /etc/nginx/conf.d/default.conf
COPY start.sh /start.sh

RUN sed -i 's/\r$//' /start.sh

EXPOSE 8080

CMD ["sh", "/start.sh"]