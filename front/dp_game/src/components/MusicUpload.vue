<template>
  <div class="music-upload">
    <div class="music-upload__header">
      <h2>曲库上传</h2>
      <div class="music-upload__actions">
        <el-button type="text" @click="goHome">返回大厅</el-button>
      </div>
    </div>

    <p class="music-upload__hint">
      音频保存在服务器目录，入库到 <code>dp_music_track</code>；支持 mp3、m4a、wav、ogg、flac，单文件建议不超过 80MB。
    </p>

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

    <h3 class="music-upload__list-title">已上架曲库</h3>
    <p v-if="listLoading" class="music-upload__hint">加载中…</p>
    <p v-else-if="listError" class="music-upload__hint music-upload__hint--err">{{ listError }}</p>
    <el-table v-else :data="tracks" stripe border style="width: 100%">
      <el-table-column prop="id" label="ID" width="72" />
      <el-table-column prop="displayName" label="展示名" min-width="140" />
      <el-table-column prop="webPath" label="访问路径" min-width="200" show-overflow-tooltip />
      <el-table-column prop="sortOrder" label="排序" width="80" />
      <el-table-column label="试听" width="280">
        <template slot-scope="scope">
          <audio v-if="scope.row.webPath" :src="audioSrc(scope.row.webPath)" controls preload="none" class="music-upload__audio" />
        </template>
      </el-table-column>
    </el-table>
  </div>
</template>

<script>
export default {
  name: 'MusicUpload',
  data() {
    return {
      user: {},
      displayName: '',
      sortOrder: 0,
      pendingFile: null,
      uploading: false,
      tracks: [],
      listLoading: true,
      listError: ''
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
  max-width: 880px;
  margin: 0 auto;
  padding: 20px;
  text-align: left;
}
.music-upload__header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}
.music-upload__hint {
  font-size: 13px;
  color: #606266;
  line-height: 1.5;
  margin-bottom: 16px;
}
.music-upload__hint--err {
  color: #f56c6c;
}
.music-upload__hint code {
  font-size: 12px;
  background: #f4f4f5;
  padding: 2px 6px;
  border-radius: 4px;
}
.music-upload__form {
  max-width: 560px;
}
.music-upload__tip {
  margin-left: 8px;
  font-size: 12px;
  color: #909399;
}
.music-upload__uploader {
  width: 100%;
}
.music-upload__list-title {
  margin: 24px 0 12px;
  font-size: 16px;
}
.music-upload__audio {
  width: 100%;
  max-width: 260px;
  height: 32px;
}
</style>
