<template>
  <dp-retro-password-gate-shell
    :visible.sync="dialogVisible"
    title="> ROOM // JOIN"
    prompt="[LOBBY] ACCESS:"
    :logs="gateLogs"
    :error-message="errorMessage"
    :submitting="submitting"
    primary-label="EXECUTE"
    primary-loading-label="JOIN..."
    input-aria-label="房间密码"
    @submit="submit"
    @close="onClose"
  />
</template>

<script>
import { dpResultMessage, dpResultSuccess } from '@/utils/dpApiResult'
import DpRetroPasswordGateShell from '@/components/DpRetroPasswordGateShell.vue'

export default {
  name: 'LobbyRoomPasswordGate',
  components: { DpRetroPasswordGateShell },
  props: {
    visible: { type: Boolean, default: false },
    roomId: { type: String, default: '' },
    nickname: { type: String, default: '' },
    userId: { type: [String, Number], default: null }
  },
  data: function () {
    return {
      errorMessage: '',
      submitting: false
    }
  },
  computed: {
    dialogVisible: {
      get: function () {
        return this.visible
      },
      set: function (v) {
        this.$emit('update:visible', v)
      }
    },
    gateLogs: function () {
      var lines = ['> clearance required to enter locked room']
      if (this.roomId) {
        lines.push('> target: ' + this.roomId)
      }
      return lines
    }
  },
  watch: {
    visible: function (v) {
      if (v) {
        this.errorMessage = ''
      }
    }
  },
  methods: {
    onClose: function () {
      this.errorMessage = ''
    },
    setError: function (msg) {
      this.errorMessage = msg
    },
    submit: async function (pwd) {
      if (this.submitting) return
      if (!pwd) {
        this.setError('PASSWORD REQUIRED')
        return
      }
      if (!this.roomId || !this.nickname) {
        this.setError('ROOM CONTEXT LOST')
        return
      }
      this.submitting = true
      this.errorMessage = ''
      try {
        var params = {
          roomId: this.roomId,
          roomPassword: pwd
        }
        var res = await this.$http.post('/dpRoom/joinRoom2', null, { params: params })
        var body = res.data
        if (!dpResultSuccess(body)) {
          this.setError(dpResultMessage(body) || 'ACCESS DENIED')
          return
        }
        this.$emit('joined', this.roomId)
        this.dialogVisible = false
        this.errorMessage = ''
      } catch (err) {
        this.setError('NETWORK ERROR')
      } finally {
        this.submitting = false
      }
    }
  }
}
</script>
