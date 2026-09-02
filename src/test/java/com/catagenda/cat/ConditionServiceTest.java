package com.catagenda.cat;

import com.catagenda.common.NotFoundException;
import com.catagenda.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConditionServiceTest {

    @Mock
    private CatService catService;

    @Mock
    private ConditionRepository conditionRepository;

    private ConditionService conditionService;

    private User owner;
    private Cat cat;

    @BeforeEach
    void setUp() {
        conditionService = new ConditionService(catService, conditionRepository);
        owner = new User("owner@example.com", "hash");
        cat = new Cat(owner, "Whiskers");
    }

    @Test
    void addSavesConditionOnlyAfterConfirmingCatOwnership() {
        when(catService.getOwnedOrThrow(5L, owner)).thenReturn(cat);
        when(conditionRepository.save(any(Condition.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ConditionForm form = new ConditionForm("Diabetes", "Insulin twice daily");
        Condition saved = conditionService.add(owner, 5L, form);

        assertThat(saved.getName()).isEqualTo("Diabetes");
        assertThat(saved.getCat()).isEqualTo(cat);
    }

    @Test
    void addRejectsConditionOnACatTheUserDoesNotOwn() {
        when(catService.getOwnedOrThrow(5L, owner)).thenThrow(new NotFoundException("not found"));

        ConditionForm form = new ConditionForm("Diabetes", null);

        assertThatThrownBy(() -> conditionService.add(owner, 5L, form))
                .isInstanceOf(NotFoundException.class);

        verifyNoInteractions(conditionRepository);
    }

    @Test
    void updateChangesNameAndNotesWhenOwned() {
        Condition condition = new Condition(cat, "Diabetes", "old notes");
        when(catService.getOwnedOrThrow(5L, owner)).thenReturn(cat);
        when(conditionRepository.findByIdAndCat(7L, cat)).thenReturn(Optional.of(condition));

        ConditionForm form = new ConditionForm("Diabetes (managed)", "new notes");
        Condition updated = conditionService.update(owner, 5L, 7L, form);

        assertThat(updated.getName()).isEqualTo("Diabetes (managed)");
        assertThat(updated.getNotes()).isEqualTo("new notes");
    }

    @Test
    void deleteRemovesConditionWhenOwned() {
        Condition condition = new Condition(cat, "Diabetes", null);
        when(catService.getOwnedOrThrow(5L, owner)).thenReturn(cat);
        when(conditionRepository.findByIdAndCat(7L, cat)).thenReturn(Optional.of(condition));

        conditionService.delete(owner, 5L, 7L);

        verify(conditionRepository).delete(condition);
    }

    @Test
    void deleteRejectsWhenConditionDoesNotBelongToTheOwnedCat() {
        when(catService.getOwnedOrThrow(5L, owner)).thenReturn(cat);
        when(conditionRepository.findByIdAndCat(7L, cat)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> conditionService.delete(owner, 5L, 7L))
                .isInstanceOf(NotFoundException.class);

        verify(conditionRepository, never()).delete(any());
    }
}
