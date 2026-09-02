CREATE TABLE app_user (
    id            BIGSERIAL PRIMARY KEY,
    email         VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    created_at    TIMESTAMP NOT NULL DEFAULT now()
);

CREATE TABLE cat (
    id               BIGSERIAL PRIMARY KEY,
    user_id          BIGINT NOT NULL REFERENCES app_user (id),
    name             VARCHAR(255) NOT NULL,
    age              INTEGER,
    sex              VARCHAR(20),
    spayed_neutered  BOOLEAN,
    created_at       TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX idx_cat_user_id ON cat (user_id);

CREATE TABLE condition (
    id      BIGSERIAL PRIMARY KEY,
    cat_id  BIGINT NOT NULL REFERENCES cat (id) ON DELETE CASCADE,
    name    VARCHAR(255) NOT NULL,
    notes   VARCHAR(2000)
);

CREATE INDEX idx_condition_cat_id ON condition (cat_id);

CREATE TABLE vaccination_record (
    id                 BIGSERIAL PRIMARY KEY,
    cat_id             BIGINT NOT NULL REFERENCES cat (id) ON DELETE CASCADE,
    vaccine_type       VARCHAR(30) NOT NULL,
    date_administered  DATE NOT NULL,
    other_description  VARCHAR(255)
);

CREATE INDEX idx_vaccination_record_cat_id ON vaccination_record (cat_id);
CREATE INDEX idx_vaccination_record_cat_id_type ON vaccination_record (cat_id, vaccine_type);
