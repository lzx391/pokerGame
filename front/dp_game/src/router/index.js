// import Home from "@features/lobby/pages/LobbyPage.vue";
// import Login from "@features/user/pages/LoginPage.vue";
// import Register from "@features/user/pages/RegisterPage.vue";
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

var router = new Router({
  routes: [
    { path: '/', redirect: '/login' },
    {
      path: '/login',
      component: () => import(/* webpackChunkName: "route-login" */ '@features/user/pages/LoginPage.vue')
    },
    {
      path: '/register',
      component: () => import(/* webpackChunkName: "route-register" */ '@features/user/pages/RegisterPage.vue')
    },
    {
      path: '/home',
      component: () => import(/* webpackChunkName: "route-home" */ '@features/lobby/pages/LobbyPage.vue')
    },
    {
      path: '/guide',
      name: 'GameButtonGuide',
      component: () => import(/* webpackChunkName: "route-guide" */ '@features/room/pages/ButtonGuidePage.vue')
    },
    {
      path: '/create-room',
      name: 'create-room',
      meta: { transition: 'slide-from-right' },
      component: () => import(/* webpackChunkName: "route-create-room" */ '@features/lobby/pages/CreateRoomPage.vue')
    },
    {
      path: '/hand-history',
      component: () => import(/* webpackChunkName: "route-hand-history" */ '@features/history/pages/HandHistoryPage.vue')
    },
    {
      path: '/leaderboard',
      component: () => import(/* webpackChunkName: "route-leaderboard" */ '@features/leaderboard/pages/LeaderboardPage.vue')
    },
    {
      path: '/hand-history/detail/:handHistoryId',
      component: () => import(/* webpackChunkName: "route-hand-history-detail" */ '@features/history/pages/HandHistoryDetailPage.vue'),
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
          '@features/room/pages/GamePage.vue'
        )
    },
    {
      path: '/image_upload',
      component: () => import(/* webpackChunkName: "route-image-upload" */ '@features/user/components/ImageUpload.vue')
    },
    {
      path: '/music-upload',
      component: () => import(/* webpackChunkName: "route-music-upload" */ '@features/music/pages/MusicUploadPage.vue')
    },
    {
      path: '/download-center',
      component: () => import(/* webpackChunkName: "route-download-center" */ '@features/download/pages/DownloadCenterPage.vue')
    },
    {
      path: '/oauth/callback',
      component: () => import(/* webpackChunkName: "route-oauth-callback" */ '@features/user/pages/OAuthCallbackPage.vue')
    },
    {
      path: '/admin',
      component: () => import(/* webpackChunkName: "route-admin" */ '@features/admin/pages/AdminLayoutPage.vue'),
      redirect: '/admin/roles',
      children: [
        {
          path: 'roles',
          component: () => import(/* webpackChunkName: "route-admin-roles" */ '@features/admin/pages/AdminRolesPage.vue')
        },
        {
          path: 'users',
          component: () => import(/* webpackChunkName: "route-admin-users" */ '@features/admin/pages/AdminUsersPage.vue')
        }
      ]
    }
  ]
})

router.beforeEach(function (to, from, next) {
  if (to.path === '/admin' || to.path.indexOf('/admin/') === 0) {
    if (sessionStorage.getItem('dp_admin_unlock') !== '1') {
      next('/home')
      return
    }
  }
  next()
})

export default router