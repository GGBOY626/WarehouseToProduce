import {formatInTimeZone,fromZonedTime} from 'date-fns-tz';
export const BUSINESS_ZONE='Pacific/Auckland';
export const nowLocalInput=()=>formatInTimeZone(new Date(),BUSINESS_ZONE,"yyyy-MM-dd'T'HH:mm");
export const toLocalInput=(iso:string)=>formatInTimeZone(iso,BUSINESS_ZONE,"yyyy-MM-dd'T'HH:mm");
export const toInstant=(local:string)=>fromZonedTime(local,BUSINESS_ZONE).toISOString();
export const showDateTime=(iso:string)=>formatInTimeZone(iso,BUSINESS_ZONE,'yyyy-MM-dd HH:mm');
export const showTime=(iso:string)=>formatInTimeZone(iso,BUSINESS_ZONE,'HH:mm');
export const today=()=>formatInTimeZone(new Date(),BUSINESS_ZONE,'yyyy-MM-dd');
