package com.infernokun.amaterasu.services;

import com.infernokun.amaterasu.models.entities.User;
import com.infernokun.amaterasu.repositories.UserRepository;
import com.infernokun.amaterasu.services.base.BaseService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class UserService extends BaseService {
    private final UserRepository userRepository;

    @Autowired
    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    // Retrieve all users
    public List<User> findAllUsers() {
        return userRepository.findAll();
    }

    // Create a new user
    public User createUser(User user) {
        return userRepository.save(user);
    }

    // Create multiple users
    public List<User> createManyUsers(List<User> users) {
        return userRepository.saveAll(users);
    }

    // Delete a user by ID
    public boolean deleteUser(String id) {
        try {
            userRepository.deleteById(id);
            return true; // Deletion successful
        } catch (Exception e) {
            return false; // Deletion failed (e.g., user not found)
        }
    }

    // Update an existing user
    public User updateUser(String id, User user) {
        Optional<User> existingUserOpt = userRepository.findById(id);
        if (existingUserOpt.isPresent()) {
            User existingUser = existingUserOpt.get();
            // Update fields as necessary
            existingUser.setUsername(user.getUsername());
            existingUser.setTeam(user.getTeam()); // Assuming User has a Team field
            // Add other fields to update as needed
            return userRepository.save(existingUser); // Save the updated user
        }
        return null; // User not found
    }

    public Optional<User> findUserById(String userId) {
        return this.userRepository.findById(userId);
    }
}
