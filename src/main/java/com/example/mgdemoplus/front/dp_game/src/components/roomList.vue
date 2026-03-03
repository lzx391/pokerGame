<template>
  <div class="room-list-page">
    <h2>房间列表</h2>
    <div v-if="rooms.length === 0" class="empty">暂无房间</div>
    <div class="card" v-for="room in rooms" :key="room.roomId">
      <h3>房间号：{{ room.roomId }}</h3>
      <p>房主：{{ room.owner }}</p>
      <p>人数：{{ room.players.length }}</p>
      <button @click="join(room.roomId)">加入房间</button>
    </div>
    <button class="back" @click="$router.back()">返回</button>
  </div>
</template>

<script>
export default {
  data() {
    return {
      rooms: [],
      user: {}
    };
  },
  created() {
    this.user = JSON.parse(localStorage.getItem("userInfo"));
    this.getList();
  },
  methods: {
    async getList() {
      try {
        const res = await this.$http.get("/dpRoom/getAllRooms");
        this.rooms = res.data;
      } catch (e) {
        console.error("获取房间列表失败", e);
      }
    },

    async join(roomId) {
      try {
        const res = await this.$http.post("/dpRoom/joinRoom", null, {
          params: {
            roomId,
            nickname: this.user.nickname
          }
        });
        alert(res.data);
        if (res.data === "加入成功") {
          this.$router.push(`/room/${roomId}`);
        }
      } catch (e) {
        console.error("加入房间失败", e);
        alert("加入房间失败");
      }
    }
  }
};
</script>

<style scoped>
.room-list-page {
  max-width: 500px;
  margin: 0 auto;
  padding: 20px;
}
.card {
  border: 1px solid #eee;
  border-radius: 10px;
  padding: 15px;
  margin: 10px 0;
  text-align: left;
}
.card button {
  background: #409eff;
  color: white;
  border: none;
  padding: 6px 12px;
  border-radius: 5px;
  cursor: pointer;
}
.back {
  margin-top: 20px;
  padding: 8px 16px;
}
.empty {
  color: #999;
  margin: 30px 0;
}
</style>