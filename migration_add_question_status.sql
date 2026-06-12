-- 新增 question_status JSONB 列，存储 SM-2 间隔重复算法状态
ALTER TABLE user_data ADD COLUMN IF NOT EXISTS question_status jsonb DEFAULT '{}';
