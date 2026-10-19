package tn.iteam.catalogueservice.service;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import tn.iteam.catalogueservice.interfaces.FileStorageService;

import java.io.IOException;
import java.nio.file.*;
import java.util.UUID;

@Service
public class FileStorageServiceImpl implements FileStorageService {
    @Value("${app.upload.dir}")
    private String uploadDirRoot;
    @Override
    public String storeFile(MultipartFile file, String subDirectory) throws IOException {
        // Create subfolder (e.g. "products")
        Path uploadPath = Paths.get(uploadDirRoot, subDirectory);
        if (!Files.exists(uploadPath)) {
            Files.createDirectories(uploadPath);
        }
        // Generate unique filename to avoid conflicts
        String originalFilename = file.getOriginalFilename();
        String extension = (originalFilename != null && originalFilename.contains("."))
                ? originalFilename.substring(originalFilename.lastIndexOf("."))
                : "";
        String uniqueFilename = UUID.randomUUID() + extension;

        // Save the file
        Path filePath = uploadPath.resolve(uniqueFilename);
        Files.copy(file.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);

        return uniqueFilename;
    }

    @Override
    public Path load(String filename, String subDirectory) {
        return Paths.get(uploadDirRoot, subDirectory).resolve(filename);
    }

    @Override
    public void delete(String filename, String subDirectory) throws IOException {
        Path filePath = load(filename, subDirectory);
        Files.deleteIfExists(filePath);
    }
}
