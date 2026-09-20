package com.richangji.record;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

@Component
public class ImageFileStore {
  private final Path directory;

  public ImageFileStore(@Value("${app.image-dir}") String directory) {
    this.directory = Path.of(directory).toAbsolutePath().normalize();
  }

  public void save(String id, MultipartFile file) throws IOException {
    Files.createDirectories(directory);
    file.transferTo(directory.resolve(id));
  }

  public byte[] read(String id) throws IOException {
    return Files.readAllBytes(directory.resolve(id));
  }

  public void deleteNow(String id) {
    try {
      Files.deleteIfExists(directory.resolve(id));
    } catch (IOException ignored) {
    }
  }

  public void deleteAfterCommit(List<String> ids) {
    if (ids.isEmpty()) return;
    TransactionSynchronizationManager.registerSynchronization(
        new TransactionSynchronization() {
          @Override
          public void afterCommit() {
            ids.forEach(ImageFileStore.this::deleteNow);
          }
        });
  }
}
