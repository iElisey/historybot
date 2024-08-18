package org.elos.historybot.service;

import org.elos.historybot.model.User;
import org.elos.historybot.repository.UserRepository;
import org.springframework.stereotype.Service;

@Service
public class UserService {
    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public void createUser(Long chatId, Long userId) {
        User user = new User();
        user.setChatId(chatId);
        user.setUserId(userId);
        user.setSelectedTopic(1);
        userRepository.save(user);
    }

    public boolean existsByUserId(Long userId) {
        return userRepository.existsByUserId(userId);
    }


    public int nextTopic(Long userId) {
        User user = userRepository.findByUserId(userId);
        user.setSelectedTopic(user.getSelectedTopic() + 1);
        return userRepository.save(user).getSelectedTopic();
    }

    public User findByChatId(Long chatId) {
        return userRepository.findByChatId(chatId);
    }
    public User findByUserId(Long chatId) {
        return userRepository.findByUserId(chatId);
    }

    public void save(User user) {
        userRepository.save(user);
    }
}
