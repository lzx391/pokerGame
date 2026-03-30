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
      <button type="button" class="hand-history-btn" @click="goHandHistory">历史对局</button>
    </div>

    <div class="room-list">
      <h3>房间列表</h3>
      <p v-if="roomsLoading" class="room-list__hint">加载中…</p>
      <p v-else-if="roomsError" class="room-list__hint room-list__hint--error">{{ roomsError }}</p>
      <p v-else-if="!roomDtos.length" class="room-list__hint">暂无房间，可先点「创建房间」开一桌。</p>
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
      roomDtos: [],
      roomsLoading: true,
      roomsError: ''
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
    goHandHistory() {
      this.$router.push('/hand-history')
    },
    async getRooms() {
      try {
        if (!this.roomDtos.length) this.roomsLoading = true
        this.roomsError = ''
        const res = await this.$http.get('/dpRoom/getAllRooms2')
        var list = res.data
        this.roomDtos = Array.isArray(list) ? list : []
      } catch (e) {
        console.error('getRooms', e)
        this.roomsError = '房间列表加载失败，请确认后端已启动。'
        this.roomDtos = []
      } finally {
        this.roomsLoading = false
      }
    },
    async createRoom() {
      const params = { nickname: this.user.nickname }
      if (this.user.userId != null && this.user.userId !== '') {
        params.userId = this.user.userId
      }
      const res = await this.$http.post('/dpRoom/createRoom', null, { params })
      this.$router.push('/room/' + res.data.roomId)
    },
    async joinRoom(roomId) {
      const params = { roomId, nickname: this.user.nickname }
      if (this.user.userId != null && this.user.userId !== '') {
        params.userId = this.user.userId
      }
      await this.$http.post('/dpRoom/joinRoom', null, { params })
      this.$router.push('/room/' + roomId)
    }
  }
}
</script>

<style scoped>
.home{max-width:600px;margin:0 auto;padding:20px}
.home-header{display:flex;justify-content:space-between;align-items:center;margin-bottom:20px}
.user-info{display:flex;align-items:center;gap:10px;font-size:14px;color:#666}
.btns{text-align:center;margin-bottom:20px;display:flex;flex-wrap:wrap;gap:10px;justify-content:center;align-items:center}
.hand-history-btn{background:#fff;color:#409eff;border:1px solid #409eff;border-radius:4px;cursor:pointer;padding:8px 14px;font-size:14px}
.hand-history-btn:hover{background:#ecf5ff}
.room-list{margin-top:20px;text-align:left}
.room-list h3{text-align:center}
.room-list__hint{margin:16px 0;padding:12px;color:#909399;font-size:14px;line-height:1.5}
.room-list__hint--error{color:#f56c6c}
.room-item{display:flex;justify-content:space-between;padding:10px;border-bottom:1px solid #eee}
button{padding:8px 12px}
.logout-btn{background:#f56c6c;color:#fff;border:none;border-radius:4px;cursor:pointer;padding:6px 10px}
.logout-btn:hover{background:#f78989}
</style>