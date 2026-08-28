package com.movie.ticket.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.movie.ticket.dto.CommentCreateDTO;
import com.movie.ticket.dto.CommentUpdateDTO;
import com.movie.ticket.entity.Comment;
import com.movie.ticket.entity.Movie;
import com.movie.ticket.entity.Showtime;
import com.movie.ticket.entity.TicketOrder;
import com.movie.ticket.entity.User;
import com.movie.ticket.exception.BusinessException;
import com.movie.ticket.mapper.CommentMapper;
import com.movie.ticket.mapper.MovieMapper;
import com.movie.ticket.mapper.ShowtimeMapper;
import com.movie.ticket.mapper.TicketOrderMapper;
import com.movie.ticket.mapper.UserMapper;
import com.movie.ticket.security.UserContext;
import com.movie.ticket.vo.CommentVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class CommentService {

    private final CommentMapper commentMapper;
    private final TicketOrderMapper orderMapper;
    private final ShowtimeMapper showtimeMapper;
    private final MovieMapper movieMapper;
    private final UserMapper userMapper;

    /**
     * 判断当前用户是否具备评价资格：已购票（已支付）且场次已经结束。
     * 返回符合条件的订单，找不到则返回 null。
     */
    private TicketOrder findWatchedOrder(Long userId, Long movieId) {
        List<TicketOrder> paidOrders = orderMapper.selectList(
                new LambdaQueryWrapper<TicketOrder>()
                        .eq(TicketOrder::getUserId, userId)
                        .eq(TicketOrder::getStatus, 1)
        );

        LocalDateTime now = LocalDateTime.now();
        for (TicketOrder order : paidOrders) {
            Showtime showtime = showtimeMapper.selectById(order.getShowtimeId());
            if (showtime != null
                    && movieId.equals(showtime.getMovieId())
                    && showtime.getEndTime() != null
                    && showtime.getEndTime().isBefore(now)) {
                return order;
            }
        }
        return null;
    }

    /**
     * 评价资格查询：返回 canComment（是否可评价）、watched（是否已观影）、commented（是否已评价）。
     */
    public Map<String, Object> checkEligibility(Long movieId) {
        Long userId = UserContext.getUserId();

        Comment ownComment = commentMapper.selectOne(
                new LambdaQueryWrapper<Comment>()
                        .eq(Comment::getUserId, userId)
                        .eq(Comment::getMovieId, movieId)
                        .last("LIMIT 1")
        );
        boolean commented = ownComment != null;

        boolean watched = findWatchedOrder(userId, movieId) != null;

        Map<String, Object> result = new HashMap<>();
        result.put("watched", watched);
        result.put("commented", commented);
        result.put("canComment", watched && !commented);
        result.put("ownComment", ownComment == null ? null : convertToVO(ownComment));
        return result;
    }

    @Transactional(rollbackFor = Exception.class)
    public CommentVO createComment(CommentCreateDTO dto) {
        Long userId = UserContext.getUserId();

        boolean commented = commentMapper.selectCount(
                new LambdaQueryWrapper<Comment>()
                        .eq(Comment::getUserId, userId)
                        .eq(Comment::getMovieId, dto.getMovieId())
        ) > 0;
        if (commented) {
            throw new BusinessException("您已评价过该电影");
        }

        TicketOrder watchedOrder = findWatchedOrder(userId, dto.getMovieId());
        if (watchedOrder == null) {
            throw new BusinessException("购票观影后才能评价");
        }

        Comment comment = new Comment();
        comment.setMovieId(dto.getMovieId());
        comment.setUserId(userId);
        comment.setOrderId(watchedOrder.getId());
        comment.setRating(dto.getRating());
        comment.setContent(dto.getContent());
        commentMapper.insert(comment);

        refreshMovieRating(dto.getMovieId());

        return convertToVO(comment);
    }

    @Transactional(rollbackFor = Exception.class)
    public CommentVO updateComment(Long id, CommentUpdateDTO dto) {
        Long userId = UserContext.getUserId();
        Comment comment = commentMapper.selectById(id);
        if (comment == null) {
            throw new BusinessException("评价不存在");
        }
        if (!userId.equals(comment.getUserId())) {
            throw new BusinessException(403, "无权修改该评价");
        }

        comment.setRating(dto.getRating());
        comment.setContent(dto.getContent());
        commentMapper.updateById(comment);
        refreshMovieRating(comment.getMovieId());
        return convertToVO(comment);
    }

    public List<CommentVO> listByMovie(Long movieId) {
        List<Comment> comments = commentMapper.selectList(
                new LambdaQueryWrapper<Comment>()
                        .eq(Comment::getMovieId, movieId)
                        .orderByDesc(Comment::getCreateTime)
        );
        List<CommentVO> result = new ArrayList<>();
        for (Comment comment : comments) {
            result.add(convertToVO(comment));
        }
        return result;
    }

    /**
     * 根据全部评价的平均星级（1-5）重新计算电影评分（0-10 分制）。
     */
    private void refreshMovieRating(Long movieId) {
        List<Comment> comments = commentMapper.selectList(
                new LambdaQueryWrapper<Comment>()
                        .eq(Comment::getMovieId, movieId)
        );
        if (comments.isEmpty()) {
            return;
        }
        int sum = 0;
        for (Comment comment : comments) {
            sum += comment.getRating();
        }
        BigDecimal avgStars = BigDecimal.valueOf(sum)
                .divide(BigDecimal.valueOf(comments.size()), 2, RoundingMode.HALF_UP);
        BigDecimal rating = avgStars.multiply(BigDecimal.valueOf(2))
                .setScale(1, RoundingMode.HALF_UP);

        Movie movie = movieMapper.selectById(movieId);
        if (movie != null) {
            movie.setRating(rating);
            movieMapper.updateById(movie);
        }
    }

    private CommentVO convertToVO(Comment comment) {
        CommentVO vo = new CommentVO();
        vo.setId(comment.getId());
        vo.setMovieId(comment.getMovieId());
        vo.setUserId(comment.getUserId());
        vo.setRating(comment.getRating());
        vo.setContent(comment.getContent());
        vo.setCreateTime(comment.getCreateTime());

        User user = userMapper.selectById(comment.getUserId());
        if (user != null) {
            vo.setUserName(user.getNickname());
            vo.setUserAvatar(user.getAvatar());
        }
        return vo;
    }
}
