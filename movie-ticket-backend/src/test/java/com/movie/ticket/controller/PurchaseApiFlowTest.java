package com.movie.ticket.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.movie.ticket.config.SecurityConfig;
import com.movie.ticket.dto.LoginRequest;
import com.movie.ticket.exception.GlobalExceptionHandler;
import com.movie.ticket.exception.BusinessException;
import com.movie.ticket.mapper.CinemaMapper;
import com.movie.ticket.mapper.CommentMapper;
import com.movie.ticket.mapper.CouponCodeMapper;
import com.movie.ticket.mapper.CouponCodeRedemptionMapper;
import com.movie.ticket.mapper.HallMapper;
import com.movie.ticket.mapper.MovieMapper;
import com.movie.ticket.mapper.ShowtimeMapper;
import com.movie.ticket.mapper.TicketOrderMapper;
import com.movie.ticket.mapper.UserCouponMapper;
import com.movie.ticket.mapper.UserMapper;
import com.movie.ticket.mapper.UserMovieBehaviorMapper;
import com.movie.ticket.security.JwtAuthenticationFilter;
import com.movie.ticket.service.AuthService;
import com.movie.ticket.service.OrderService;
import com.movie.ticket.utils.JwtUtil;
import com.movie.ticket.vo.LoginResponse;
import com.movie.ticket.vo.OrderVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest({AuthController.class, OrderController.class})
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, JwtUtil.class, GlobalExceptionHandler.class})
@TestPropertySource(properties = {
        "jwt.secret=integration-test-secret-must-be-at-least-thirty-two-bytes-long",
        "jwt.expiration=3600000"
})
@MockBean(classes = {
        CinemaMapper.class, CommentMapper.class, HallMapper.class, MovieMapper.class,
        ShowtimeMapper.class, TicketOrderMapper.class, UserCouponMapper.class,
        UserMapper.class, UserMovieBehaviorMapper.class, CouponCodeMapper.class,
        CouponCodeRedemptionMapper.class
})
class PurchaseApiFlowTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtUtil jwtUtil;

    @MockBean
    private AuthService authService;
    @MockBean private com.movie.ticket.service.LoginCaptchaService loginCaptchaService;

    @MockBean
    private OrderService orderService;

    private String token;
    @Autowired private UserMapper authenticationUsers;

    @BeforeEach
    void setUp() {
        var buyer = new com.movie.ticket.entity.User(); buyer.setId(7L); buyer.setUsername("buyer"); buyer.setRole("USER"); buyer.setStatus(1);
        when(authenticationUsers.selectById(7L)).thenReturn(buyer);
        token = jwtUtil.generateToken(7L, "buyer", "USER");

        LoginResponse.UserInfo userInfo = new LoginResponse.UserInfo();
        userInfo.setId(7L);
        userInfo.setUsername("buyer");
        userInfo.setNickname("接口测试用户");
        userInfo.setRole("USER");
        when(authService.login(any(LoginRequest.class)))
                .thenReturn(new LoginResponse(token, userInfo));

        when(orderService.createOrder(any())).thenReturn("ORDER-API-001");

        OrderVO paidOrder = new OrderVO();
        paidOrder.setOrderNo("ORDER-API-001");
        paidOrder.setStatus(1);
        paidOrder.setStatusText("已支付");
        paidOrder.setSeats("5排6座,5排7座");
        paidOrder.setSeatCount(2);
        paidOrder.setTotalPrice(new BigDecimal("96.00"));
        paidOrder.setTicketCode("12345678");
        when(orderService.getByOrderNo("ORDER-API-001")).thenReturn(paidOrder);
        when(orderService.pickTicket("12345678")).thenReturn(paidOrder);
    }

    @Test
    void loginCreatePayPickAndRefundExposeACompleteApiContract() throws Exception {
        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(login("buyer", "secret"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.token").value(token))
                .andExpect(jsonPath("$.data.userInfo.role").value("USER"));

        mockMvc.perform(post("/order")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"showtimeId":101,"seats":["5排6座","5排7座"],"remark":"API flow"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").value("ORDER-API-001"));

        mockMvc.perform(post("/order/ORDER-API-001/pay")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("支付成功"));

        mockMvc.perform(get("/order/ORDER-API-001")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value(1))
                .andExpect(jsonPath("$.data.ticketCode").value("12345678"));

        mockMvc.perform(post("/order/pick")
                        .header("Authorization", "Bearer " + token)
                        .param("ticketCode", "12345678"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("取票成功"));

        mockMvc.perform(post("/order/ORDER-API-001/refund")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("退票申请已受理"));

        verify(orderService).payOrder("ORDER-API-001", null);
        verify(orderService).pickTicket("12345678");
        verify(orderService).refundOrder("ORDER-API-001");
    }

    @Test
    void orderStateConflictUsesHttp409AndKeepsTheUnifiedBody() throws Exception {
        doThrow(new BusinessException(409, "订单状态已变化，支付未完成"))
                .when(orderService).payOrder("ORDER-CONFLICT", null);

        mockMvc.perform(post("/order/ORDER-CONFLICT/pay")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(409))
                .andExpect(jsonPath("$.message").value("订单状态已变化，支付未完成"));
    }

    private LoginRequest login(String username, String password) {
        LoginRequest request = new LoginRequest();
        request.setUsername(username);
        request.setPassword(password);
        request.setCaptchaId("test-captcha");
        request.setCaptcha("ABCD");
        return request;
    }
}
