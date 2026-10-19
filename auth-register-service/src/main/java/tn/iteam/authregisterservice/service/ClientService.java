package tn.iteam.authregisterservice.service;

import org.springframework.stereotype.Service;
import tn.iteam.authregisterservice.model.Client;
import tn.iteam.authregisterservice.model.Role;
import tn.iteam.authregisterservice.repos.ClientRepository;

import java.util.List;
import java.util.Optional;

@Service
public class ClientService {
    private final ClientRepository clientRepository;

    public ClientService(ClientRepository clientRepository) {
        this.clientRepository = clientRepository;
    }

    public Client registerClient(Client client) {
        if (clientRepository.findByEmail(client.getEmail()).isPresent()) {
            throw new IllegalArgumentException("Email already in use: " + client.getEmail());
        }

        if (client.getRoles() == null || client.getRoles().isEmpty()) {
            client.setRoles(List.of(new Role("CLIENT")));
        }
        return clientRepository.save(client);
    }

    public Optional<Client> findByEmail(String email) {
        return clientRepository.findByEmail(email);
    }
}