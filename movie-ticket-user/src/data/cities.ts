/**
 * 城市选择相关类型与本地兜底数据。
 *
 * 全国城市列表优先通过高德 AMap.DistrictSearch 动态获取（含中心点坐标）；
 * 高德不可用时回退到这里的常用城市（省会/直辖市 + 广西城市），保证选择器仍可用。
 */
export interface CityOption {
  name: string
  lng: number
  lat: number
}

export interface ProvinceCities {
  province: string
  cities: CityOption[]
}

/** 高德行政区查询失败时的兜底城市。 */
export const CITY_OPTIONS: CityOption[] = [
  { name: '北京市', lng: 116.4074, lat: 39.9042 },
  { name: '天津市', lng: 117.2008, lat: 39.0842 },
  { name: '上海市', lng: 121.4737, lat: 31.2304 },
  { name: '重庆市', lng: 106.5516, lat: 29.563 },
  { name: '石家庄市', lng: 114.5149, lat: 38.0428 },
  { name: '太原市', lng: 112.5489, lat: 37.8706 },
  { name: '呼和浩特市', lng: 111.7492, lat: 40.8426 },
  { name: '沈阳市', lng: 123.4315, lat: 41.8057 },
  { name: '长春市', lng: 125.3235, lat: 43.8171 },
  { name: '哈尔滨市', lng: 126.5349, lat: 45.8038 },
  { name: '南京市', lng: 118.7969, lat: 32.0603 },
  { name: '杭州市', lng: 120.1551, lat: 30.2741 },
  { name: '合肥市', lng: 117.2272, lat: 31.8206 },
  { name: '福州市', lng: 119.2965, lat: 26.0745 },
  { name: '南昌市', lng: 115.8582, lat: 28.6829 },
  { name: '济南市', lng: 117.1201, lat: 36.6512 },
  { name: '郑州市', lng: 113.6254, lat: 34.7466 },
  { name: '武汉市', lng: 114.3054, lat: 30.5931 },
  { name: '长沙市', lng: 112.9388, lat: 28.2282 },
  { name: '广州市', lng: 113.2644, lat: 23.1291 },
  { name: '南宁市', lng: 108.3665, lat: 22.817 },
  { name: '海口市', lng: 110.1983, lat: 20.044 },
  { name: '成都市', lng: 104.0665, lat: 30.5723 },
  { name: '贵阳市', lng: 106.6302, lat: 26.6477 },
  { name: '昆明市', lng: 102.8329, lat: 24.8801 },
  { name: '拉萨市', lng: 91.1409, lat: 29.6456 },
  { name: '西安市', lng: 108.9398, lat: 34.3416 },
  { name: '兰州市', lng: 103.8343, lat: 36.0611 },
  { name: '西宁市', lng: 101.7782, lat: 36.6171 },
  { name: '银川市', lng: 106.2309, lat: 38.4872 },
  { name: '乌鲁木齐市', lng: 87.6168, lat: 43.8256 },
  { name: '香港特别行政区', lng: 114.1694, lat: 22.3193 },
  { name: '澳门特别行政区', lng: 113.5439, lat: 22.1987 },
  { name: '柳州市', lng: 109.4155, lat: 24.3255 },
  { name: '桂林市', lng: 110.29, lat: 25.2736 },
  { name: '北海市', lng: 109.1193, lat: 21.4733 },
  { name: '玉林市', lng: 110.164, lat: 22.6293 },
  { name: '钦州市', lng: 108.6247, lat: 21.9671 },
  { name: '防城港市', lng: 108.3547, lat: 21.6867 },
  { name: '贵港市', lng: 109.5989, lat: 23.1115 },
  { name: '百色市', lng: 106.6163, lat: 23.9022 },
  { name: '贺州市', lng: 111.5526, lat: 24.4034 },
  { name: '河池市', lng: 108.085, lat: 24.6929 },
  { name: '来宾市', lng: 109.2214, lat: 23.7503 },
  { name: '崇左市', lng: 107.3539, lat: 22.4041 }
]
