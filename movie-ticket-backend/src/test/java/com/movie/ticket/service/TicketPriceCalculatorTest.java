package com.movie.ticket.service;

import com.movie.ticket.exception.BusinessException;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.*;

class TicketPriceCalculatorTest {
    private BigDecimal n(String value) { return new BigDecimal(value); }

    @Test void couponThenPointsUsesPaymentRounding() {
        var price = TicketPriceCalculator.calculate(n("80"), n("6.99"), n("83.49"), n("500"));
        assertEquals(n("83.4"), price.pointsUsed());
        assertEquals(n("0.83"), price.pointsDiscount());
        assertEquals(n("72.18"), price.totalPrice());
        assertEquals(n("7.82"), price.discountAmount());
    }

    @Test void capsPointsDiscountAtOneYuanPerOrder() {
        assertEquals(n("100.0"), TicketPriceCalculator.maxPoints(n("80"), n("6.99")));
        var price = TicketPriceCalculator.calculate(n("80"), n("6.99"), n("100"), n("500"));
        assertEquals(n("1.00"), price.pointsDiscount());
        assertEquals(n("72.01"), price.totalPrice());
        assertThrows(BusinessException.class, () -> TicketPriceCalculator.calculate(n("80"), n("0"), n("100.1"), n("500")));
        assertEquals(n("50.0"), TicketPriceCalculator.maxPoints(n("1"), n("0.50")));
    }

    @Test void refusesInsufficientPointsAndOverpayment() {
        assertThrows(BusinessException.class, () -> TicketPriceCalculator.calculate(n("80"), n("0"), n("501"), n("500")));
        assertThrows(BusinessException.class, () -> TicketPriceCalculator.calculate(n("1"), n("0.50"), n("50.1"), n("500")));
        assertThrows(BusinessException.class, () -> TicketPriceCalculator.calculate(n("80"), n("0"), n("-0.01"), n("500")));
        assertEquals(n("0.00"), TicketPriceCalculator.calculate(n("1"), n("0.5"), n("50"), n("500")).totalPrice());
    }
}
