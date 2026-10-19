package tn.iteam.authregisterservice.repos;
import org.springframework.data.jpa.repository.JpaRepository;
import tn.iteam.authregisterservice.model.Client;

import java.util.Optional;

public interface ClientRepository extends JpaRepository<Client, Long> {
    Optional<Client> findByEmail(String email);
}
