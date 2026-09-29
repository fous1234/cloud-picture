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