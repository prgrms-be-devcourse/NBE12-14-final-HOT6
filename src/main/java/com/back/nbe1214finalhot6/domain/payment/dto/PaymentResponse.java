package com.back.nbe1214finalhot6.domain.payment.dto;

import com.back.nbe1214finalhot6.domain.payment.entity.Payment;
import com.back.nbe1214finalhot6.domain.payment.entity.PaymentStatus;

public record PaymentResponse(Long paymentId, PaymentStatus status) {

    public static PaymentResponse of(Payment payment) {
        return new PaymentResponse(payment.getId(), payment.getStatus());
    }
}
