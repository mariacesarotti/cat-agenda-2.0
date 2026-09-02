package com.catagenda.cat;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CheckupRecordRepository extends JpaRepository<CheckupRecord, Long> {

    List<CheckupRecord> findByCat(Cat cat);

    Optional<CheckupRecord> findByIdAndCat(Long id, Cat cat);

    // The reasoning layer's anchor point (T3.2): whichever checkup record is
    // currently the most recent for this cat, straight from the database
    // rather than sorting an already-loaded collection in Java — stays
    // correct automatically after any add/edit/delete (P1-13's AC).
    Optional<CheckupRecord> findFirstByCatOrderByVisitDateDesc(Cat cat);
}
