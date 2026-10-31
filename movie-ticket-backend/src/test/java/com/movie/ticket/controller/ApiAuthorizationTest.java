package com.movie.ticket.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.movie.ticket.config.SecurityConfig;
import com.movie.ticket.entity.User;
import com.movie.ticket.exception.GlobalExceptionHandler;
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
import com.movie.ticket.service.HallService;
import com.movie.ticket.service.OrderService;
import com.movie.ticket.service.CouponAdminService;
import com.movie.ticket.service.UserProfileService;
import com.movie.ticket.utils.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest({UserController.class, OrderController.class, HallController.class, AdminCouponController.class, AssistantConversationController.class, AssistantPreferenceController.class, AssistantCheckoutController.class, PricingController.class, MarketingController.class, MarketingPublicController.class, ListingController.class})
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, JwtUtil.class, GlobalExceptionHandler.class})
@TestPropertySource(properties = {
        "jwt.secret=authorization-test-secret-must-be-at-least-thirty-two-bytes",
        "jwt.expiration=3600000"
})
@MockBean(classes = {
        CinemaMapper.class, CommentMapper.class, HallMapper.class, MovieMapper.class,
        ShowtimeMapper.class, TicketOrderMapper.class, UserCouponMapper.class,
        UserMovieBehaviorMapper.class, CouponCodeMapper.class, CouponCodeRedemptionMapper.class
})
class ApiAuthorizationTest {
    @MockBean private com.movie.ticket.service.listing.ListingService listing;
    @MockBean private com.movie.ticket.service.listing.ListingImportService listingImports;

    @Test void listingDraftsAreManagerOnlyAndTemplatesDownloadWithCorrectFormats() throws Exception {
        mockMvc.perform(get("/listing/drafts")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/listing/drafts").header("Authorization","Bearer "+userToken)).andExpect(status().isForbidden());
        mockMvc.perform(get("/listing/drafts").header("Authorization","Bearer "+cinemaAdminToken)).andExpect(status().isOk());
        mockMvc.perform(get("/listing/drafts").header("Authorization","Bearer "+adminToken)).andExpect(status().isOk());
        mockMvc.perform(get("/listing/templates/MOVIE").header("Authorization","Bearer "+cinemaAdminToken)).andExpect(status().isForbidden());
        mockMvc.perform(get("/listing/templates/MOVIE").header("Authorization","Bearer "+adminToken))
            .andExpect(status().isOk()).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().contentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
        mockMvc.perform(get("/listing/templates/MOVIE").param("format","csv").header("Authorization","Bearer "+adminToken)).andExpect(status().isOk());
        mockMvc.perform(get("/listing/templates/MOVIE").param("format","json").header("Authorization","Bearer "+adminToken)).andExpect(status().isOk());
        mockMvc.perform(get("/listing/templates/HALL").header("Authorization","Bearer "+cinemaAdminToken)).andExpect(status().isOk());
    }
    @MockBean private com.movie.ticket.service.marketing.MarketingService marketing;

    @Test void marketingAllowsManagersAndPublicPlacementsButProtectsDrafts() throws Exception {
        mockMvc.perform(get("/admin/marketing/campaigns")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/admin/marketing/campaigns").header("Authorization","Bearer "+userToken)).andExpect(status().isForbidden());
        mockMvc.perform(get("/admin/marketing/campaigns").header("Authorization","Bearer "+cinemaAdminToken)).andExpect(status().isOk());
        mockMvc.perform(get("/admin/marketing/campaigns").header("Authorization","Bearer "+adminToken)).andExpect(status().isOk());
        mockMvc.perform(get("/marketing/placements").param("slot","HOME")).andExpect(status().isOk());
        mockMvc.perform(get("/admin/marketing/assets/private/file")).andExpect(status().isUnauthorized());
        verify(marketing,org.mockito.Mockito.times(2)).campaigns();
    }

    @Test void marketingReviewAndPublicationArePlatformOnly() throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/admin/marketing/versions/v/review")
                .header("Authorization","Bearer "+cinemaAdminToken).contentType("application/json").content("{\"approve\":true}")).andExpect(status().isForbidden());
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/admin/marketing/publications/p/offline")
                .header("Authorization","Bearer "+cinemaAdminToken)).andExpect(status().isForbidden());
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/admin/marketing/versions/v/review")
                .header("Authorization","Bearer "+adminToken).contentType("application/json").content("{\"approve\":true}")).andExpect(status().isOk());
        verify(marketing).review(org.mockito.ArgumentMatchers.eq("v"),any());
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtUtil jwtUtil;

    @MockBean
    private UserMapper userMapper;

    @MockBean
    private OrderService orderService;

    @MockBean
    private HallService hallService;

    @MockBean
    private UserProfileService userProfileService;

    @MockBean
    private CouponAdminService couponAdminService;

    @MockBean
    private com.movie.ticket.service.AssistantConversationService conversations;

    @MockBean
    private com.movie.ticket.service.AssistantBookingService booking;

    @MockBean private com.movie.ticket.service.AssistantCheckoutService checkout;

    @Test void assistantCheckoutRequiresAuthenticationAndPreparationBindsTheJwtOwner() throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/assistant/checkout/prepare")
                .contentType("application/json").content("{\"criteria\":{\"showtimeId\":1,\"people\":2}}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/assistant/checkout/private/confirm"))
                .andExpect(status().isUnauthorized());
        org.mockito.Mockito.verifyNoInteractions(checkout);
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/assistant/checkout/prepare")
                .header("Authorization", "Bearer " + userToken).param("userId", "999")
                .contentType("application/json").content("{\"criteria\":{\"showtimeId\":1,\"people\":2},\"seats\":[\"3排3座\",\"3排4座\"]}"))
                .andExpect(status().isOk());
        verify(checkout).prepare(org.mockito.ArgumentMatchers.eq(1L), any(), org.mockito.ArgumentMatchers.eq(java.util.List.of("3排3座", "3排4座")));
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/assistant/checkout/prepare")
                .header("Authorization", "Bearer " + userToken).contentType("application/json").content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test void comparisonSupportsGuestsButOrderQuotesRequireLogin() throws Exception {
        mockMvc.perform(get("/pricing/compare").param("movieId", "2").param("people", "2"))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().string("Cache-Control", "no-store"));
        verify(booking).compare(org.mockito.ArgumentMatchers.isNull(), org.mockito.ArgumentMatchers.argThat(r -> r.getMovieId() == 2 && r.getPeople() == 2));
        mockMvc.perform(get("/pricing/orders/private/quote")).andExpect(status().isUnauthorized());
        verify(booking, never()).quote(any(), any(), any(), any(), org.mockito.ArgumentMatchers.anyBoolean());
    }

    @Test void pricingBindsOnlyAuthenticatedIdentityAndRejectsInvalidCounts() throws Exception {
        mockMvc.perform(get("/pricing/compare").header("Authorization", "Bearer " + userToken)
                        .param("movieId", "2").param("userId", "999")).andExpect(status().isOk());
        verify(booking).compare(org.mockito.ArgumentMatchers.eq(1L), any());
        mockMvc.perform(get("/pricing/compare").param("movieId", "2").param("people", "6"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/pricing/orders/OWN/quote").header("Authorization", "Bearer " + userToken)
                        .param("userId", "999")).andExpect(status().isOk());
        verify(booking).quote(1L, null, null, "OWN", true);
    }

    @Test
    void anonymousUserCannotReadOrDeleteSavedAssistantConversations() throws Exception {
        mockMvc.perform(get("/assistant/conversations")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/assistant/conversations/private")).andExpect(status().isUnauthorized());
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete("/assistant/conversations/private"))
                .andExpect(status().isUnauthorized());
        org.mockito.Mockito.verifyNoInteractions(conversations);
    }

    @Test void assistantPreferencesRequireLoginAndAlwaysBindAuthenticatedOwner() throws Exception {
        mockMvc.perform(get("/assistant/preferences")).andExpect(status().isUnauthorized());
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/assistant/preferences")
                .contentType("application/json").content("{}" )).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/assistant/preferences").header("Authorization", "Bearer " + userToken).param("userId", "999"))
                .andExpect(status().isOk());
        verify(conversations).preferences(1L);
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/assistant/preferences")
                .header("Authorization", "Bearer " + userToken).param("userId", "999").contentType("application/json")
                .content("{\"excludedGenres\":[\"恐怖\"]}" )).andExpect(status().isOk());
        verify(conversations).savePreferences(org.mockito.ArgumentMatchers.eq(1L), any());
    }

    private String userToken;
    private String cinemaAdminToken;
    private String adminToken;

    @BeforeEach
    void setUp() {
        userToken = jwtUtil.generateToken(1L, "user", "USER");
        cinemaAdminToken = jwtUtil.generateToken(2L, "cinema-admin", "CINEMA_ADMIN");
        adminToken = jwtUtil.generateToken(3L, "admin", "ADMIN");
        for (long id = 1; id <= 3; id++) {
            User user = new User(); user.setId(id); user.setUsername("account-" + id); user.setStatus(1);
            user.setRole(id == 1 ? "USER" : id == 2 ? "CINEMA_ADMIN" : "ADMIN");
            if (id == 2) user.setCinemaId(10L);
            when(userMapper.selectById(id)).thenReturn(user);
        }
    }

    @Test void databaseRoleAndStatusOverrideAnOldAdministratorToken() throws Exception {
        User changed = new User(); changed.setId(3L); changed.setUsername("admin"); changed.setStatus(1); changed.setRole("USER");
        when(userMapper.selectById(3L)).thenReturn(changed);
        mockMvc.perform(get("/user/page").header("Authorization", "Bearer " + adminToken)).andExpect(status().isForbidden());
        changed.setStatus(0);
        mockMvc.perform(get("/order/my").header("Authorization", "Bearer " + adminToken)).andExpect(status().isUnauthorized());
        when(userMapper.selectById(3L)).thenReturn(null);
        mockMvc.perform(get("/order/my").header("Authorization", "Bearer " + adminToken)).andExpect(status().isUnauthorized());
    }

    @Test
    void anonymousUserCannotAccessAuthenticatedOrderApi() throws Exception {
        mockMvc.perform(get("/order/my"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401))
                .andExpect(jsonPath("$.message").value("未登录或登录已过期"));
    }

    @Test
    void anonymousUserCanReadHallFilterData() throws Exception {
        when(hallService.getPage(1, 200, null)).thenReturn(new Page<>(1, 200));

        mockMvc.perform(get("/hall/page").param("page", "1").param("size", "200"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.records").isArray());
    }

    @Test
    void regularUserCannotAccessOrderManagementApi() throws Exception {
        mockMvc.perform(get("/order/page")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403))
                .andExpect(jsonPath("$.message").value("权限不足"));

        verify(orderService, never()).getPage(any(), any(), any(), any());
    }

    @Test
    void cinemaAdminCannotCrossThePlatformAdminBoundary() throws Exception {
        mockMvc.perform(get("/user/page")
                        .header("Authorization", "Bearer " + cinemaAdminToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403))
                .andExpect(jsonPath("$.message").value("权限不足"));

        verify(userMapper, never()).selectPage(any(), any());
    }

    @Test
    void platformAdminCanAccessUserManagementApi() throws Exception {
        when(userMapper.selectPage(any(), any())).thenReturn(new Page<User>(1, 10));

        mockMvc.perform(get("/user/page")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.records").isArray());
    }

    @Test
    void onlyPlatformAdminCanAccessCouponCodeManagement() throws Exception {
        mockMvc.perform(get("/admin/coupon-codes")
                        .header("Authorization", "Bearer " + cinemaAdminToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("权限不足"));

        when(couponAdminService.getPage(1, 10, null, null)).thenReturn(new Page<>(1, 10));
        mockMvc.perform(get("/admin/coupon-codes")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records").isArray());
    }
}
