package com.omp.order.dto;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class OrderValidateDtoTest {

    @Test
    void 세_조건이_모두_유효할_때만_통과한다() {
        assertThat(new OrderValidateDto(true, true, true).isValid()).isTrue();
    }

    @Test
    void 하나라도_무효면_실패한다() {
        assertThat(new OrderValidateDto(false, true, true).isValid()).isFalse();
        assertThat(new OrderValidateDto(true, false, true).isValid()).isFalse();
        assertThat(new OrderValidateDto(true, true, false).isValid()).isFalse();
    }
}
