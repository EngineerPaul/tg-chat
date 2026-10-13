/** Конфиг Next.js: чат живёт под /extra/chat, API — соседний префикс. */
const basePath = "/extra/chat";

/** @type {import('next').NextConfig} */
const nextConfig = {
  output: "standalone",
  basePath,
  trailingSlash: false,
};

export default nextConfig;
