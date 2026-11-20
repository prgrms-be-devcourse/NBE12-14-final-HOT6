package com.back.nbe1214finalhot6.setting.repository;

import com.back.nbe1214finalhot6.setting.SettingKey;
import com.back.nbe1214finalhot6.setting.entity.OperatorSetting;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

public interface OperatorSettingRepository extends JpaRepository<OperatorSetting, SettingKey> {

  /**
   * 행을 잠그고 읽는다. 같은 키를 동시에 바꿀 때 변경 전 값이 낡은 값으로 이력에 남지 않게 한다. 행이 아직 없으면 잠글 것이 없다.
   */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  Optional<OperatorSetting> findWithLockBySettingKey(SettingKey settingKey);
}
