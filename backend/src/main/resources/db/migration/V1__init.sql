CREATE TABLE app_user (
  id VARCHAR(36) PRIMARY KEY,
  openid VARCHAR(128) NOT NULL UNIQUE,
  created_at TIMESTAMP(3) NOT NULL
);
CREATE TABLE app_session (
  token_hash VARCHAR(64) PRIMARY KEY,
  user_id VARCHAR(36) NOT NULL,
  expires_at TIMESTAMP(3) NOT NULL,
  CONSTRAINT fk_session_user FOREIGN KEY (user_id) REFERENCES app_user(id)
);
CREATE TABLE fragment (
  id VARCHAR(36) PRIMARY KEY,
  owner_id VARCHAR(36) NOT NULL,
  occurred_at VARCHAR(19) NOT NULL,
  content TEXT NOT NULL,
  created_at TIMESTAMP(3) NOT NULL,
  updated_at TIMESTAMP(3) NOT NULL,
  INDEX idx_fragment_owner_time (owner_id, occurred_at)
);
CREATE TABLE fragment_tag (
  fragment_id VARCHAR(36) NOT NULL,
  tag VARCHAR(64) NOT NULL,
  PRIMARY KEY (fragment_id, tag),
  CONSTRAINT fk_tag_fragment FOREIGN KEY (fragment_id) REFERENCES fragment(id) ON DELETE CASCADE
);
CREATE TABLE image_asset (
  id VARCHAR(36) PRIMARY KEY,
  owner_id VARCHAR(36) NOT NULL,
  fragment_id VARCHAR(36),
  content_type VARCHAR(64) NOT NULL,
  created_at TIMESTAMP(3) NOT NULL,
  CONSTRAINT fk_image_fragment FOREIGN KEY (fragment_id) REFERENCES fragment(id) ON DELETE SET NULL
);
CREATE TABLE vehicle_record (
  id VARCHAR(36) PRIMARY KEY,
  owner_id VARCHAR(36) NOT NULL,
  occurred_at VARCHAR(19) NOT NULL,
  category VARCHAR(32) NOT NULL,
  amount DECIMAL(12,2) NOT NULL,
  odometer DECIMAL(12,1),
  quantity DECIMAL(12,2),
  unit_price DECIMAL(12,2),
  location VARCHAR(200),
  note VARCHAR(1000),
  created_at TIMESTAMP(3) NOT NULL,
  updated_at TIMESTAMP(3) NOT NULL,
  INDEX idx_vehicle_owner_time (owner_id, occurred_at)
);
CREATE TABLE housing_record (
  id VARCHAR(36) PRIMARY KEY,
  owner_id VARCHAR(36) NOT NULL,
  occurred_at VARCHAR(19) NOT NULL,
  category VARCHAR(32) NOT NULL,
  amount DECIMAL(12,2) NOT NULL,
  billing_month VARCHAR(7) NOT NULL,
  note VARCHAR(1000),
  created_at TIMESTAMP(3) NOT NULL,
  updated_at TIMESTAMP(3) NOT NULL,
  INDEX idx_housing_owner_time (owner_id, occurred_at)
);
CREATE TABLE contact (
  id VARCHAR(36) PRIMARY KEY,
  owner_id VARCHAR(36) NOT NULL,
  name VARCHAR(80) NOT NULL,
  relation_name VARCHAR(80),
  note VARCHAR(1000),
  created_at TIMESTAMP(3) NOT NULL,
  updated_at TIMESTAMP(3) NOT NULL,
  INDEX idx_contact_owner (owner_id)
);
CREATE TABLE gift_record (
  id VARCHAR(36) PRIMARY KEY,
  owner_id VARCHAR(36) NOT NULL,
  contact_id VARCHAR(36) NOT NULL,
  occurred_at VARCHAR(19) NOT NULL,
  event_name VARCHAR(120) NOT NULL,
  direction VARCHAR(8) NOT NULL,
  gift_kind VARCHAR(8) NOT NULL,
  amount DECIMAL(12,2) NOT NULL,
  gift_name VARCHAR(120),
  note VARCHAR(1000),
  created_at TIMESTAMP(3) NOT NULL,
  updated_at TIMESTAMP(3) NOT NULL,
  INDEX idx_gift_owner_time (owner_id, occurred_at),
  CONSTRAINT fk_gift_contact FOREIGN KEY (contact_id) REFERENCES contact(id) ON DELETE CASCADE
);
