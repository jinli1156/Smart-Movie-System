import request from '@/utils/request'

export interface MarketingMovie { id: number; title: string; genre?: string; description?: string }
export interface MarketingCampaign { id: string; name: string; brief: string; audience: string; cinemaId: number; movieSnapshot: MarketingMovie[]; styles: string[]; ratios: string[]; createdAt: string }
export interface MarketingPosterContent { movieId: number; title: string; copy: string }
export interface MarketingTask { id: string; batchId: string; movieId: number; kind: 'COPY' | 'IMAGE'; style?: string; ratio?: string; status: string; attempts: number; providerCalls: number; errorMessage?: string; durationMs?: number; result?: { titles?: string[]; shortCopy?: string; assetId?: string; prompt?: string; promptModel?: string; posterContent?: MarketingPosterContent }; model?: string }
export interface MarketingAsset { id: string; movieId: number; previewUrl: string; style: string; ratio: string; width: number; height: number; sourceType: string; model: string; parentAssetId?: string }
export interface MarketingVersion { id: string; movieId: number; version: number; status: string; payload: { title: string; copy: string; assetId: string; cta: string }; createdAt: string }
export interface MarketingPublication { id: string; contentVersionId: string; slot: 'HOME' | 'CINEMA'; startAt: string; endAt: string; status: string }
export interface MarketingDetail extends MarketingCampaign { tasks: MarketingTask[]; assets: MarketingAsset[]; versions: MarketingVersion[]; publications: MarketingPublication[]; logs: { id: string; action: string; reason?: string; createdAt: string }[] }
export interface MarketingCapabilities { imageConfigured: boolean; textConfigured: boolean; posterContentRequired: boolean; imageModel: string; imageProvider?: string; maxImagesPerBatch: number; dailyImageLimit: number; styles: string[]; ratios: string[] }
const base = '/admin/marketing'
export const marketingApi = {
  capabilities: () => request.get<any, MarketingCapabilities>(base + '/capabilities'),
  campaigns: () => request.get<any, MarketingCampaign[]>(base + '/campaigns'),
  detail: (id: string) => request.get<any, MarketingDetail>(base + '/campaigns/' + id),
  create: (data: unknown) => request.post<any, MarketingDetail>(base + '/campaigns', data),
  generate: (id: string, kind: 'COPY' | 'IMAGE', requestKey: string, imageCount?: number, posterContent?: MarketingPosterContent) => request.post(base + '/campaigns/' + id + '/batches', { kind, requestKey, imageCount, posterContent }),
  retry: (id: string, acknowledgePossibleCharge: boolean) => request.post(base + '/tasks/' + id + '/retry', { acknowledgePossibleCharge }),
  save: (id: string, data: unknown) => request.post<any, MarketingVersion>(base + '/campaigns/' + id + '/versions', data),
  submit: (id: string) => request.post(base + '/versions/' + id + '/submit-review'),
  review: (id: string, approve: boolean, reason: string) => request.post(base + '/versions/' + id + '/review', { approve, reason }),
  publish: (id: string, data: unknown) => request.post(base + '/versions/' + id + '/publish', data),
  offline: (id: string) => request.post(base + '/publications/' + id + '/offline')
}
export const marketingStatus: Record<string, string> = {
  QUEUED: '排队中', RUNNING: '处理中', PLANNING: '构思画面中', SUBMITTING: '生成中', PERSISTING: '保存素材中',
  SUCCEEDED: '已完成', FAILED: '失败', SUBMIT_UNKNOWN: '结果待核实',
  DRAFT: '草稿', PENDING_REVIEW: '待审核', APPROVED: '审核通过', REJECTED: '已退回', PUBLISHED: '已发布', OFFLINE: '已撤下'
}
