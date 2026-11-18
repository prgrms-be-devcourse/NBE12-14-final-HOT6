package com.back.nbe1214finalhot6.domain.qr.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record QrCreateRequest(@NotNull @Positive Long amount) {}
