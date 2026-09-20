package com.richangji.profile;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/profile/categories")
public class CategoryController {
  private final CategoryService categories;

  public CategoryController(CategoryService categories) {
    this.categories = categories;
  }

  public record Input(@NotBlank String name) {}

  @GetMapping
  public List<CategoryService.Category> list(
      @RequestAttribute String ownerId, @RequestParam String module) {
    return categories.list(ownerId, module);
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @Transactional
  public CategoryService.Category create(
      @RequestAttribute String ownerId,
      @RequestParam String module,
      @Valid @RequestBody Input input) {
    return categories.create(ownerId, module, input.name());
  }

  @PutMapping("/{id}")
  @Transactional
  public CategoryService.Category rename(
      @RequestAttribute String ownerId, @PathVariable String id, @Valid @RequestBody Input input) {
    return categories.rename(ownerId, id, input.name());
  }

  @DeleteMapping("/{id}")
  @Transactional
  public void delete(@RequestAttribute String ownerId, @PathVariable String id) {
    categories.delete(ownerId, id);
  }
}
