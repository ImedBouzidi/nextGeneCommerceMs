package tn.iteam.authregisterservice.repos;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;
import tn.iteam.authregisterservice.model.User;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);

    /**
     * Moves an already persisted account to the ADMIN single-table-inheritance subtype.
     * The discriminator column cannot be changed through the JPA entity, so it is updated natively.
     */
    @Modifying
    @Transactional
    @Query(value = "UPDATE users SET user_type = 'ADMIN' WHERE email = :email", nativeQuery = true)
    int promoteToAdmin(@Param("email") String email);
}
