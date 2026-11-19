package com.back.nbe1214finalhot6.domain.qr.dto;

import com.back.nbe1214finalhot6.domain.qr.entity.QrCode;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record QrCreateRequest(@NotNull @Positive @Max(QrCode.MAX_AMOUNT) Long amount) {}
