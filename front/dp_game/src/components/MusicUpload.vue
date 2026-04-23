<template>
  <div
    class="dp-game-root"
    :data-dp-game-theme="effectiveThemeForCss"
    :style="customThemeInlineStyle"
  >
    <div class="dp-lobby-inner dp-lobby-inner--wide music-upload">
      <div class="music-upload__toolbar">
        <div class="dp-game-theme-row music-upload__theme-row">
          <span class="dp-game-theme-row__label">界面主题</span>
            <dp-theme-picker
              :game-ui-theme="gameUiTheme"
              :theme-options="gameThemeOptions"
              :custom-theme-base="customThemeBase"
              :custom-theme-overrides="customThemeOverrides"
              @input-theme="onLobbyThemeChange($event)"
              @custom-base="$store.commit('dpGame/SET_CUSTOM_THEME', { baseId: $event })"
              @custom-overrides="$store.commit('dpGame/SET_CUSTOM_THEME', { overrides: $event })"
            />
        </div>
      </div>
      <div class="music-upload__header">
        <h2 class="music-upload__title">曲库上传</h2>
        <div class="music-upload__actions">
          <el-button type="text" class="music-upload__home-link" @click="goHome">返回大厅</el-button>
        </div>
      </div>

      <p class="music-upload__hint">
        音频保存在服务器目录，入库到 <code>dp_music_track</code>；支持 mp3、m4a、wav、ogg、flac，单文件建议不超过 80MB。
      </p>

      <div class="dp-lobby-panel music-upload__panel">
        <el-form label-width="100px" class="music-upload__form" @submit.native.prevent>
          <el-form-item label="展示名称">
            <el-input
              v-model="displayName"
              placeholder="可选，默认用文件名（去扩展名）"
              clearable
            />
          </el-form-item>
          <el-form-item label="排序权重">
            <el-input-number v-model="sortOrder" :min="0" :max="999999" controls-position="right" />
            <span class="music-upload__tip">越大越靠前（与列表排序一致）</span>
          </el-form-item>
          <el-form-item label="选择文件">
            <el-upload
              ref="uploadRef"
              class="music-upload__uploader"
              drag
              :action="''"
              :auto-upload="false"
              :limit="1"
              :on-change="onFileChange"
              :on-remove="onFileRemove"
              :on-exceed="onExceed"
              accept=".mp3,.m4a,.wav,.ogg,.flac,audio/*"
            >
              <i class="el-icon-upload"></i>
              <div class="el-upload__text">将音频拖到此处，或<em>点击选择</em></div>
            </el-upload>
          </el-form-item>
          <el-form-item>
            <el-button type="primary" :loading="uploading" :disabled="!pendingFile" @click="doUpload">
              上传并入库
            </el-button>
          </el-form-item>
        </el-form>
      </div>

      <h3 class="music-upload__list-title">已上架曲库</h3>
      <p v-if="listLoading" class="music-upload__hint">加载中…</p>
      <p v-else-if="listError" class="music-upload__hint music-upload__hint--err">{{ listError }}</p>
      <!-- 窄屏用卡片：试听在首行全宽，避免表格最后一列被裁掉导致「点不到播放」 -->
      <div v-else-if="useCompactTrackList" class="music-upload__cards dp-lobby-panel">
        <article v-for="t in tracks" :key="t.id" class="music-upload__card">
          <audio
            v-if="t.webPath"
            :src="audioSrc(t.webPath)"
            controls
            preload="none"
            playsinline
            class="music-upload__audio music-upload__audio--card"
          />
          <div class="music-upload__card-title">{{ t.displayName || '（未命名）' }}</div>
          <div class="music-upload__card-meta">ID {{ t.id }} · 排序 {{ t.sortOrder != null ? t.sortOrder : '—' }}</div>
          <div class="music-upload__card-path" :title="t.webPath">{{ t.webPath }}</div>
        </article>
      </div>
      <div v-else class="music-upload__table-wrap dp-lobby-panel">
        <el-table :data="tracks" stripe border style="width: 100%">
          <el-table-column prop="id" label="ID" width="72" />
          <el-table-column prop="displayName" label="展示名" min-width="140" />
          <el-table-column prop="webPath" label="访问路径" min-width="200" show-overflow-tooltip />
          <el-table-column prop="sortOrder" label="排序" width="80" />
          <el-table-column label="试听" width="280">
            <template slot-scope="scope">
              <audio
                v-if="scope.row.webPath"
                :src="audioSrc(scope.row.webPath)"
                controls
                preload="none"
                playsinline
                class="music-upload__audio"
              />
            </template>
          </el-table-column>
        </el-table>
      </div>
    </div>
  </div>
</template>

<script>
import '@/styles/dp-game-themes.css'
import '@/styles/dp-lobby-shell.css'
import dpLobbyThemeMixin from '@/mixins/dpLobbyThemeMixin'

export default {
  name: 'MusicUpload',
  mixins: [dpLobbyThemeMixin],
  data() {
    return {
      user: {},
      displayName: '',
      sortOrder: 0,
      pendingFile: null,
      uploading: false,
      tracks: [],
      listLoading: true,
      listError: '',
      /** 与窄视口对齐：表格最后一列（试听）易溢出，改用紧凑列 */
      useCompactTrackList: false
    }
  },
  created() {
    try {
      this.user = JSON.parse(localStorage.getItem('userInfo') || '{}')
    } catch (e) {
      this.user = {}
    }
    this.loadList()
  },
  mounted() {
    /* 平板竖屏 / 手机横屏仍常 <900px 可用宽：宽表 + 试听列易溢出，与表格模式统一用卡片更稳 */
    this._mqCompact = window.matchMedia('(max-width: 1023px)')
    this._onMqCompact = () => {
      this.useCompactTrackList = this._mqCompact.matches
    }
    this._onMqCompact()
    if (this._mqCompact.addEventListener) {
      this._mqCompact.addEventListener('change', this._onMqCompact)
    } else {
      this._mqCompact.addListener(this._onMqCompact)
    }
  },
  beforeDestroy() {
    if (this._mqCompact && this._onMqCompact) {
      if (this._mqCompact.removeEventListener) {
        this._mqCompact.removeEventListener('change', this._onMqCompact)
      } else {
        this._mqCompact.removeListener(this._onMqCompact)
      }
    }
  },
  methods: {
    audioSrc(webPath) {
      if (!webPath) return ''
      var base = process.env.NODE_ENV === 'production' ? '' : '/dev-api'
      if (webPath.startsWith('http')) return webPath
      return base + webPath
    },
    goHome() {
      this.$router.push('/home')
    },
    onFileChange(file, fileList) {
      this.pendingFile = file && file.raw ? file.raw : null
      if (fileList.length > 1) fileList.splice(0, fileList.length - 1)
    },
    onFileRemove() {
      this.pendingFile = null
    },
    onExceed() {
      this.$message.warning('请先上传当前文件或刷新页面再选')
    },
    async loadList() {
      this.listLoading = true
      this.listError = ''
      try {
        const res = await this.$http.get('/dpMusic/list')
        this.tracks = Array.isArray(res.data) ? res.data : []
      } catch (e) {
        console.error('dpMusic/list', e)
        this.listError = '列表加载失败，请确认已建表 dp_music_track 且后端已启动。'
        this.tracks = []
      } finally {
        this.listLoading = false
      }
    },
    async doUpload() {
      if (!this.pendingFile) {
        this.$message.warning('请先选择音频文件')
        return
      }
      const fd = new FormData()
      fd.append('file', this.pendingFile)
      if (this.displayName && String(this.displayName).trim()) {
        fd.append('displayName', String(this.displayName).trim())
      }
      fd.append('sortOrder', String(this.sortOrder != null ? this.sortOrder : 0))
      if (this.user && this.user.userId != null && this.user.userId !== '') {
        fd.append('userId', String(this.user.userId))
      }
      this.uploading = true
      try {
        await this.$http.post('/dpMusic/upload', fd)
        this.$message.success('上传成功')
        this.pendingFile = null
        this.displayName = ''
        this.sortOrder = 0
        if (this.$refs.uploadRef) this.$refs.uploadRef.clearFiles()
        await this.loadList()
      } catch (e) {
        var msg = '上传失败'
        if (e.response && e.response.data) {
          if (typeof e.response.data === 'string') msg = e.response.data
          else if (e.response.data.error) msg = e.response.data.error
        }
        this.$message.error(msg)
      } finally {
        this.uploading = false
      }
    }
  }
}
</script>

<style scoped>
.music-upload {
  padding: 16px 0 28px;
  text-align: left;
  min-width: 0;
  max-width: 100%;
  box-sizing: border-box;
}
.music-upload__toolbar {
  margin-bottom: 8px;
}
.music-upload__theme-row {
  justify-content: flex-end;
}
.music-upload__header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
  flex-wrap: wrap;
  gap: 8px;
}
.music-upload__title {
  margin: 0;
  font-size: 1.25rem;
  font-weight: 600;
  color: var(--dp-text-primary);
}
.music-upload__hint {
  font-size: 13px;
  color: var(--dp-text-secondary);
  line-height: 1.5;
  margin-bottom: 16px;
}
.music-upload__hint--err {
  color: var(--dp-danger);
}
.music-upload__hint code {
  font-size: 12px;
  background: var(--dp-subpanel-bg);
  color: var(--dp-text-primary);
  padding: 2px 6px;
  border-radius: 4px;
  border: 1px solid var(--dp-subpanel-border);
}
.music-upload__panel {
  margin-top: 0;
  margin-bottom: 8px;
}
.music-upload__form {
  max-width: min(560px, 100%);
}
.music-upload__tip {
  margin-left: 8px;
  font-size: 12px;
  color: var(--dp-text-muted);
}
.music-upload__uploader {
  width: 100%;
}
.music-upload__list-title {
  margin: 24px 0 12px;
  font-size: 16px;
  font-weight: 600;
  color: var(--dp-text-primary);
}
.music-upload__table-wrap {
  padding: 12px;
  overflow-x: auto;
  overflow-y: visible;
  max-height: none;
  width: 100%;
  max-width: 100%;
  min-width: 0;
  -webkit-overflow-scrolling: touch;
  overscroll-behavior-x: contain;
  touch-action: pan-x pan-y;
}
.music-upload__cards {
  padding: 12px 14px;
}
.music-upload__card {
  padding: 14px 0;
  border-bottom: 1px solid var(--dp-subpanel-border);
}
.music-upload__card:last-child {
  border-bottom: none;
  padding-bottom: 4px;
}
.music-upload__card-title {
  font-size: 15px;
  font-weight: 600;
  color: var(--dp-text-primary);
  word-break: break-word;
  margin-top: 10px;
}
.music-upload__card-meta {
  font-size: 12px;
  color: var(--dp-text-muted);
  margin-top: 6px;
}
.music-upload__card-path {
  font-size: 11px;
  font-family: ui-monospace, monospace;
  color: var(--dp-text-secondary);
  word-break: break-all;
  margin-top: 8px;
  line-height: 1.45;
}
.music-upload__audio {
  width: 100%;
  max-width: 260px;
  height: 32px;
}
.music-upload__audio--card {
  display: block;
  max-width: none;
  width: 100%;
  min-height: 40px;
}
/* Element：链接色随主题（变量由 body / .dp-game-root 继承） */
.music-upload__home-link {
  color: var(--dp-accent) !important;
}
</style>

<style scoped>
/* Element 表格默认可能带内部滚动高度；大厅页由整页 body 滚动 */
.music-upload__table-wrap >>> .el-table {
  max-height: none !important;
  min-width: 720px;
}
.music-upload__table-wrap >>> .el-table__body-wrapper {
  max-height: none !important;
  overflow: visible !important;
}
</style>
