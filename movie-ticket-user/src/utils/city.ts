/**
 * 城市名称处理：地址解析与“市”后缀归一化，供首页和影院页按城市过滤。
 */

/** 从“广西壮族自治区南宁市青秀区…”这类地址中提取最后一个“XX市”。 */
export const cityOfAddress = (address: string) => {
  const matched = address.match(/[^省市自治区]+市/g)
  return matched?.length ? matched[matched.length - 1] : ''
}

/** 去掉末尾“市”，便于“南宁”和“南宁市”互相匹配。 */
export const normalizeCityName = (name?: string) => (name || '').replace(/市$/, '').trim()
