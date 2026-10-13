"""Сохранение вложений и сжатие видео через ffmpeg."""

import subprocess
import uuid
from io import BytesIO
from pathlib import Path

from fastapi import HTTPException, UploadFile
from PIL import Image, ImageOps

from app.config import settings

PHOTO_MIME = {"image/jpeg", "image/jpg", "image/png", "image/webp", "image/heic", "image/heif"}
VIDEO_MIME = {"video/mp4", "video/quicktime", "video/webm", "video/3gpp", "video/x-matroska"}


def uploads_dir() -> Path:
    """Каталог загрузок в volume."""
    path = Path(settings.data_dir) / "uploads"
    path.mkdir(parents=True, exist_ok=True)
    return path


def new_stored_name(ext: str) -> str:
    """Случайное имя файла на диске."""
    return f"{uuid.uuid4().hex}{ext}"


def save_bytes(data: bytes, ext: str) -> str:
    """Записать байты на диск и вернуть имя."""
    name = new_stored_name(ext)
    (uploads_dir() / name).write_bytes(data)
    return name


async def read_upload(file: UploadFile, max_bytes: int) -> bytes:
    """Прочитать upload с лимитом размера."""
    data = await file.read()
    if len(data) > max_bytes:
        raise HTTPException(413, "Файл слишком большой")
    return data


def compress_photo_if_needed(data: bytes, mime: str) -> tuple[bytes, str]:
    """Если веб не сжал фото — привести к JPEG 1280px q=80."""
    if mime not in PHOTO_MIME and not mime.startswith("image/"):
        raise HTTPException(400, "Это не изображение")
    image = Image.open(BytesIO(data))
    image = ImageOps.exif_transpose(image)
    if image.mode in ("RGBA", "LA", "P"):
        background = Image.new("RGB", image.size, (255, 255, 255))
        converted = image.convert("RGBA")
        background.paste(converted, mask=converted.split()[-1])
        image = background
    else:
        image = image.convert("RGB")
    image.thumbnail((1280, 1280))
    out = BytesIO()
    image.save(out, format="JPEG", quality=80, subsampling=2, optimize=True)
    result = out.getvalue()
    if len(result) > 10 * 1024 * 1024:
        out = BytesIO()
        image.save(out, format="JPEG", quality=60, subsampling=2, optimize=True)
        result = out.getvalue()
    if len(result) > 10 * 1024 * 1024:
        raise HTTPException(413, "Фото слишком большое даже после сжатия")
    return result, "image/jpeg"


def compress_video(src_path: Path) -> tuple[Path, int | None]:
    """Сжать видео как в ТЗ. Вернуть путь mp4 и длительность мс."""
    dest = uploads_dir() / new_stored_name(".mp4")
    scale = "scale='min(640,iw)':'min(640,ih)':force_original_aspect_ratio=decrease"
    bitrate = "500k"
    for scale, bitrate in (
        ("scale='min(640,iw)':'min(640,ih)':force_original_aspect_ratio=decrease", "500k"),
        ("scale='min(480,iw)':'min(480,ih)':force_original_aspect_ratio=decrease", "300k"),
    ):
        if dest.exists():
            dest.unlink()
        proc = subprocess.run(
            [
                "ffmpeg", "-y", "-i", str(src_path),
                "-vf", scale, "-c:v", "libx264", "-pix_fmt", "yuv420p", "-b:v", bitrate,
                "-r", "30", "-c:a", "aac", "-b:a", "64k", "-ac", "2",
                "-movflags", "+faststart", str(dest),
            ],
            capture_output=True,
            text=True,
        )
        if proc.returncode == 0 and dest.exists() and dest.stat().st_size <= 25 * 1024 * 1024:
            return dest, probe_duration_ms(dest)
    dest.unlink(missing_ok=True)
    raise HTTPException(413, "Видео слишком большое после сжатия")


def probe_duration_ms(path: Path) -> int | None:
    """Длительность видео в миллисекундах через ffprobe."""
    proc = subprocess.run(
        [
            "ffprobe", "-v", "error", "-show_entries", "format=duration",
            "-of", "default=noprint_wrappers=1:nokey=1", str(path),
        ],
        capture_output=True,
        text=True,
    )
    try:
        return int(float(proc.stdout.strip()) * 1000)
    except (ValueError, TypeError):
        return None


def make_video_preview(video_path: Path) -> str:
    """Снять JPEG-кадр для пузыря."""
    name = new_stored_name(".jpg")
    dest = uploads_dir() / name
    subprocess.run(
        ["ffmpeg", "-y", "-i", str(video_path), "-ss", "0.3", "-frames:v", "1", str(dest)],
        capture_output=True,
    )
    return name if dest.exists() else ""
