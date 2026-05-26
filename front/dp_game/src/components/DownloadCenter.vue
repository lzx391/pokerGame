<template>
  <div
    class="dp-game-root"
    :data-dp-game-theme="effectiveThemeForCss"
    :style="customThemeInlineStyle"
  >
    <div class="dp-lobby-inner dp-lobby-inner--wide download-center">
      <div class="download-center__toolbar">
        <div class="dp-game-theme-row download-center__theme-row">
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
      <div class="download-center__header">
        <h2 class="download-center__title">下载中心</h2>
        <div class="download-center__actions">
          <el-button type="text" class="download-center__home-link" @click="goHome">返回大厅</el-button>
        </div>
      </div>

      <p class="download-center__hint">
        {{ catCopy.downloadCenterHint }}
      </p>

      <div v-if="!isLoggedIn" class="download-center__hint download-center__hint--warn">
        未登录仅可浏览与下载；上传安装包请先登录。
      </div>

      <div v-if="isLoggedIn" class="dp-lobby-panel download-center__panel">
        <el-form label-width="100px" class="download-center__form" @submit.native.prevent>
          <el-form-item label="展示名称">
            <el-input
              v-model="displayName"
              placeholder="可选，默认用文件名（去扩展名）"
              clearable
            />
          </el-form-item>
          <el-form-item label="排序权重">
            <el-input-number v-model="sortOrder" :min="0" :max="999999" controls-position="right" />
            <span class="download-center__tip">越大越靠前（与列表排序一致）</span>
          </el-form-item>
          <el-form-item label="选择文件">
            <el-upload
              ref="uploadRef"
              class="download-center__uploader"
              drag
              :action="''"
              :auto-upload="false"
              :limit="1"
              :on-change="onFileChange"
              :on-remove="onFileRemove"
              :on-exceed="onExceed"
              accept=".exe,.apk,.msi,.zip"
            >
              <i class="el-icon-upload"></i>
              <div class="el-upload__text">将安装包拖到此处，或<em>点击选择</em></div>
            </el-upload>
          </el-form-item>
          <el-form-item>
            <el-button type="primary" :loading="uploading" :disabled="!pendingFile" @click="doUpload">
              上传并入库
            </el-button>
          </el-form-item>
        </el-form>
      </div>

      <h3 class="download-center__list-title">可下载安装包</h3>
      <p v-if="listLoading" class="download-center__hint">加载中…</p>
      <p v-else-if="listError" class="download-center__hint download-center__hint--err">{{ listError }}</p>
      <div v-else-if="useCompactList" class="download-center__cards dp-lobby-panel">
        <article v-for="a in assets" :key="a.id" class="download-center__card">
          <div class="download-center__card-title">{{ a.displayName || '（未命名）' }}</div>
          <div class="download-center__card-meta">ID {{ a.id }} · 排序 {{ a.sortOrder != null ? a.sortOrder : '—' }}</div>
          <div class="download-center__card-path" :title="a.webPath">{{ a.webPath }}</div>
          <a
            v-if="a.webPath"
            class="dp-btn dp-btn--primary download-center__dl-btn"
            :href="fileSrc(a.webPath)"
            :download="downloadFilename(a)"
          >下载</a>
        </article>
      </div>
      <div v-else class="download-center__table-wrap dp-lobby-panel">
        <el-table :data="assets" stripe border style="width: 100%">
          <el-table-column prop="id" label="ID" width="72" />
          <el-table-column prop="displayName" label="展示名" min-width="140" />
          <el-table-column prop="webPath" label="访问路径" min-width="200" show-overflow-tooltip />
          <el-table-column prop="sortOrder" label="排序" width="80" />
          <el-table-column label="操作" width="120">
            <template slot-scope="scope">
              <a
                v-if="scope.row.webPath"
                class="download-center__dl-link"
                :href="fileSrc(scope.row.webPath)"
                :download="downloadFilename(scope.row)"
              >下载</a>
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
import { CAT_COPY } from '@/constants/dpCatThemeCopy'
import { downloadFileSrc } from '@/utils/dpDownloadFileUrl'

export default {
  name: 'DownloadCenter',
  mixins: [dpLobbyThemeMixin],
  data() {
    return {
      catCopy: CAT_COPY,
      user: {},
      displayName: '',
      sortOrder: 0,
      pendingFile: null,
      uploading: false,
      assets: [],
      listLoading: true,
      listError: '',
      useCompactList: false
    }
  },
  computed: {
    isLoggedIn() {
      return !!(this.user && this.user.userId != null && this.user.userId !== '')
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
    this._mqCompact = window.matchMedia('(max-width: 1023px)')
    this._onMqCompact = () => {
      this.useCompactList = this._mqCompact.matches
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
    fileSrc(webPath) {
      return downloadFileSrc(webPath)
    },
    downloadFilename(row) {
      var name = (row && row.displayName) ? String(row.displayName).trim() : ''
      if (!name) return ''
      var path = row && row.webPath ? String(row.webPath) : ''
      var ext = ''
      var dot = path.lastIndexOf('.')
      if (dot >= 0) ext = path.substring(dot)
      if (ext && name.toLowerCase().indexOf(ext.toLowerCase()) === name.length - ext.length) {
        return name
      }
      return name + ext
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
        const res = await this.$http.get('/dpDownload/list')
        this.assets = Array.isArray(res.data) ? res.data : []
      } catch (e) {
        console.error('dpDownload/list', e)
        this.listError = CAT_COPY.downloadListLoadFailed
        this.assets = []
      } finally {
        this.listLoading = false
      }
    },
    async doUpload() {
      if (!this.isLoggedIn) {
        this.$message.warning('请先登录后再上传')
        return
      }
      if (!this.pendingFile) {
        this.$message.warning('请先选择安装包文件')
        return
      }
      const fd = new FormData()
      fd.append('file', this.pendingFile)
      if (this.displayName && String(this.displayName).trim()) {
        fd.append('displayName', String(this.displayName).trim())
      }
      fd.append('sortOrder', String(this.sortOrder != null ? this.sortOrder : 0))
      fd.append('userId', String(this.user.userId))
      this.uploading = true
      try {
        await this.$http.post('/dpDownload/upload', fd)
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
.download-center {
  padding: 16px 0 28px;
  text-align: left;
  min-width: 0;
  max-width: 100%;
  box-sizing: border-box;
}
.download-center__toolbar {
  margin-bottom: 8px;
}
.download-center__theme-row {
  justify-content: flex-end;
}
.download-center__header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
  flex-wrap: wrap;
  gap: 8px;
}
.download-center__title {
  margin: 0;
  font-size: 1.25rem;
  font-weight: 600;
  color: var(--dp-text-primary);
}
.download-center__hint {
  font-size: 13px;
  color: var(--dp-text-secondary);
  line-height: 1.5;
  margin-bottom: 16px;
}
.download-center__hint--warn {
  color: var(--dp-accent);
}
.download-center__hint--err {
  color: var(--dp-danger);
}
.download-center__panel {
  margin-top: 0;
  margin-bottom: 8px;
}
.download-center__form {
  max-width: min(560px, 100%);
}
.download-center__tip {
  margin-left: 8px;
  font-size: 12px;
  color: var(--dp-text-muted);
}
.download-center__uploader {
  width: 100%;
}
.download-center__list-title {
  margin: 24px 0 12px;
  font-size: 16px;
  font-weight: 600;
  color: var(--dp-text-primary);
}
.download-center__table-wrap {
  padding: 12px;
  overflow-x: auto;
  max-width: 100%;
  min-width: 0;
}
.download-center__cards {
  padding: 12px 14px;
}
.download-center__card {
  padding: 14px 0;
  border-bottom: 1px solid var(--dp-subpanel-border);
}
.download-center__card:last-child {
  border-bottom: none;
}
.download-center__card-title {
  font-size: 15px;
  font-weight: 600;
  color: var(--dp-text-primary);
  word-break: break-word;
}
.download-center__card-meta {
  font-size: 12px;
  color: var(--dp-text-muted);
  margin-top: 6px;
}
.download-center__card-path {
  font-size: 11px;
  font-family: ui-monospace, monospace;
  color: var(--dp-text-secondary);
  word-break: break-all;
  margin-top: 8px;
}
.download-center__dl-btn {
  margin-top: 12px;
  display: inline-block;
  text-decoration: none;
}
.download-center__dl-link {
  color: var(--dp-accent);
  font-weight: 600;
  text-decoration: none;
}
.download-center__dl-link:hover {
  text-decoration: underline;
}
.download-center__home-link {
  color: var(--dp-accent) !important;
}
</style>

<style scoped>
.download-center__table-wrap >>> .el-table {
  max-height: none !important;
  min-width: 640px;
}
.download-center__table-wrap >>> .el-table__body-wrapper {
  max-height: none !important;
  overflow: visible !important;
}
</style>
