CREATE TABLE user_category (
  id VARCHAR(36) PRIMARY KEY,
  owner_id VARCHAR(36) NOT NULL,
  module_name VARCHAR(24) NOT NULL,
  name VARCHAR(64) NOT NULL,
  sort_order INT NOT NULL,
  created_at TIMESTAMP(3) NOT NULL,
  updated_at TIMESTAMP(3) NOT NULL,
  UNIQUE KEY uk_category_owner_module_name (owner_id, module_name, name),
  INDEX idx_category_owner_module (owner_id, module_name, sort_order)
);
CREATE TABLE user_tag (
  id VARCHAR(36) PRIMARY KEY,
  owner_id VARCHAR(36) NOT NULL,
  name VARCHAR(64) NOT NULL,
  created_at TIMESTAMP(3) NOT NULL,
  updated_at TIMESTAMP(3) NOT NULL,
  UNIQUE KEY uk_tag_owner_name (owner_id, name),
  INDEX idx_tag_owner_name (owner_id, name)
);
