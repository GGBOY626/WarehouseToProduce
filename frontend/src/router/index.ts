import {createRouter,createWebHistory} from 'vue-router';
import HomeView from '../views/HomeView.vue';
import {ensureAuth} from '../auth';
const router=createRouter({history:createWebHistory(),scrollBehavior:()=>({top:0}),routes:[
  {path:'/',component:HomeView},
  {path:'/login',component:()=>import('../views/LoginView.vue')},
  {path:'/movements/new/:direction',component:()=>import('../views/MovementFormView.vue'),meta:{requiresAuth:true}},
  {path:'/movements/:id/edit',component:()=>import('../views/MovementFormView.vue'),meta:{requiresAuth:true}},
  {path:'/movements/:id',component:()=>import('../views/MovementDetailView.vue')},
  {path:'/history',redirect:'/'},
  {path:'/products',component:()=>import('../views/ProductView.vue')},
  {path:'/persons',component:()=>import('../views/PersonView.vue')},
  {path:'/reports',component:()=>import('../views/ReportView.vue')},
  {path:'/production-tasks',component:()=>import('../views/ProductionTaskView.vue')},
  {path:'/production-tasks/:id',component:()=>import('../views/ProductionTaskDetailView.vue')}
]});
router.beforeEach(async to=>!to.meta.requiresAuth||await ensureAuth()?true:{path:'/login',query:{redirect:to.fullPath}});
export default router;
