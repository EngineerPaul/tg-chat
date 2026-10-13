/**
 * Сжатие фото на клиенте и gzip файлов, если выигрыш ≥ 10%.
 */

/**
 * Сжать изображение в JPEG 1280px, качество 80, без EXIF.
 * @param file исходный файл
 */
export async function compressPhoto(file: File): Promise<Blob> {
  const bitmap = await createImageBitmap(file);
  const scale = Math.min(1, 1280 / Math.max(bitmap.width, bitmap.height));
  const w = Math.round(bitmap.width * scale);
  const h = Math.round(bitmap.height * scale);
  const canvas = document.createElement("canvas");
  canvas.width = w;
  canvas.height = h;
  const ctx = canvas.getContext("2d");
  if (!ctx) throw new Error("Нет canvas");
  ctx.fillStyle = "#fff";
  ctx.fillRect(0, 0, w, h);
  ctx.drawImage(bitmap, 0, 0, w, h);
  const blob = await new Promise<Blob | null>((resolve) => canvas.toBlob(resolve, "image/jpeg", 0.8));
  if (!blob) throw new Error("Не удалось сжать фото");
  if (blob.size > 10 * 1024 * 1024) {
    const second = await new Promise<Blob | null>((resolve) => canvas.toBlob(resolve, "image/jpeg", 0.6));
    if (!second || second.size > 10 * 1024 * 1024) throw new Error("Фото слишком большое");
    return second;
  }
  return blob;
}

/**
 * Gzip файла в браузере. Если сжатие < 10% — вернуть исходник.
 * @param file исходный файл
 */
export async function maybeGzip(file: File): Promise<{ blob: Blob; gzip: boolean; origSize: number }> {
  if (typeof CompressionStream === "undefined") {
    return { blob: file, gzip: false, origSize: file.size };
  }
  const stream = file.stream().pipeThrough(new CompressionStream("gzip"));
  const compressed = await new Response(stream).blob();
  if (compressed.size <= file.size * 0.9) {
    return { blob: compressed, gzip: true, origSize: file.size };
  }
  return { blob: file, gzip: false, origSize: file.size };
}
