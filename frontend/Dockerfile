FROM node:20-alpine AS build
WORKDIR /app
# Copiamos todo, incluyendo el node_modules generado en Windows y el esbuild-linux.tgz
COPY . .
# Extraemos el binario de Linux manualmente en la carpeta de dependencias donde Angular espera encontrarlo
RUN mkdir -p node_modules/@esbuild/linux-x64 && tar -xzf esbuild-linux.tgz -C node_modules/@esbuild/linux-x64 --strip-components=1
# Usamos node directamente para llamar a ng.js porque el .bin/ng copiado de Windows no funciona en Linux
RUN node node_modules/@angular/cli/bin/ng.js build

FROM nginx:alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist/frontend/browser /usr/share/nginx/html
EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]
