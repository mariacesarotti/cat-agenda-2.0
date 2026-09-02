package com.catagenda.cat;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ConditionRepository extends JpaRepository<Condition, Long> {

    List<Condition> findByCat(Cat cat);

    Optional<Condition> findByIdAndCat(Long id, Cat cat);
}
