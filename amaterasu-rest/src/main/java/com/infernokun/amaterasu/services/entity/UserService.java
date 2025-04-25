package com.infernokun.amaterasu.services.entity;

import com.infernokun.amaterasu.exceptions.ResourceNotFoundException;
import com.infernokun.amaterasu.models.entities.Team;
import com.infernokun.amaterasu.models.entities.User;
import com.infernokun.amaterasu.repositories.TeamRepository;
import com.infernokun.amaterasu.repositories.UserRepository;
import com.infernokun.amaterasu.services.BaseService;
import com.infernokun.amaterasu.utils.dto.UserMapper;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class UserService extends BaseService implements UserDetailsService {
    private final UserRepository userRepository;
    private final TeamRepository teamRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserMapper userMapper;

    public UserService(UserRepository userRepository, TeamRepository teamRepository, PasswordEncoder passwordEncoder, UserMapper userMapper) {
        this.userRepository = userRepository;
        this.teamRepository = teamRepository;
        this.passwordEncoder = passwordEncoder;
        this.userMapper = userMapper;
    }

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

    /*@Cacheable(value = "users", key = "#userId")
    public User findUserById(String id) {
        return userRepository.findById(id).orElseThrow(() -> new UsernameNotFoundException("User not found!"));
    }*/

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

        Team team = teamRepository.findById(teamId)
                .orElseThrow(() -> new ResourceNotFoundException("Team ID not found"));

        user.setTeam(team);
        return userRepository.save(user);
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        return this.userRepository.findByUsername(username).orElseThrow(() -> new UsernameNotFoundException("user is not valid"));
    }
}
