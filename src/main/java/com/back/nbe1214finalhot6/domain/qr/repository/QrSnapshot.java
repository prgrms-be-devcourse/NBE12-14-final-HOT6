package com.back.nbe1214finalhot6.domain.qr.repository;

import com.back.nbe1214finalhot6.domain.qr.entity.QrStatus;

public record QrSnapshot(Long merchantId, Long amount, QrStatus status) {}
