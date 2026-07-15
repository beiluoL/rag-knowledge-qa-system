-- 持久化结构化学习路径：路径 → 有序节点 → 用户节点进度。
CREATE TABLE learning_paths (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id),
    title VARCHAR(300) NOT NULL,
    description TEXT,
    knowledge_base_id BIGINT,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_learning_paths_user ON learning_paths(user_id);

CREATE TABLE learning_path_nodes (
    id BIGSERIAL PRIMARY KEY,
    path_id BIGINT NOT NULL REFERENCES learning_paths(id) ON DELETE CASCADE,
    title VARCHAR(300) NOT NULL,
    description TEXT,
    order_index INT NOT NULL DEFAULT 0,
    node_type VARCHAR(30) NOT NULL DEFAULT 'DOCUMENT',
    ref_json TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_lp_nodes_path ON learning_path_nodes(path_id);

CREATE TABLE learning_path_node_progress (
    id BIGSERIAL PRIMARY KEY,
    node_id BIGINT NOT NULL REFERENCES learning_path_nodes(id) ON DELETE CASCADE,
    user_id BIGINT NOT NULL REFERENCES users(id),
    status VARCHAR(20) NOT NULL DEFAULT 'NOT_STARTED',
    score INT,
    completed_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    UNIQUE (node_id, user_id)
);
CREATE INDEX idx_lp_progress_node ON learning_path_node_progress(node_id);
CREATE INDEX idx_lp_progress_user ON learning_path_node_progress(user_id);
