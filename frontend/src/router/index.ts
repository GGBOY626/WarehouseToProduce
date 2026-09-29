import {createRouter,createWebHistory} from 'vue-router';
import HomeView from '../views/HomeView.vue';
const router=createRouter({history:createWebHistory(),scrollBehavior:()=>({top:0}),routes:[
  {path:'/',component:HomeView},
  {path:'/movements/new/:direction',component:()=>import('../views/MovementFormView.vue')},
  {path:'/movements/:id/edit',component:()=>import('../views/MovementFormView.vue')},
  {path:'/movements/:id',component:()=>import('../views/MovementDetailView.vue')},
  {path:'/history',component:()=>import('../views/HistoryView.vue')},
  {path:'/products',component:()=>import('../views/ProductView.vue')},
  {path:'/persons',component:()=>import('../views/PersonView.vue')},
  {path:'/reports',component:()=>import('../views/ReportView.vue')}
]});
export default router;
