package com.catagenda.spayneuter;

import com.catagenda.cat.Cat;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SpayNeuterPlanRepository extends JpaRepository<SpayNeuterPlan, Long> {

    Optional<SpayNeuterPlan> findByCat(Cat cat);
}
