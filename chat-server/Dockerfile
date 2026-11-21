FROM node:20-alpine

WORKDIR /app
ENV NODE_ENV=production

COPY package*.json ./
RUN npm ci --omit=dev

COPY schema.sql ./schema.sql
COPY src ./src

USER node
EXPOSE 10000
CMD ["node", "src/server.js"]
