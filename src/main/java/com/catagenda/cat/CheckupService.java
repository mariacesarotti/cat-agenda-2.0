package com.catagenda.cat;

import com.catagenda.common.NotFoundException;
import com.catagenda.user.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Same ownership convention as VaccinationService: every lookup goes
 * through CatService.getOwnedOrThrow first, so a CheckupRecord is only ever
 * reachable via a cat the requesting user actually owns.
 */
@Service
public class CheckupService {

    private final CatService catService;
    private final CheckupRecordRepository checkupRecordRepository;

    public CheckupService(CatService catService, CheckupRecordRepository checkupRecordRepository) {
        this.catService = catService;
        this.checkupRecordRepository = checkupRecordRepository;
    }

    @Transactional
    public CheckupRecord add(User owner, Long catId, CheckupForm form) {
        Cat cat = catService.getOwnedOrThrow(catId, owner);
        CheckupRecord record = new CheckupRecord(cat, form.getVisitDate());
        return checkupRecordRepository.save(record);
    }

    @Transactional
    public CheckupRecord update(User owner, Long catId, Long recordId, CheckupForm form) {
        CheckupRecord record = getOwnedOrThrow(owner, catId, recordId);
        record.setVisitDate(form.getVisitDate());
        return record;
    }

    @Transactional
    public void delete(User owner, Long catId, Long recordId) {
        CheckupRecord record = getOwnedOrThrow(owner, catId, recordId);
        checkupRecordRepository.delete(record);
    }

    @Transactional(readOnly = true)
    public CheckupRecord getOwnedOrThrow(User owner, Long catId, Long recordId) {
        Cat cat = catService.getOwnedOrThrow(catId, owner);
        return checkupRecordRepository.findByIdAndCat(recordId, cat)
                .orElseThrow(() -> new NotFoundException("No checkup record with id " + recordId + " on this cat."));
    }

    /**
     * The reasoning layer's read-only entry point (T0.3/T3.2): the most
     * recently dated checkup for this cat, or empty if none has ever been
     * recorded. {@code cat} is expected to already be owner-scoped by the
     * caller (e.g. via getOwnedOrThrow), same convention as
     * VaccinationRecordRepository lookups elsewhere in this package.
     */
    @Transactional(readOnly = true)
    public Optional<CheckupRecord> getMostRecentForCat(Cat cat) {
        return checkupRecordRepository.findFirstByCatOrderByVisitDateDesc(cat);
    }
}
