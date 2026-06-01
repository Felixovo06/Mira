import { useEffect, useRef, useState } from 'react'
import {
  deleteDocument,
  documentDownloadUrl,
  getDocuments,
  uploadDocument,
} from '../api'
import type { DocumentInfo } from '../types'
import './DocumentsView.css'

function fmtSize(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(0)} KB`
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`
}

function fmtTime(iso: string): string {
  if (!iso) return ''
  try {
    return new Date(iso).toLocaleString()
  } catch {
    return iso
  }
}

function extOf(name: string): string {
  const dot = name.lastIndexOf('.')
  return dot >= 0 ? name.slice(dot + 1).toUpperCase() : 'FILE'
}

export default function DocumentsView() {
  const [items, setItems] = useState<DocumentInfo[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState<string | null>(null)
  const [uploading, setUploading] = useState(false)
  const [dragOver, setDragOver] = useState(false)
  const fileInput = useRef<HTMLInputElement>(null)

  async function refresh() {
    setError(null)
    try {
      setItems(await getDocuments())
    } catch (e) {
      setError(String(e))
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    void refresh()
  }, [])

  async function doUpload(files: FileList | null) {
    if (!files || files.length === 0) return
    setUploading(true)
    setError(null)
    try {
      for (const f of Array.from(files)) {
        await uploadDocument(f)
      }
      await refresh()
    } catch (e) {
      setError(String(e))
    } finally {
      setUploading(false)
      if (fileInput.current) fileInput.current.value = ''
    }
  }

  async function del(d: DocumentInfo) {
    if (!confirm(`删除文档「${d.name}」？`)) return
    setBusy(d.name)
    try {
      await deleteDocument(d.name)
      await refresh()
    } catch (e) {
      setError(String(e))
    } finally {
      setBusy(null)
    }
  }

  return (
    <div className="docs glass">
      <header className="docs-head">
        <div>
          <h1 className="docs-title">文档</h1>
          <p className="docs-sub">
            {loading ? '加载中…' : `${items.length} 个文档 · Agent 可读取与编辑`}
          </p>
        </div>
        <div className="docs-actions">
          <button className="btn" onClick={() => void refresh()}>刷新</button>
          <button
            className="btn btn-primary"
            onClick={() => fileInput.current?.click()}
            disabled={uploading}
          >
            {uploading ? '上传中…' : '上传文档'}
          </button>
          <input
            ref={fileInput}
            type="file"
            multiple
            hidden
            onChange={(e) => void doUpload(e.target.files)}
          />
        </div>
      </header>

      {error && <div className="docs-error">{error}</div>}

      <div
        className={`docs-drop ${dragOver ? 'over' : ''}`}
        onDragOver={(e) => {
          e.preventDefault()
          setDragOver(true)
        }}
        onDragLeave={() => setDragOver(false)}
        onDrop={(e) => {
          e.preventDefault()
          setDragOver(false)
          void doUpload(e.dataTransfer.files)
        }}
      >
        <span>把文件拖到这里上传，或点右上角「上传文档」</span>
        <small>支持 PDF / Word / Excel / PPT / 文本等；Agent 解析后可总结、改写并生成新文档</small>
      </div>

      <div className="docs-scroll">
        {!loading && items.length === 0 && (
          <div className="docs-empty">
            <div className="empty-ring" />
            <p>工作区还没有文档</p>
            <span>上传一份资料，在对话里让 Agent 读取或编辑它。</span>
          </div>
        )}

        {items.map((d) => (
          <div key={d.name} className="doc-card">
            <span className="doc-ext">{extOf(d.name)}</span>
            <div className="doc-meta">
              <p className="doc-name" title={d.name}>{d.name}</p>
              <span className="doc-info">{fmtSize(d.size)} · {fmtTime(d.modified)}</span>
            </div>
            <div className="doc-ops">
              <a
                className="doc-dl"
                href={documentDownloadUrl(d.name)}
                title="下载"
              >
                下载
              </a>
              <button
                className="doc-del"
                title="删除"
                onClick={() => void del(d)}
                disabled={busy === d.name}
              >
                {busy === d.name ? '…' : '删除'}
              </button>
            </div>
          </div>
        ))}
      </div>
    </div>
  )
}
