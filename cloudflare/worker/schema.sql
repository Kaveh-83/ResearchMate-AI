CREATE TABLE IF NOT EXISTS research_jobs (
  id TEXT PRIMARY KEY,
  query TEXT NOT NULL,
  year_from INTEGER NOT NULL,
  year_to INTEGER NOT NULL,
  max_papers INTEGER NOT NULL DEFAULT 8,
  status TEXT NOT NULL CHECK (status IN ('queued', 'running', 'completed', 'failed')),
  result_json TEXT,
  error_message TEXT,
  created_at TEXT NOT NULL,
  updated_at TEXT NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_research_jobs_status_created
ON research_jobs(status, created_at DESC);
