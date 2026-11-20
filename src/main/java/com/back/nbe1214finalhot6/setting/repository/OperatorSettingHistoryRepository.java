package com.back.nbe1214finalhot6.setting.repository;

import com.back.nbe1214finalhot6.setting.SettingKey;
import com.back.nbe1214finalhot6.setting.entity.OperatorSettingHistory;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OperatorSettingHistoryRepository
    extends JpaRepository<OperatorSettingHistory, Long> {

  /** 해당 키의 변경 이력을 오래된 순으로 돌려준다. */
  List<OperatorSettingHistory> findAllBySettingKeyOrderByIdAsc(SettingKey settingKey);
}
