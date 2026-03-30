// import Home from "@/components/home.vue";
// import Login from "@/components/login.vue";
// import Register from "@/components/register.vue";
// import Room from "@/components/room.vue";
// import RoomList from "@/components/roomList.vue";
// import Vue from "vue";
// import VueRouter from "vue-router";
// import Game from "../components/game.vue";


// Vue.use(VueRouter)

// const router = new VueRouter({
//     routes: [
//         { path: '/', redirect: '/login' },
//         { path: '/login', component: Login },
//         { path: '/register', component: Register },
//         { path: '/home', component: Home },   // 用户主页
//         { path: '/room', component: Room ,
//             children:[
//                   {path:":roomId",component:Room}
//             ]
//         },
//         { path: '/roomList', component: RoomList },
//         { path: '/gameList', component: Game,
//             children:[
//                 {path:":roomId",component:Game}
//             ]
//          }
//         // {path: '/friends',component: Friends}
//     ]
// })
// export default router
// 意思是导出路由供其他文件引用
import Home from '@/components/home.vue'
import Login from '@/components/login.vue'
import Register from '@/components/register.vue'
import Room from '@/components/room.vue'
import Vue from 'vue'
import Router from 'vue-router'
import Game from '../components/game.vue'
import ImageUpload from '@/components/image_upload.vue'
import HandHistory from '@/components/HandHistory.vue'
import HandHistoryDetail from '@/components/HandHistoryDetail.vue'

Vue.use(Router)

export default new Router({
  routes: [
    { path: '/', redirect: '/login' },
    { path: '/login', component: Login },
    { path: '/register', component: Register },
    { path: '/home', component: Home },
    { path: '/hand-history', component: HandHistory },
    {
      path: '/hand-history/detail/:handHistoryId',
      component: HandHistoryDetail,
      props: true
    },
    { path: '/room/:roomId', component: Room },   // 动态路由，不是嵌套
    { path: '/game/:roomId', component: Game } ,  // 动态路由，不是嵌套
    { path: '/image_upload', component: ImageUpload }
  ]
})