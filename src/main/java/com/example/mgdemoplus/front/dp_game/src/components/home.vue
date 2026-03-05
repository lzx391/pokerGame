<template>
  <div class="home">
    <h2>游戏大厅</h2>
    <div class="btns">
      <button @click="createRoom">创建房间</button>
    </div>

    <div class="room-list">
      <h3>房间列表</h3>
      <div class="room-item" v-for="room in rooms" :key="room.roomId">
        <span>房间 {{ room.roomId }} ({{ room.players.length }}人)</span>
        <button @click="joinRoom(room.roomId)">加入</button>
      </div>
    </div>
  </div>
</template>

<script>
export default {
  data() {
    return {
      user: {},
      rooms: []
    }
  },
  created() {
    this.user = JSON.parse(localStorage.getItem('userInfo'))
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
    async getRooms() {
      const res = await this.$http.get('/dpRoom/getAllRooms')
      this.rooms = res.data
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
.btns{text-align:center;margin-bottom:20px}
.room-list{margin-top:20px}
.room-item{display:flex;justify-content:space-between;padding:10px;border-bottom:1px solid #eee}
button{padding:8px 12px}
</style>