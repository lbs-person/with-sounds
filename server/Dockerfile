FROM node:22-alpine

WORKDIR /app
COPY server.js package.json ./

ENV PORT=8080
ENV DATA_DIR=/data
ENV SERVER_NAME=同频电台

RUN mkdir -p /data
VOLUME ["/data"]
EXPOSE 8080

CMD ["node", "server.js"]
