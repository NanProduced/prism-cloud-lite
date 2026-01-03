-- V16__feedback_bug_reports_and_admin_mail.sql
-- User feedback (bug reports) + admin mail log

CREATE TABLE IF NOT EXISTS pcc_bug_report (
    id UUID PRIMARY KEY NOT NULL,
    user_id UUID NOT NULL,
    user_public_id VARCHAR(64) NOT NULL,
    user_email VARCHAR(160) NOT NULL,
    user_phone VARCHAR(40),
    contact_email VARCHAR(160),
    title VARCHAR(200) NOT NULL,
    content_html TEXT NOT NULL,
    content_plain TEXT NOT NULL,
    page_url VARCHAR(500),
    user_agent VARCHAR(500),
    attachments JSONB NOT NULL DEFAULT '[]'::jsonb,
    status VARCHAR(32) NOT NULL DEFAULT 'OPEN',
    admin_note TEXT,
    reply_subject VARCHAR(200),
    reply_html TEXT,
    replied_at TIMESTAMPTZ,
    replied_by VARCHAR(64),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_pcc_bug_report_user FOREIGN KEY (user_id)
        REFERENCES pcc_user_profile(id)
        ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_pcc_bug_report_user_time
    ON pcc_bug_report(user_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_pcc_bug_report_status_time
    ON pcc_bug_report(status, created_at DESC);

CREATE TABLE IF NOT EXISTS pcc_admin_mail_log (
    id UUID PRIMARY KEY NOT NULL,
    template VARCHAR(64) NOT NULL,
    to_email VARCHAR(160) NOT NULL,
    subject VARCHAR(200) NOT NULL,
    variables JSONB NOT NULL DEFAULT '{}'::jsonb,
    success BOOLEAN NOT NULL DEFAULT FALSE,
    error_message TEXT,
    actor_public_id VARCHAR(64),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_pcc_admin_mail_log_time
    ON pcc_admin_mail_log(created_at DESC);

