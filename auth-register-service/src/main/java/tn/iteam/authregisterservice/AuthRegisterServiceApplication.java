package tn.iteam.authregisterservice;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.transaction.annotation.Transactional;
import tn.iteam.authregisterservice.model.Role;
import tn.iteam.authregisterservice.repos.RoleRepository;

@SpringBootApplication
public class AuthRegisterServiceApplication implements CommandLineRunner {

    @Autowired
    private RoleRepository roleRepository;

    public static void main(String[] args) {
        SpringApplication.run(AuthRegisterServiceApplication.class, args);
    }

    @Override
    @Transactional
    public void run(String... args) throws Exception {
        // -----------------------------
        // 1️⃣ Seed roles locally
        // -----------------------------
        roleRepository.findByName("ADMIN")
                .orElseGet(() -> roleRepository.save(new Role(null, "ADMIN")));

        roleRepository.findByName("CLIENT")
                .orElseGet(() -> roleRepository.save(new Role(null, "CLIENT")));
    }
}
