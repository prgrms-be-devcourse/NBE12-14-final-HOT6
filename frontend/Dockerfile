FROM node:22-alpine AS build
WORKDIR /app

COPY package.json package-lock.json ./
RUN npm ci

COPY . .
# next.config.ts 의 rewrites 는 빌드 시점에 확정되므로, 컴포즈 네트워크 안의 백엔드 주소를 인자로 전달한다.
ARG BACKEND_URL=http://app:8080
ENV BACKEND_URL=$BACKEND_URL
# NEXT_PUBLIC_* 값은 빌드 때 번들에 들어가므로 인자로 전달한다. 네이버 지도 Client ID 는 브라우저에 보이는 값이다(Secret 은 넣지 않는다).
ARG NEXT_PUBLIC_NAVER_MAP_CLIENT_ID=
ENV NEXT_PUBLIC_NAVER_MAP_CLIENT_ID=$NEXT_PUBLIC_NAVER_MAP_CLIENT_ID
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
