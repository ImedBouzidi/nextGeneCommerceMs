package tn.iteam.catalogueservice.interfaces;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.nio.file.Path;

public interface FileStorageService {
    String storeFile(MultipartFile file, String subDirectory) throws IOException;
    Path load(String filename, String subDirectory);
    void delete(String filename, String subDirectory) throws IOException;
}

