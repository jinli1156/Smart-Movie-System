import request from '@/utils/request'

export type ListingKind = 'MOVIE' | 'CINEMA' | 'HALL'
export type ListingStatus = 'DRAFT' | 'REJECTED' | 'PUBLISHED'
export interface ListingField { key: string; label: string; type: string; required: boolean; maxLength: number }
export interface ListingCapabilities { textConfigured: boolean; visionConfigured: boolean; schemas: Partial<Record<ListingKind, ListingField[]>> }
export interface ListingSummary { id: string; kind: ListingKind; status: ListingStatus; title: string; extractionMode: string; updatedAt: string }
export interface ListingDraft {
  id: string; kind: ListingKind; batchId?: string; cinemaId?: number; status: ListingStatus; fields: Record<string, any>; extracted: Record<string, any>
  sourceText: string; sourceNames: string[]; sourceImages: string[]; extractionMode: string; extractionWarning: string; reviewNote: string
  entityId?: number; version: number; ownerId: number; reviewerId?: number; updatedAt: string; reviewedAt?: string
  issues: string[]; history: Array<{ action: string; note: string; operatorId: number; createdAt: string; version: number }>
}
export interface ListingImport { id: string; kind: ListingKind; total: number; drafts: ListingDraft[]; extractionMode: string; warning: string }
export const listingApi = {
  template: (kind: ListingKind, format: 'xlsx' | 'csv' | 'json') => request.get<any, Blob>(`/listing/templates/${kind}`, { params: { format }, responseType: 'blob' }),
  capabilities: () => request.get<any, ListingCapabilities>('/listing/capabilities'),
  list: (params: { kind?: string; status?: string; page: number; size: number }) => request.get<any, { records: ListingSummary[]; total: number }>('/listing/drafts', { params }),
  get: (id: string) => request.get<any, ListingDraft>(`/listing/drafts/${id}`),
  create: (data: FormData) => request.post<any, ListingImport>('/listing/import', data, { timeout: 100_000 }),
  batch: (id: string) => request.get<any, ListingSummary[]>(`/listing/batches/${id}`),
  save: (id: string, version: number, fields: Record<string, any>) => request.put<any, ListingDraft>(`/listing/drafts/${id}`, { version, fields }),
  reject: (id: string, version: number, note: string) => request.post<any, ListingDraft>(`/listing/drafts/${id}/reject`, { version, note }),
  publish: (id: string, version: number) => request.post<any, ListingDraft>(`/listing/drafts/${id}/publish`, { version, acknowledged: true })
}
