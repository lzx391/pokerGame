<template>
  <div class="home">
    <div class="home-header">
      <h2>游戏大厅</h2>
      <div class="user-info">
        <span v-if="user && user.nickname">当前用户：{{ user.nickname }}</span>
        <button class="logout-btn" @click="logout">退出登录</button>
      </div>
    </div>
    <div class="btns">
      <button @click="createRoom">创建房间</button>
    </div>

    <div class="room-list">
      <h3>房间列表</h3>
      <div class="room-item" v-for="roomDto in roomDtos" :key="roomDto.roomId">
        <span>房间 {{ roomDto.roomId }} ({{ roomDto.playerSize }}人)</span>
        <button @click="joinRoom(roomDto.roomId)">加入</button>
      </div>
    </div>
  </div>
</template>

<script>
export default {
  data() {
    return {
      user: {},
      roomDtos: []

    }
  },
  created() {
    this.user = JSON.parse(localStorage.getItem('userInfo'))
    this.getRooms();
    // 2. 使用箭头函数确保 this 指向，并保存定时器引用
    this.timer = setInterval(() => {
      this.getRooms();
    }, 2000);
  },
  beforeDestroy() {
    if (this.timer) {
      console.log("正在销毁定时器")
      clearInterval(this.timer);
      this.timer = null;
    }
  },
  methods: {
    logout() {
      localStorage.removeItem('userInfo')
      this.$router.push('/')
    },
    async getRooms() {
      const res = await this.$http.get('/dpRoom/getAllRooms2')
      this.roomDtos = res.data
    },
    async createRoom() {
      const res = await this.$http.post('/dpRoom/createRoom', null, {
        params: { nickname: this.user.nickname }
      })
      this.$router.push('/room/' + res.data.roomId)
    },
    async joinRoom(roomId) {
      await this.$http.post('/dpRoom/joinRoom', null, {
        params: { roomId, nickname: this.user.nickname }
      })
      this.$router.push('/room/' + roomId)
    }
  }
}
</script>

<style scoped>
.home{max-width:600px;margin:0 auto;padding:20px}
.home-header{display:flex;justify-content:space-between;align-items:center;margin-bottom:20px}
.user-info{display:flex;align-items:center;gap:10px;font-size:14px;color:#666}
.btns{text-align:center;margin-bottom:20px}
.room-list{margin-top:20px}
.room-item{display:flex;justify-content:space-between;padding:10px;border-bottom:1px solid #eee}
button{padding:8px 12px}
.logout-btn{background:#f56c6c;color:#fff;border:none;border-radius:4px;cursor:pointer;padding:6px 10px}
.logout-btn:hover{background:#f78989}
</style>