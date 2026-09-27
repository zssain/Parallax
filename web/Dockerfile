# Build the React app and serve it from nginx, which also proxies the API exactly
# like the Vite dev proxy (see web/nginx.conf). Build context is ./web.
FROM node:20 AS build
WORKDIR /web
COPY package.json package-lock.json ./
# --legacy-peer-deps: the codegen-only openapi-typescript@7 declares peer typescript@^5
# while the project is on typescript 6; it is not in the build path.
RUN npm ci --legacy-peer-deps
COPY . .
RUN npm run build

FROM nginx:alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /web/dist /usr/share/nginx/html
EXPOSE 80
