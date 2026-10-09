package com.booking.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.booking.payment.domain.MockCards;
import com.booking.payment.domain.PaymentStatus;
import com.booking.platform.error.ApiException;

import org.junit.jupiter.api.Test;

class MockCardsTest {

    @Test
    void stripeStyleTestCardsBehaveAsDocumented() {
        assertThat(MockCards.charge("4242 4242 4242 4242").approved()).isTrue();
        assertThat(MockCards.charge("4000-0000-0000-0002").code()).isEqualTo("card_declined");
        assertThat(MockCards.charge("4000000000009995").code()).isEqualTo("insufficient_funds");
        assertThatThrownBy(() -> MockCards.charge("4242 4242 4242 4241")).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> MockCards.charge("not a card")).isInstanceOf(ApiException.class);
    }

    @Test
    void paymentStatusesOnlyMoveForward() {
        assertThat(PaymentStatus.PENDING.canMoveTo(PaymentStatus.SUCCEEDED)).isTrue();
        assertThat(PaymentStatus.SUCCEEDED.canMoveTo(PaymentStatus.PENDING)).isFalse();
        assertThat(PaymentStatus.REFUNDED.canMoveTo(PaymentStatus.SUCCEEDED)).isFalse();
    }
}
