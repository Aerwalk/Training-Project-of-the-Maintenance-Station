package org.example.sto.service;

import org.example.sto.exception.AuthenticationException;
import org.example.sto.exception.ValidationException;
import org.example.sto.model.User;
import org.example.sto.repository.UserRepository;

import java.util.Locale;
import java.util.Optional;

/**
 * Аутентификация пользователей. Пароль проверяется в БД через pgcrypto/bcrypt.
 */
public final class AuthService {

    private final UserRepository userRepository;

    public AuthService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public User login(String username, char[] password) {
        if (username == null || username.isBlank()) {
            throw new ValidationException("Логин не может быть пустым");
        }
        if (password == null || password.length == 0) {
            throw new ValidationException("Пароль не может быть пустым");
        }

        Optional<User> user = userRepository.authenticate(
                username.trim().toLowerCase(Locale.ROOT), password);
        return user.orElseThrow(() -> new AuthenticationException("Неверный логин или пароль"));
    }
}
