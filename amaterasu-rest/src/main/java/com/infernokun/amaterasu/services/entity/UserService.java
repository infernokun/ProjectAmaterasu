package com.infernokun.amaterasu.services.entity;

import com.infernokun.amaterasu.exceptions.ResourceNotFoundException;
import com.infernokun.amaterasu.models.entities.Team;
import com.infernokun.amaterasu.models.entities.User;
import com.infernokun.amaterasu.repositories.TeamRepository;
import com.infernokun.amaterasu.repositories.UserRepository;
import com.infernokun.amaterasu.services.BaseService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class UserService extends BaseService implements UserDetailsService {
    private final UserRepository userRepository;
    private final TeamRepository teamRepository;
    private final PasswordEncoder passwordEncoder;

    public boolean existsByUsername(String username) {
        return this.userRepository.existsByUsername(username);
    }

    public void registerUser(User user) {
        String encodedPassword  = this.passwordEncoder.encode(user.getPassword());
        user.setPassword(encodedPassword);

        this.userRepository.save(user);
    }

    public List<User> findAllUsers() {
        return userRepository.findAll();
    }

    public User findUserByUsername(String username) {
        return userRepository.findByUsernameIgnoreCase(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));
    }

    public User findUserById(String userId) {
        if (userId == null) {
            throw new IllegalArgumentException("User ID cannot be null");
        }
        return userRepository.findById(userId).orElseThrow(() -> new ResourceNotFoundException("User not found!"));
    }

    public User createUser(User user) {
        return userRepository.save(user);
    }


    public List<User> createManyUsers(List<User> users) {
        return userRepository.saveAll(users);
    }

    public User deleteUser(String id) {
        User deletedUser = findUserById(id);
        userRepository.deleteById(deletedUser.getId());
        return deletedUser;
    }

    public User updateUser(String id, User user) {
        User existingUser = findUserById(id);

        existingUser.setUsername(user.getUsername());
        existingUser.setTeam(user.getTeam());
        existingUser.setUpdatedAt(LocalDateTime.now());

        return userRepository.save(existingUser);

    }

    public User updateUserTeam(String userId, String teamId) {
        User user = findUserById(userId);

        Team team = teamId == null ? null : teamRepository.findById(teamId)
                .orElseThrow(() -> new ResourceNotFoundException("Team ID not found"));

        user.setTeam(team);
        return userRepository.save(user);
    }

    /** Moves every listed user, or none of them when one id or the team is unknown. */
    @Transactional
    public List<User> updateUsersTeam(List<String> userIds, String teamId) {
        if (userIds == null || userIds.isEmpty()) {
            throw new IllegalArgumentException("Pick at least one user to move");
        }
        Team team = teamId == null ? null : teamRepository.findById(teamId)
                .orElseThrow(() -> new ResourceNotFoundException("Team ID not found"));
        List<User> users = new ArrayList<>(new LinkedHashSet<>(userIds)).stream().map(this::findUserById).toList();
        users.forEach(user -> user.setTeam(team));
        return userRepository.saveAll(users);
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        return userRepository.findByUsernameIgnoreCase(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));
    }

    public boolean existsByUsernameIgnoreCase(String username) {
        return userRepository.existsByUsernameIgnoreCase(username);
    }

    public Optional<User> findByUsernameIgnoreCase(String username) {
        return userRepository.findByUsernameIgnoreCase(username);
    }
}
