package com.catagenda.cat;

import com.catagenda.common.NotFoundException;
import com.catagenda.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CatServiceTest {

    @Mock
    private CatRepository catRepository;

    private CatService catService;

    private User ownerA;
    private User ownerB;

    @BeforeEach
    void setUp() {
        catService = new CatService(catRepository);
        ownerA = userWithId(1L, "a@example.com");
        ownerB = userWithId(2L, "b@example.com");
    }

    @Test
    void createSavesACatOwnedByTheGivenUser() {
        CatForm form = new CatForm("Whiskers", null, null, null);
        when(catRepository.save(any(Cat.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Cat created = catService.create(ownerA, form);

        assertThat(created.getName()).isEqualTo("Whiskers");
        assertThat(created.getOwner()).isEqualTo(ownerA);
    }

    @Test
    void listForOwnerDelegatesToRepositoryScopedByOwner() {
        Cat cat = new Cat(ownerA, "Whiskers");
        when(catRepository.findByOwner(ownerA)).thenReturn(List.of(cat));

        List<Cat> cats = catService.listForOwner(ownerA);

        assertThat(cats).containsExactly(cat);
    }

    @Test
    void getOwnedOrThrowReturnsCatWhenOwnedByRequester() {
        Cat cat = new Cat(ownerA, "Whiskers");
        when(catRepository.findByIdAndOwner(10L, ownerA)).thenReturn(Optional.of(cat));

        assertThat(catService.getOwnedOrThrow(10L, ownerA)).isEqualTo(cat);
    }

    @Test
    void getOwnedOrThrowRejectsAnotherUsersCat() {
        // Simulates the real repository query: findByIdAndOwner filters by
        // owner, so a cat belonging to ownerA returns empty for ownerB.
        when(catRepository.findByIdAndOwner(10L, ownerB)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> catService.getOwnedOrThrow(10L, ownerB))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void updateAppliesChangesOnlyWhenCatIsOwnedByRequester() {
        Cat cat = new Cat(ownerA, "Whiskers");
        when(catRepository.findByIdAndOwner(10L, ownerA)).thenReturn(Optional.of(cat));

        CatForm form = new CatForm("Sir Whiskers", 3, Sex.MALE, true, true);
        Cat updated = catService.update(10L, ownerA, form);

        assertThat(updated.getName()).isEqualTo("Sir Whiskers");
        assertThat(updated.getAge()).isEqualTo(3);
        assertThat(updated.getSex()).isEqualTo(Sex.MALE);
        assertThat(updated.getSpayedNeutered()).isTrue();
        assertThat(updated.getOutdoorAccess()).isTrue();
    }

    @Test
    void createWithNullOutdoorAccessLeavesItUnsetRatherThanThrowing() {
        // T1.4: outdoorAccess == null behaves like any other unset optional
        // field (age, sex, spayedNeutered) — no default, no exception.
        CatForm form = new CatForm("Whiskers", null, null, null, null);
        when(catRepository.save(any(Cat.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Cat created = catService.create(ownerA, form);

        assertThat(created.getOutdoorAccess()).isNull();
    }

    @Test
    void updateCanExplicitlySetOutdoorAccessToFalse() {
        Cat cat = new Cat(ownerA, "Whiskers");
        cat.setOutdoorAccess(true);
        when(catRepository.findByIdAndOwner(10L, ownerA)).thenReturn(Optional.of(cat));

        CatForm form = new CatForm("Whiskers", null, null, null, false);
        Cat updated = catService.update(10L, ownerA, form);

        assertThat(updated.getOutdoorAccess()).isFalse();
    }

    @Test
    void updateRejectsAnotherUsersCat() {
        when(catRepository.findByIdAndOwner(10L, ownerB)).thenReturn(Optional.empty());

        CatForm form = new CatForm("Hijacked", null, null, null);

        assertThatThrownBy(() -> catService.update(10L, ownerB, form))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void deleteRemovesCatWhenOwnedByRequester() {
        Cat cat = new Cat(ownerA, "Whiskers");
        when(catRepository.findByIdAndOwner(10L, ownerA)).thenReturn(Optional.of(cat));

        catService.delete(10L, ownerA);

        verify(catRepository).delete(cat);
    }

    @Test
    void deleteRejectsAnotherUsersCat() {
        when(catRepository.findByIdAndOwner(10L, ownerB)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> catService.delete(10L, ownerB))
                .isInstanceOf(NotFoundException.class);

        verify(catRepository, never()).delete(any());
    }

    /**
     * User's constructor requires a persisted id to come from JPA
     * (GeneratedValue), so tests set it via reflection rather than adding a
     * test-only setter to production code.
     */
    private static User userWithId(Long id, String email) {
        User user = new User(email, "hash");
        try {
            Field idField = User.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(user, id);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
        return user;
    }
}
