import axios from "axios";
import { movementsApi } from "../api";
import { errorMessage } from "../api/http";

const DB_NAME = "warehouse-photo-uploads";
const STORE_NAME = "pending";
const DB_VERSION = 2;
const MAX_ATTEMPTS = 3;

interface PendingPhoto {
  id: string;
  movementId: number;
  file: File;
  attempts: number;
  nextAttemptAt: number;
}

type QueueStatus = "queued" | "uploading" | "complete" | "retrying" | "failed";
let running = false;
let retryTimer: number | undefined;

function openDatabase(): Promise<IDBDatabase> {
  return new Promise((resolve, reject) => {
    const request = indexedDB.open(DB_NAME, DB_VERSION);
    request.onupgradeneeded = () => {
      // Version 1 could retry permanent failures forever. Remove those stale tasks once.
      if (request.result.objectStoreNames.contains(STORE_NAME))
        request.result.deleteObjectStore(STORE_NAME);
      request.result.createObjectStore(STORE_NAME, { keyPath: "id" });
    };
    request.onsuccess = () => resolve(request.result);
    request.onerror = () => reject(request.error);
  });
}

async function transaction<T>(mode: IDBTransactionMode, action: (store: IDBObjectStore) => IDBRequest<T>): Promise<T> {
  const database = await openDatabase();
  return new Promise((resolve, reject) => {
    const tx = database.transaction(STORE_NAME, mode);
    const request = action(tx.objectStore(STORE_NAME));
    request.onsuccess = () => resolve(request.result);
    request.onerror = () => reject(request.error);
    tx.oncomplete = () => database.close();
    tx.onerror = () => reject(tx.error);
  });
}

const allPending = () => transaction<PendingPhoto[]>("readonly", (store) => store.getAll());
const savePending = (photo: PendingPhoto) => transaction<IDBValidKey>("readwrite", (store) => store.put(photo));
const deletePending = (id: string) => transaction<undefined>("readwrite", (store) => store.delete(id));

function notify(status: QueueStatus, pending: number, movementId?: number, message?: string) {
  window.dispatchEvent(new CustomEvent("photo-upload-queue", { detail: { status, pending, movementId, message } }));
}

export async function queuePhotoUploads(movementId: number, files: File[]) {
  if (!files.length) return;
  try {
    for (const file of files)
      await savePending({ id: crypto.randomUUID(), movementId, file, attempts: 0, nextAttemptAt: 0 });
    notify("queued", (await allPending()).length, movementId);
    void runQueue();
  } catch {
    notify("failed", 0, movementId, "无法保存待上传照片，请在记录详情中重新选择照片。");
  }
}

function retryable(error: unknown) {
  if (!axios.isAxiosError(error) || !error.response) return true;
  const status = error.response.status;
  return status === 408 || status === 429 || status >= 500;
}

async function upload(photo: PendingPhoto) {
  try {
    await movementsApi.uploadPhoto(photo.movementId, photo.file);
    await deletePending(photo.id);
    notify("complete", (await allPending()).length, photo.movementId);
  } catch (error) {
    photo.attempts++;
    if (!retryable(error) || photo.attempts >= MAX_ATTEMPTS) {
      await deletePending(photo.id);
      notify("failed", (await allPending()).length, photo.movementId, errorMessage(error));
      return;
    }
    photo.nextAttemptAt = Date.now() + 2 ** photo.attempts * 2_000;
    await savePending(photo);
  }
}

async function runQueue() {
  if (running || !navigator.onLine) return;
  running = true;
  window.clearTimeout(retryTimer);
  try {
    const pending = await allPending();
    const ready = pending.filter((photo) => photo.nextAttemptAt <= Date.now());
    if (ready.length) {
      notify("uploading", pending.length);
      // One at a time avoids saturating a phone's CPU and upstream connection.
      for (const photo of ready) await upload(photo);
    }
    const remaining = await allPending();
    if (remaining.length) {
      notify("retrying", remaining.length);
      const delay = Math.max(1_000, Math.min(...remaining.map((photo) => photo.nextAttemptAt - Date.now())));
      retryTimer = window.setTimeout(() => void runQueue(), delay);
    }
  } finally {
    running = false;
  }
}

export function startPhotoUploadQueue() {
  window.addEventListener("online", () => void runQueue());
  void runQueue();
}
