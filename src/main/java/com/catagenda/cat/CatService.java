package com.catagenda.cat;

import com.catagenda.common.NotFoundException;
import com.catagenda.user.User;
import org.hibernate.Hibernate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Every read/write here takes the requesting {@link User} and scopes the
 * query or check to it. No method looks up a Cat by id alone — that's the
 * core security rule for this feature (T2.3): a user can only ever reach
 * cats they own, and a cat owned by someone else looks exactly like a cat
 * that doesn't exist (see {@link NotFoundException}).
 */
@Service
public class CatService {

    private final CatRepository catRepository;

    public CatService(CatRepository catRepository) {
        this.catRepository = catRepository;
    }

    @Transactional
    public Cat create(User owner, CatForm form) {
        Cat cat = new Cat(owner, form.getName());
        applyForm(cat, form);
        return catRepository.save(cat);
    }

    @Transactional(readOnly = true)
    public List<Cat> listForOwner(User owner) {
        return catRepository.findByOwner(owner);
    }

    @Transactional(readOnly = true)
    public Cat getOwnedOrThrow(Long id, User owner) {
        return catRepository.findByIdAndOwner(id, owner)
                .orElseThrow(() -> new NotFoundException("No cat with id " + id + " for this user."));
    }

    /**
     * Same as {@link #getOwnedOrThrow}, plus the two child collections the
     * profile view needs. With {@code open-in-view: false} (see
     * application.yml), the Hibernate session closes as soon as this method
     * returns — the Thymeleaf template renders *after* that, outside any
     * transaction, so a lazy `@OneToMany` touched only in the view would
     * throw `LazyInitializationException`. Loading them here, while the
     * session is still open, keeps that boundary explicit in the service
     * layer instead of silently depending on open-in-view.
     */
    @Transactional(readOnly = true)
    public Cat getOwnedWithDetailsOrThrow(Long id, User owner) {
        Cat cat = getOwnedOrThrow(id, owner);
        Hibernate.initialize(cat.getConditions());
        Hibernate.initialize(cat.getVaccinationRecords());
        Hibernate.initialize(cat.getCheckupRecords());
        return cat;
    }

    @Transactional
    public Cat update(Long id, User owner, CatForm form) {
        Cat cat = getOwnedOrThrow(id, owner);
        applyForm(cat, form);
        return cat;
    }

    @Transactional
    public void delete(Long id, User owner) {
        Cat cat = getOwnedOrThrow(id, owner);
        catRepository.delete(cat);
    }

    /**
     * P6-3 (ADR 0009) — flips the Phase 1 status flag to true when a
     * spay/neuter procedure is marked completed. Deliberately just this one
     * setter call, not a new workflow method: {@code cat} is expected to
     * already be an owner-scoped, managed entity (see
     * {@code SpayNeuterService#markCompleted}), so this stays the small,
     * additive change ADR 0009 called for — {@code CatService} doesn't grow
     * spay/neuter's own 3-state workflow logic, that lives entirely in
     * {@code SpayNeuterService}.
     */
    @Transactional
    public void markSpayedNeutered(Cat cat) {
        cat.setSpayedNeutered(true);
    }

    private void applyForm(Cat cat, CatForm form) {
        cat.setName(form.getName());
        cat.setAge(form.getAge());
        cat.setSex(form.getSex());
        cat.setSpayedNeutered(form.getSpayedNeutered());
        cat.setOutdoorAccess(form.getOutdoorAccess());
    }
}
