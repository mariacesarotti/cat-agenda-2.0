package com.catagenda.supply;

import com.catagenda.user.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SupplyTrackerRepository extends JpaRepository<SupplyTracker, Long> {

    List<SupplyTracker> findByOwner(User owner);

    Optional<SupplyTracker> findByOwnerAndItemType(User owner, SupplyItemType itemType);
}
