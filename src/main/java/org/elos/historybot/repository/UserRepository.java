package org.elos.historybot.repository;

import org.elos.historybot.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {
    User findByUserId(Long userId);

    boolean existsByUserId(Long userId);

    User findByChatId(Long chatId);
}