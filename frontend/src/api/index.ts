import {http} from './http';
import type {Direction,MovementDetail,MovementPage,MovementStatus,Person,Product,ProductUsage,ProductionTask,ProductionTaskStatus,QueryStats,TodayStats} from '../types';
export interface AuthStatus {authenticated:boolean;username?:string}
export const authApi={status:async()=>(await http.get<AuthStatus>('/auth/status')).data,login:async(username:string,password:string)=>(await http.post<AuthStatus>('/auth/login',{username,password})).data,logout:async()=>(await http.post<AuthStatus>('/auth/logout')).data};
export const productsApi={search:async(q='',includeInactive=false,direction?:ProductUsage)=>(await http.get<Product[]>('/products',{params:{q,includeInactive,direction}})).data,create:async(data:object)=>(await http.post<Product>('/products',data)).data,update:async(id:number,data:object)=>(await http.put<Product>(`/products/${id}`,data)).data,status:async(id:number,active:boolean)=>(await http.patch<Product>(`/products/${id}/status`,null,{params:{active}})).data,delete:async(id:number)=>http.delete(`/products/${id}`)};
export const personsApi={search:async(q='',includeInactive=false)=>(await http.get<Person[]>('/persons',{params:{q,includeInactive}})).data,create:async(data:object)=>(await http.post<Person>('/persons',data)).data,update:async(id:number,data:object)=>(await http.put<Person>(`/persons/${id}`,data)).data,status:async(id:number,active:boolean)=>(await http.patch<Person>(`/persons/${id}/status`,null,{params:{active}})).data};
export const productionTasksApi={list:async(status?:ProductionTaskStatus)=>(await http.get<ProductionTask[]>('/production-tasks',{params:{status}})).data,detail:async(id:number)=>(await http.get<ProductionTask>(`/production-tasks/${id}`)).data,create:async(data:object)=>(await http.post<ProductionTask>('/production-tasks',data)).data,update:async(id:number,data:object)=>(await http.put<ProductionTask>(`/production-tasks/${id}`,data)).data,status:async(id:number,status:ProductionTaskStatus)=>(await http.patch<ProductionTask>(`/production-tasks/${id}/status`,null,{params:{status}})).data};
export const movementsApi={
  create:async(data:object)=>(await http.post<MovementDetail>('/movements',data)).data,
  update:async(id:number,data:object)=>(await http.put<MovementDetail>(`/movements/${id}`,data)).data,
  detail:async(id:number)=>(await http.get<MovementDetail>(`/movements/${id}`)).data,
  list:async(params:{from:string;to:string;direction?:Direction;status?:MovementStatus;missingPhoto?:boolean;hasIssue?:boolean;q?:string;page?:number;size?:number})=>(await http.get<MovementPage>('/movements',{params})).data,
  stats:async(date:string,direction?:Direction)=>(await http.get<TodayStats>('/movements/stats',{params:{date,direction}})).data,
  queryStats:async(params:{from:string;to:string;direction?:Direction;status?:MovementStatus;missingPhoto?:boolean;hasIssue?:boolean;q?:string})=>(await http.get<QueryStats>('/movements/stats/query',{params})).data,
  void:async(id:number,reason:string)=>(await http.post<MovementDetail>(`/movements/${id}/void`,{reason})).data,
  delete:async(id:number)=>http.delete(`/movements/${id}`),
  uploadPhoto:async(id:number,file:File,onProgress?:(n:number)=>void)=>{const form=new FormData();form.append('file',file);return(await http.post(`/movements/${id}/photos`,form,{timeout:30000,onUploadProgress:e=>onProgress?.(e.total?Math.round(e.loaded/e.total*100):0)})).data},
  deletePhoto:async(id:number,photoId:number)=>http.delete(`/movements/${id}/photos/${photoId}`)
};
