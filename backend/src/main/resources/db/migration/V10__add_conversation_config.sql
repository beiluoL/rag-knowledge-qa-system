CREATE TABLE conversation_config (
  id BIGSERIAL PRIMARY KEY,
  ai_mode VARCHAR(32) NOT NULL DEFAULT 'offline',
  ai_framework VARCHAR(32) NOT NULL DEFAULT 'spring-ai',
  rag_visualization_enabled BOOLEAN NOT NULL DEFAULT true,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO conversation_config (id, ai_mode, ai_framework, rag_visualization_enabled) VALUES (1, 'offline', 'spring-ai', true);
