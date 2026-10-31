export interface Cinema {
  id?: number
  name: string
  address: string
  lng?: number
  lat?: number
  phone?: string
  description?: string
  images?: string
  status?: number
  createTime?: string
  updateTime?: string
}

export interface Hall {
  id?: number
  cinemaId: number
  cinemaName?: string
  name: string
  totalRows: number
  totalCols: number
  seatLayout: string
  status?: number
  createTime?: string
  updateTime?: string
}

export interface Movie {
  id?: number
  title: string
  englishTitle?: string
  director?: string
  actors?: string
  genre?: string
  country?: string
  language?: string
  duration: number
  releaseDate?: string
  poster?: string
  trailer?: string
  description?: string
  rating?: number
  status?: number
  statusText?: string
  createTime?: string
  updateTime?: string
}

export interface Showtime {
  id?: number
  movieId: number
  movieTitle?: string
  moviePoster?: string
  movieDuration?: number
  cinemaId: number
  cinemaName?: string
  hallId: number
  hallName?: string
  startTime: string
  endTime: string
  basePrice: number
  totalSeats?: number
  availableSeats?: number
  status?: number
  statusText?: string
  createTime?: string
  updateTime?: string
}

export interface PageResult<T> {
  records: T[]
  total: number
  size: number
  current: number
  pages: number
}

export interface Order {
  isPicked?: number
  pickTime?: string
  ticketCode?: string
  id?: number
  orderNo: string
  userId?: number
  userName?: string
  showtimeId?: number
  movieTitle?: string
  moviePoster?: string
  cinemaName?: string
  hallName?: string
  showtime?: string
  seats?: string
  seatCount?: number
  totalPrice?: number
  status?: number
  statusText?: string
  payTime?: string
  remark?: string
  createTime?: string
  updateTime?: string
}

export interface User {
  cinemaId?: number | null
  id?: number
  username: string
  password?: string
  nickname?: string
  phone?: string
  email?: string
  avatar?: string
  role?: string
  status?: number
  createTime?: string
  updateTime?: string
}

export interface CouponCode {
  id?: number
  code: string
  name: string
  amount: number
  minSpend: number
  validDays: number
  maxRedemptions: number
  redeemedCount?: number
  validFrom: string
  expireTime: string
  status: number
  createTime?: string
  updateTime?: string
}

export interface CouponRedemption {
  id: number
  couponCodeId: number
  code: string
  couponName: string
  userId: number
  username: string
  nickname?: string
  userCouponId: number
  couponStatus: number
  redeemTime: string
  useTime?: string
}
