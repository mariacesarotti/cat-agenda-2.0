package com.catagenda.cat;

import com.catagenda.user.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CatRepository extends JpaRepository<Cat, Long> {

    List<Cat> findByOwner(User owner);

    Optional<Cat> findByIdAndOwner(Long id, User owner);
}
