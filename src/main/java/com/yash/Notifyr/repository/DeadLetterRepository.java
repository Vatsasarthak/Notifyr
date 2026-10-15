package com.yash.Notifyr.repository;

import com.yash.Notifyr.entity.DeadLetter;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DeadLetterRepository extends JpaRepository<DeadLetter, Long> {

    List<DeadLetter> findByNotificationId(Long notificationId);
}
