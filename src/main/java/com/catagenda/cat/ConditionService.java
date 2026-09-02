package com.catagenda.cat;

import com.catagenda.common.NotFoundException;
import com.catagenda.user.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * A Condition has no meaning outside its parent Cat, so ownership is
 * always checked by first resolving the Cat through {@link CatService}
 * (which itself scopes to the owner) and only then touching the
 * Condition — the same "no bare id lookup" rule as T2.3, one level down.
 */
@Service
public class ConditionService {

    private final CatService catService;
    private final ConditionRepository conditionRepository;

    public ConditionService(CatService catService, ConditionRepository conditionRepository) {
        this.catService = catService;
        this.conditionRepository = conditionRepository;
    }

    @Transactional
    public Condition add(User owner, Long catId, ConditionForm form) {
        Cat cat = catService.getOwnedOrThrow(catId, owner);
        Condition condition = new Condition(cat, form.getName(), form.getNotes());
        return conditionRepository.save(condition);
    }

    @Transactional
    public Condition update(User owner, Long catId, Long conditionId, ConditionForm form) {
        Condition condition = getOwnedOrThrow(owner, catId, conditionId);
        condition.setName(form.getName());
        condition.setNotes(form.getNotes());
        return condition;
    }

    @Transactional
    public void delete(User owner, Long catId, Long conditionId) {
        Condition condition = getOwnedOrThrow(owner, catId, conditionId);
        conditionRepository.delete(condition);
    }

    @Transactional(readOnly = true)
    public Condition getOwnedOrThrow(User owner, Long catId, Long conditionId) {
        Cat cat = catService.getOwnedOrThrow(catId, owner);
        return conditionRepository.findByIdAndCat(conditionId, cat)
                .orElseThrow(() -> new NotFoundException("No condition with id " + conditionId + " on this cat."));
    }
}
