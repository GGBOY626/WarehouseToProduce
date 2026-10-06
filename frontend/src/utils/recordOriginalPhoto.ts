const MAX_ORIGINAL_BYTES = 12 * 1024 * 1024;
const MAX_COMPRESSED_BYTES = 3 * 1024 * 1024;
const SMALL_IMAGE_BYTES = 700 * 1024;
const MAX_EDGE = 1600;

export class RecordPhotoPreparationError extends Error {}

function jpegName(name: string) {
  const base = name.replace(/\.[^.]+$/, "") || "record-photo";
  return `${base}.record.jpg`;
}

function canvasJpeg(canvas: HTMLCanvasElement, quality: number) {
  return new Promise<Blob | null>((resolve) => canvas.toBlob(resolve, "image/jpeg", quality));
}

/** Prepare document-style photos without changing the ordinary movement-photo settings. */
export async function prepareRecordOriginalPhoto(source: File): Promise<File> {
  if (!source.type.startsWith("image/")) {
    throw new RecordPhotoPreparationError("暂不支持该照片格式，请使用 JPG 或 PNG。");
  }
  if (source.size > MAX_ORIGINAL_BYTES) {
    throw new RecordPhotoPreparationError("照片文件过大，请重新选择。");
  }

  let bitmap: ImageBitmap;
  try {
    bitmap = await createImageBitmap(source, { imageOrientation: "from-image" });
  } catch {
    throw new RecordPhotoPreparationError("照片处理失败，请重新选择或重新拍照。");
  }

  try {
    const isJpeg = source.type === "image/jpeg";
    if (isJpeg && source.size <= SMALL_IMAGE_BYTES && Math.max(bitmap.width, bitmap.height) <= MAX_EDGE) {
      return source;
    }

    const scale = Math.min(1, MAX_EDGE / Math.max(bitmap.width, bitmap.height));
    const width = Math.max(1, Math.round(bitmap.width * scale));
    const height = Math.max(1, Math.round(bitmap.height * scale));
    const canvas = document.createElement("canvas");
    canvas.width = width;
    canvas.height = height;
    const context = canvas.getContext("2d", { alpha: false });
    if (!context) throw new RecordPhotoPreparationError("照片处理失败，请重新选择或重新拍照。");
    context.fillStyle = "#fff";
    context.fillRect(0, 0, width, height);
    context.drawImage(bitmap, 0, 0, width, height);

    let blob: Blob | null = null;
    for (const quality of [0.72, 0.68, 0.64, 0.60]) {
      blob = await canvasJpeg(canvas, quality);
      if (!blob || blob.size <= SMALL_IMAGE_BYTES) break;
    }
    if (!blob) throw new RecordPhotoPreparationError("照片处理失败，请重新选择或重新拍照。");
    if (blob.size > MAX_COMPRESSED_BYTES) {
      throw new RecordPhotoPreparationError("照片压缩后仍然过大，请重新拍照或选择其他照片。");
    }
    return new File([blob], jpegName(source.name), { type: "image/jpeg", lastModified: source.lastModified });
  } finally {
    bitmap.close();
  }
}
