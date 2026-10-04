export function formatSize(bytes: number | null | undefined): string {
  if (bytes === null || bytes === undefined || bytes <= 0) return '—'
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  return `${(bytes / 1024 / 1024).toFixed(2)} MB`
}

export function formatDateTime(value: string | null | undefined): string {
  if (!value) return '—'
  return value.replace('T', ' ').slice(0, 16)
}

export function formatDimensions(
  width: number | null | undefined,
  height: number | null | undefined,
): string {
  return width && height ? `${width} × ${height}` : '—'
}

/** 相对时间：刚刚 / X分钟前 / X小时前 / X天前，超过 7 天回落到日期 */
export function formatRelativeTime(value: string | null | undefined): string {
  if (!value) return '—'
  const time = new Date(value.replace(' ', 'T')).getTime()
  if (Number.isNaN(time)) return '—'
  const diff = Date.now() - time
  if (diff < 60_000) return '刚刚'
  const minutes = Math.floor(diff / 60_000)
  if (minutes < 60) return `${minutes}分钟前`
  const hours = Math.floor(minutes / 60)
  if (hours < 24) return `${hours}小时前`
  const days = Math.floor(hours / 24)
  if (days < 7) return `${days}天前`
  return formatDateTime(value).slice(0, 10)
}