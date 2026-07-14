package com.example.ragkb.repository;

import com.example.ragkb.model.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;
import java.util.List;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByUsername(String username);
    boolean existsByUsername(String username);

    /** 查询所有用户，按创建时间倒序（管理员用户列表） */
    List<User> findAllByOrderByCreatedAtDesc();
}
