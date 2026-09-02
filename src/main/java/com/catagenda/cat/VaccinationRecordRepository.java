package com.catagenda.cat;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface VaccinationRecordRepository extends JpaRepository<VaccinationRecord, Long> {

    List<VaccinationRecord> findByCat(Cat cat);

    List<VaccinationRecord> findByCatAndVaccineType(Cat cat, VaccineType vaccineType);

    Optional<VaccinationRecord> findByIdAndCat(Long id, Cat cat);
}
