CREATE TABLE restaurant (
  id VARCHAR(36) PRIMARY KEY,
  owner_id VARCHAR(36) NOT NULL,
  name VARCHAR(120) NOT NULL,
  cuisine VARCHAR(60) NOT NULL,
  average_price DECIMAL(12,2),
  address VARCHAR(240),
  distance_km DECIMAL(8,2),
  rating DECIMAL(2,1),
  recommended_dishes VARCHAR(500),
  avoided_dishes VARCHAR(500),
  last_visited_date VARCHAR(10),
  visit_count INT NOT NULL DEFAULT 0,
  note VARCHAR(1000),
  created_at TIMESTAMP(3) NOT NULL,
  updated_at TIMESTAMP(3) NOT NULL,
  INDEX idx_restaurant_owner_cuisine (owner_id, cuisine)
);
CREATE TABLE restaurant_tag (
  restaurant_id VARCHAR(36) NOT NULL,
  tag VARCHAR(64) NOT NULL,
  PRIMARY KEY (restaurant_id, tag),
  CONSTRAINT fk_restaurant_tag FOREIGN KEY (restaurant_id) REFERENCES restaurant(id) ON DELETE CASCADE
);
CREATE TABLE leisure_place (
  id VARCHAR(36) PRIMARY KEY,
  owner_id VARCHAR(36) NOT NULL,
  name VARCHAR(120) NOT NULL,
  category VARCHAR(32) NOT NULL,
  address VARCHAR(240),
  distance_km DECIMAL(8,2),
  average_cost DECIMAL(12,2),
  recommendation INT,
  visit_status VARCHAR(8) NOT NULL,
  last_visited_date VARCHAR(10),
  visit_count INT NOT NULL DEFAULT 0,
  note VARCHAR(1000),
  created_at TIMESTAMP(3) NOT NULL,
  updated_at TIMESTAMP(3) NOT NULL,
  INDEX idx_place_owner_category (owner_id, category)
);
CREATE TABLE leisure_place_tag (
  place_id VARCHAR(36) NOT NULL,
  tag VARCHAR(64) NOT NULL,
  PRIMARY KEY (place_id, tag),
  CONSTRAINT fk_place_tag FOREIGN KEY (place_id) REFERENCES leisure_place(id) ON DELETE CASCADE
);
ALTER TABLE image_asset ADD COLUMN restaurant_id VARCHAR(36);
ALTER TABLE image_asset ADD COLUMN place_id VARCHAR(36);
ALTER TABLE image_asset ADD CONSTRAINT fk_image_restaurant FOREIGN KEY (restaurant_id) REFERENCES restaurant(id) ON DELETE SET NULL;
ALTER TABLE image_asset ADD CONSTRAINT fk_image_place FOREIGN KEY (place_id) REFERENCES leisure_place(id) ON DELETE SET NULL;
CREATE INDEX idx_image_restaurant ON image_asset(restaurant_id);
CREATE INDEX idx_image_place ON image_asset(place_id);
