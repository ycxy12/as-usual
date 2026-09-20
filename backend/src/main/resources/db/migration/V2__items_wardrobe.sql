CREATE TABLE personal_item (
  id VARCHAR(36) PRIMARY KEY,
  owner_id VARCHAR(36) NOT NULL,
  name VARCHAR(120) NOT NULL,
  category VARCHAR(32) NOT NULL,
  brand VARCHAR(100),
  model VARCHAR(100),
  purchase_date VARCHAR(10),
  purchase_price DECIMAL(12,2),
  current_value DECIMAL(12,2),
  warranty_until VARCHAR(10),
  status VARCHAR(16) NOT NULL,
  storage_location VARCHAR(200),
  note VARCHAR(1000),
  created_at TIMESTAMP(3) NOT NULL,
  updated_at TIMESTAMP(3) NOT NULL,
  INDEX idx_item_owner_category (owner_id, category)
);
CREATE TABLE personal_item_tag (
  item_id VARCHAR(36) NOT NULL,
  tag VARCHAR(64) NOT NULL,
  PRIMARY KEY (item_id, tag),
  CONSTRAINT fk_item_tag FOREIGN KEY (item_id) REFERENCES personal_item(id) ON DELETE CASCADE
);
CREATE TABLE garment (
  id VARCHAR(36) PRIMARY KEY,
  owner_id VARCHAR(36) NOT NULL,
  name VARCHAR(120) NOT NULL,
  category VARCHAR(16) NOT NULL,
  brand VARCHAR(100),
  color VARCHAR(60),
  size_label VARCHAR(60),
  season VARCHAR(16),
  purchase_date VARCHAR(10),
  purchase_price DECIMAL(12,2),
  wear_count INT NOT NULL DEFAULT 0,
  last_worn_date VARCHAR(10),
  status VARCHAR(16) NOT NULL,
  created_at TIMESTAMP(3) NOT NULL,
  updated_at TIMESTAMP(3) NOT NULL,
  INDEX idx_garment_owner_category (owner_id, category)
);
CREATE TABLE garment_tag (
  garment_id VARCHAR(36) NOT NULL,
  tag VARCHAR(64) NOT NULL,
  PRIMARY KEY (garment_id, tag),
  CONSTRAINT fk_garment_tag FOREIGN KEY (garment_id) REFERENCES garment(id) ON DELETE CASCADE
);
ALTER TABLE image_asset ADD COLUMN item_id VARCHAR(36);
ALTER TABLE image_asset ADD COLUMN garment_id VARCHAR(36);
ALTER TABLE image_asset ADD CONSTRAINT fk_image_item FOREIGN KEY (item_id) REFERENCES personal_item(id) ON DELETE SET NULL;
ALTER TABLE image_asset ADD CONSTRAINT fk_image_garment FOREIGN KEY (garment_id) REFERENCES garment(id) ON DELETE SET NULL;
CREATE INDEX idx_image_item ON image_asset(item_id);
CREATE INDEX idx_image_garment ON image_asset(garment_id);
