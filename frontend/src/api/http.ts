import axios from 'axios';
export const http=axios.create({baseURL:'/api',timeout:20000});
export function errorMessage(error:unknown){if(axios.isAxiosError(error))return error.response?.data?.message||(!error.response?'网络连接失败，请检查网络后重试。':'请求失败，请稍后重试。');return '操作失败，请稍后重试。'}
