package com.back.nbe1214finalhot6.setting.repository;

import com.back.nbe1214finalhot6.setting.SettingKey;
import com.back.nbe1214finalhot6.setting.entity.OperatorSetting;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OperatorSettingRepository extends JpaRepository<OperatorSetting, SettingKey> {}
