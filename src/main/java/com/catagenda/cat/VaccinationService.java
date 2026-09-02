package com.catagenda.cat;

import com.catagenda.common.NotFoundException;
import com.catagenda.user.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Same ownership convention as ConditionService: every lookup goes through
 * CatService.getOwnedOrThrow first, so a VaccinationRecord is only ever
 * reachable via a cat the requesting user actually owns.
 */
@Service
public class VaccinationService {

    private final CatService catService;
    private final VaccinationRecordRepository vaccinationRecordRepository;

    public VaccinationService(CatService catService, VaccinationRecordRepository vaccinationRecordRepository) {
        this.catService = catService;
        this.vaccinationRecordRepository = vaccinationRecordRepository;
    }

    @Transactional
    public VaccinationRecord add(User owner, Long catId, VaccinationForm form) {
        validateOtherDescription(form);
        Cat cat = catService.getOwnedOrThrow(catId, owner);
        VaccinationRecord record = new VaccinationRecord(
                cat, form.getVaccineType(), form.getDateAdministered(), resolveOtherDescription(form));
        return vaccinationRecordRepository.save(record);
    }

    @Transactional
    public VaccinationRecord update(User owner, Long catId, Long recordId, VaccinationForm form) {
        validateOtherDescription(form);
        VaccinationRecord record = getOwnedOrThrow(owner, catId, recordId);
        record.setVaccineType(form.getVaccineType());
        record.setDateAdministered(form.getDateAdministered());
        record.setOtherDescription(resolveOtherDescription(form));
        return record;
    }

    @Transactional
    public void delete(User owner, Long catId, Long recordId) {
        VaccinationRecord record = getOwnedOrThrow(owner, catId, recordId);
        vaccinationRecordRepository.delete(record);
    }

    @Transactional(readOnly = true)
    public VaccinationRecord getOwnedOrThrow(User owner, Long catId, Long recordId) {
        Cat cat = catService.getOwnedOrThrow(catId, owner);
        return vaccinationRecordRepository.findByIdAndCat(recordId, cat)
                .orElseThrow(() -> new NotFoundException("No vaccination record with id " + recordId + " on this cat."));
    }

    private void validateOtherDescription(VaccinationForm form) {
        if (form.getVaccineType() == VaccineType.OTHER
                && (form.getOtherDescription() == null || form.getOtherDescription().isBlank())) {
            throw new MissingOtherDescriptionException();
        }
    }

    private String resolveOtherDescription(VaccinationForm form) {
        return form.getVaccineType() == VaccineType.OTHER ? form.getOtherDescription() : null;
    }
}
