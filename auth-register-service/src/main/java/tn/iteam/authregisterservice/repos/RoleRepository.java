package tn.iteam.authregisterservice.repos;
import org.springframework.data.jpa.repository.JpaRepository;
import tn.iteam.authregisterservice.model.Role;

import java.util.Optional;

public interface RoleRepository extends JpaRepository<Role, Long> {
    Optional<Role> findByName(String name);
}
