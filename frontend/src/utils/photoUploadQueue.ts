import { movementsApi } from "../api";

const DB_NAME = "warehouse-photo-uploads";
const STORE_NAME = "pending";
const CONCURRENCY = 3;

interface PendingPhoto { id:string; movementId:number; file:File; attempts:number; nextAttemptAt:number }
let running=false;
let retryTimer:number|undefined;

function openDatabase():Promise<IDBDatabase>{return new Promise((resolve,reject)=>{const request=indexedDB.open(DB_NAME,1);request.onupgradeneeded=()=>{if(!request.result.objectStoreNames.contains(STORE_NAME))request.result.createObjectStore(STORE_NAME,{keyPath:"id"})};request.onsuccess=()=>resolve(request.result);request.onerror=()=>reject(request.error)})}

async function transaction<T>(mode:IDBTransactionMode,action:(store:IDBObjectStore)=>IDBRequest<T>):Promise<T>{const database=await openDatabase();return new Promise((resolve,reject)=>{const tx=database.transaction(STORE_NAME,mode);const request=action(tx.objectStore(STORE_NAME));request.onsuccess=()=>resolve(request.result);request.onerror=()=>reject(request.error);tx.oncomplete=()=>database.close();tx.onerror=()=>reject(tx.error)})}

const allPending=()=>transaction<PendingPhoto[]>("readonly",store=>store.getAll());
const savePending=(photo:PendingPhoto)=>transaction<IDBValidKey>("readwrite",store=>store.put(photo));
const deletePending=(id:string)=>transaction<undefined>("readwrite",store=>store.delete(id));

function notify(status:"queued"|"uploading"|"complete"|"retrying",pending:number,movementId?:number){window.dispatchEvent(new CustomEvent("photo-upload-queue",{detail:{status,pending,movementId}}))}

export async function queuePhotoUploads(movementId:number,files:File[]){if(!files.length)return;await Promise.all(files.map(file=>savePending({id:crypto.randomUUID(),movementId,file,attempts:0,nextAttemptAt:0})));notify("queued",(await allPending()).length,movementId);void runQueue()}

async function upload(photo:PendingPhoto){try{await movementsApi.uploadPhoto(photo.movementId,photo.file);await deletePending(photo.id);notify("complete",(await allPending()).length,photo.movementId)}catch{photo.attempts++;photo.nextAttemptAt=Date.now()+Math.min(60_000,2**photo.attempts*2_000);await savePending(photo)}}

async function runQueue(){if(running||!navigator.onLine)return;running=true;window.clearTimeout(retryTimer);try{const pending=await allPending();const ready=pending.filter(photo=>photo.nextAttemptAt<=Date.now());if(ready.length){notify("uploading",pending.length);let next=0;async function worker(){while(next<ready.length)await upload(ready[next++])}await Promise.all(Array.from({length:Math.min(CONCURRENCY,ready.length)},worker))}const remaining=await allPending();if(remaining.length){notify("retrying",remaining.length);const delay=Math.max(1_000,Math.min(...remaining.map(photo=>photo.nextAttemptAt-Date.now())));retryTimer=window.setTimeout(()=>void runQueue(),delay)}}finally{running=false}}

export function startPhotoUploadQueue(){window.addEventListener("online",()=>void runQueue());void runQueue()}
