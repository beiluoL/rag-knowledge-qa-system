-- V8: 学习系统（任务驱动成长 + 经验/等级 + 成就）
CREATE TABLE IF NOT EXISTS study_tasks (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    knowledge_base_id BIGINT REFERENCES knowledge_bases(id) ON DELETE SET NULL,
    title VARCHAR(300) NOT NULL,
    mode VARCHAR(30) NOT NULL,
    cycle VARCHAR(20),
    content TEXT,
    target_count INT NOT NULL DEFAULT 0,
    progress_count INT NOT NULL DEFAULT 0,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    due_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_study_tasks_user ON study_tasks(user_id);
CREATE INDEX IF NOT EXISTS idx_study_tasks_cycle ON study_tasks(cycle);

CREATE TABLE IF NOT EXISTS user_progress (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL UNIQUE REFERENCES users(id) ON DELETE CASCADE,
    xp BIGINT NOT NULL DEFAULT 0,
    level INT NOT NULL DEFAULT 1,
    current_streak INT NOT NULL DEFAULT 0,
    longest_streak INT NOT NULL DEFAULT 0,
    last_study_date DATE,
    cards_studied INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS achievements (
    code VARCHAR(50) NOT NULL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    description VARCHAR(300),
    icon VARCHAR(50),
    metric VARCHAR(30),
    threshold BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS user_achievements (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    achievement_code VARCHAR(50) NOT NULL REFERENCES achievements(code) ON DELETE CASCADE,
    unlocked_at TIMESTAMP NOT NULL DEFAULT NOW(),
    UNIQUE(user_id, achievement_code)
);

-- 默认成就定义
INSERT INTO achievements (code, name, description, icon, metric, threshold) VALUES
    ('first_step', '第一步', '完成首次学习', '🌱', 'cards', 1),
    ('card50', '小有积累', '累计学习 50 张卡片', '📚', 'cards', 50),
    ('card200', '博学多闻', '累计学习 200 张卡片', '📖', 'cards', 200),
    ('xp500', '渐入佳境', '累计获得 500 经验', '✨', 'xp', 500),
    ('xp2000', '学富五车', '累计获得 2000 经验', '🏆', 'xp', 2000),
    ('streak3', '三日之寒', '连续学习 3 天', '🔥', 'streak', 3),
    ('streak7', '持之以恒', '连续学习 7 天', '⚡', 'streak', 7),
    ('level5', '初露锋芒', '达到 5 级', '⭐', 'level', 5),
    ('level10', '登堂入室', '达到 10 级', '🌟', 'level', 10),
    ('level20', '一代宗师', '达到 20 级', '👑', 'level', 20)
ON CONFLICT (code) DO NOTHING;
