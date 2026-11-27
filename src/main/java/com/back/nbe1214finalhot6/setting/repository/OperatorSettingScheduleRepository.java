package com.back.nbe1214finalhot6.setting.repository;

import com.back.nbe1214finalhot6.setting.SettingKey;
import com.back.nbe1214finalhot6.setting.entity.OperatorSettingSchedule;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

public interface OperatorSettingScheduleRepository
    extends JpaRepository<OperatorSettingSchedule, SettingKey> {

  /** 행을 잠그고 읽는다. 같은 예약을 두 번 반영하지 않게 한다. */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  Optional<OperatorSettingSchedule> findWithLockBySettingKey(SettingKey settingKey);
}
