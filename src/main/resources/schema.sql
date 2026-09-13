-- =============================================================
-- Stock Portfolio Tracker - Database Schema
-- Supports both MySQL and SQLite syntax
-- =============================================================

-- -------------------------------------------------------
-- Table: holdings
-- Stores each stock holding in the portfolio
-- -------------------------------------------------------
CREATE TABLE IF NOT EXISTS holdings (
    id            INTEGER PRIMARY KEY AUTO_INCREMENT,
    symbol        VARCHAR(20)    NOT NULL UNIQUE,
    name          VARCHAR(255)   NOT NULL,
    quantity      DECIMAL(15, 4) NOT NULL,
    buy_price     DECIMAL(15, 4) NOT NULL,
    current_price DECIMAL(15, 4) NOT NULL DEFAULT 0.0000,
    last_updated  DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- -------------------------------------------------------
-- Table: alerts_log
-- Records every price-change alert that fires
-- -------------------------------------------------------
CREATE TABLE IF NOT EXISTS alerts_log (
    id              INTEGER PRIMARY KEY AUTO_INCREMENT,
    symbol          VARCHAR(20)    NOT NULL,
    previous_price  DECIMAL(15, 4) NOT NULL,
    current_price   DECIMAL(15, 4) NOT NULL,
    percent_change  DECIMAL(10, 4) NOT NULL,
    direction       VARCHAR(4)     NOT NULL,   -- 'UP' or 'DOWN'
    created_at      DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Index for fast lookup of recent alerts per symbol
CREATE INDEX IF NOT EXISTS idx_alerts_symbol ON alerts_log (symbol);
CREATE INDEX IF NOT EXISTS idx_alerts_created ON alerts_log (created_at);

-- =============================================================
-- MySQL-specific variant (comment out if using SQLite):
-- =============================================================
-- CREATE TABLE IF NOT EXISTS holdings (
--     id            INT            NOT NULL AUTO_INCREMENT PRIMARY KEY,
--     symbol        VARCHAR(20)    NOT NULL UNIQUE,
--     name          VARCHAR(255)   NOT NULL,
--     quantity      DECIMAL(15,4)  NOT NULL,
--     buy_price     DECIMAL(15,4)  NOT NULL,
--     current_price DECIMAL(15,4)  NOT NULL DEFAULT 0.0000,
--     last_updated  DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP
--                                          ON UPDATE CURRENT_TIMESTAMP
-- ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
--
-- CREATE TABLE IF NOT EXISTS alerts_log (
--     id             INT            NOT NULL AUTO_INCREMENT PRIMARY KEY,
--     symbol         VARCHAR(20)    NOT NULL,
--     previous_price DECIMAL(15,4)  NOT NULL,
--     current_price  DECIMAL(15,4)  NOT NULL,
--     percent_change DECIMAL(10,4)  NOT NULL,
--     direction      ENUM('UP','DOWN') NOT NULL,
--     created_at     DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP
-- ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
