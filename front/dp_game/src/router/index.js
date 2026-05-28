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
import Vue from 'vue'
import Router from 'vue-router'

Vue.use(Router)

export default new Router({
  routes: [
    { path: '/', redirect: '/login' },
    {
      path: '/login',
      component: () => import(/* webpackChunkName: "route-login" */ '@/components/login.vue')
    },
    {
      path: '/register',
      component: () => import(/* webpackChunkName: "route-register" */ '@/components/register.vue')
    },
    {
      path: '/home',
      component: () => import(/* webpackChunkName: "route-home" */ '@/components/home.vue')
    },
    {
      path: '/guide',
      name: 'GameButtonGuide',
      component: () => import(/* webpackChunkName: "route-guide" */ '@/components/GameButtonGuidePage.vue')
    },
    {
      path: '/create-room',
      name: 'create-room',
      meta: { transition: 'slide-from-right' },
      component: () => import(/* webpackChunkName: "route-create-room" */ '@/components/CreateRoom.vue')
    },
    {
      path: '/hand-history',
      component: () => import(/* webpackChunkName: "route-hand-history" */ '@/components/HandHistory.vue')
    },
    {
      path: '/leaderboard',
      component: () => import(/* webpackChunkName: "route-leaderboard" */ '@/components/LeaderboardPage.vue')
    },
    {
      path: '/hand-history/detail/:handHistoryId',
      component: () => import(/* webpackChunkName: "route-hand-history-detail" */ '@/components/HandHistoryDetail.vue'),
      props: true
    },
    {
      path: '/room/:roomId',
      redirect: (to) => ({ path: '/game/' + to.params.roomId })
    },
    {
      path: '/game/:roomId',
      name: 'game',
      meta: { transition: 'zoom-fade-in' },
      component: () =>
        import(
          /* webpackChunkName: "route-game" */
          /* webpackPrefetch: true */
          '@/components/game.vue'
        )
    },
    {
      path: '/image_upload',
      component: () => import(/* webpackChunkName: "route-image-upload" */ '@/components/image_upload.vue')
    },
    {
      path: '/music-upload',
      component: () => import(/* webpackChunkName: "route-music-upload" */ '@/components/MusicUpload.vue')
    },
    {
      path: '/download-center',
      component: () => import(/* webpackChunkName: "route-download-center" */ '@/components/DownloadCenter.vue')
    }
  ]
})