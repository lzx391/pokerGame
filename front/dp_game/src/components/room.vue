<template>
    <div class="room-page">
        <h1>房间内</h1>
        <p>房间号：{{ roomId }}</p>
        <p>房主：{{ room?.owner }}</p>

        <h3>玩家列表</h3>
        <div class="player" v-for="p in room?.players" :key="p.nickname">
            <span>{{ p.nickname }}</span>
            <span :class="p.ready ? 'ok' : 'no'">{{ p.ready ? "已准备" : "未准备" }}</span>
        </div>

        <div class="btns">
            <button @click="ready">准备/取消</button>
            <button v-if="user.nickname === room?.owner" @click="start" :disabled="!allReady">
                开始游戏
            </button>
            <button @click="exit">退出房间</button>
        </div>
    </div>
</template>

<script>
export default {
    name: "Room",
    data() {
        return {
            roomId: "",
            room: null,
            user: {},
            allReady: false,
            timer: null,
            heartbeatTimer: null
        };
    },
    created() {
        this.roomId = this.$route.params.roomId;
        this.user = JSON.parse(localStorage.getItem("userInfo"));
        this.fetchRoomInfo();

        this.timer = setInterval(this.fetchRoomInfo, 2000);

        // 心跳定时器（保活）
        this.heartbeatTimer = setInterval(() => {
            this.$http.post("/dpRoom/heartbeat", null, {
                params: {
                    roomId: this.roomId,
                    nickname: this.user.nickname
                }
            });
        }, 5000);
    },
    beforeDestroy() {
        clearInterval(this.timer);
        clearInterval(this.heartbeatTimer);
    },
    methods: {
        async ready() {
            try {
                await this.$http.post("/dpRoom/toggleReady", null, {
                    params: {
                        roomId: this.roomId,
                        nickname: this.user.nickname
                    }
                });
                this.fetchRoomInfo();
            } catch (e) {
                console.error("准备失败", e);
            }
        },

        async start() {
            const res = await this.$http.post("/dpRoom/startGame", null, {
                params: {
                    roomId: this.roomId,
                    ownerNickname: this.user.nickname
                }
            });
            alert(res.data);
            this.$router.push(`/game/${this.roomId}`);
        },

        async exit() {
            try {
                await this.$http.post("/dpRoom/exitRoom", null, {
                    params: {
                        roomId: this.roomId,
                        nickname: this.user.nickname
                    }
                });
                this.$router.push("/home");
            } catch (e) {
                console.error("退出房间失败", e);
                alert("退出房间失败");
            }
        },

        async fetchRoomInfo() {
            try {
              const res = await this.$http.get('/dpRoom/getNowRoom',{
                params: { roomId: this.roomId }
              })
              const room = res.data
                if (!room) {
                    this.$router.push("/home");
                    return;
                }

                this.room = room;
                this.allReady = room.players.every(p => p.ready);

                // 如果游戏已经开始，所有人自动跳转到游戏页！
                if (room.playing) {
                    this.$router.push("/game/" + this.roomId);
                }
            } catch (e) {
                console.error("获取房间信息失败", e);
            }
        }
    }
};
</script>

<style scoped>
.room-page {
    text-align: center;
    margin-top: 40px;
}

.player {
    display: flex;
    justify-content: space-between;
    max-width: 280px;
    margin: 10px auto;
    padding: 8px;
    border-bottom: 1px solid #eee;
}

.ok {
    color: green;
}

.no {
    color: red;
}

.btns {
    margin-top: 30px;
    display: flex;
    gap: 15px;
    justify-content: center;
}

button {
    padding: 10px 16px;
    border: none;
    border-radius: 5px;
    cursor: pointer;
    color: white;
}

button:nth-child(1) {
    background: #67c23a;
}

button:nth-child(2) {
    background: #409eff;
}

button:nth-child(3) {
    background: #ff4d4f;
}

button:disabled {
    background: #ccc;
    cursor: not-allowed;
}
</style>