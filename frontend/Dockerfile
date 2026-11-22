FROM node:22-alpine AS build
WORKDIR /app

COPY package.json package-lock.json ./
RUN npm ci

COPY . .
# next.config.ts 의 rewrites 는 빌드 시점에 확정되므로, 컴포즈 네트워크 안의 백엔드 주소를 인자로 전달한다.
ARG BACKEND_URL=http://app:8080
ENV BACKEND_URL=$BACKEND_URL
RUN npm run build

FROM node:22-alpine
WORKDIR /app
ENV NODE_ENV=production

COPY --from=build /app/package.json /app/package-lock.json ./
COPY --from=build /app/node_modules node_modules
COPY --from=build /app/.next .next
COPY --from=build /app/public public
COPY --from=build /app/next.config.ts ./

EXPOSE 3000
CMD ["npm", "start"]
